package com.mmo.service;

import com.mmo.entity.*;
import com.mmo.repository.ShopFlagRepository;
import com.mmo.repository.ShopInfoRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ShopFlagService {
    private static final Logger log = LoggerFactory.getLogger(ShopFlagService.class);

    @Autowired
    private ShopFlagRepository shopFlagRepository;

    @Autowired
    private ShopInfoRepository shopInfoRepository;

    @Autowired
    private NotificationService notificationService;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Create a new flag for a shop
     */
    @Transactional
    public ShopFlag createFlag(Long shopId, Long adminId, Long relatedComplaintId,
                               String reason, ShopFlag.FlagLevel flagLevel) throws Exception {
        log.info("Creating flag for shop {} with level {} by admin {}", shopId, flagLevel, adminId);

        ShopInfo shop = shopInfoRepository.findById(shopId)
            .orElseThrow(() -> {
                log.error("Shop not found: {}", shopId);
                return new Exception("Shop not found with ID: " + shopId);
            });

        // Validate shop is active: check both shopStatus AND isDelete
        // Shop must have: user.shopStatus = "Active" AND shop.isDelete = false
        if (shop.isDelete()) {
            log.warn("Attempted to flag deleted shop: {}, isDelete: true", shopId);
            throw new Exception("Cannot flag a deleted shop. Shop must be active.");
        }

        if (shop.getUser() == null || !"Active".equalsIgnoreCase(shop.getUser().getShopStatus())) {
            log.warn("Attempted to flag shop with inactive status: {}, shopStatus: {}",
                    shopId, shop.getUser() != null ? shop.getUser().getShopStatus() : "null");
            throw new Exception("Cannot flag a shop with Inactive status. Shop owner must have Active status.");
        }

        User admin = entityManager.find(User.class, adminId);
        if (admin == null) {
            log.error("Admin not found: {}", adminId);
            throw new Exception("Admin not found with ID: " + adminId);
        }

        log.debug("Creating flag entity for shop {}", shop.getShopName());

        ShopFlag flag = new ShopFlag();
        flag.setShop(shop);
        flag.setAdmin(admin);
        flag.setReason(reason);
        flag.setFlagLevel(flagLevel);
        flag.setStatus(ShopFlag.FlagStatus.ACTIVE);

        if (relatedComplaintId != null) {
            log.debug("Linking flag to complaint {}", relatedComplaintId);
            Complaint complaint = entityManager.find(Complaint.class, relatedComplaintId);
            if (complaint != null) {
                flag.setRelatedComplaint(complaint);
            } else {
                log.warn("Complaint {} not found, flag will be created without complaint link", relatedComplaintId);
            }
        }

        try {
            flag = shopFlagRepository.save(flag);
            log.info("Flag {} saved successfully for shop {}", flag.getId(), shopId);
        } catch (Exception e) {
            log.error("Error saving flag for shop {}", shopId, e);
            throw new Exception("Failed to save flag: " + e.getMessage());
        }

        // Check penalties and apply them
        try {
            log.debug("Applying penalties for shop {}", shopId);
            applyPenalties(shop);
        } catch (Exception e) {
            log.error("Error applying penalties for shop {}", shopId, e);
            // Continue even if penalties fail
        }

        // Notify shop owner
        try {
            String levelText = flagLevel == ShopFlag.FlagLevel.WARNING ? "Warning" :
                              flagLevel == ShopFlag.FlagLevel.SEVERE ? "Severe" : "Banned";
            notificationService.createNotificationForUser(
                shop.getUser().getId(),
                "Shop Flag: " + levelText,
                "Your shop has received a " + levelText.toLowerCase() + " flag. Reason: " + reason
            );
            log.info("Notification sent to shop owner for shop {}", shopId);
        } catch (Exception e) {
            log.error("Error sending notification for shop {}", shopId, e);
            // Continue even if notification fails
        }

        log.info("Flag created successfully for shop {} by admin {}", shopId, adminId);
        return flag;
    }

    /**
     * Resolve (remove) a flag
     */
    @Transactional
    public ShopFlag resolveFlag(Long flagId, String resolutionNotes) throws Exception {
        ShopFlag flag = shopFlagRepository.findById(flagId)
            .orElseThrow(() -> new Exception("Flag not found"));

        if (flag.getStatus() == ShopFlag.FlagStatus.RESOLVED) {
            throw new Exception("Flag is already resolved");
        }

        flag.setStatus(ShopFlag.FlagStatus.RESOLVED);
        flag.setResolutionNotes(resolutionNotes);
        flag.setResolvedAt(new Date());

        flag = shopFlagRepository.save(flag);

        // Notify shop owner
        notificationService.createNotificationForUser(
            flag.getShop().getUser().getId(),
            "Shop Flag Resolved",
            "A flag on your shop has been resolved. " +
            (resolutionNotes != null ? "Notes: " + resolutionNotes : "")
        );

        log.info("Flag {} resolved", flagId);
        return flag;
    }

    /**
     * Get all active flags for a shop
     */
    public List<ShopFlag> getActiveFlags(Long shopId) {
        return shopFlagRepository.findByShopIdAndStatus(shopId, ShopFlag.FlagStatus.ACTIVE);
    }

    /**
     * Get all flags (active and resolved) for a shop
     */
    public List<ShopFlag> getAllFlags(Long shopId) {
        return shopFlagRepository.findByShopIdOrderByCreatedAtDesc(shopId);
    }

    /**
     * Get flag statistics for a shop
     */
    public Map<String, Object> getShopFlagStats(Long shopId) {
        List<ShopFlag> allFlags = getAllFlags(shopId);
        List<ShopFlag> activeFlags = allFlags.stream()
            .filter(f -> f.getStatus() == ShopFlag.FlagStatus.ACTIVE)
            .collect(Collectors.toList());

        // Count flags in different time periods
        Calendar cal = Calendar.getInstance();
        Date now = new Date();

        cal.setTime(now);
        cal.add(Calendar.DAY_OF_MONTH, -30);
        Date thirtyDaysAgo = cal.getTime();

        cal.setTime(now);
        cal.add(Calendar.DAY_OF_MONTH, -90);
        Date ninetyDaysAgo = cal.getTime();

        long flagsLast30Days = allFlags.stream()
            .filter(f -> f.getCreatedAt() != null && f.getCreatedAt().after(thirtyDaysAgo))
            .count();

        long flagsLast90Days = allFlags.stream()
            .filter(f -> f.getCreatedAt() != null && f.getCreatedAt().after(ninetyDaysAgo))
            .count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalFlags", allFlags.size());
        stats.put("activeFlags", activeFlags.size());
        stats.put("resolvedFlags", allFlags.size() - activeFlags.size());
        stats.put("flagsLast30Days", flagsLast30Days);
        stats.put("flagsLast90Days", flagsLast90Days);
        stats.put("warningFlags", activeFlags.stream().filter(f -> f.getFlagLevel() == ShopFlag.FlagLevel.WARNING).count());
        stats.put("severeFlags", activeFlags.stream().filter(f -> f.getFlagLevel() == ShopFlag.FlagLevel.SEVERE).count());
        stats.put("bannedFlags", activeFlags.stream().filter(f -> f.getFlagLevel() == ShopFlag.FlagLevel.BANNED).count());

        return stats;
    }

    /**
     * Apply automatic penalties based on flag accumulation
     * Business rules:
     * - 3 flags in 30 days: Downgrade 1 level
     * - 5 flags in 90 days: Downgrade 2 levels
     * - 7 flags total (active): Ban account permanently
     * - 1 fraud flag: Ban account permanently + confiscate balance
     */
    @Transactional
    public void applyPenalties(ShopInfo shop) {
        Map<String, Object> stats = getShopFlagStats(shop.getId());
        long flagsLast30Days = (long) stats.get("flagsLast30Days");
        long flagsLast90Days = (long) stats.get("flagsLast90Days");
        long totalActiveFlags = (long) stats.get("activeFlags");
        long bannedFlags = (long) stats.get("bannedFlags");

        User seller = shop.getUser();
        boolean shouldBan = false;
        String banReason = "";

        // Check for fraud/banned flag (immediate permanent ban)
        if (bannedFlags > 0) {
            shouldBan = true;
            banReason = "Fraud/banned activity detected";

            // Confiscate balance
            seller.setCoins(0L);
            entityManager.merge(seller);

            log.warn("Shop {} banned due to fraud flag - balance confiscated", shop.getId());
        }
        // Check for 7+ total active flags (permanent ban)
        else if (totalActiveFlags >= 7) {
            shouldBan = true;
            banReason = "Accumulated 7 or more active flags";
            log.warn("Shop {} banned due to 7+ active flags", shop.getId());
        }
        // Check for 3 flags in 30 days (notify admin for review)
        else if (flagsLast30Days >= 3) {
            // Send notification to ALL admins for review
            try {
                List<User> admins = entityManager.createQuery(
                    "SELECT u FROM User u WHERE LOWER(u.role) = 'admin'", User.class)
                    .getResultList();

                for (User admin : admins) {
                    notificationService.createNotificationForUser(
                        admin.getId(),
                        "Shop Penalty Alert: 3 Flags in 30 Days",
                        String.format("Shop '%s' (ID: %d) has accumulated 3 flags in the last 30 days. " +
                                     "Business rule: Should downgrade 1 level. Please review and take action.",
                                     shop.getShopName(), shop.getId())
                    );
                }
                log.warn("Shop {} has 3 flags in 30 days - admin notification sent for review", shop.getId());
            } catch (Exception e) {
                log.error("Failed to notify admins about shop {} penalty (3 flags)", shop.getId(), e);
            }
        }
        // Check for 5 flags in 90 days (notify admin for review)
        else if (flagsLast90Days >= 5) {
            // Send notification to ALL admins for review
            try {
                List<User> admins = entityManager.createQuery(
                    "SELECT u FROM User u WHERE LOWER(u.role) = 'admin'", User.class)
                    .getResultList();

                for (User admin : admins) {
                    notificationService.createNotificationForUser(
                        admin.getId(),
                        "Shop Penalty Alert: 5 Flags in 90 Days",
                        String.format("Shop '%s' (ID: %d) has accumulated 5 flags in the last 90 days. " +
                                     "Business rule: Should downgrade 2 levels. Please review and take action.",
                                     shop.getShopName(), shop.getId())
                    );
                }
                log.warn("Shop {} has 5 flags in 90 days - admin notification sent for review", shop.getId());
            } catch (Exception e) {
                log.error("Failed to notify admins about shop {} penalty (5 flags)", shop.getId(), e);
            }
        }

        // Execute ban if needed
        if (shouldBan) {
            // Set both shopStatus and isDelete for consistency
            seller.setShopStatus("Inactive");
            shop.setDelete(true);

            entityManager.merge(seller);
            entityManager.merge(shop);

            // Hide all products from this banned shop
            try {
                int productsUpdated = entityManager.createQuery(
                    "UPDATE Product p SET p.isDelete = true WHERE p.seller.id = :sellerId AND p.isDelete = false")
                    .setParameter("sellerId", seller.getId())
                    .executeUpdate();

                if (productsUpdated > 0) {
                    log.info("Banned shop {} - also hid {} products", shop.getId(), productsUpdated);
                }
            } catch (Exception e) {
                log.error("Could not hide products for banned shop {}", shop.getId(), e);
            }

            notificationService.createNotificationForUser(
                seller.getId(),
                "Shop Banned",
                "Your shop has been permanently banned. Reason: " + banReason
            );

            log.warn("Shop {} permanently banned - set shopStatus=Inactive and isDelete=true. Reason: {}",
                    shop.getId(), banReason);
        }
    }

    /**
     * Process amnesty request - remove 1 minor flag after 90 days of good behavior
     */
    @Transactional
    public boolean processAmnestyRequest(Long shopId) throws Exception {
        ShopInfo shop = shopInfoRepository.findById(shopId)
            .orElseThrow(() -> new Exception("Shop not found"));

        List<ShopFlag> activeFlags = getActiveFlags(shopId);

        // Check if shop has any warning flags
        Optional<ShopFlag> warningFlag = activeFlags.stream()
            .filter(f -> f.getFlagLevel() == ShopFlag.FlagLevel.WARNING)
            .findFirst();

        if (warningFlag.isEmpty()) {
            return false; // No warning flags to remove
        }

        // Check if there are any flags in the last 90 days
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -90);
        Date ninetyDaysAgo = cal.getTime();

        boolean hasRecentFlags = activeFlags.stream()
            .anyMatch(f -> f.getCreatedAt() != null && f.getCreatedAt().after(ninetyDaysAgo));

        if (hasRecentFlags) {
            return false; // Has flags in last 90 days, not eligible
        }

        // Grant amnesty - resolve the oldest warning flag
        ShopFlag flagToResolve = warningFlag.get();
        resolveFlag(flagToResolve.getId(), "Amnesty granted after 90 days of good behavior");

        log.info("Amnesty granted for shop {} - flag {} resolved", shopId, flagToResolve.getId());
        return true;
    }

    /**
     * Auto-remove flags older than 180 days (business rule)
     * Called by scheduler daily
     */
    @Transactional
    public void autoRemoveOldFlags() {
        log.info("Starting auto-remove old flags process...");

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -180);
        Date oneEightyDaysAgo = cal.getTime();

        List<ShopFlag> allActiveFlags = shopFlagRepository.findByStatusOrderByFlagLevelDesc(ShopFlag.FlagStatus.ACTIVE);

        log.info("Found {} active flags to check", allActiveFlags.size());

        int removedCount = 0;
        int errorCount = 0;

        for (ShopFlag flag : allActiveFlags) {
            if (flag.getCreatedAt() != null && flag.getCreatedAt().before(oneEightyDaysAgo)) {
                try {
                    // Check if shop has any NEW violations in last 180 days
                    List<ShopFlag> recentFlags = shopFlagRepository.findByShopIdAndStatus(
                        flag.getShop().getId(),
                        ShopFlag.FlagStatus.ACTIVE
                    ).stream()
                    .filter(f -> !f.getId().equals(flag.getId())) // Exclude current flag
                    .filter(f -> f.getCreatedAt() != null && f.getCreatedAt().after(oneEightyDaysAgo))
                    .collect(java.util.stream.Collectors.toList());

                    if (recentFlags.isEmpty()) {
                        // No new violations - safe to remove
                        flag.setStatus(ShopFlag.FlagStatus.RESOLVED);
                        flag.setResolutionNotes("Auto-resolved after 180 days without new violations");
                        flag.setResolvedAt(new Date());
                        shopFlagRepository.save(flag);

                        // Notify shop owner
                        try {
                            notificationService.createNotificationForUser(
                                flag.getShop().getUser().getId(),
                                "Flag Auto-Resolved",
                                "A flag on your shop has been automatically removed after 180 days without new violations."
                            );
                        } catch (Exception e) {
                            log.warn("Could not send notification for auto-resolved flag {}", flag.getId(), e);
                        }

                        removedCount++;
                        log.info("Auto-resolved flag {} for shop {} (created {}, no new violations)",
                                flag.getId(), flag.getShop().getId(), flag.getCreatedAt());
                    } else {
                        log.debug("Flag {} not removed - shop has {} recent violations",
                                flag.getId(), recentFlags.size());
                    }
                } catch (Exception e) {
                    errorCount++;
                    log.error("Error auto-removing flag {}", flag.getId(), e);
                }
            }
        }

        log.info("Auto-remove old flags completed: {} removed, {} errors, {} total checked",
                removedCount, errorCount, allActiveFlags.size());
    }

    /**
     * Check flags and notify admins about shops needing review
     * Called by scheduler periodically
     *
     * Does NOT auto-downgrade levels - only sends notifications to admins
     * Auto-ban still applies for 7 flags or BANNED flag
     */
    @Transactional
    public void checkAndNotifyAdmins() {
        log.info("Starting check flags and notify admins for all shops...");

        // Get all shops with active flags
        List<ShopFlag> activeFlags = shopFlagRepository.findByStatusOrderByFlagLevelDesc(ShopFlag.FlagStatus.ACTIVE);

        // Group by shop
        Map<Long, List<ShopFlag>> flagsByShop = activeFlags.stream()
            .collect(java.util.stream.Collectors.groupingBy(f -> f.getShop().getId()));

        log.info("Found {} shops with active flags to check", flagsByShop.size());

        int shopsChecked = 0;
        int notificationsSent = 0;
        int autoBanned = 0;
        int errorCount = 0;

        for (Map.Entry<Long, List<ShopFlag>> entry : flagsByShop.entrySet()) {
            Long shopId = entry.getKey();
            try {
                ShopInfo shop = shopInfoRepository.findById(shopId).orElse(null);
                if (shop != null) {
                    shopsChecked++;

                    // Apply penalties (will send notification for 3/5 flags, auto-ban for 7/BANNED)
                    applyPenalties(shop);

                    // Check if shop was banned (shopStatus changed to Inactive)
                    if ("Inactive".equalsIgnoreCase(shop.getUser().getShopStatus())) {
                        autoBanned++;
                    }

                    // Count as notification sent if shop has 3+ or 5+ flags
                    long totalActive = entry.getValue().stream()
                        .filter(f -> f.getStatus() == ShopFlag.FlagStatus.ACTIVE)
                        .count();

                    if (totalActive >= 3) {
                        notificationsSent++;
                    }
                }
            } catch (Exception e) {
                errorCount++;
                log.error("Error checking flags for shop {}", shopId, e);
            }
        }

        log.info("Check flags completed: {} shops checked, {} admin notifications sent, {} auto-banned, {} errors",
                shopsChecked, notificationsSent, autoBanned, errorCount);
    }

    /**
     * Generate daily report of flag statistics
     */
    @Transactional(readOnly = true)
    public void generateDailyReport() {
        log.info("Generating daily flag statistics report...");

        try {
            List<ShopFlag> allFlags = shopFlagRepository.findAll();

            long totalFlags = allFlags.size();
            long activeFlags = allFlags.stream().filter(f -> f.getStatus() == ShopFlag.FlagStatus.ACTIVE).count();
            long resolvedFlags = allFlags.stream().filter(f -> f.getStatus() == ShopFlag.FlagStatus.RESOLVED).count();

            long warningFlags = allFlags.stream()
                .filter(f -> f.getStatus() == ShopFlag.FlagStatus.ACTIVE)
                .filter(f -> f.getFlagLevel() == ShopFlag.FlagLevel.WARNING)
                .count();
            long severeFlags = allFlags.stream()
                .filter(f -> f.getStatus() == ShopFlag.FlagStatus.ACTIVE)
                .filter(f -> f.getFlagLevel() == ShopFlag.FlagLevel.SEVERE)
                .count();
            long bannedFlags = allFlags.stream()
                .filter(f -> f.getStatus() == ShopFlag.FlagStatus.ACTIVE)
                .filter(f -> f.getFlagLevel() == ShopFlag.FlagLevel.BANNED)
                .count();

            // Flags created in last 24 hours
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.HOUR, -24);
            Date last24Hours = cal.getTime();

            long flagsLast24h = allFlags.stream()
                .filter(f -> f.getCreatedAt() != null && f.getCreatedAt().after(last24Hours))
                .count();

            // Flags resolved in last 24 hours
            long resolvedLast24h = allFlags.stream()
                .filter(f -> f.getResolvedAt() != null && f.getResolvedAt().after(last24Hours))
                .count();

            // Number of unique shops with active flags
            long shopsWithActiveFlags = allFlags.stream()
                .filter(f -> f.getStatus() == ShopFlag.FlagStatus.ACTIVE)
                .map(f -> f.getShop().getId())
                .distinct()
                .count();

            log.info("========================================");
            log.info("DAILY FLAG STATISTICS REPORT");
            log.info("========================================");
            log.info("Total Flags: {}", totalFlags);
            log.info("Active Flags: {}", activeFlags);
            log.info("Resolved Flags: {}", resolvedFlags);
            log.info("----------------------------------------");
            log.info("Active by Level:");
            log.info("  - WARNING: {}", warningFlags);
            log.info("  - SEVERE: {}", severeFlags);
            log.info("  - BANNED: {}", bannedFlags);
            log.info("----------------------------------------");
            log.info("Last 24 Hours:");
            log.info("  - Flags Created: {}", flagsLast24h);
            log.info("  - Flags Resolved: {}", resolvedLast24h);
            log.info("----------------------------------------");
            log.info("Shops with Active Flags: {}", shopsWithActiveFlags);
            log.info("========================================");

        } catch (Exception e) {
            log.error("Error generating daily report", e);
        }
    }
}

