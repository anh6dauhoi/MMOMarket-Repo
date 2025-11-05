# Hướng dẫn sử dụng bộ lọc Status cho Blog Management

## Thay đổi

Đã thay đổi bộ lọc `status` từ **"popular/new"** sang **"Active/Inactive"** để lọc theo trạng thái thực tế của blog.

## Các giá trị status mới

### 1. **All** (Mặc định)
Hiển thị tất cả blogs (cả Active và Inactive)

**URL:**
```
GET /admin/blogs?status=All
```

### 2. **Active** 
Chỉ hiển thị blogs đang hoạt động (status = true)

**URL:**
```
GET /admin/blogs?status=Active
```

### 3. **Inactive**
Chỉ hiển thị blogs đã bị vô hiệu hóa (status = false)

**URL:**
```
GET /admin/blogs?status=Inactive
```

## Cách sử dụng

### Trong URL
```
http://localhost:8080/admin/blogs?page=0&status=Active&search=keyword&sort=date_desc
```

### Tham số URL

| Tham số | Mô tả | Giá trị mặc định | Giá trị hợp lệ |
|---------|-------|------------------|----------------|
| `page` | Trang hiện tại | 0 | 0, 1, 2, ... |
| `status` | Lọc theo trạng thái | All | All, Active, Inactive |
| `search` | Tìm kiếm theo title/content | "" | Bất kỳ chuỗi nào |
| `sort` | Sắp xếp | date_desc | likes_asc, likes_desc, views_asc, views_desc, date_asc, date_desc |

## Ví dụ sử dụng

### 1. Lấy tất cả blogs Active
```
GET /admin/blogs?status=Active
```

### 2. Lấy tất cả blogs Inactive
```
GET /admin/blogs?status=Inactive
```

### 3. Tìm kiếm blogs Active có từ khóa "tutorial"
```
GET /admin/blogs?status=Active&search=tutorial
```

### 4. Lấy blogs Inactive, sắp xếp theo lượt xem giảm dần
```
GET /admin/blogs?status=Inactive&sort=views_desc
```

### 5. Trang 2, chỉ blogs Active, tìm "guide", sắp xếp theo likes
```
GET /admin/blogs?page=1&status=Active&search=guide&sort=likes_desc
```

## Cải tiến so với trước

### Trước đây:
- ❌ `status=popular`: Lọc blogs có likes >= 100 (không liên quan đến trạng thái thực)
- ❌ `status=new`: Lọc blogs trong 7 ngày gần đây (không liên quan đến trạng thái)
- ❌ Không thể lọc theo trạng thái Active/Inactive của blog

### Bây giờ:
- ✅ `status=Active`: Lọc blogs đang hiển thị (status = true)
- ✅ `status=Inactive`: Lọc blogs bị ẩn (status = false)
- ✅ `status=All`: Hiển thị tất cả
- ✅ Phù hợp với chức năng quản lý blog của admin

## Tìm kiếm nâng cao

Tìm kiếm đã được cải tiến để tìm trong cả **title** và **content**:

```java
// Trước: chỉ tìm trong title
AND LOWER(b.title) LIKE LOWER(:search)

// Bây giờ: tìm trong cả title và content
AND (LOWER(b.title) LIKE LOWER(:search) OR LOWER(b.content) LIKE LOWER(:search))
```

## Trong HTML/JavaScript

### Dropdown filter
```html
<select name="status" onchange="filterBlogs()">
    <option value="All">Tất cả</option>
    <option value="Active">Đang hoạt động</option>
    <option value="Inactive">Đã vô hiệu hóa</option>
</select>
```

### JavaScript
```javascript
function filterBlogs() {
    const status = document.querySelector('select[name="status"]').value;
    const search = document.querySelector('input[name="search"]').value;
    const sort = document.querySelector('select[name="sort"]').value;
    
    window.location.href = `/admin/blogs?status=${status}&search=${search}&sort=${sort}`;
}
```

## Response Model Attributes

Controller trả về các attributes sau cho view:

```java
model.addAttribute("blogs", blogPage.getContent());           // List<Blog>
model.addAttribute("currentPage", page);                      // int
model.addAttribute("currentSearch", search);                  // String
model.addAttribute("currentStatus", status);                  // String: All/Active/Inactive
model.addAttribute("currentSort", sort);                      // String
model.addAttribute("totalPages", blogPage.getTotalPages());   // int
model.addAttribute("pageTitle", "Blogs Management");          // String
```

## Thống kê

### Đếm blogs theo status

```sql
-- Active blogs
SELECT COUNT(*) FROM Blogs WHERE status = 1 AND isDelete = 0;

-- Inactive blogs
SELECT COUNT(*) FROM Blogs WHERE status = 0 AND isDelete = 0;

-- All blogs
SELECT COUNT(*) FROM Blogs WHERE isDelete = 0;
```

## Lưu ý

- ⚠️ Giá trị `status` **không phân biệt hoa thường** (case-insensitive)
- ⚠️ Blogs có `isDelete = true` sẽ **không được hiển thị** dù status là gì
- ✅ Tìm kiếm trong cả title và content
- ✅ Hỗ trợ pagination đầy đủ
- ✅ Performance tốt với COUNT query riêng

## Testing

### Tạo blog Active
```bash
curl -X POST "http://localhost:8080/admin/blogs" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Active Blog",
    "content": "This is active",
    "image": "/uploads/test.png"
  }'
```

### Toggle status về Inactive
```bash
curl -X PUT "http://localhost:8080/admin/blogs/1/toggle-status"
```

### Kiểm tra filter
```bash
# All blogs
curl "http://localhost:8080/admin/blogs?status=All"

# Active only
curl "http://localhost:8080/admin/blogs?status=Active"

# Inactive only
curl "http://localhost:8080/admin/blogs?status=Inactive"
```

