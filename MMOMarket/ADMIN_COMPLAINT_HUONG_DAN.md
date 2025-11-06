# 🎯 COMPLAINT MANAGEMENT CHO ADMIN - TÀI LIỆU HƯỚNG DẪN

## 📋 TỔNG QUAN

Đã hoàn thành việc tạo giao diện quản lý khiếu nại (Complaint Management) cho Admin với đầy đủ tính năng theo đúng luồng nghiệp vụ được mô tả trong tài liệu `COMPLAINT_WORKFLOW.md`.

---

## 🎨 CÁC TRANG ĐÃ TẠO

### 1. Trang Danh Sách Khiếu Nại
**File:** `admin/complaint-management.html`
**URL:** `/admin/complaints`

#### ✨ Tính Năng:
- **Bộ lọc mạnh mẽ:**
  - 🔍 Tìm kiếm theo ID, tên customer, tên seller
  - 📊 Lọc theo trạng thái (7 trạng thái)
  - 📝 Lọc theo loại khiếu nại (4 loại)
  - 🔄 Sắp xếp linh hoạt

- **Hiển thị thông tin:**
  - ID khiếu nại với màu đỏ nổi bật
  - Thông tin Customer (avatar + tên + email)
  - Thông tin Seller (avatar + tên + email)
  - Loại khiếu nại với icon và màu phù hợp
  - Trạng thái với badge màu sắc
  - Ngày giờ tạo
  - Nút hành động (View, Resolve)

- **Giao diện:**
  - 🖥️ Desktop: Bảng đầy đủ với tất cả cột
  - 📱 Mobile: Card layout dễ sử dụng
  - 🎨 Phân trang đẹp mắt
  - 📊 Thống kê: Tổng số + Số escalated

#### 🎨 Màu Sắc Trạng Thái:
```
NEW (Mới)                    → 🔵 Xanh dương
IN_PROGRESS (Đang xử lý)     → 🟡 Vàng
PENDING_CONFIRMATION (Chờ)    → 🟠 Cam
ESCALATED (Lên Admin)         → 🔴 Đỏ (Ưu tiên cao!)
RESOLVED (Đã giải quyết)     → 🟢 Xanh lá
CLOSED_BY_ADMIN (Admin đóng)  → ⚫ Xám
CANCELLED (Đã hủy)            → ⚫ Xám
```

#### 🏷️ Màu Sắc Loại Khiếu Nại:
```
ITEM_NOT_WORKING         → 🔧 Cam (Item không hoạt động)
ITEM_NOT_AS_DESCRIBED    → 📄 Tím (Không đúng mô tả)
FRAUD_SUSPICION          → ⚠️ Đỏ (Nghi ngờ lừa đảo)
OTHER                    → ❓ Xám (Khác)
```

---

### 2. Trang Chi Tiết Khiếu Nại
**File:** `admin/complaint-detail.html`
**URL:** `/admin/complaints/{id}`

#### ✨ Tính Năng:

**Cột Trái (Thông tin chính):**
1. **Banner Cảnh Báo** (nếu escalated)
   - Màu đỏ nổi bật
   - Icon cảnh báo
   - Nhắc nhở admin review kỹ

2. **Thông Tin Khiếu Nại**
   - Loại khiếu nại
   - Mã giao dịch liên quan
   - Mô tả chi tiết
   - Bằng chứng (links có thể click)

3. **Phản Hồi Của Seller** (nếu có)
   - Hành động: APPROVE/REJECT
   - Nội dung phản hồi
   - Thời gian phản hồi

4. **Quyết Định Của Admin** (nếu đã đóng)
   - Loại quyết định
   - Ghi chú chi tiết
   - Thời gian quyết định

5. **Lịch Sử Chat**
   - Xem tất cả tin nhắn
   - Avatar và tên người gửi
   - Thời gian gửi
   - Scroll được

**Cột Phải (Thông tin bên liên quan):**

1. **Card Customer** (Màu xanh dương)
   - Avatar tròn
   - Tên + Email
   - Số điện thoại
   - Nút "View Profile"

2. **Card Seller** (Màu xanh lá)
   - Avatar tròn
   - Tên + Email
   - Số điện thoại
   - Nút "View Profile"

3. **Card Transaction** (Màu tím)
   - Mã giao dịch
   - Số tiền
   - Ngày giao dịch
   - Nút "View Transaction"

4. **Timeline**
   - Complaint Created
   - Seller Responded
   - Admin Closed
   - Với timestamps

#### 🎯 Nút Hành Động Chính:
- **"Make Decision"** (Chỉ hiện khi status = ESCALATED)
- Màu gradient đỏ nổi bật
- Icon búa (gavel) - biểu tượng công lý

---

### 3. Modal Quyết Định Của Admin
**Tên:** "Make Final Decision"

#### 📝 Form Fields:

1. **Decision Type** (Bắt buộc)
   - Favor Customer (Refund/Bồi thường cho khách)
   - Favor Seller (Từ chối khiếu nại)
   - Neutral (Giải quyết một phần)

2. **Decision Notes** (Bắt buộc, tối thiểu 30 ký tự)
   - Textarea lớn
   - Placeholder hướng dẫn
   - Yêu cầu chi tiết, chuyên nghiệp
   - Sẽ hiển thị cho cả 2 bên

3. **Action Items** (Tùy chọn)
   - ☐ Issue Refund (Hoàn tiền cho customer)
   - ☐ Send Warning (Cảnh cáo seller)
   - ☐ Provide Compensation Points (Điểm bù)
   - ☐ Ban Seller (Chỉ dùng cho trường hợp nghiêm trọng)

4. **Confirmation Checkbox** (Bắt buộc)
   - Xác nhận đã review kỹ
   - Hiểu quyết định là cuối cùng
   - Background vàng nhạc nhở

#### 🔒 Validation:
- Decision phải được chọn
- Notes tối thiểu 30 ký tự
- Phải tick confirmation
- CSRF token protection
- Toast notification kết quả

---

## 🔗 TÍCH HỢP

### Sidebar Menu
Link đã có sẵn trong sidebar:
```
Operations > Complain Management
Icon: fas fa-exclamation-circle
URL: /admin/complaints
```

### Backend Cần Implement:

#### 1. Controller: `AdminComplaintController`

**Endpoint 1: Danh sách**
```java
@GetMapping("/admin/complaints")
public String listComplaints(
    @RequestParam(required = false) String search,
    @RequestParam(required = false) String status,
    @RequestParam(required = false) String type,
    @RequestParam(required = false) String sort,
    @RequestParam(defaultValue = "0") int page,
    Model model
) {
    // Logic lọc và phân trang
    model.addAttribute("complaints", complaints);
    model.addAttribute("totalPages", totalPages);
    model.addAttribute("escalatedCount", escalatedCount);
    return "admin/complaint-management";
}
```

**Endpoint 2: Chi tiết**
```java
@GetMapping("/admin/complaints/{id}")
public String viewComplaint(
    @PathVariable Long id,
    Model model
) {
    Complaint complaint = complaintService.findById(id);
    List<Message> messages = messageService.findByComplaintId(id);
    
    model.addAttribute("complaint", complaint);
    model.addAttribute("messages", messages);
    return "admin/complaint-detail";
}
```

**Endpoint 3: Giải quyết**
```java
@PostMapping("/admin/complaints/{id}/resolve")
@ResponseBody
public ResponseEntity<?> resolveComplaint(
    @PathVariable Long id,
    @RequestBody AdminDecisionDTO dto
) {
    // 1. Validate
    if (dto.getNotes().length() < 30) {
        return ResponseEntity.badRequest().body("Notes too short");
    }
    
    // 2. Update complaint
    complaint.setStatus(ComplaintStatus.CLOSED_BY_ADMIN);
    complaint.setAdminDecision(dto.getDecision());
    complaint.setAdminNotes(dto.getNotes());
    complaint.setClosedAt(LocalDateTime.now());
    
    // 3. Process actions
    if (dto.isRefund()) {
        // Logic hoàn tiền
    }
    if (dto.isWarningSeller()) {
        // Logic cảnh cáo
    }
    if (dto.isCompensate()) {
        // Logic bồi thường điểm
    }
    if (dto.isBanSeller()) {
        // Logic ban seller
    }
    
    // 4. Notify parties
    notificationService.notifyCustomer(complaint);
    notificationService.notifySeller(complaint);
    
    return ResponseEntity.ok().build();
}
```

#### 2. DTO Class
```java
public class AdminDecisionDTO {
    private String decision;      // FAVOR_CUSTOMER, FAVOR_SELLER, NEUTRAL
    private String notes;
    private boolean refund;
    private boolean warningSeller;
    private boolean compensate;
    private boolean banSeller;
    
    // Getters and setters
}
```

---

## 📊 DATABASE

### Complaint Table Cần Có:
```sql
ALTER TABLE complaints ADD COLUMN admin_decision VARCHAR(50);
ALTER TABLE complaints ADD COLUMN admin_notes TEXT;
ALTER TABLE complaints ADD COLUMN admin_handler_id BIGINT;
ALTER TABLE complaints ADD COLUMN closed_at TIMESTAMP;

-- Indexes for performance
CREATE INDEX idx_complaints_status ON complaints(status);
CREATE INDEX idx_complaints_type ON complaints(type);
CREATE INDEX idx_complaints_created_at ON complaints(created_at);
```

---

## 🎓 LUỒNG SỬ DỤNG

### Kịch Bản: Admin Xử Lý Khiếu Nại Escalated

1. **Admin đăng nhập** → Vào sidebar "Complain Management"

2. **Xem tổng quan**
   - Thấy có 5 complaints escalated (màu đỏ)
   - Filter: Status = "Escalated"
   - Click vào complaint #123

3. **Review chi tiết**
   - Đọc mô tả của customer
   - Xem evidence (screenshots, videos)
   - Đọc phản hồi của seller
   - Xem toàn bộ chat history
   - Check thông tin transaction

4. **Ra quyết định**
   - Click nút "Make Decision"
   - Modal hiện ra
   - Chọn decision type: "Favor Customer"
   - Viết notes chi tiết: "Sau khi xem xét bằng chứng, tài khoản game thực sự không hoạt động. Seller cần hoàn tiền."
   - Tick: ✅ Issue Refund, ✅ Send Warning to Seller
   - Tick confirmation checkbox
   - Click "Submit Final Decision"

5. **Kết quả**
   - Toast: "Complaint resolved successfully"
   - Complaint chuyển sang status "CLOSED_BY_ADMIN"
   - Customer nhận notification + refund
   - Seller nhận notification + warning
   - Email tự động gửi cho cả 2 bên

---

## 🎨 THIẾT KẾ UX/UI

### Nguyên Tắc:
✅ Nhất quán với các trang admin khác
✅ Màu sắc phân biệt rõ ràng
✅ Icons trực quan, dễ hiểu
✅ Mobile-first responsive
✅ Loading states & feedback
✅ Accessibility (ARIA labels)

### Color Palette:
- **Primary:** Red/Rose (#DC2626 → #FB7185)
- **Success:** Green (#10B981)
- **Warning:** Yellow/Orange (#F59E0B)
- **Danger:** Red (#EF4444)
- **Info:** Blue (#3B82F6)
- **Customer:** Blue tones
- **Seller:** Green tones
- **Transaction:** Purple tones

### Typography:
- Headers: Font-bold, 2xl-3xl
- Body: Text-sm to text-base
- Labels: Text-xs, font-semibold

---

## ✅ CHECKLIST HOÀN THÀNH

### Frontend (100% Done)
- ✅ Trang danh sách complaint
- ✅ Filters & search
- ✅ Pagination
- ✅ Mobile responsive
- ✅ Trang chi tiết complaint
- ✅ Customer/Seller/Transaction cards
- ✅ Timeline display
- ✅ Chat history section
- ✅ Admin decision modal
- ✅ Form validation
- ✅ Toast notifications
- ✅ CSRF protection
- ✅ Consistent styling
- ✅ Icons & badges
- ✅ Accessibility basics

### Backend (Cần Làm)
- ⏳ Controller endpoints
- ⏳ Service layer logic
- ⏳ DTO classes
- ⏳ Database migrations
- ⏳ Notification system
- ⏳ Refund logic
- ⏳ Warning/Ban logic
- ⏳ Compensation logic
- ⏳ Email integration
- ⏳ Authorization checks
- ⏳ Audit logging

---

## 🚀 BƯỚC TIẾP THEO

### Ưu Tiên Cao:
1. Tạo `AdminComplaintController` với 3 endpoints
2. Service layer xử lý logic nghiệp vụ
3. Test với data mẫu
4. Integration với notification system

### Ưu Tiên Trung Bình:
5. Implement refund/warning/ban/compensation
6. Email notifications
7. Audit trail logging
8. Performance optimization

### Ưu Tiên Thấp:
9. Export to Excel
10. Advanced analytics
11. Bulk actions
12. Search improvements

---

## 📞 HỖ TRỢ

### Tham Khảo:
- `COMPLAINT_WORKFLOW.md` - Luồng chi tiết
- `COMPLAINT_FLOW_SUMMARY_VI.md` - Tóm tắt tiếng Việt
- `admin/users.html` - Mẫu trang admin
- `admin/withdraw-management.html` - Mẫu với modal
- `customer/complaint-detail.html` - UI complaint từ phía customer

### Testing:
```bash
# Start server
mvn spring-boot:run

# Access:
http://localhost:8080/admin/complaints

# Login as admin:
email: admin@mmomarket.com
password: [your_password]
```

---

## 🎉 KẾT LUẬN

✅ **Hoàn thành 100% giao diện frontend**
✅ **Tuân thủ đầy đủ workflow document**
✅ **Thiết kế chuyên nghiệp, thân thiện**
✅ **Responsive trên mọi thiết bị**
✅ **Sẵn sàng cho backend integration**

**Trạng thái:** 🟢 READY FOR BACKEND DEVELOPMENT

---

*Created: 2024-11-06*
*Version: 1.0*
*Status: Production Ready (Frontend)*

