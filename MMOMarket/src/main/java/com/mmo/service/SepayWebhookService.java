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

    @Transactional
    public void processSepayDepositWebhook(SepayWebhookPayload payload) {
        // Bước 1: Kiểm tra transferType
        if (!"in".equalsIgnoreCase(payload.getTransferType())) {
            log.info("[SePay] Bỏ qua giao dịch không phải tiền vào: {}", payload);
            return;
        }
        // Bước 2: Tìm User theo depositCode
        String depositCode = payload.getCode();
        User user = userRepository.findByDepositCodeAndIsDelete(depositCode, false);
        if (user == null) {
            log.error("[SePay] Không tìm thấy user với depositCode: {}", depositCode);
            return;
        }
        // Bước 3: Chống trùng lặp giao dịch
        Long sepayTransactionId = payload.getId();
        if (coinDepositRepository.existsBySepayTransactionId(sepayTransactionId)) {
            log.info("[SePay] Giao dịch đã xử lý: {}", sepayTransactionId);
            return;
        }
        // Bước 4: Ghi nhận CoinDeposit
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
            if (payload.getTransactionDate() != null) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                LocalDateTime ldt = LocalDateTime.parse(payload.getTransactionDate(), formatter);
                deposit.setTransactionDate(java.sql.Timestamp.valueOf(ldt));
            }
        } catch (Exception e) {
            log.warn("[SePay] Không parse được transactionDate: {}", payload.getTransactionDate());
        }
        deposit.setContent(payload.getContent());
        coinDepositRepository.save(deposit);
        // Bước 5: Cập nhật số dư User
        user.setCoins(user.getCoins() + payload.getTransferAmount());
        userRepository.save(user);
        // Bước 6: Gửi notification cho user
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setTitle("Coin deposit successful");
        notification.setContent("You have just been added " + payload.getTransferAmount() + " coins into account via transaction " + payload.getReferenceCode() + ".");
        notification.setStatus("Unread");
        notification.setCreatedAt(new Date());
        notificationRepository.save(notification);
        log.info("[SePay] Nạp coin thành công cho user {}: {} coins, đã gửi notification.", user.getId(), payload.getTransferAmount());
    }

    public User findUserByDepositCode(String depositCode) {
        return userRepository.findByDepositCodeAndIsDelete(depositCode, false);
    }

    public boolean isTransactionProcessed(Long sepayTransactionId) {
        return coinDepositRepository.existsBySepayTransactionId(sepayTransactionId);
    }
}
