package com.mmo.mq;

import com.mmo.entity.ShopInfo;
import com.mmo.entity.User;
import com.mmo.mq.dto.BuyPointsMessage;
import com.mmo.repository.EmailVerificationRepository;
import com.mmo.repository.ShopInfoRepository;
import com.mmo.repository.UserRepository;
import com.mmo.service.EmailService;
import com.mmo.service.NotificationService;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.util.Date;
import java.util.Locale;
import com.mmo.entity.ShopPointPurchase;
import com.mmo.repository.ShopPointPurchaseRepository;

@Component
public class BuyPointsListener {
    private static final Logger log = LoggerFactory.getLogger(BuyPointsListener.class);

    private final UserRepository userRepository;
    private final ShopInfoRepository shopInfoRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final ShopPointPurchaseRepository shopPointPurchaseRepository;

    public BuyPointsListener(UserRepository userRepository,
                             ShopInfoRepository shopInfoRepository,
                             EmailVerificationRepository emailVerificationRepository,
                             NotificationService notificationService,
                             EmailService emailService,
                             ShopPointPurchaseRepository shopPointPurchaseRepository) {
        this.userRepository = userRepository;
        this.shopInfoRepository = shopInfoRepository;
        this.emailVerificationRepository = emailVerificationRepository;
        this.notificationService = notificationService;
        this.emailService = emailService;
        this.shopPointPurchaseRepository = shopPointPurchaseRepository;
    }

    @Transactional
    @RabbitListener(queues = RabbitConfig.BUY_POINTS_QUEUE)
    public void handle(BuyPointsMessage msg) {
        if (msg == null || msg.userId() == null || msg.pointsToBuy() == null || msg.pointsToBuy() <= 0) return;
        log.info("Consuming buy-points message userId={} pointsToBuy={} cost={} dedupeKey={}",
                msg.userId(), msg.pointsToBuy(), msg.costCoins(), msg.dedupeKey());

        User user = userRepository.findById(msg.userId()).orElse(null);
        if (user == null) {
            log.warn("User id={} not found, skipping", msg.userId());
            return;
        }

        // Validate and mark OTP used
        try {
            var ov = emailVerificationRepository.findTopByUserAndVerificationCodeAndIsUsedFalseOrderByCreatedAtDesc(user, msg.otp());
            var ev = ov.orElse(null);
            if (ev == null || (ev.getExpiryDate() != null && ev.getExpiryDate().before(new Date()))) {
                log.warn("OTP invalid/expired for user id={}, skipping buy points", user.getId());
                return;
            }
            ev.setUsed(true);
            emailVerificationRepository.save(ev);
        } catch (Exception ex) {
            log.error("Error validating OTP for user id={}: {}", user.getId(), ex.getMessage(), ex);
            throw ex;
        }

        long pointsToBuy = msg.pointsToBuy();
        long expectedCost = pointsToBuy; // 1 coin per point
        if (msg.costCoins() != null && !msg.costCoins().equals(expectedCost)) {
            // log mismatch; we will recalc and proceed with recalculated cost
            log.info("Cost mismatch in message for user {}: msg={} recalculated={}", user.getId(), msg.costCoins(), expectedCost);
        }
        // Atomic coin deduction
        int updated = userRepository.deductCoinsIfEnough(user.getId(), expectedCost);
        if (updated == 0) {
            log.warn("Insufficient coins for user id={} cost={}", user.getId(), expectedCost);
            try { notificationService.createNotificationForUser(user.getId(), "Buy points failed", "Insufficient Coins to purchase the required points."); } catch (Exception ignored) {}
            return;
        }

        // Update ShopInfo points and possibly level
        ShopInfo shop = shopInfoRepository.findByUserIdAndIsDeleteFalse(user.getId()).orElseGet(() -> shopInfoRepository.findByUser_Id(user.getId()).orElse(null));
        if (shop == null) {
            log.warn("ShopInfo not found for user id={}, refunding? Skipping update.", user.getId());
            // In case shop missing, return coins back to user to avoid loss
            try { userRepository.addCoins(user.getId(), expectedCost); } catch (Exception ignored) {}
            return;
        }
        long current = shop.getPoints() == null ? 0L : shop.getPoints();
        long nextTotal = current + pointsToBuy;
        shop.setPoints(nextTotal);
        // Note: level and commission are automatically updated by database trigger
        shopInfoRepository.save(shop);

        // Persist purchase audit record
        try {
            ShopPointPurchase purchase = new ShopPointPurchase();
            purchase.setUser(user);
            purchase.setPointsBought(pointsToBuy);
            purchase.setCoinsSpent(expectedCost);
            purchase.setPointsBefore(current);
            purchase.setPointsAfter(nextTotal);
            shopPointPurchaseRepository.save(purchase);
        } catch (Exception ex) {
            log.error("Failed to record ShopPointPurchase for user {}: {}", user.getId(), ex.getMessage(), ex);
            throw ex; // rethrow to ensure transaction rollback to keep consistency
        }

        // Refresh shop to get the updated level and commission from trigger
        shop = shopInfoRepository.findById(shop.getId()).orElse(shop);

        // Notify and email
        try {
            NumberFormat nf = NumberFormat.getInstance(Locale.US);
            notificationService.createNotificationForUser(user.getId(), "Points purchased successfully", "You have bought " + nf.format(pointsToBuy) + " points. Your new total is " + nf.format(nextTotal) + ".");
        } catch (Exception ignored) {}
        try {
            String username = user.getFullName() != null ? user.getFullName() : (user.getEmail() != null ? user.getEmail() : "User");
            String commission = shop.getCommission() != null ? shop.getCommission().toString() : "5.0";
            String html = com.mmo.util.EmailTemplate.buyPointsSuccessEmail(username,
                    String.format("%,d", pointsToBuy),
                    Short.toString(shop.getShopLevel() == null ? 0 : shop.getShopLevel()),
                    String.format("%,d", nextTotal),
                    commission);
            if (user.getEmail() != null && !user.getEmail().isBlank()) {
                emailService.sendEmailAsync(user.getEmail(), "[MMOMarket] Points Purchase Successful", html);
            }
        } catch (Exception ignored) {}

        log.info("Buy points completed for user id={} +{} points, total={}, newLevel={}", user.getId(), pointsToBuy, nextTotal, shop.getShopLevel());
    }
}
