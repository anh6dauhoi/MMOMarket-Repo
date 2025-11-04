-- Tạo database
CREATE DATABASE IF NOT EXISTS MMO_System;
USE MMO_System;

-- Bảng Users - Quản lý thông tin người dùng (Customer, Seller, Admin, Customer_Seller)
CREATE TABLE IF NOT EXISTS Users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, -- Mã ID tự tăng
    email VARCHAR(255) NOT NULL UNIQUE, -- Email người dùng, duy nhất
    password VARCHAR(255), 
    full_name VARCHAR(255), -- Tên đầy đủ
    role VARCHAR(255) NOT NULL, 
    phone VARCHAR(20), -- Số điện thoại
    shop_status VARCHAR(50) DEFAULT 'Inactive',
    coins BIGINT DEFAULT 0, -- Coin để thanh toán trong hệ thống, 1 Coin = 1 VNĐ
    depositCode VARCHAR(50),
    isVerified TINYINT(1) DEFAULT 0, -- Trạng thái xác minh email
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP, -- Thời gian tạo
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, -- Thời gian cập nhật
    created_by BIGINT, -- Người tạo
    deleted_by BIGINT, -- Người xóa
    isDelete TINYINT(1) DEFAULT 0, -- Trạng thái xóa mềm: 0 - tồn tại, 1 - xóa
	FOREIGN KEY (created_by) REFERENCES Users(id),
    FOREIGN KEY (deleted_by) REFERENCES Users(id),
    INDEX idx_email (email) -- Index cho email
);

-- Bảng SystemConfigurations - Lưu trữ các cấu hình động của hệ thống
CREATE TABLE IF NOT EXISTS SystemConfigurations (
    config_key VARCHAR(100) PRIMARY KEY,     
    config_value VARCHAR(255) NOT NULL,               
    description VARCHAR(255) NULL,         
    value_type VARCHAR(255) NULL, 
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, 
    updated_by BIGINT NULL,                   

    FOREIGN KEY (updated_by) REFERENCES Users(id) ON DELETE SET NULL
);

-- Bảng EmailVerifications - Quản lý mã xác minh email
CREATE TABLE IF NOT EXISTS EmailVerifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    verification_code VARCHAR(6) NOT NULL,
    expiry_date DATETIME NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    is_used TINYINT(1) DEFAULT 0,
    FOREIGN KEY (user_id) REFERENCES Users(id) ON DELETE CASCADE
);

-- Bảng ShopInfo (Đăng ký Bán hàng & Thông tin Shop)
CREATE TABLE IF NOT EXISTS ShopInfo (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, -- Mã ID tự tăng
    user_id BIGINT NOT NULL, -- Mã người dùng
    shop_name VARCHAR(255) NOT NULL, -- Tên cửa hàng
    description TEXT, -- Mô tả
	shop_level TINYINT UNSIGNED DEFAULT 0,
	commission DECIMAL(5,2) NOT NULL, -- Tỷ lệ hoa hồng
    points BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP, -- Thời gian tạo
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, -- Thời gian cập nhật
    created_by BIGINT, -- Người tạo
    deleted_by BIGINT, -- Người xóa
    isDelete TINYINT(1) DEFAULT 0, -- Trạng thái xóa mềm
    FOREIGN KEY (user_id) REFERENCES Users(id) ON DELETE CASCADE,
    FOREIGN KEY (created_by) REFERENCES Users(id) ON DELETE SET NULL,
    FOREIGN KEY (deleted_by) REFERENCES Users(id) ON DELETE SET NULL,
    UNIQUE KEY unique_active_shop (user_id, isDelete)
);

-- Bảng SellerBankInfo - Quản lý thông tin ngân hàng của Seller
CREATE TABLE IF NOT EXISTS SellerBankInfo (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, -- Mã ID tự tăng
    user_id BIGINT NOT NULL, -- Mã Seller
    bank_name VARCHAR(100) NOT NULL, -- Tên ngân hàng
    account_number VARCHAR(50) NOT NULL, -- Số tài khoản
    account_name VARCHAR(100) NOT NULL, -- Tên người thụ hưởng
    branch VARCHAR(100), -- Chi nhánh ngân hàng
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP, -- Thời gian tạo
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, -- Thời gian cập nhật
    created_by BIGINT, -- Người tạo
    deleted_by BIGINT, -- Người xóa
    isDelete TINYINT(1) DEFAULT 0, -- Trạng thái xóa mềm
	FOREIGN KEY (user_id) REFERENCES Users(id),
    FOREIGN KEY (created_by) REFERENCES Users(id),
    FOREIGN KEY (deleted_by) REFERENCES Users(id),
    INDEX idx_user_id (user_id)
);

-- Bảng Categories - Quản lý danh mục sản phẩm/dịch vụ (do Admin tạo)
CREATE TABLE IF NOT EXISTS Categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, -- Mã ID tự tăng
    name VARCHAR(100) NOT NULL, -- Tên danh mục
    description VARCHAR(500), -- Mô tả
    type ENUM('Common', 'Warning') NOT NULL DEFAULT 'Common',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP, -- Thời gian tạo
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, -- Thời gian cập nhật
    created_by BIGINT, -- Người tạo
    deleted_by BIGINT, -- Người xóa
    isDelete TINYINT(1) DEFAULT 0, -- Trạng thái xóa mềm
	FOREIGN KEY (created_by) REFERENCES Users(id),
    FOREIGN KEY (deleted_by) REFERENCES Users(id)
);

-- Bảng Products - Quản lý sản phẩm/dịch vụ
CREATE TABLE IF NOT EXISTS Products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, -- Mã ID tự tăng
    seller_id BIGINT NOT NULL, -- Mã Seller
    category_id BIGINT NOT NULL, -- Mã danh mục
    name VARCHAR(255) NOT NULL, -- Tên sản phẩm
    description TEXT, -- Mô tả
    image VARCHAR(255), -- Đường dẫn ảnh
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP, -- Thời gian tạo
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, -- Thời gian cập nhật
    created_by BIGINT, -- Người tạo
    deleted_by BIGINT, -- Người xóa
    isDelete TINYINT(1) DEFAULT 0, -- Trạng thái xóa mềm
	FOREIGN KEY (seller_id) REFERENCES Users(id) ON DELETE CASCADE,
    FOREIGN KEY (category_id) REFERENCES Categories(id),
    FOREIGN KEY (created_by) REFERENCES Users(id),
    FOREIGN KEY (deleted_by) REFERENCES Users(id),
    INDEX idx_seller_id (seller_id),
    INDEX idx_category_id (category_id)
);

-- Bảng ProductVariants - Quản lý biến thể sản phẩm
CREATE TABLE IF NOT EXISTS ProductVariants (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, -- Mã ID tự tăng
    product_id BIGINT NOT NULL, -- Mã sản phẩm
    variant_name VARCHAR(255) NOT NULL, -- Tên biến thể
    price BIGINT NOT NULL, -- Giá
	status VARCHAR(20) DEFAULT 'Pending', 
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP, -- Thời gian tạo
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, -- Thời gian cập nhật
    created_by BIGINT, -- Người tạo
    deleted_by BIGINT, -- Người xóa
    isDelete TINYINT(1) DEFAULT 0, -- Trạng thái xóa mềm
	FOREIGN KEY (product_id) REFERENCES Products(id) ,
    FOREIGN KEY (created_by) REFERENCES Users(id),
    FOREIGN KEY (deleted_by) REFERENCES Users(id) ,
    INDEX idx_product_id (product_id)
);

-- Bảng Wishlists - Lưu trữ danh sách sản phẩm yêu thích của người dùng
CREATE TABLE IF NOT EXISTS Wishlists (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,              
    product_id BIGINT NOT NULL,           
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP, 
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, 
    UNIQUE KEY unique_wishlist_item (user_id, product_id),
    FOREIGN KEY (user_id) REFERENCES Users(id) ON DELETE CASCADE,   
    FOREIGN KEY (product_id) REFERENCES Products(id) ON DELETE CASCADE 
);

-- Bảng Transactions - Quản lý giao dịch mua hàng
CREATE TABLE IF NOT EXISTS Transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    variant_id BIGINT NOT NULL,
    quantity BIGINT NOT NULL DEFAULT 1,
    amount BIGINT NOT NULL,
    commission BIGINT NOT NULL,
    coinAdmin BIGINT NOT NULL,
    coinSeller BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'CREATED', -- Trạng thái (VD: CREATED, ESCROW, DISPUTED, RELEASED, COMPLETED)
    escrow_release_date DATETIME(6) NULL, -- Ngày dự kiến giải ngân (VD: NOW() + 3 ngày)
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT,
    deleted_by BIGINT,
    isDelete TINYINT(1) DEFAULT 0,

	FOREIGN KEY (customer_id) REFERENCES Users(id),
    FOREIGN KEY (seller_id) REFERENCES Users(id),
    FOREIGN KEY (product_id) REFERENCES Products(id),
    FOREIGN KEY (variant_id) REFERENCES ProductVariants(id),
    FOREIGN KEY (created_by) REFERENCES Users(id),
    FOREIGN KEY (deleted_by) REFERENCES Users(id)
);

-- Bảng ProductVariantAccounts (Có FK đến Transactions)
CREATE TABLE IF NOT EXISTS ProductVariantAccounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    variant_id BIGINT NOT NULL,
    account_data TEXT NOT NULL,
    status ENUM('Available', 'Sold') NOT NULL DEFAULT 'Available',
    transaction_id BIGINT NULL,
    is_activated TINYINT(1) DEFAULT 0,
    activated_at DATETIME NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT,
    deleted_by BIGINT,
    isDelete TINYINT(1) DEFAULT 0,

    FOREIGN KEY (variant_id) REFERENCES ProductVariants(id) ON DELETE CASCADE,
    FOREIGN KEY (transaction_id) REFERENCES Transactions(id) ON DELETE SET NULL,
    FOREIGN KEY (created_by) REFERENCES Users(id),
    FOREIGN KEY (deleted_by) REFERENCES Users(id),
    INDEX idx_variant_id_status (variant_id, status)
);

-- Bảng CoinDeposits - Cập nhật để tích hợp SePay Webhook
CREATE TABLE IF NOT EXISTS CoinDeposits (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,             -- Mã ID tự tăng
    user_id BIGINT NOT NULL,                           -- Mã người dùng
    amount BIGINT NOT NULL,                           -- Số tiền nạp (VNĐ) từ SePay
    coins_added BIGINT NOT NULL,                       -- Coin được cộng (thường bằng amount)
    status VARCHAR(20) DEFAULT 'Pending',             -- Trạng thái: Pending, Completed, Failed
    sepay_transaction_id BIGINT NULL UNIQUE,           -- ID giao dịch gốc từ SePay (UNIQUE để chống trùng)
    sepay_reference_code VARCHAR(255) NULL,            -- Mã tham chiếu gốc từ SePay
    gateway VARCHAR(100) NULL,                         -- Ngân hàng/Cổng thanh toán từ SePay
    transaction_date DATETIME NULL,                    -- Thời gian giao dịch gốc từ SePay
    content TEXT NULL,                                 -- Nội dung chuyển khoản gốc từ SePay
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,     -- Thời gian tạo bản ghi trong hệ thống
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, -- Thời gian cập nhật bản ghi
    created_by BIGINT,                                 -- Người tạo (thường là user_id hoặc null nếu là hệ thống)
    deleted_by BIGINT,                                 -- Người xóa
    isDelete TINYINT(1) DEFAULT 0,                     -- Trạng thái xóa mềm

    FOREIGN KEY (user_id) REFERENCES Users(id),
    FOREIGN KEY (created_by) REFERENCES Users(id),
    FOREIGN KEY (deleted_by) REFERENCES Users(id),

    INDEX idx_sepay_ref_code (sepay_reference_code)    -- Index để tìm kiếm nhanh theo mã tham chiếu
);

-- Bảng Withdrawals - Quản lý yêu cầu rút tiền của Seller
CREATE TABLE IF NOT EXISTS Withdrawals (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, -- Mã ID tự tăng
    seller_id BIGINT NOT NULL, -- Mã Seller
    bank_info_id BIGINT NOT NULL, -- Mã thông tin ngân hàng
    amount BIGINT NOT NULL, -- Số Coin rút
    status VARCHAR(20) DEFAULT 'Pending', -- Trạng thái: Pending, Approved, Rejected
    bank_name VARCHAR(100), -- Tên ngân hàng
    account_number VARCHAR(50), -- Số tài khoản
    account_name VARCHAR(100), -- Tên người thụ hưởng
    branch VARCHAR(100), -- Chi nhánh
    proof_file VARCHAR(255), -- Bằng chứng chuyển tiền | Lý do từ chối
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP, -- Thời gian tạo
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, -- Thời gian cập nhật
    created_by BIGINT, -- Người tạo
    deleted_by BIGINT, -- Người xóa
    isDelete TINYINT(1) DEFAULT 0, -- Trạng thái xóa mềm
	FOREIGN KEY (seller_id) REFERENCES Users(id) ON DELETE NO ACTION,
    FOREIGN KEY (bank_info_id) REFERENCES SellerBankInfo(id) ON DELETE NO ACTION,
    FOREIGN KEY (created_by) REFERENCES Users(id),
    FOREIGN KEY (deleted_by) REFERENCES Users(id)
);

-- Bảng Complaints - Quản lý khiếu nại
CREATE TABLE IF NOT EXISTS Complaints (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, -- Mã ID tự tăng
    transaction_id BIGINT, -- Mã giao dịch
    customer_id BIGINT NOT NULL, -- Mã khách hàng
    seller_id BIGINT NOT NULL, -- Mã người bán
    description TEXT NOT NULL, -- Mô tả khiếu nại
    evidence TEXT, -- Bằng chứng
    status VARCHAR(20) DEFAULT 'Open', -- Trạng thái: Open, Resolved, Rejected
    resolution TEXT, -- Giải quyết
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP, -- Thời gian tạo
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, -- Thời gian cập nhật
    created_by BIGINT, -- Người tạo
    deleted_by BIGINT, -- Người xóa
    isDelete TINYINT(1) DEFAULT 0, -- Trạng thái xóa mềm
    FOREIGN KEY (transaction_id) REFERENCES Transactions(id) ON DELETE SET NULL,
    FOREIGN KEY (customer_id) REFERENCES Users(id) ON DELETE NO ACTION,
    FOREIGN KEY (seller_id) REFERENCES Users(id) ON DELETE NO ACTION,
    FOREIGN KEY (created_by) REFERENCES Users(id),
    FOREIGN KEY (deleted_by) REFERENCES Users(id)
);

-- Bảng Chats - Quản lý tin nhắn
CREATE TABLE IF NOT EXISTS Chats (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, -- Mã ID tự tăng
    sender_id BIGINT NOT NULL, -- Mã người gửi
    receiver_id BIGINT NOT NULL, -- Mã người nhận
    complaint_id BIGINT, -- Mã khiếu nại liên kết
    message TEXT NOT NULL, -- Nội dung tin nhắn
    file_path VARCHAR(500) NULL,
    file_type VARCHAR(50) NULL,
    file_name VARCHAR(255) NULL,
    file_size BIGINT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP, -- Thời gian tạo
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, -- Thời gian cập nhật
    created_by BIGINT, -- Người tạo
    deleted_by BIGINT, -- Người xóa
    isDelete TINYINT(1) DEFAULT 0, -- Trạng thái xóa mềm
    FOREIGN KEY (sender_id) REFERENCES Users(id) ON DELETE NO ACTION,
    FOREIGN KEY (receiver_id) REFERENCES Users(id) ON DELETE NO ACTION,
    FOREIGN KEY (complaint_id) REFERENCES Complaints(id) ON DELETE SET NULL,
    FOREIGN KEY (created_by) REFERENCES Users(id),
    FOREIGN KEY (deleted_by) REFERENCES Users(id)
);

-- Bảng Reviews - Quản lý đánh giá sản phẩm
CREATE TABLE IF NOT EXISTS Reviews (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, -- Mã ID tự tăng
    product_id BIGINT NOT NULL, -- Mã sản phẩm
    user_id BIGINT NOT NULL, -- Mã người đánh giá
    rating INT NOT NULL CHECK (rating BETWEEN 1 AND 5), -- Xếp hạng
    comment TEXT, -- Nội dung đánh giá
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP, -- Thời gian tạo
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, -- Thời gian cập nhật
    created_by BIGINT, -- Người tạo
    deleted_by BIGINT, -- Người xóa
    isDelete TINYINT(1) DEFAULT 0, -- Trạng thái xóa mềm
    FOREIGN KEY (product_id) REFERENCES Products(id) ON DELETE NO ACTION,
    FOREIGN KEY (user_id) REFERENCES Users(id) ON DELETE NO ACTION,
    FOREIGN KEY (created_by) REFERENCES Users(id),
    FOREIGN KEY (deleted_by) REFERENCES Users(id)
);

-- Bảng Blogs - Quản lý bài viết blog với đếm số lượt thích
CREATE TABLE IF NOT EXISTS Blogs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, -- Mã ID tự tăng
    title VARCHAR(255) NOT NULL, -- Tiêu đề
    content TEXT NOT NULL, -- Nội dung
    image VARCHAR(255), -- Đường dẫn ảnh
    author_id BIGINT NOT NULL, -- Mã tác giả
    views BIGINT DEFAULT 0, -- Lượt xem
    likes BIGINT DEFAULT 0, -- Số lượt thích
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP, -- Thời gian tạo
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, -- Thời gian cập nhật
    created_by BIGINT, -- Người tạo
    deleted_by BIGINT, -- Người xóa
    isDelete TINYINT(1) DEFAULT 0, -- Trạng thái xóa mềm
    FOREIGN KEY (author_id) REFERENCES Users(id) ON DELETE NO ACTION,
    FOREIGN KEY (created_by) REFERENCES Users(id),
    FOREIGN KEY (deleted_by) REFERENCES Users(id)
);

-- Bảng BlogComments - Quản lý bình luận blog
CREATE TABLE IF NOT EXISTS BlogComments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, -- Mã ID tự tăng
    blog_id BIGINT NOT NULL, -- Mã blog
    user_id BIGINT NOT NULL, -- Mã người bình luận
    content TEXT NOT NULL, -- Nội dung bình luận
    parent_comment_id BIGINT, -- Bình luận cha
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP, -- Thời gian tạo
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, -- Thời gian cập nhật
    created_by BIGINT, -- Người tạo
    deleted_by BIGINT, -- Người xóa
    isDelete TINYINT(1) DEFAULT 0, -- Trạng thái xóa mềm
    FOREIGN KEY (blog_id) REFERENCES Blogs(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES Users(id) ON DELETE NO ACTION,
    FOREIGN KEY (parent_comment_id) REFERENCES BlogComments(id) ON DELETE SET NULL,
    FOREIGN KEY (created_by) REFERENCES Users(id),
    FOREIGN KEY (deleted_by) REFERENCES Users(id)
);

-- Bảng Notifications - Quản lý thông báo
CREATE TABLE IF NOT EXISTS Notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, -- Mã ID tự tăng
    user_id BIGINT NOT NULL, -- Mã người dùng liên kết
    title VARCHAR(255) NOT NULL, -- Tiêu đề thông báo
    content TEXT, -- Nội dung thông báo
    status VARCHAR(20) DEFAULT 'Unread',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP, -- Thời gian tạo
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, -- Thời gian cập nhật
    created_by BIGINT, -- Người tạo
    deleted_by BIGINT, -- Người xóa
    isDelete TINYINT(1) DEFAULT 0, -- Trạng thái xóa mềm
    FOREIGN KEY (user_id) REFERENCES Users(id) ON DELETE CASCADE,
    FOREIGN KEY (created_by) REFERENCES Users(id),
    FOREIGN KEY (deleted_by) REFERENCES Users(id)
);

CREATE TABLE IF NOT EXISTS Orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id VARCHAR(255) NULL,
    customer_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    variant_id BIGINT NOT NULL,
    quantity BIGINT NOT NULL DEFAULT 1,
    total_price BIGINT NOT NULL,
    status ENUM('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED') NOT NULL DEFAULT 'PENDING',
    error_message TEXT,
    transaction_id BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    processed_at DATETIME,
    INDEX idx_status (status),
    INDEX idx_customer (customer_id),
    UNIQUE KEY uk_orders_request_id (request_id),
    FOREIGN KEY (customer_id) REFERENCES Users(id),
    FOREIGN KEY (product_id) REFERENCES Products(id),
    FOREIGN KEY (variant_id) REFERENCES ProductVariants(id),
	FOREIGN KEY (transaction_id) REFERENCES Transactions(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Bảng ShopPointPurchases - Ghi lại lịch sử Seller mua points
CREATE TABLE IF NOT EXISTS ShopPointPurchases (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,        
    points_bought BIGINT NOT NULL, 
    coins_spent BIGINT NOT NULL,   
    points_before BIGINT NOT NULL,
    points_after BIGINT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES Users(id) ON DELETE NO ACTION,
    INDEX idx_user_id (user_id)
);

-- Trigger update points with transaction completed
DELIMITER $$

CREATE TRIGGER trg_update_shop_points_after_transaction
AFTER UPDATE ON Transactions
FOR EACH ROW
BEGIN
    IF NEW.status = 'Completed' AND OLD.status != 'Completed' THEN
        UPDATE ShopInfo
        SET points = points + NEW.coinSeller
        WHERE user_id = NEW.seller_id AND isDelete = 0;
        
    END IF;
END $$

DELIMITER ;

DELIMITER $$

CREATE TRIGGER trg_update_shop_level_before_update
BEFORE UPDATE ON ShopInfo
FOR EACH ROW
BEGIN
    DECLARE new_level TINYINT;
    DECLARE new_commission DECIMAL(5,2);
    IF NEW.points <> OLD.points THEN
        
        IF NEW.points >= 50000000 THEN
            SET new_level = 7;
            SET new_commission = 3.50; 
        ELSEIF NEW.points >= 40000000 THEN
            SET new_level = 6;
            SET new_commission = 3.70;
        ELSEIF NEW.points >= 20000000 THEN
            SET new_level = 5;
            SET new_commission = 4.00;
        ELSEIF NEW.points >= 10000000 THEN
            SET new_level = 4;
            SET new_commission = 4.30;
        ELSEIF NEW.points >= 5000000 THEN
            SET new_level = 3;
            SET new_commission = 4.50;
        ELSEIF NEW.points >= 3000000 THEN
            SET new_level = 2;
            SET new_commission = 4.70;
        ELSEIF NEW.points >= 1000000 THEN
            SET new_level = 1;
            SET new_commission = 5.00;
        ELSE
            SET new_level = 0;
            SET new_commission = 0.00;
        END IF;
        SET NEW.shop_level = new_level;
        SET NEW.commission = new_commission;
    END IF;
END $$

DELIMITER ;