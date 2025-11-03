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
     * Process coin deposit webhook from SePay
     *
     * @throws IllegalArgumentException Business logic error (should not be retried)
     * @throws Exception System error (should be retried)
     */
    @Transactional
    public void processSepayDepositWebhook(SepayWebhookPayload payload) {
        Long sepayTransactionId = payload.getId();

        // ===== STEP 1: Check transferType =====
        if (!"in".equalsIgnoreCase(payload.getTransferType())) {
            log.info("[SePay] Skipping non-incoming transaction: sepayId={}, type={}",
                    sepayTransactionId, payload.getTransferType());
            throw new IllegalArgumentException("Transaction is not an incoming transfer (transferType != 'in')");
        }

        // ===== STEP 2: Prevent duplicate transactions (MOST IMPORTANT) =====
        // This is the main protection against replay attacks and SePay retries
        if (coinDepositRepository.existsBySepayTransactionId(sepayTransactionId)) {
            log.info("[SePay] Transaction has been processed before: sepayId={}", sepayTransactionId);
            throw new IllegalArgumentException("Transaction has already been processed (duplicate sepayId: " + sepayTransactionId + ")");
        }

        // ===== STEP 3: Find User by depositCode =====
        String depositCode = payload.getCode();
        User user = userRepository.findByDepositCodeAndIsDelete(depositCode, false);
        if (user == null) {
            log.error("[SePay] User not found with depositCode: {}, sepayId={}",
                    depositCode, sepayTransactionId);
            throw new IllegalArgumentException("User not found with depositCode: " + depositCode);
        }

        log.info("[SePay] Found user: userId={}, depositCode={}, amount={}",
                user.getId(), depositCode, payload.getTransferAmount());

        // ===== STEP 4: Record CoinDeposit =====
        try {
            CoinDeposit deposit = new CoinDeposit();
            deposit.setUser(user);
            deposit.setAmount(payload.getTransferAmount());
            deposit.setCoinsAdded(payload.getTransferAmount());
            deposit.setStatus("Completed");
            deposit.setSepayTransactionId(sepayTransactionId);
            deposit.setSepayReferenceCode(payload.getReferenceCode());
            deposit.setGateway(payload.getGateway());

            // Convert transactionDate if present
            try {
                if (payload.getTransactionDate() != null && !payload.getTransactionDate().isEmpty()) {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    LocalDateTime ldt = LocalDateTime.parse(payload.getTransactionDate(), formatter);
                    deposit.setTransactionDate(java.sql.Timestamp.valueOf(ldt));
                }
            } catch (Exception e) {
                log.warn("[SePay] Could not parse transactionDate: {}, will be set to null",
                        payload.getTransactionDate());
            }

            deposit.setContent(payload.getContent());
            coinDepositRepository.save(deposit);

            log.info("[SePay] Saved CoinDeposit: depositId={}, sepayId={}",
                    deposit.getId(), sepayTransactionId);

        } catch (Exception e) {
            log.error("[SePay] Error saving CoinDeposit: sepayId={}", sepayTransactionId, e);
            throw new RuntimeException("Error saving CoinDeposit to database", e);
        }

        // ===== STEP 5: Update User's balance =====
        try {
            Long oldBalance = user.getCoins();
            Long newBalance = oldBalance + payload.getTransferAmount();
            user.setCoins(newBalance);
            userRepository.save(user);

            log.info("[SePay] Updated user balance: userId={}, {} -> {} (+{})",
                    user.getId(), oldBalance, newBalance, payload.getTransferAmount());

        } catch (Exception e) {
            log.error("[SePay] Error updating user balance: userId={}, sepayId={}",
                    user.getId(), sepayTransactionId, e);
            throw new RuntimeException("Error updating user balance", e);
        }

        // ===== STEP 6: Send notification to user =====
        // Notification is sent in a separate transaction (REQUIRES_NEW)
        // to avoid rolling back the entire process if notification fails
        try {
            sendNotificationInNewTransaction(user, payload.getTransferAmount(), payload.getReferenceCode());
            log.info("[SePay] Sent notification to user: userId={}", user.getId());
        } catch (Exception e) {
            // Notification failure does not fail the main transaction
            log.error("[SePay] Error sending notification (does not affect transaction): userId={}, sepayId={}",
                    user.getId(), sepayTransactionId, e);
        }

        log.info("[SePay] ===== Finished processing webhook: sepayId={}, userId={}, amount={} =====",
                sepayTransactionId, user.getId(), payload.getTransferAmount());
    }

    /**
     * Sends a notification in a separate transaction.
     * The REQUIRES_NEW annotation creates a new transaction, independent of the parent transaction.
     * If this method fails, the parent transaction (processSepayDepositWebhook) will still commit.
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
