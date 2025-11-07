# Hướng dẫn Upload File Bất Đồng Bộ với Google Drive

## Tổng quan
Hệ thống đã được cải tiến để giải quyết 2 vấn đề chính:
1. **Upload chậm**: File được lưu local trước (nhanh), sau đó upload lên Google Drive bất đồng bộ
2. **Không xem được ảnh**: Đã thay đổi link từ `uc?export=download` sang `uc?export=view&id=` để hiển thị trực tiếp

## Luồng hoạt động

```
┌─────────────┐
│ User Upload │
└──────┬──────┘
       │
       ▼
┌──────────────────────┐
│ Lưu file local ngay  │ ◄─── Trả về ngay lập tức (nhanh)
└──────┬───────────────┘
       │
       ▼
┌──────────────────────────┐
│ Upload Google Drive async│ ◄─── Chạy background (không blocking)
└──────┬───────────────────┘
       │
       ▼
┌──────────────────────────┐
│ Cập nhật URL trong DB    │ ◄─── Thay local URL bằng Drive URL
└──────┬───────────────────┘
       │
       ▼
┌──────────────────────┐
│ Xóa file local       │ ◄─── Tiết kiệm dung lượng
└──────────────────────┘
```

## Các thay đổi chính

### 1. AsyncConfig.java
**Thêm executor cho file upload:**
```java
@Bean(name = "fileUploadExecutor")
public Executor fileUploadExecutor() {
    ThreadPoolTaskExecutor t = new ThreadPoolTaskExecutor();
    t.setCorePoolSize(3);
    t.setMaxPoolSize(10);
    t.setQueueCapacity(100);
    t.setThreadNamePrefix("file-upload-");
    t.initialize();
    return t;
}
```

**Cấu hình:**
- Core pool: 3 threads (xử lý 3 file cùng lúc)
- Max pool: 10 threads (tối đa 10 file)
- Queue: 100 tasks (hàng đợi 100 file)

### 2. GoogleDriveService.java
**Thay đổi link format:**
```java
// CŨ (không xem được ảnh):
String downloadLink = "https://drive.google.com/uc?export=download&id=" + fileId;

// MỚI (xem được ảnh trực tiếp):
String viewLink = "https://drive.google.com/uc?export=view&id=" + fileId;
```

### 3. FileUploadTask.java (MỚI)
**Service xử lý async upload và cập nhật database:**

#### Phương thức 1: Upload với entity tracking
```java
@Async("fileUploadExecutor")
@Transactional
public void uploadToGoogleDriveAsync(String localFilePath, String folderType, 
                                     String entityType, Long entityId, 
                                     String customFilename)
```

**Chức năng:**
1. Upload file từ local lên Google Drive
2. Cập nhật URL trong database (Chat, Complaint, Product, Blog)
3. Xóa file local sau khi upload thành công

**Hỗ trợ entity types:**
- `CHAT`: Cập nhật `Chat.filePath`
- `COMPLAINT`: Cập nhật `Complaint.evidence` (JSON)
- `PRODUCT`: Cập nhật `Product.image`
- `BLOG`: Cập nhật `Blog.content` (thay thế URL trong nội dung)

#### Phương thức 2: Upload không có entity
```java
@Async("fileUploadExecutor")
public void uploadToGoogleDriveAsync(String localFilePath, String folderType, 
                                     String customFilename)
```

**Chức năng:**
- Upload file lên Drive
- Xóa file local
- Không cập nhật database (dùng cho withdrawal proofs, v.v.)

### 4. FileStorageService.java
**Cập nhật upload strategy:**

#### Phương thức uploadFile (cơ bản)
```java
public String uploadFile(MultipartFile file, String folderType, Long userId)
```

**Flow:**
1. Lưu file local ngay → Trả về local URL
2. Schedule async upload lên Drive (nếu enabled)
3. Xóa file local sau khi upload xong

#### Phương thức uploadFileWithEntityTracking (nâng cao)
```java
public String uploadFileWithEntityTracking(MultipartFile file, String folderType, 
                                           Long userId, String entityType, Long entityId)
```

**Flow:**
1. Lưu file local ngay → Trả về local URL
2. Schedule async upload lên Drive (nếu enabled)
3. Cập nhật URL trong database
4. Xóa file local sau khi upload xong

## Cách sử dụng

### 1. Upload file cơ bản (không cần track entity)
```java
// Ví dụ: Upload withdrawal proof
String fileUrl = fileStorageService.uploadFile(file, "withdrawals", adminId);
// Trả về ngay: /uploads/withdrawals/timestamp-uuid.jpg
// Background: Upload lên Drive và xóa local
```

### 2. Upload file với entity tracking
```java
// Ví dụ: Upload chat file
String fileUrl = fileStorageService.uploadFileWithEntityTracking(
    file, 
    "chat",           // folderType
    userId,           // userId
    "Chat",           // entityType
    chatId            // entityId
);
// Trả về ngay: /uploads/chat/timestamp-uuid.jpg
// Background: 
//   1. Upload lên Drive
//   2. Cập nhật Chat.filePath với Drive URL
//   3. Xóa local file
```

### 3. Upload product image
```java
String imageUrl = fileStorageService.uploadFileWithEntityTracking(
    file, 
    "products", 
    userId, 
    "Product", 
    productId
);
```

### 4. Upload complaint evidence
```java
String evidenceUrl = fileStorageService.uploadFileWithEntityTracking(
    file, 
    "complaints", 
    customerId, 
    "Complaint", 
    complaintId
);
```

## Ưu điểm

### 1. Response nhanh
- File được lưu local ngay lập tức
- User không phải chờ upload Google Drive
- Trải nghiệm mượt mà hơn

### 2. Không blocking
- Upload Drive chạy background
- Không ảnh hưởng đến request chính
- Xử lý được nhiều file cùng lúc

### 3. Tự động cập nhật
- URL trong database được cập nhật tự động
- Không cần code thêm ở controller
- Transparent đối với client

### 4. Tiết kiệm dung lượng
- File local được xóa sau khi upload Drive thành công
- Chỉ giữ file trên Drive
- Giảm chi phí storage

### 5. Fallback an toàn
- Nếu Drive upload fail, vẫn giữ file local
- Database không bị update URL sai
- User vẫn truy cập được file

## Logging

Hệ thống có logging chi tiết để theo dõi:

```
=== File Upload Request with Entity Tracking ===
Entity: Chat ID: 123
✓ File saved locally: /uploads/chat/1699300000000-uuid.jpg
→ Scheduling async upload to Google Drive with DB update
✓ Async upload with DB update scheduled

[Background Thread]
=== Starting Async Google Drive Upload ===
Local file: uploads/chat/1699300000000-uuid.jpg
Folder type: chat
Entity type: Chat
Entity ID: 123
✓ File uploaded to Google Drive: https://drive.google.com/uc?export=view&id=ABC123
✓ Database updated with Drive URL
✓ Local file deleted: uploads/chat/1699300000000-uuid.jpg
=== Async Upload Complete ===
```

## Cấu hình

### application.properties
```properties
# Enable/disable Google Drive
google.drive.enabled=true

# Credentials file path
google.drive.credentials.file=credentials/credentials.json

# Root folder ID (optional)
google.drive.folder.id=

# Folder names
google.drive.folder.chat=Chat Files
google.drive.folder.complaints=Complaint Evidence
google.drive.folder.products=Product Images
google.drive.folder.blogs=Blog Images
google.drive.folder.withdrawals=Withdrawal Proofs
```

## Xử lý lỗi

### 1. Google Drive disabled
- File được lưu local
- Không có background upload
- Vẫn hoạt động bình thường

### 2. Upload Drive thất bại
- File local được giữ lại
- Database không được cập nhật
- Log warning để admin biết

### 3. Database update thất bại
- File local được giữ lại
- Drive upload vẫn hoàn thành
- Admin cần fix manual

### 4. Local file không tồn tại
- Bỏ qua background upload
- Log error
- Không ảnh hưởng hệ thống

## Best Practices

### 1. Validation trước khi upload
```java
// Validate file size and type
fileStorageService.validateFile(
    file, 
    10 * 1024 * 1024,           // 10MB max
    "image/jpeg", "image/png"   // Allowed types
);
```

### 2. Xử lý trong controller
```java
try {
    String fileUrl = fileStorageService.uploadFileWithEntityTracking(
        file, "chat", userId, "Chat", chatId
    );
    // Use fileUrl immediately (local path)
    // Drive upload happens in background
} catch (IOException e) {
    // Handle error
}
```

### 3. Transaction safety
```java
@Transactional
public void createChatWithFile(MultipartFile file) {
    // 1. Create chat entity first
    Chat chat = chatRepository.save(newChat);
    
    // 2. Upload file with entity ID
    String fileUrl = fileStorageService.uploadFileWithEntityTracking(
        file, "chat", userId, "Chat", chat.getId()
    );
    
    // 3. Set URL and save
    chat.setFilePath(fileUrl);
    chatRepository.save(chat);
}
```

## Monitoring

### Check async executor status
```java
@Autowired
private ThreadPoolTaskExecutor fileUploadExecutor;

public void checkStatus() {
    log.info("Active threads: {}", fileUploadExecutor.getActiveCount());
    log.info("Queue size: {}", fileUploadExecutor.getThreadPoolExecutor().getQueue().size());
}
```

### Check pending uploads
- Kiểm tra folder `uploads/` để xem file nào chưa upload Drive
- File local = Đang chờ upload hoặc upload failed
- Không có file local = Đã upload Drive thành công

## Troubleshooting

### File không xem được trên Drive
**Nguyên nhân**: Permission chưa set đúng

**Giải pháp**: Đảm bảo code set permission "anyone" "reader"

### Upload chậm vẫn
**Nguyên nhân**: Async không hoạt động

**Kiểm tra**:
1. `@EnableAsync` có trong `AsyncConfig`?
2. `@Async("fileUploadExecutor")` có đúng tên?
3. Method async có `public` không?
4. Method async có được gọi từ bean khác không? (không được self-invoke)

### File local không bị xóa
**Nguyên nhân**: Database update failed

**Giải pháp**: Check log để tìm nguyên nhân, fix và chạy lại

### Database không được update
**Nguyên nhân**: Transaction không hoạt động

**Giải pháp**: Đảm bảo `@Transactional` có trong method async

## Kết luận

Hệ thống mới giải quyết được:
- ✅ Upload nhanh (local first)
- ✅ Xem được ảnh trên Drive (view link)
- ✅ Tự động cập nhật database
- ✅ Tự động xóa file local
- ✅ Không blocking user request
- ✅ Có fallback an toàn
- ✅ Logging chi tiết

Người dùng sẽ có trải nghiệm tốt hơn với response time nhanh, trong khi hệ thống tự động xử lý việc upload Drive và quản lý storage ở background.

