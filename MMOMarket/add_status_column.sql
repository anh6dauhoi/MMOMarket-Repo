-- ========================================
-- Migration: Thêm cột STATUS vào bảng Categories
-- Date: 2024-11-04
-- Description: Thêm cột status (boolean) để quản lý trạng thái Active/Inactive
-- ========================================

USE MMO_System;

-- Thêm cột status vào bảng Categories
ALTER TABLE Categories
ADD COLUMN IF NOT EXISTS status TINYINT(1) NOT NULL DEFAULT 1
COMMENT 'Trạng thái: 1=Active, 0=Inactive'
AFTER type;

-- Cập nhật tất cả records hiện có thành Active (status = 1)
UPDATE Categories
SET status = 1
WHERE status IS NULL OR status = 0;

-- Kiểm tra kết quả
SELECT 'Migration completed successfully!' AS message;
SELECT 'Checking Categories table structure...' AS step;
DESCRIBE Categories;

SELECT 'Sample data from Categories:' AS step;
SELECT id, name, type, status, isDelete, created_at
FROM Categories
LIMIT 5;

