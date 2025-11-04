# Hướng dẫn Migration Database - Thêm cột Status cho Categories

## Mục đích
Thêm cột `status` vào bảng `Categories` để quản lý trạng thái Active/Inactive của các danh mục.

## Chi tiết thay đổi

### Cột mới: `status`
- **Kiểu dữ liệu**: `TINYINT(1)`
- **Giá trị mặc định**: `1` (Active)
- **Vị trí**: Sau cột `type`
- **Ý nghĩa**: 
  - `1` = Active (Danh mục đang hoạt động)
  - `0` = Inactive (Danh mục bị vô hiệu hóa)

## Cách chạy Migration

### Phương pháp 1: Sử dụng MySQL Command Line

```bash
mysql -u root -p MMO_System < migration_add_status_to_categories.sql
```

### Phương pháp 2: Sử dụng MySQL Workbench hoặc phpMyAdmin

1. Mở MySQL Workbench hoặc phpMyAdmin
2. Chọn database `MMO_System`
3. Mở file `migration_add_status_to_categories.sql`
4. Chạy toàn bộ script

### Phương pháp 3: Chạy thủ công (nếu migration script lỗi)

```sql
USE MMO_System;

ALTER TABLE Categories 
ADD COLUMN status TINYINT(1) DEFAULT 1 
COMMENT 'Trạng thái hoạt động: 1 - Active, 0 - Inactive' 
AFTER type;

-- Cập nhật giá trị mặc định cho các bản ghi hiện có
UPDATE Categories SET status = 1 WHERE status IS NULL;
```

## Kiểm tra sau khi Migration

Chạy câu lệnh sau để kiểm tra:

```sql
DESCRIBE Categories;
```

Bạn sẽ thấy cột `status` trong cấu trúc bảng.

## Rollback (Nếu cần)

Nếu muốn xóa cột status:

```sql
ALTER TABLE Categories DROP COLUMN status;
```

## Ảnh hưởng đến ứng dụng

✅ **Không ảnh hưởng** - Ứng dụng Java đã được chuẩn bị sẵn:
- Entity `Category.java` đã có field `status`
- Service layer đã có method `toggleCategoryStatus()`
- Controller đã có endpoint `/admin/categories/{id}/toggle-status`
- Frontend đã có nút Active/Inactive

## Ghi chú

- Migration script được thiết kế để **an toàn** - không thêm cột nếu đã tồn tại
- Tất cả danh mục hiện có sẽ được set `status = 1` (Active) theo mặc định
- Cột này độc lập với `isDelete` (soft delete)

---

**Ngày tạo**: 2025-11-04  
**Phiên bản**: 1.0

