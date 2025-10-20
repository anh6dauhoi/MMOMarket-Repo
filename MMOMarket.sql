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
    shop_status VARCHAR(50) DEFAULT 'Inactive', -- Trạng thái cửa hàng, dùng ENUM để đảm bảo dữ liệu
    shop_level TINYINT UNSIGNED DEFAULT 0,
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
    FOREIGN KEY (user_id) REFERENCES Users(id) ON DELETE CASCADE,
    FOREIGN KEY (created_by) REFERENCES Users(id),
    FOREIGN KEY (deleted_by) REFERENCES Users(id),
    INDEX idx_user_id (user_id) -- Index cho user_id
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
    FOREIGN KEY (category_id) REFERENCES Categories(id) ON DELETE NO ACTION,
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
    status VARCHAR(20) DEFAULT 'Pending', -- Trạng thái Bán hoặc không do seller
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP, -- Thời gian tạo
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, -- Thời gian cập nhật
    created_by BIGINT, -- Người tạo
    deleted_by BIGINT, -- Người xóa
    isDelete TINYINT(1) DEFAULT 0, -- Trạng thái xóa mềm
    FOREIGN KEY (product_id) REFERENCES Products(id) ON DELETE CASCADE,
    FOREIGN KEY (created_by) REFERENCES Users(id),
    FOREIGN KEY (deleted_by) REFERENCES Users(id),
    INDEX idx_product_id (product_id)
);

-- Bảng Transactions - Quản lý giao dịch mua hàng
-- Bảng Transactions (Tạm thời chưa có FK đến ProductVariantAccounts)
CREATE TABLE IF NOT EXISTS Transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    variant_id BIGINT NOT NULL,
    delivered_account_id BIGINT NULL, -- Sẽ thêm FK sau
    amount BIGINT NOT NULL,
    commission BIGINT NOT NULL,
    coins_used BIGINT NOT NULL,
    status VARCHAR(20) DEFAULT 'Pending',
    escrow_release_date DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT,
    deleted_by BIGINT,
    isDelete TINYINT(1) DEFAULT 0,

    FOREIGN KEY (customer_id) REFERENCES Users(id) ON DELETE NO ACTION,
    FOREIGN KEY (seller_id) REFERENCES Users(id) ON DELETE NO ACTION,
    FOREIGN KEY (product_id) REFERENCES Products(id) ON DELETE NO ACTION,
    FOREIGN KEY (variant_id) REFERENCES ProductVariants(id) ON DELETE NO ACTION,
    FOREIGN KEY (created_by) REFERENCES Users(id) ON DELETE SET NULL,
    FOREIGN KEY (deleted_by) REFERENCES Users(id) ON DELETE SET NULL
    -- FK (delivered_account_id) sẽ được thêm bằng ALTER TABLE
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

    FOREIGN KEY (variant_id) REFERENCES ProductVariants(id) ON DELETE NO ACTION, -- Đổi thành NO ACTION
    FOREIGN KEY (transaction_id) REFERENCES Transactions(id) ON DELETE SET NULL,
    FOREIGN KEY (created_by) REFERENCES Users(id) ON DELETE SET NULL,
    FOREIGN KEY (deleted_by) REFERENCES Users(id) ON DELETE SET NULL,

    INDEX idx_variant_id_status (variant_id, status)
);

-- Thêm lại FK cho bảng Transactions
ALTER TABLE Transactions
ADD CONSTRAINT fk_delivered_account
FOREIGN KEY (delivered_account_id) REFERENCES ProductVariantAccounts(id) ON DELETE NO ACTION;

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

    FOREIGN KEY (user_id) REFERENCES Users(id) ON DELETE CASCADE,
    FOREIGN KEY (created_by) REFERENCES Users(id) ON DELETE SET NULL,
    FOREIGN KEY (deleted_by) REFERENCES Users(id) ON DELETE SET NULL,

    INDEX idx_sepay_ref_code (sepay_reference_code)    -- Index để tìm kiếm nhanh theo mã tham chiếu
);

-- Bảng Withdrawals - Quản lý yêu cầu rút tiền của Seller
CREATE TABLE IF NOT EXISTS Withdrawals (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, -- Mã ID tự tăng
    seller_id BIGINT NOT NULL, -- Mã Seller
    bank_info_id BIGINT NOT NULL, -- Mã thông tin ngân hàng
    amount BIGINT NOT NULL, -- Số Coin rút
    status VARCHAR(20) DEFAULT 'Pending', -- Trạng thái: Pending, Approved, Rejected, Completed
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

-- Bảng Commissions - Quản lý hoa hồng theo cửa hàng, mặc định 5% nếu không cấu hình
CREATE TABLE IF NOT EXISTS Commissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, -- Mã ID tự tăng
    user_id BIGINT NOT NULL UNIQUE, -- Mã người dùng (Seller)
    percentage DECIMAL(5,2) NOT NULL DEFAULT 5.00, -- Tỷ lệ hoa hồng, mặc định 5%
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP, -- Thời gian tạo
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, -- Thời gian cập nhật
    created_by BIGINT, -- Người tạo
    deleted_by BIGINT, -- Người xóa
    isDelete TINYINT(1) DEFAULT 0, -- Trạng thái xóa mềm
    FOREIGN KEY (user_id) REFERENCES Users(id) ON DELETE CASCADE,
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

-- Bảng SellerRegistrations - Quản lý yêu cầu đăng ký bán hàng
CREATE TABLE IF NOT EXISTS SellerRegistrations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, -- Mã ID tự tăng
    user_id BIGINT NOT NULL UNIQUE, -- Mã người dùng
    shop_name VARCHAR(255) NOT NULL, -- Tên cửa hàng
    description TEXT, -- Mô tả
    contract VARCHAR(255), -- Hợp đồng
    signed_contract VARCHAR(255), -- Hợp đồng đã ký
    status VARCHAR(50) DEFAULT 'Pending', -- Trạng thái
    reason VARCHAR(255), -- Lý do từ chối
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP, -- Thời gian tạo
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, -- Thời gian cập nhật
    created_by BIGINT, -- Người tạo
    deleted_by BIGINT, -- Người xóa
    isDelete TINYINT(1) DEFAULT 0, -- Trạng thái xóa mềm
    FOREIGN KEY (user_id) REFERENCES Users(id) ON DELETE CASCADE,
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