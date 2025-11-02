package com.mmo.mq;

import com.mmo.entity.ShopInfo;
import com.mmo.entity.User;
import com.mmo.mq.dto.SellerRegistrationMessage;
import com.mmo.repository.UserRepository;
import com.mmo.service.EmailService;
import com.mmo.service.NotificationService;
import com.mmo.service.SystemConfigurationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Date;

@Component
public class SellerRegistrationListener {
    private static final Logger log = LoggerFactory.getLogger(SellerRegistrationListener.class);

    private static final long REGISTRATION_FEE = 200_000L;

    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final SystemConfigurationService systemConfigurationService;
    private final EmailService emailService;

    @PersistenceContext
    private EntityManager entityManager;

    public SellerRegistrationListener(UserRepository userRepository,
                                      NotificationService notificationService,
                                      SystemConfigurationService systemConfigurationService,
                                      EmailService emailService) {
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.systemConfigurationService = systemConfigurationService;
        this.emailService = emailService;
    }

    @Transactional
    @RabbitListener(queues = RabbitConfig.SELLER_REGISTRATION_QUEUE)
    public void handle(SellerRegistrationMessage msg) {
        if (msg == null || msg.userId() == null) return;
        log.info("Consuming seller-registration message userId={} shopName={} dedupeKey={}", msg.userId(), msg.shopName(), msg.dedupeKey());

        User user = userRepository.findById(msg.userId()).orElse(null);
        if (user == null) {
            log.warn("User id={} not found, skipping seller registration", msg.userId());
            return;
        }

        // Attempt atomic activation + deduction; safe under concurrent consumers.
        int updated = userRepository.activateSellerAndDeductIfNotActive(user.getId(), REGISTRATION_FEE);
        // Reload the latest state after guarded update
        user = userRepository.findById(user.getId()).orElse(user);

        if (updated == 0) {
            // Either already active or not enough coins
            boolean active = user.getShopStatus() != null && user.getShopStatus().equalsIgnoreCase("Active");
            if (active) {
                log.info("User id={} already Active; treating as idempotent registration", user.getId());
                // Optionally update shop info with latest provided name/description (idempotent improvement)
                upsertShopInfo(user, msg.shopName(), msg.description());
                return;
            } else {
                log.warn("Insufficient coins for user id={} to pay registration fee {}", user.getId(), REGISTRATION_FEE);
                try {
                    notificationService.createNotificationForUser(user.getId(),
                            "Seller registration failed",
                            "Insufficient balance. A fee of 200,000 coins is required to activate your seller account.");
                } catch (Exception ignored) {}
                return;
            }
        }

        // updated > 0: fee deducted and status set to Active. Ensure role SELLER.
        try {
            String role = user.getRole();
            if (role == null || !"SELLER".equalsIgnoreCase(role)) {
                user.setRole("SELLER");
                userRepository.save(user);
            }
        } catch (Exception ex) {
            log.warn("Failed to set SELLER role for user id={}: {}", user.getId(), ex.getMessage());
        }

        // Create or update ShopInfo with defaults and provided data
        upsertShopInfo(user, msg.shopName(), msg.description());

        // Notify success
        try {
            notificationService.createNotificationForUser(
                    user.getId(),
                    "Seller account activated",
                    "Your seller registration has been completed successfully. Your shop is now active. A fee of 200,000 coins has been deducted from your account."
            );
        } catch (Exception ignored) {}

        // Optional: send email confirmation (best-effort)
        try {
            if (user.getEmail() != null && !user.getEmail().isBlank()) {
                String subject = "[MMOMarket] Seller account activated";
                String html = "<p>Your seller account is now active.</p><p>200,000 coins have been deducted from your balance.</p>";
                emailService.sendEmailAsync(user.getEmail(), subject, html);
            }
        } catch (Exception ex) {
            log.warn("Failed sending seller activation email to user id={}: {}", user.getId(), ex.getMessage());
        }

        log.info("Seller activated for user id={} with fee={} coins", user.getId(), REGISTRATION_FEE);
    }

    private void upsertShopInfo(User user, String shopName, String description) {
        ShopInfo shop = entityManager.createQuery("SELECT s FROM ShopInfo s WHERE s.user = :u AND s.isDelete = false", ShopInfo.class)
                .setParameter("u", user)
                .getResultStream().findFirst().orElse(null);
        if (shop == null) {
            shop = new ShopInfo();
            shop.setUser(user);
            shop.setShopName(shopName != null && !shopName.isBlank() ? shopName :
                    (user.getFullName() != null ? user.getFullName() + "'s Shop" : "My Shop"));
            shop.setDescription(description != null ? description : "");
            try {
                BigDecimal defCommission = systemConfigurationService != null ? systemConfigurationService.getDefaultCommissionPercentage() : new BigDecimal("5.00");
                if (defCommission != null) shop.setCommission(defCommission);
            } catch (Exception ignored) { }
            entityManager.persist(shop);
        } else {
            if (shopName != null && !shopName.isBlank()) shop.setShopName(shopName);
            if (description != null) shop.setDescription(description);
            entityManager.merge(shop);
        }
    }
}

