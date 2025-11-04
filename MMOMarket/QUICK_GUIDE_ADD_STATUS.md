# 🚀 HƯỚNG DẪN THÊM CỘT STATUS VÀO CATEGORIES

## Cột status sẽ thêm vào:
- **Tên**: `status`
- **Kiểu**: `TINYINT(1)` (boolean trong MySQL)
- **Default**: `1` (Active)
- **Vị trí**: Sau cột `type`

## 📋 Giá trị:
- `1` hoặc `true` = **Active** (Danh mục đang hoạt động)
- `0` hoặc `false` = **Inactive** (Danh mục tạm ngưng)

---

## ⚡ CÁCH CHẠY - Chọn 1 trong 3 cách

### 🔷 CÁCH 1: Qua MySQL Command Line (Khuyến nghị)

```bash
# Mở Command Prompt hoặc Terminal
cd "C:\Users\ADMIN\Desktop\New folder (2)\MMOMarket-Repo\MMOMarket"

# Chạy migration
mysql -u root -p MMO_System < add_status_column.sql

# Nếu báo lỗi, thử:
mysql -u root -p MMO_System < add_status_simple.sql
```

### 🔷 CÁCH 2: Qua MySQL Workbench

1. Mở MySQL Workbench
2. Connect vào database `MMO_System`
3. File → Open SQL Script → Chọn file `add_status_column.sql`
4. Click nút ⚡ Execute
5. Kiểm tra kết quả

### 🔷 CÁCH 3: Copy-Paste vào phpMyAdmin hoặc MySQL Workbench

```sql
USE MMO_System;

ALTER TABLE Categories 
ADD COLUMN status TINYINT(1) NOT NULL DEFAULT 1 
COMMENT 'Trạng thái: 1=Active, 0=Inactive' 
AFTER type;

UPDATE Categories SET status = 1;

DESCRIBE Categories;
```

---

## ✅ KIỂM TRA SAU KHI CHẠY

### Xem cấu trúc bảng:
```sql
DESCRIBE Categories;
```

**Kết quả mong đợi** - Bạn sẽ thấy dòng:
```
status | tinyint(1) | NO | | 1 |
```

### Xem dữ liệu:
```sql
SELECT id, name, type, status, isDelete FROM Categories LIMIT 5;
```

---

## 🔧 NẾU GẶP LỖI

### Lỗi: "Duplicate column name 'status'"
➡️ **Nguyên nhân**: Cột đã tồn tại rồi
✅ **Giải pháp**: Không cần làm gì, đã OK!

### Lỗi: "Table 'Categories' doesn't exist"
➡️ **Nguyên nhân**: Chưa tạo database
✅ **Giải pháp**: Chạy file `MMOMarket.sql` trước

### Lỗi: "Access denied"
➡️ **Nguyên nhân**: Không có quyền
✅ **Giải pháp**: Dùng user `root` hoặc user có quyền ALTER TABLE

---

## 📦 FILES MIGRATION

1. ✅ `add_status_column.sql` - Script chính (có kiểm tra)
2. ✅ `add_status_simple.sql` - Script đơn giản (backup)
3. ✅ `MMOMarket.sql` - Đã update (dùng cho DB mới)

---

## 🎯 SAU KHI CHẠY MIGRATION

Ứng dụng Java sẽ tự động hoạt động với cột status vì:
- ✅ Entity `Category.java` đã có field `status`
- ✅ Service đã có method `toggleCategoryStatus()`
- ✅ API endpoint sẵn sàng: `PUT /admin/categories/{id}/toggle-status`

**Không cần restart app** nếu đang chạy, JPA sẽ tự detect cột mới!

---

## 📞 HỖ TRỢ

Nếu gặp vấn đề, check:
1. MySQL đang chạy chưa?
2. Database `MMO_System` đã tồn tại chưa?
3. User có quyền ALTER TABLE chưa?

---

**Cập nhật**: 2024-11-04  
**Trạng thái**: ✅ READY TO RUN

