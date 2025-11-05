-- ========================================
-- SIMPLE Migration: Thêm cột STATUS vào Categories
-- Chạy script này nếu add_status_column.sql báo lỗi
-- ========================================

USE MMO_System;

-- Cách 1: Thêm cột status (nếu chưa có sẽ thêm, nếu có rồi sẽ báo lỗi - bỏ qua lỗi)
ALTER TABLE Categories
ADD COLUMN status TINYINT(1) NOT NULL DEFAULT 1
COMMENT 'Trạng thái: 1=Active, 0=Inactive'
AFTER type;

-- Đảm bảo tất cả records có status = 1
UPDATE Categories SET status = 1;

-- Xem kết quả
DESCRIBE Categories;
SELECT * FROM Categories LIMIT 5;

