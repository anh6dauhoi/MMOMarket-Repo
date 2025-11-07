# Complaint Workflow - Luồng Xử Lý Khiếu Nại

## Các Trạng Thái Complaint (ComplaintStatus)

```java
public enum ComplaintStatus {
    NEW,                    // Mới tạo
    IN_PROGRESS,           // Đang xử lý
    PENDING_CONFIRMATION,  // Chờ xác nhận
    ESCALATED,            // Chuyển lên Admin
    RESOLVED,             // Đã giải quyết
    CLOSED_BY_ADMIN,      // Admin đóng
    CANCELLED             // Đã hủy
}
```

## Các Loại Khiếu Nại (ComplaintType)

```java
public enum ComplaintType {
    ITEM_NOT_WORKING,        // Sản phẩm không hoạt động
    ITEM_NOT_AS_DESCRIBED,   // Sản phẩm không đúng mô tả
    FRAUD_SUSPICION,         // Nghi ngờ lừa đảo
    OTHER                    // Khác
}
```

---

## Luồng Chuyển Đổi Trạng Thái

### 1. **NEW** (Trạng thái khởi tạo)
**Điều kiện:** Khi Customer tạo complaint mới

**Các hành động khả dụng:**
- ✅ **Customer:** 
  - Chat với Seller
  - Hủy complaint → chuyển sang **CANCELLED**
  
- ✅ **Seller:** 
  - Xem chi tiết complaint
  - Trả lời complaint:
    - **APPROVE** (Chấp nhận) → chuyển sang **IN_PROGRESS**
    - **REJECT** (Từ chối) → chuyển sang **PENDING_CONFIRMATION**

---

### 2. **IN_PROGRESS** (Đang xử lý)
**Điều kiện:** Seller chấp nhận complaint (APPROVE)

**Ý nghĩa:** Seller đã thừa nhận vấn đề và đang làm việc để giải quyết

**Các hành động khả dụng:**
- ✅ **Customer:** 
  - Chat với Seller
  - Request Admin Support → chuyển sang **ESCALATED** *(Chưa implement)*
  
- ✅ **Seller:** 
  - Tiếp tục xử lý và chat với Customer
  - Đề xuất giải pháp → chuyển sang **PENDING_CONFIRMATION** *(Chưa implement)*

---

### 3. **PENDING_CONFIRMATION** (Chờ xác nhận từ Customer)
**Điều kiện:** 
- Seller từ chối complaint (REJECT)
- Seller đã đưa ra giải pháp và chờ Customer xác nhận

**Ý nghĩa:** Đang chờ Customer phản hồi về giải pháp của Seller

**Các hành động khả dụng:**
- ✅ **Customer:** 
  - Chat với Seller
  - Accept giải pháp → chuyển sang **RESOLVED** *(Chưa implement)*
  - Reject giải pháp → Request Admin Support → **ESCALATED** *(Chưa implement)*
  
- ⏰ **Auto:** 
  - Sau 3 ngày không phản hồi → Auto chuyển sang **RESOLVED** *(Chưa implement)*

---

### 4. **ESCALATED** (Chuyển lên Admin)
**Điều kiện:** 
- Customer không hài lòng với giải pháp của Seller
- Customer request Admin support

**Ý nghĩa:** Complaint được chuyển lên Admin để xem xét và ra quyết định cuối cùng

**Các hành động khả dụng:**
- ✅ **Admin:** 
  - Xem xét toàn bộ lịch sử chat và chứng cứ
  - Ra quyết định cuối cùng → chuyển sang **CLOSED_BY_ADMIN** *(Chưa implement)*
  
- ✅ **Customer & Seller:** 
  - Chỉ có thể xem, không thể thay đổi
  - Admin có thể yêu cầu thêm thông tin

---

### 5. **RESOLVED** (Đã giải quyết)
**Điều kiện:** 
- Customer chấp nhận giải pháp của Seller
- Auto-resolve sau 3 ngày không phản hồi

**Ý nghĩa:** Vấn đề đã được giải quyết thỏa đáng

**Các hành động khả dụng:**
- ✅ **Customer & Seller:** 
  - Vẫn có thể chat để follow-up
  - Xem lại lịch sử
  
- ❌ **Không thể:**
  - Thay đổi trạng thái
  - Mở lại complaint (phải tạo mới nếu có vấn đề khác)

---

### 6. **CLOSED_BY_ADMIN** (Admin đóng)
**Điều kiện:** Admin đã xem xét và đưa ra quyết định cuối cùng

**Ý nghĩa:** Quyết định cuối cùng và không thể thay đổi

**Các hành động khả dụng:**
- ✅ **All users:** 
  - Chỉ có thể xem
  - Không thể thay đổi hoặc appeal
  
- ℹ️ **Note:** Admin decision notes sẽ hiển thị lý do đóng

---

### 7. **CANCELLED** (Đã hủy)
**Điều kiện:** Customer hủy complaint khi còn ở trạng thái **NEW**

**Ý nghĩa:** Customer đã tự giải quyết hoặc không muốn tiếp tục

**Các hành động khả dụng:**
- ❌ **Không thể thay đổi**
- ℹ️ **Note:** Có thể tạo complaint mới nếu cần

---

## Bảng Ma Trận Chuyển Đổi Trạng Thái

| Từ Trạng Thái | Đến Trạng Thái | Điều Kiện | Actor | Status |
|--------------|---------------|-----------|-------|--------|
| NEW | IN_PROGRESS | Seller APPROVE | Seller | ✅ Implemented |
| NEW | PENDING_CONFIRMATION | Seller REJECT | Seller | ✅ Implemented |
| NEW | CANCELLED | Customer cancel | Customer | ⏳ UI Ready |
| IN_PROGRESS | PENDING_CONFIRMATION | Seller propose solution | Seller | ❌ Not Implemented |
| IN_PROGRESS | ESCALATED | Customer request admin | Customer | ⏳ UI Ready |
| PENDING_CONFIRMATION | RESOLVED | Customer accept | Customer | ❌ Not Implemented |
| PENDING_CONFIRMATION | ESCALATED | Customer reject | Customer | ❌ Not Implemented |
| PENDING_CONFIRMATION | RESOLVED | Auto after 3 days | System | ❌ Not Implemented |
| ESCALATED | CLOSED_BY_ADMIN | Admin decision | Admin | ❌ Not Implemented |

---

## Chi Tiết Implementation Hiện Tại

### ✅ Đã Implement:

1. **Create Complaint** (Customer)
   - Tạo complaint với status = NEW
   - Upload evidence (JSON array)
   - Link với transaction_id

2. **View Complaints** (Customer)
   - List view với pagination, search, sort
   - Detail view với đầy đủ thông tin

3. **View Complaints** (Seller) 
   - List view complaints của mình
   - Detail view (API endpoint)

4. **Seller Response** (Seller)
   - POST `/seller/complaints/{id}/respond`
   - Body: `{ "action": "APPROVE/REJECT", "reason": "..." }`
   - Validation: reason >= 10 characters
   - Auto chuyển status:
     - APPROVE → IN_PROGRESS
     - REJECT → PENDING_CONFIRMATION
   - Tạo notification cho Customer

### ⏳ UI Ready (Backend chưa implement):

1. **Cancel Complaint** (Customer)
   - Button có sẵn ở status NEW
   - Alert placeholder: "Cancel feature will be implemented soon!"
   - Cần API: `POST /account/complaints/{id}/cancel`

2. **Request Admin Support** (Customer)
   - Button hiển thị khi status = IN_PROGRESS hoặc PENDING_CONFIRMATION
   - Alert placeholder: "This feature will be available soon!"
   - Cần API: `POST /account/complaints/{id}/escalate`

### ❌ Chưa Implement:

1. **Customer Accept/Reject Solution**
   - Khi Seller đề xuất giải pháp
   - UI: Modal hoặc form để accept/reject
   - API: `POST /account/complaints/{id}/confirm`

2. **Admin Complaint Management**
   - Admin dashboard để xem complaints
   - Filter theo status, type, seller
   - Detail view và action buttons
   - API: 
     - `GET /admin/complaints` (list)
     - `GET /admin/complaints/{id}` (detail)
     - `POST /admin/complaints/{id}/close` (close with decision)

3. **Auto-resolve Logic**
   - Scheduled job để check PENDING_CONFIRMATION > 3 days
   - Auto chuyển sang RESOLVED
   - Send notification

4. **Seller Propose Solution**
   - Form để Seller đề xuất giải pháp cụ thể
   - Chuyển từ IN_PROGRESS → PENDING_CONFIRMATION
   - API: `POST /seller/complaints/{id}/propose-solution`

---

## Business Rules

### Validation Rules:

1. **Tạo Complaint:**
   - Phải có transaction_id hợp lệ
   - Description >= 10 characters
   - Chỉ Customer mới tạo được
   - Không được tạo duplicate complaint cho cùng 1 transaction (nếu đã có complaint đang active)

2. **Seller Response:**
   - Chỉ response được 1 lần
   - Reason >= 10 characters
   - Chỉ khi status = NEW

3. **Customer Actions:**
   - Cancel: chỉ khi status = NEW
   - Request Admin: khi status = IN_PROGRESS hoặc PENDING_CONFIRMATION
   - Accept/Reject: khi status = PENDING_CONFIRMATION

4. **Admin Actions:**
   - Có thể intervene ở bất kỳ stage nào
   - Decision là final, không thể thay đổi

### Notification Rules:

- Seller response → notify Customer
- Customer escalate → notify Admin
- Admin decision → notify cả Customer và Seller
- Auto-resolve → notify cả hai

---

## Database Schema

```sql
CREATE TABLE Complaints (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_id BIGINT,
    customer_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    complaint_type ENUM('ITEM_NOT_WORKING','ITEM_NOT_AS_DESCRIBED','FRAUD_SUSPICION','OTHER') DEFAULT 'ITEM_NOT_WORKING',
    description TEXT NOT NULL,
    evidence JSON,
    status ENUM('NEW','IN_PROGRESS','PENDING_CONFIRMATION','ESCALATED','RESOLVED','CLOSED_BY_ADMIN','CANCELLED') DEFAULT 'NEW',
    seller_final_response TEXT,
    admin_decision_notes TEXT,
    admin_handler_id BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT,
    deleted_by BIGINT,
    isDelete TINYINT(1) DEFAULT 0,
    
    INDEX idx_seller_status_type (seller_id, status, complaint_type),
    INDEX idx_status_updated_at (status, updated_at),
    INDEX idx_admin_handler_status (admin_handler_id, status),
    
    FOREIGN KEY (customer_id) REFERENCES Users(id),
    FOREIGN KEY (seller_id) REFERENCES Users(id),
    FOREIGN KEY (admin_handler_id) REFERENCES Users(id)
);
```

---

## Recommendations (Đề xuất)

### High Priority:

1. **Implement Cancel Complaint**
   - Simple, chỉ cần update status
   - Validation: chỉ khi NEW
   - Add soft delete nếu cần

2. **Implement Request Admin Support**
   - Chuyển status → ESCALATED
   - Assign admin_handler_id (round-robin hoặc manual)
   - Send notification đến admin

3. **Admin Dashboard cho Complaints**
   - Cần thiết để xử lý ESCALATED cases
   - List view, filter, search
   - Detail view với action buttons

### Medium Priority:

4. **Customer Confirmation Flow**
   - UI: Accept/Reject buttons khi PENDING_CONFIRMATION
   - Backend API
   - Validation logic

5. **Seller Propose Solution**
   - Form để nhập giải pháp chi tiết
   - Có thể attach files/images
   - Log trong history

### Low Priority:

6. **Auto-resolve Scheduled Job**
   - Cron job chạy daily
   - Check PENDING_CONFIRMATION > 3 days
   - Auto resolve + notify

7. **Complaint History/Timeline**
   - Log mọi thay đổi status
   - Display timeline trong detail view
   - Audit trail

---

## Testing Scenarios

### Test Case 1: Happy Path - Seller Accepts
```
1. Customer tạo complaint (NEW)
2. Seller xem và APPROVE (IN_PROGRESS)
3. Seller propose solution (PENDING_CONFIRMATION)
4. Customer accept (RESOLVED)
```

### Test Case 2: Escalation Path
```
1. Customer tạo complaint (NEW)
2. Seller REJECT (PENDING_CONFIRMATION)
3. Customer không đồng ý, request admin (ESCALATED)
4. Admin review và đưa ra quyết định (CLOSED_BY_ADMIN)
```

### Test Case 3: Quick Cancel
```
1. Customer tạo complaint (NEW)
2. Customer tự resolve vấn đề, cancel (CANCELLED)
```

### Test Case 4: Auto-resolve
```
1. Customer tạo complaint (NEW)
2. Seller REJECT (PENDING_CONFIRMATION)
3. Customer không phản hồi trong 3 ngày
4. System auto-resolve (RESOLVED)
```

---

## API Endpoints Summary

### Implemented:
- `GET /account/complaints` - List complaints (Customer)
- `GET /account/complaints/{id}` - View detail (Customer)
- `GET /seller/complaints` - List complaints (Seller)
- `GET /seller/complaints/{id}/json` - View detail (Seller)
- `POST /seller/complaints/{id}/respond` - Respond to complaint (Seller)

### Need to Implement:
- `POST /account/complaints/create` - Create complaint
- `POST /account/complaints/{id}/cancel` - Cancel complaint
- `POST /account/complaints/{id}/escalate` - Request admin support
- `POST /account/complaints/{id}/confirm` - Accept/reject solution
- `GET /admin/complaints` - List all complaints (Admin)
- `GET /admin/complaints/{id}` - View detail (Admin)
- `POST /admin/complaints/{id}/close` - Close with decision (Admin)
- `POST /seller/complaints/{id}/propose-solution` - Propose solution (Seller)

---

**Last Updated:** November 6, 2025
**Version:** 1.0
**Status:** In Development

