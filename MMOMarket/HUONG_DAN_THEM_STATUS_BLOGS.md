# HƯỚNG DẪN THÊM CỘT STATUS VÀO BẢNG BLOGS

## ⚡ LỆNH SQL NHANH (KHUYẾN NGHỊ)

**Copy và chạy trực tiếp:**

```sql
USE MMOMarket;
ALTER TABLE Blogs ADD COLUMN status TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Trạng thái: 1=Active, 0=Inactive' AFTER image;
```

**Hoặc chạy file:**
```cmd
mysql -u root -p < quick_add_status.sql
```

---

## Mô tả
Script này thêm cột `status` (boolean/TINYINT) vào bảng Blogs để quản lý trạng thái hiển thị của blog.

## Thông tin cột status
- **Tên database**: `MMOMarket` (KHÔNG phải MMO_System)
- **Tên bảng**: `Blogs`
- **Vị trí**: Sau cột `image`
- **Kiểu dữ liệu**: TINYINT(1) 
- **NOT NULL**: Có (bắt buộc phải có giá trị)
- **Giá trị mặc định**: 1 (Active)
- **Ý nghĩa**: 
  - `1` = Blog đang hoạt động (hiển thị)
  - `0` = Blog bị vô hiệu hóa (ẩn)

## Cách 1: Sử dụng batch script (Windows)

```cmd
cd MMOMarket
run_blog_status_migration.bat
```

Nhập thông tin khi được yêu cầu:
- Host: localhost (mặc định)
- Database: MMOMarket (mặc định)
- User: root (mặc định)
- Password: [nhập password của bạn]

## Cách 2: Chạy trực tiếp với MySQL

**Khuyến nghị - Copy và paste trực tiếp:**

```cmd
mysql -u root -p
```

Sau đó nhập các lệnh:

```sql
USE MMOMarket;
ALTER TABLE Blogs ADD COLUMN status TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Trạng thái: 1=Active, 0=Inactive' AFTER image;
```

**Hoặc chạy từ file:**

```cmd
mysql -u root -p < quick_add_status.sql
```

Hoặc:

```cmd
mysql -u root -p MMOMarket < add_status_to_blogs.sql
```

## Cách 3: Sử dụng phpMyAdmin hoặc MySQL Workbench

1. Mở phpMyAdmin hoặc MySQL Workbench
2. Chọn database `MMOMarket` (KHÔNG phải MMO_System)
3. Chọn bảng `Blogs`
4. Chạy câu lệnh SQL:

```sql
USE MMOMarket;
ALTER TABLE Blogs ADD COLUMN status TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Trạng thái: 1=Active, 0=Inactive' AFTER image;
```

## Kiểm tra kết quả

Sau khi chạy migration, kiểm tra bằng câu lệnh:

```sql
DESCRIBE Blogs;
```

Kết quả phải có cột `status`:

```
+------------+--------------+------+-----+-------------------+-------+
| Field      | Type         | Null | Key | Default           | Extra |
+------------+--------------+------+-----+-------------------+-------+
| id         | bigint       | NO   | PRI | NULL              | auto_increment |
| title      | varchar(255) | NO   |     | NULL              |       |
| content    | text         | NO   |     | NULL              |       |
| image      | varchar(255) | YES  |     | NULL              |       |
| status     | tinyint(1)   | YES  |     | 1                 |       | ← CỘT MỚI
| author_id  | bigint       | NO   | MUL | NULL              |       |
| views      | bigint       | YES  |     | 0                 |       |
| likes      | bigint       | YES  |     | 0                 |       |
| created_at | datetime     | YES  |     | CURRENT_TIMESTAMP |       |
| updated_at | datetime     | YES  |     | CURRENT_TIMESTAMP | on update CURRENT_TIMESTAMP |
| created_by | bigint       | YES  | MUL | NULL              |       |
| deleted_by | bigint       | YES  | MUL | NULL              |       |
| isDelete   | tinyint(1)   | YES  |     | 0                 |       |
+------------+--------------+------+-----+-------------------+-------+
```

## Kiểm tra dữ liệu

```sql
SELECT id, title, status FROM Blogs LIMIT 5;
```

Tất cả các blog hiện có sẽ có `status = 1` (active).

## Sử dụng trong ứng dụng

Sau khi thêm cột, bạn có thể:

### Toggle status của blog (Admin)
```sql
-- Vô hiệu hóa blog
UPDATE Blogs SET status = 0 WHERE id = 1;

-- Kích hoạt lại blog
UPDATE Blogs SET status = 1 WHERE id = 1;
```

### Lọc chỉ hiển thị blog active
```sql
SELECT * FROM Blogs WHERE status = 1 AND isDelete = 0;
```

### Thống kê
```sql
-- Đếm blog active
SELECT COUNT(*) FROM Blogs WHERE status = 1 AND isDelete = 0;

-- Đếm blog inactive
SELECT COUNT(*) FROM Blogs WHERE status = 0 AND isDelete = 0;
```

## Lưu ý

- ✅ Script sử dụng `ADD COLUMN IF NOT EXISTS` nên an toàn khi chạy nhiều lần
- ✅ Không ảnh hưởng đến dữ liệu hiện có
- ✅ Tất cả blog cũ sẽ tự động có status = 1 (active)
- ⚠️ Backup database trước khi chạy migration (khuyến nghị)

## Rollback (nếu cần)

Nếu muốn xóa cột status:

```sql
ALTER TABLE Blogs DROP COLUMN status;
```

**CẢNH BÁO**: Thao tác này sẽ xóa vĩnh viễn dữ liệu status!

