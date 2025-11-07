package com.mmo.scheduler;

import com.mmo.service.ShopFlagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Scheduled tasks for Shop Flag Management
 *
 * Business Rules:
 * - Auto-remove flags older than 180 days without new violations
 * - Check and apply penalties for flag accumulation
 */
@Component
public class ShopFlagScheduler {

    private static final Logger log = LoggerFactory.getLogger(ShopFlagScheduler.class);

    @Autowired
    private ShopFlagService shopFlagService;

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Auto-remove flags older than 180 days
     * Runs daily at 2:00 AM
     *
     * Business Rule: Cờ Cảnh Cáo sẽ được tự động xóa sau 180 ngày
     * nếu không phát sinh vi phạm mới
     */
    @Scheduled(cron = "0 0 2 * * ?") // Every day at 2:00 AM
    public void autoRemoveOldFlags() {
        String timestamp = LocalDateTime.now().format(formatter);
        log.info("==========================================");
        log.info("Starting scheduled job: Auto-remove old flags");
        log.info("Timestamp: {}", timestamp);
        log.info("==========================================");

        try {
            shopFlagService.autoRemoveOldFlags();
            log.info("Successfully completed auto-remove old flags job");
        } catch (Exception e) {
            log.error("Error in auto-remove old flags job", e);
        }

        log.info("==========================================");
    }

    /**
     * Check flag accumulation and notify admins
     * Runs every 6 hours
     *
     * Business Rules:
     * - 3 flags in 30 days → Notify admin (should downgrade 1 level)
     * - 5 flags in 90 days → Notify admin (should downgrade 2 levels)
     * - 7 total active flags → Auto-ban (no admin approval needed)
     * - 1 BANNED flag → Auto-ban (no admin approval needed)
     */
    @Scheduled(cron = "0 0 */6 * * ?") // Every 6 hours
    public void checkFlagsAndNotifyAdmins() {
        String timestamp = LocalDateTime.now().format(formatter);
        log.info("==========================================");
        log.info("Starting scheduled job: Check flags and notify admins");
        log.info("Timestamp: {}", timestamp);
        log.info("==========================================");

        try {
            shopFlagService.checkAndNotifyAdmins();
            log.info("Successfully completed check flags and notify admins job");
        } catch (Exception e) {
            log.error("Error in check flags and notify admins job", e);
        }

        log.info("==========================================");
    }

    /**
     * Generate daily flag statistics report
     * Runs daily at 8:00 AM
     */
    @Scheduled(cron = "0 0 8 * * ?") // Every day at 8:00 AM
    public void generateDailyFlagReport() {
        String timestamp = LocalDateTime.now().format(formatter);
        log.info("==========================================");
        log.info("Starting scheduled job: Generate daily flag report");
        log.info("Timestamp: {}", timestamp);
        log.info("==========================================");

        try {
            shopFlagService.generateDailyReport();
            log.info("Successfully generated daily flag report");
        } catch (Exception e) {
            log.error("Error generating daily flag report", e);
        }

        log.info("==========================================");
    }

    /**
     * Manual trigger for testing purposes
     * Call this method to test scheduler without waiting
     */
    public void runAutoRemoveOldFlagsManually() {
        log.info("Manual trigger: Auto-remove old flags");
        autoRemoveOldFlags();
    }

    /**
     * Manual trigger for testing purposes
     */
    public void runCheckFlagsManually() {
        log.info("Manual trigger: Check flags and notify admins");
        checkFlagsAndNotifyAdmins();
    }
}

