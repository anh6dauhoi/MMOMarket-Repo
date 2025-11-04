-- LỆNH SQL CHÍNH XÁC ĐỂ THÊM CỘT STATUS VÀO BẢNG BLOGS
-- Copy và chạy trực tiếp trong MySQL

USE MMOMarket;

ALTER TABLE Blogs ADD COLUMN status TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Trạng thái: 1=Active, 0=Inactive' AFTER image;

