# Hướng Dẫn Sử Dụng Anti-DevTools

## 📋 Tổng Quan

Hệ thống chống mở Developer Tools (F12) đã được tích hợp vào dự án. File script được đặt tại:
- **File JS:** `src/main/resources/static/js/anti-devtools.js`
- **Tích hợp:** `src/main/resources/templates/fragments/header.html`

## 🔧 Cách Bật/Tắt Chức Năng

### ✅ BẬT Chức Năng (Mặc định)

Mở file `anti-devtools.js` và tìm phần cấu hình:

```javascript
const CONFIG = {
    enabled: true,  // ✅ BẬT - Đổi thành true
    // ... các cấu hình khác
};
```

### ❌ TẮT Chức Năng

Mở file `anti-devtools.js` và đổi giá trị:

```javascript
const CONFIG = {
    enabled: false,  // ❌ TẮT - Đổi thành false
    // ... các cấu hình khác
};
```

**Lưu ý:** Sau khi thay đổi, chỉ cần refresh trình duyệt (Ctrl+F5) để áp dụng.

---

## ⚙️ Các Tùy Chọn Cấu Hình

File `anti-devtools.js` có nhiều tùy chọn bạn có thể điều chỉnh:

```javascript
const CONFIG = {
    enabled: true,                    // Bật/tắt toàn bộ chức năng
    blockContextMenu: true,           // Chặn chuột phải
    blockKeyboardShortcuts: true,     // Chặn phím tắt (F12, Ctrl+Shift+I, ...)
    detectDevTools: true,             // Phát hiện DevTools đang mở
    showWarning: true,                // Hiển thị cảnh báo
    warningMessage: 'Developer tools are disabled...',  // Nội dung cảnh báo
    redirectOnPersist: false,         // Chuyển hướng nếu cố tình mở
    redirectUrl: '/security-warning'  // URL chuyển hướng
};
```

### 📝 Giải Thích Các Tùy Chọn

| Tùy Chọn | Mô Tả | Giá Trị |
|----------|-------|---------|
| `enabled` | Bật/tắt toàn bộ script | `true` / `false` |
| `blockContextMenu` | Chặn chuột phải | `true` / `false` |
| `blockKeyboardShortcuts` | Chặn F12, Ctrl+Shift+I, Ctrl+U,... | `true` / `false` |
| `detectDevTools` | Phát hiện DevTools mở bằng kích thước | `true` / `false` |
| `showWarning` | Hiển thị popup cảnh báo | `true` / `false` |
| `warningMessage` | Nội dung thông báo | Chuỗi text |
| `redirectOnPersist` | Chuyển trang sau 3 lần cảnh báo | `true` / `false` |
| `redirectUrl` | Trang đích khi chuyển hướng | URL string |

---

## 🎯 Các Tình Huống Sử Dụng

### 1️⃣ Chặn Hoàn Toàn (Khuyến Nghị cho Production)

```javascript
const CONFIG = {
    enabled: true,
    blockContextMenu: true,
    blockKeyboardShortcuts: true,
    detectDevTools: true,
    showWarning: true,
    redirectOnPersist: false,
    redirectUrl: '/security-warning'
};
```

### 2️⃣ Chỉ Cảnh Báo Nhẹ (Không chặn cứng)

```javascript
const CONFIG = {
    enabled: true,
    blockContextMenu: false,          // Cho phép chuột phải
    blockKeyboardShortcuts: false,    // Cho phép phím tắt
    detectDevTools: true,             // Chỉ phát hiện và cảnh báo
    showWarning: true,
    redirectOnPersist: false,
    redirectUrl: '/security-warning'
};
```

### 3️⃣ Chế Độ Phát Triển (Development Mode)

```javascript
const CONFIG = {
    enabled: false,  // TẮT hoàn toàn khi đang phát triển
    // ... các cấu hình khác không quan trọng
};
```

### 4️⃣ Chế Độ Nghiêm Khắc (Redirect sau 3 lần)

```javascript
const CONFIG = {
    enabled: true,
    blockContextMenu: true,
    blockKeyboardShortcuts: true,
    detectDevTools: true,
    showWarning: true,
    redirectOnPersist: true,          // ✅ BẬT chuyển hướng
    redirectUrl: '/security-warning'  // Cần tạo trang này
};
```

---

## 🚀 Cách Kiểm Tra

1. **Bật chức năng** trong file `anti-devtools.js`
2. **Build lại project** (nếu cần):
   ```bash
   mvn clean package
   ```
3. **Chạy ứng dụng**:
   ```bash
   java -jar target/MMOMarket-0.0.1-SNAPSHOT.jar
   ```
4. **Mở trình duyệt** và truy cập trang web
5. **Thử các hành động**:
   - Nhấn F12
   - Nhấn Ctrl+Shift+I
   - Nhấn chuột phải
   - Thay đổi kích thước DevTools

➡️ Nếu thấy popup cảnh báo màu đỏ = ✅ Hoạt động

---

## 🔴 Lưu Ý Quan Trọng

### ⚠️ Hạn Chế

1. **Không thể chặn 100%**: Người dùng có kinh nghiệm vẫn có thể:
   - Vô hiệu hóa JavaScript
   - Dùng Proxy/Burp Suite
   - Mở DevTools trước khi trang load
   - Bypass bằng cách chỉnh sửa code

2. **Có thể gây khó chịu**: User thông thường có thể vô tình nhấn F12

3. **Không thay thế bảo mật thật**: 
   - Vẫn phải bảo mật backend
   - Vẫn phải validate dữ liệu server-side
   - Chỉ là lớp bảo vệ "răn đe"

### ✅ Nên Làm

- ✔️ Sử dụng cho trang có nội dung nhạy cảm
- ✔️ Kết hợp với bảo mật backend
- ✔️ Tắt trong môi trường development
- ✔️ Test kỹ trước khi deploy

### ❌ Không Nên

- ✖️ Dựa hoàn toàn vào script này cho bảo mật
- ✖️ Bật trong môi trường dev/test
- ✖️ Quá nghiêm khắc với user thông thường

---

## 🛠️ Troubleshooting

### ❓ Script không hoạt động?

**Kiểm tra:**
1. File `anti-devtools.js` có tồn tại trong `src/main/resources/static/js/`?
2. Đã include trong `header.html`?
   ```html
   <script defer th:src="@{/js/anti-devtools.js}"></script>
   ```
3. `CONFIG.enabled = true`?
4. Đã build lại project?
5. Đã clear cache trình duyệt? (Ctrl+F5)

### ❓ Làm sao tạm thời tắt để debug?

**Cách 1:** Đổi `enabled: false` trong file `anti-devtools.js`

**Cách 2:** Comment dòng include trong `header.html`:
```html
<!-- <script defer th:src="@{/js/anti-devtools.js}"></script> -->
```

**Cách 3:** Mở DevTools TRƯỚC khi load trang, sau đó disable JavaScript trong DevTools settings

### ❓ Muốn chỉ chặn ở một số trang cụ thể?

Sửa file `header.html`, thêm điều kiện:

```html
<!-- Chỉ bật anti-devtools cho trang customer/seller -->
<script defer th:src="@{/js/anti-devtools.js}" 
        th:if="${#strings.startsWith(uri, '/customer') or #strings.startsWith(uri, '/seller')}">
</script>
```

---

## 📞 Hỗ Trợ

Nếu gặp vấn đề, kiểm tra:
1. Console log có thông báo `[Anti-DevTools] Protection Active`?
2. File JS có lỗi syntax không?
3. Có conflict với script khác không?

---

## 📊 Các Phím Tắt Bị Chặn

| Phím Tắt | Chức Năng | Hệ Điều Hành |
|----------|-----------|--------------|
| F12 | Mở DevTools | All |
| Ctrl+Shift+I | Mở DevTools | Windows/Linux |
| Ctrl+Shift+J | Mở Console | Windows/Linux |
| Ctrl+Shift+C | Inspect Element | Windows/Linux |
| Ctrl+U | View Source | Windows/Linux |
| Cmd+Option+I | Mở DevTools | macOS |
| Cmd+Option+J | Mở Console | macOS |
| Cmd+Option+C | Inspect Element | macOS |

---

## 📝 Changelog

- **v1.0** (2025-11-02): Phiên bản đầu tiên với đầy đủ tính năng

---

**🎉 Chúc bạn sử dụng hiệu quả!**

