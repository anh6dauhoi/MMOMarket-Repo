package com.mmo.service;

import com.mmo.dto.SepayWebhookPayload;
import com.mmo.entity.CoinDeposit;
import com.mmo.entity.Notification;
import com.mmo.entity.User;
import com.mmo.repository.CoinDepositRepository;
import com.mmo.repository.NotificationRepository;
import com.mmo.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

@Slf4j
@Service
@RequiredArgsConstructor
public class SepayWebhookService {
    private final UserRepository userRepository;
    private final CoinDepositRepository coinDepositRepository;
    private final NotificationRepository notificationRepository;

    /**
     * Xử lý webhook nạp tiền từ SePay
     *
     * @throws IllegalArgumentException Lỗi logic nghiệp vụ (không retry)
     * @throws Exception Lỗi hệ thống (nên retry)
     */
    @Transactional
    public void processSepayDepositWebhook(SepayWebhookPayload payload) {
        Long sepayTransactionId = payload.getId();

        // ===== BƯỚC 1: Kiểm tra transferType =====
        if (!"in".equalsIgnoreCase(payload.getTransferType())) {
            log.info("[SePay] Bỏ qua giao dịch không phải tiền vào: sepayId={}, type={}",
                    sepayTransactionId, payload.getTransferType());
            throw new IllegalArgumentException("Giao dịch không phải tiền vào (transferType != 'in')");
        }

        // ===== BƯỚC 2: Chống trùng lặp giao dịch (QUAN TRỌNG NHẤT) =====
        // Đây là cơ chế bảo vệ chính chống replay attack và retry của SePay
        if (coinDepositRepository.existsBySepayTransactionId(sepayTransactionId)) {
            log.info("[SePay] Giao dịch đã được xử lý trước đó: sepayId={}", sepayTransactionId);
            throw new IllegalArgumentException("Giao dịch đã được xử lý (duplicate sepayId: " + sepayTransactionId + ")");
        }

        // ===== BƯỚC 3: Tìm User theo depositCode =====
        String depositCode = payload.getCode();
        User user = userRepository.findByDepositCodeAndIsDelete(depositCode, false);
        if (user == null) {
            log.error("[SePay] Không tìm thấy user với depositCode: {}, sepayId={}",
                    depositCode, sepayTransactionId);
            throw new IllegalArgumentException("Không tìm thấy user với depositCode: " + depositCode);
        }

        log.info("[SePay] Tìm thấy user: userId={}, depositCode={}, amount={}",
                user.getId(), depositCode, payload.getTransferAmount());

        // ===== BƯỚC 4: Ghi nhận CoinDeposit =====
        try {
            CoinDeposit deposit = new CoinDeposit();
            deposit.setUser(user);
            deposit.setAmount(payload.getTransferAmount());
            deposit.setCoinsAdded(payload.getTransferAmount());
            deposit.setStatus("Completed");
            deposit.setSepayTransactionId(sepayTransactionId);
            deposit.setSepayReferenceCode(payload.getReferenceCode());
            deposit.setGateway(payload.getGateway());

            // Chuyển đổi transactionDate nếu có
            try {
                if (payload.getTransactionDate() != null && !payload.getTransactionDate().isEmpty()) {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    LocalDateTime ldt = LocalDateTime.parse(payload.getTransactionDate(), formatter);
                    deposit.setTransactionDate(java.sql.Timestamp.valueOf(ldt));
                }
            } catch (Exception e) {
                log.warn("[SePay] Không parse được transactionDate: {}, sẽ để null",
                        payload.getTransactionDate());
            }

            deposit.setContent(payload.getContent());
            coinDepositRepository.save(deposit);

            log.info("[SePay] Đã lưu CoinDeposit: depositId={}, sepayId={}",
                    deposit.getId(), sepayTransactionId);

        } catch (Exception e) {
            log.error("[SePay] Lỗi khi lưu CoinDeposit: sepayId={}", sepayTransactionId, e);
            throw new RuntimeException("Lỗi khi lưu CoinDeposit vào database", e);
        }

        // ===== BƯỚC 5: Cập nhật số dư User =====
        try {
            Long oldBalance = user.getCoins();
            Long newBalance = oldBalance + payload.getTransferAmount();
            user.setCoins(newBalance);
            userRepository.save(user);

            log.info("[SePay] Đã cập nhật số dư user: userId={}, {} -> {} (+{})",
                    user.getId(), oldBalance, newBalance, payload.getTransferAmount());

        } catch (Exception e) {
            log.error("[SePay] Lỗi khi cập nhật số dư user: userId={}, sepayId={}",
                    user.getId(), sepayTransactionId, e);
            throw new RuntimeException("Lỗi khi cập nhật số dư user", e);
        }

        // ===== BƯỚC 6: Gửi notification cho user =====
        // Notification gửi trong transaction riêng (REQUIRES_NEW)
        // để tránh rollback toàn bộ nếu notification lỗi
        try {
            sendNotificationInNewTransaction(user, payload.getTransferAmount(), payload.getReferenceCode());
            log.info("[SePay] Đã gửi notification cho user: userId={}", user.getId());
        } catch (Exception e) {
            // Notification lỗi không làm fail transaction chính
            log.error("[SePay] Lỗi khi gửi notification (không ảnh hưởng transaction): userId={}, sepayId={}",
                    user.getId(), sepayTransactionId, e);
        }

        log.info("[SePay] ===== Hoàn tất xử lý webhook: sepayId={}, userId={}, amount={} =====",
                sepayTransactionId, user.getId(), payload.getTransferAmount());
    }

    /**
     * Gửi notification trong transaction riêng biệt
     * Annotation REQUIRES_NEW tạo transaction mới, không phụ thuộc transaction cha
     * Nếu method này lỗi, transaction cha (processSepayDepositWebhook) vẫn commit
     */
    @org.springframework.transaction.annotation.Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendNotificationInNewTransaction(User user, Long amount, String referenceCode) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setTitle("Coin deposit successful");
        notification.setContent("You have just been added " + amount
                + " coins into account via transaction " + referenceCode + ".");
        notification.setStatus("Unread");
        notification.setCreatedAt(new Date());
        notificationRepository.save(notification);
    }

    public User findUserByDepositCode(String depositCode) {
        return userRepository.findByDepositCodeAndIsDelete(depositCode, false);
    }

    public boolean isTransactionProcessed(Long sepayTransactionId) {
        return coinDepositRepository.existsBySepayTransactionId(sepayTransactionId);
    }
}
