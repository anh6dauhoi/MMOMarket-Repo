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
    stock INT DEFAULT 0, -- Tồn kho
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

-- Bảng CoinDeposits - Quản lý giao dịch nạp Coin qua Sepay
CREATE TABLE IF NOT EXISTS CoinDeposits (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, -- Mã ID tự tăng
    user_id BIGINT NOT NULL, -- Mã người dùng
    amount BIGINT NOT NULL, -- Số tiền nạp (VNĐ)
    coins_added BIGINT NOT NULL, -- Coin được cộng (1 VNĐ = 1 Coin)
    status VARCHAR(20) DEFAULT 'Pending', -- Trạng thái: Pending, Completed, Failed
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP, -- Thời gian tạo
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, -- Thời gian cập nhật
    created_by BIGINT, -- Người tạo
    deleted_by BIGINT, -- Người xóa
    isDelete TINYINT(1) DEFAULT 0, -- Trạng thái xóa mềm
    FOREIGN KEY (user_id) REFERENCES Users(id) ON DELETE CASCADE,
    FOREIGN KEY (created_by) REFERENCES Users(id),
    FOREIGN KEY (deleted_by) REFERENCES Users(id)
);

-- Bảng Transactions - Quản lý giao dịch mua hàng
CREATE TABLE IF NOT EXISTS Transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, -- Mã ID tự tăng
    customer_id BIGINT NOT NULL, -- Mã khách hàng
    seller_id BIGINT NOT NULL, -- Mã người bán
    product_id BIGINT NOT NULL, -- Mã sản phẩm
    variant_id BIGINT NOT NULL, -- Mã biến thể
    amount BIGINT NOT NULL, -- Tổng Coin thanh toán
    commission BIGINT NOT NULL, -- Coin hoa hồng khấu trừ
    coins_used BIGINT NOT NULL, -- Coin sử dụng để thanh toán
    status VARCHAR(20) DEFAULT 'Pending', -- Trạng thái: Pending, Completed, Refunded, Cancelled
    escrow_release_date DATETIME, -- Ngày tự động chuyển Coin cho Seller (3 ngày sau)
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP, -- Thời gian tạo
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, -- Thời gian cập nhật
    created_by BIGINT, -- Người tạo
    deleted_by BIGINT, -- Người xóa
    isDelete TINYINT(1) DEFAULT 0, -- Trạng thái xóa mềm
    FOREIGN KEY (customer_id) REFERENCES Users(id) ON DELETE NO ACTION,
    FOREIGN KEY (seller_id) REFERENCES Users(id) ON DELETE NO ACTION,
    FOREIGN KEY (product_id) REFERENCES Products(id) ON DELETE NO ACTION,
    FOREIGN KEY (variant_id) REFERENCES ProductVariants(id) ON DELETE NO ACTION,
    FOREIGN KEY (created_by) REFERENCES Users(id),
    FOREIGN KEY (deleted_by) REFERENCES Users(id)
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
    user_id BIGINT NOT NULL, -- Mã người dùng (Seller)
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
    user_id BIGINT NOT NULL, -- Mã người dùng
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

-- Bảng Notifications - Quản lý thông báo (bỏ cột status)
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

-- Trigger gửi thông báo sau khi hoàn thành đơn hàng
DELIMITER //
CREATE TRIGGER trg_SendOrderNotifications
AFTER UPDATE ON Transactions
FOR EACH ROW
BEGIN
    IF NEW.status = 'Completed' AND OLD.status != 'Completed' THEN
        INSERT INTO Notifications (user_id, title, content, created_at)
        VALUES (NEW.customer_id, 'Transaction Processed', CONCAT('Giao dịch ', NEW.id, ' đã được xử lý.'), CURRENT_TIMESTAMP);
        INSERT INTO Notifications (user_id, title, content, created_at)
        VALUES (NEW.customer_id, 'Order Completed', CONCAT('Đơn hàng ', NEW.id, ' đã hoàn thành.'), CURRENT_TIMESTAMP);
        INSERT INTO Notifications (user_id, title, content, created_at)
        VALUES (NEW.customer_id, 'Coins Deducted', CONCAT('Đã trừ ', NEW.coins_used, ' Coin từ tài khoản.'), CURRENT_TIMESTAMP);
    END IF;
END//
DELIMITER ;

-- Trigger kiểm tra số tiền rút và cảnh báo
DELIMITER //
CREATE TRIGGER trg_CheckWithdrawalAmount
AFTER INSERT ON Withdrawals
FOR EACH ROW
BEGIN
    DECLARE total_coins BIGINT;
    SELECT coins INTO total_coins FROM Users WHERE id = NEW.seller_id;
    IF NEW.amount > total_coins * 0.9 AND (NEW.bank_name IS NULL OR NEW.account_number IS NULL OR NEW.branch IS NULL) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Thông tin ngân hàng không đầy đủ, vượt 90% tổng số tiền. Vui lòng xác nhận!';
    END IF;
END//
DELIMITER ;

-- Trigger cho phép nạp thêm tiền để rút khi tài khoản không đủ
DELIMITER //
CREATE TRIGGER trg_AllowTopUpForWithdrawal
AFTER INSERT ON Withdrawals
FOR EACH ROW
BEGIN
    DECLARE total_coins BIGINT;
    SELECT coins INTO total_coins FROM Users WHERE id = NEW.seller_id;
    IF NEW.amount > total_coins THEN
        INSERT INTO Notifications (user_id, title, content, created_at)
        VALUES (NEW.seller_id, 'Insufficient Funds', CONCAT('Số dư không đủ (', total_coins, ' Coin). Vui lòng nạp thêm ', NEW.amount - total_coins, ' Coin.'), CURRENT_TIMESTAMP);
    END IF;
END//
DELIMITER ;

-- Trigger giữ tiền tạm thời
DELIMITER //
CREATE TRIGGER trg_HoldFunds
AFTER INSERT ON Transactions
FOR EACH ROW
BEGIN
    UPDATE Transactions SET status = 'Held' WHERE id = NEW.id;
END//
DELIMITER ;

-- Trigger cập nhật bằng chứng rút tiền sau khi duyệt
DELIMITER //
CREATE TRIGGER trg_UpdateWithdrawalProof
AFTER UPDATE ON Withdrawals
FOR EACH ROW
BEGIN
    IF NEW.status = 'Completed' AND OLD.status != 'Completed' THEN
        UPDATE Withdrawals SET proof_file = CONCAT('path_to_proof_', id) WHERE id = NEW.id;
    END IF;
END//
DELIMITER ;

-- Trigger tăng lượt xem blog
DELIMITER //
CREATE TRIGGER trg_IncrementBlogViews
AFTER UPDATE ON Blogs
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM inserted WHERE id = NEW.id) THEN
        UPDATE Blogs SET views = views + 1 WHERE id = NEW.id;
    END IF;
END//
DELIMITER ;

-- Trigger ngăn xóa blog khi có bình luận hoặc lượt thích
DELIMITER //
CREATE TRIGGER trg_PreventBlogDelete
BEFORE DELETE ON Blogs
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM BlogComments WHERE blog_id = OLD.id AND isDelete = 0) OR 
       EXISTS (SELECT 1 FROM BlogLikes WHERE blog_id = OLD.id AND isDelete = 0) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Không thể xóa blog khi có bình luận hoặc lượt thích.';
    END IF;
END//
DELIMITER ;

-- Trigger cập nhật Coin khi nạp tiền hoàn thành
DELIMITER //
CREATE TRIGGER trg_UpdateCoinsOnDeposit
AFTER UPDATE ON CoinDeposits
FOR EACH ROW
BEGIN
    IF NEW.status = 'Completed' AND OLD.status != 'Completed' THEN
        UPDATE Users SET coins = coins + NEW.coins_added WHERE id = NEW.user_id;
        INSERT INTO AuditLogs (user_id, action, details, created_at)
        VALUES (NEW.user_id, 'Deposit Coins', CONCAT('Nạp ', NEW.coins_added, ' Coin'), CURRENT_TIMESTAMP);
    END IF;
END//
DELIMITER ;

-- Trigger trừ Coin khi tạo giao dịch
DELIMITER //
CREATE TRIGGER trg_UseCoins
AFTER INSERT ON Transactions
FOR EACH ROW
BEGIN
    IF NEW.coins_used > 0 AND (SELECT coins FROM Users WHERE id = NEW.customer_id) >= NEW.coins_used THEN
        UPDATE Users SET coins = coins - NEW.coins_used WHERE id = NEW.customer_id;
        UPDATE Transactions SET escrow_release_date = DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 3 DAY) WHERE id = NEW.id;
        INSERT INTO AuditLogs (user_id, action, details, created_at)
        VALUES (NEW.customer_id, 'Purchase', CONCAT('Thanh toán ', NEW.coins_used, ' Coin cho sản phẩm ', NEW.product_id, ', biến thể ', NEW.variant_id), CURRENT_TIMESTAMP);
    ELSE
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Số dư Coin không đủ để thực hiện giao dịch.';
    END IF;
END//
DELIMITER ;

-- Trigger tự động chuyển Coin cho Seller sau 3 ngày nếu không có khiếu nại
DELIMITER //
CREATE TRIGGER trg_AutoReleaseEscrow
AFTER UPDATE ON Transactions
FOR EACH ROW
BEGIN
    IF NEW.status = 'Completed' AND OLD.status = 'Pending' AND NEW.escrow_release_date <= CURRENT_TIMESTAMP 
       AND NOT EXISTS (SELECT 1 FROM Complaints WHERE transaction_id = NEW.id AND status = 'Open' AND isDelete = 0) THEN
        UPDATE Users SET coins = coins + (NEW.amount - NEW.commission) WHERE id = NEW.seller_id;
        INSERT INTO AuditLogs (user_id, action, details, created_at)
        VALUES (NEW.seller_id, 'Receive Coins', CONCAT('Nhận ', (NEW.amount - NEW.commission), ' Coin từ giao dịch ', NEW.id), CURRENT_TIMESTAMP);
    END IF;
END//
DELIMITER ;

-- Trigger xử lý yêu cầu rút tiền của Seller
DELIMITER //
CREATE TRIGGER trg_ProcessWithdrawal
AFTER UPDATE ON Withdrawals
FOR EACH ROW
BEGIN
    IF NEW.status = 'Completed' AND OLD.status != 'Completed' THEN
        IF (SELECT coins FROM Users WHERE id = NEW.seller_id) >= NEW.amount THEN
            UPDATE Users SET coins = coins - NEW.amount WHERE id = NEW.seller_id;
            INSERT INTO AuditLogs (user_id, action, details, created_at)
            VALUES (NEW.seller_id, 'Withdraw Coins', CONCAT('Rút ', NEW.amount, ' Coin vào tài khoản ngân hàng ', NEW.account_number), CURRENT_TIMESTAMP);
        ELSE
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Số dư Coin của Seller không đủ để rút.';
        END IF;
    END IF;
END//
DELIMITER ;

-- Trigger ngăn xóa sản phẩm khi đang có giao dịch
DELIMITER //
CREATE TRIGGER trg_PreventProductDelete
BEFORE DELETE ON Products
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM Transactions WHERE product_id = OLD.id AND status IN ('Pending', 'Completed') AND isDelete = 0) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Không thể xóa sản phẩm khi đang có giao dịch.';
    END IF;
END//
DELIMITER ;

-- Trigger ngăn xóa biến thể sản phẩm khi đang có giao dịch
DELIMITER //
CREATE TRIGGER trg_PreventVariantDelete
BEFORE DELETE ON ProductVariants
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM Transactions WHERE variant_id = OLD.id AND status IN ('Pending', 'Completed') AND isDelete = 0) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Không thể xóa biến thể sản phẩm khi đang có giao dịch.';
    END IF;
END//
DELIMITER ;