-- Shop Flags table migration script
-- This will be automatically created by Hibernate, but here's the reference SQL

CREATE TABLE IF NOT EXISTS ShopFlags (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id BIGINT NULL,                   -- Nullable in case shop gets deleted
    admin_id BIGINT NOT NULL,
    related_complaint_id BIGINT NULL,
    reason TEXT NOT NULL,
    flag_level ENUM('WARNING', 'SEVERE', 'BANNED') NOT NULL DEFAULT 'WARNING',
    status ENUM('ACTIVE', 'RESOLVED') NOT NULL DEFAULT 'ACTIVE',
    resolution_notes TEXT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    resolved_at DATETIME NULL,

    INDEX idx_shop_status (shop_id, status),

    FOREIGN KEY (shop_id) REFERENCES ShopInfo(id) ON DELETE CASCADE,
    FOREIGN KEY (admin_id) REFERENCES Users(id) ON DELETE NO ACTION,
    FOREIGN KEY (related_complaint_id) REFERENCES Complaints(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Sample queries to test:
-- 1. Get active flags for a shop
-- SELECT * FROM ShopFlags WHERE shop_id = ? AND status = 'ACTIVE';

-- 2. Count flags in last 30 days
-- SELECT COUNT(*) FROM ShopFlags
-- WHERE shop_id = ? AND created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY);

-- 3. Get all flags with shop info
-- SELECT f.*, s.shop_name, u.full_name as admin_name
-- FROM ShopFlags f
-- LEFT JOIN ShopInfo s ON f.shop_id = s.id
-- LEFT JOIN Users u ON f.admin_id = u.id
-- ORDER BY f.created_at DESC;

