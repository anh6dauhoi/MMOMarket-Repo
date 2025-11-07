# TỔNG KẾT LUỒNG COMPLAINT - ĐIỀU KIỆN CHUYỂN ĐỔI TRẠNG THÁI

## 📊 7 TRẠNG THÁI CHÍNH

1. **NEW** - Mới tạo
2. **IN_PROGRESS** - Đang xử lý
3. **PENDING_CONFIRMATION** - Chờ xác nhận
4. **ESCALATED** - Chuyển lên Admin
5. **RESOLVED** - Đã giải quyết
6. **CLOSED_BY_ADMIN** - Admin đóng
7. **CANCELLED** - Đã hủy

---

## 🔄 BẢNG CHUYỂN ĐỔI TRẠNG THÁI

| # | TỪ TRẠNG THÁI | → | ĐẾN TRẠNG THÁI | ĐIỀU KIỆN | AI LÀM | STATUS |
|---|---------------|---|----------------|-----------|---------|--------|
| 1 | NEW | → | IN_PROGRESS | Seller chấp nhận (APPROVE) | Seller | ✅ |
| 2 | NEW | → | PENDING_CONFIRMATION | Seller từ chối (REJECT) | Seller | ✅ |
| 3 | NEW | → | CANCELLED | Customer hủy | Customer | ⏳ |
| 4 | IN_PROGRESS | → | PENDING_CONFIRMATION | Seller đề xuất giải pháp | Seller | ❌ |
| 5 | IN_PROGRESS | → | ESCALATED | Customer yêu cầu Admin | Customer | ⏳ |
| 6 | PENDING_CONFIRMATION | → | RESOLVED | Customer chấp nhận | Customer | ❌ |
| 7 | PENDING_CONFIRMATION | → | ESCALATED | Customer từ chối | Customer | ❌ |
| 8 | PENDING_CONFIRMATION | → | RESOLVED | Tự động sau 3 ngày | System | ❌ |
| 9 | ESCALATED | → | CLOSED_BY_ADMIN | Admin ra quyết định | Admin | ❌ |

**Chú thích:**
- ✅ = Đã implement
- ⏳ = UI đã có, backend chưa
- ❌ = Chưa implement

---

## 📝 CHI TIẾT TỪNG TRẠNG THÁI

### 1️⃣ NEW (Mới tạo)
**Khi nào:** Customer vừa tạo complaint

**Ai làm được gì:**
- ✅ **Customer:** Xem chi tiết, chat với Seller, Hủy complaint
- ✅ **Seller:** Xem chi tiết, Trả lời (APPROVE hoặc REJECT)

**Điều kiện chuyển đổi:**
- Seller APPROVE → **IN_PROGRESS**
- Seller REJECT → **PENDING_CONFIRMATION**
- Customer Cancel → **CANCELLED**

**Validation:**
- Seller chỉ được response 1 lần
- Response phải có lý do >= 10 ký tự

---

### 2️⃣ IN_PROGRESS (Đang xử lý)
**Khi nào:** Seller đã chấp nhận và đang giải quyết

**Ai làm được gì:**
- ✅ **Customer:** Xem chi tiết, Chat, Request Admin Support
- ✅ **Seller:** Chat, Đề xuất giải pháp

**Điều kiện chuyển đổi:**
- Seller propose solution → **PENDING_CONFIRMATION** (chưa có)
- Customer request admin → **ESCALATED** (chưa có)

**Business Logic:**
- Seller và Customer có thể chat thoải mái
- Nên có timeline để track progress

---

### 3️⃣ PENDING_CONFIRMATION (Chờ xác nhận)
**Khi nào:** 
- Seller từ chối complaint, HOẶC
- Seller đã đề xuất giải pháp và chờ Customer xác nhận

**Ai làm được gì:**
- ✅ **Customer:** Xem, Chat, Accept/Reject giải pháp, Request Admin
- ✅ **Seller:** Xem, Chat

**Điều kiện chuyển đổi:**
- Customer Accept → **RESOLVED** (chưa có)
- Customer Reject → **ESCALATED** (chưa có)
- Không phản hồi 3 ngày → **RESOLVED** (auto) (chưa có)

**Timeout Rule:**
- ⏰ Nếu Customer không phản hồi trong 3 ngày → tự động RESOLVED
- Cần có notification reminder trước khi timeout

---

### 4️⃣ ESCALATED (Chuyển lên Admin)
**Khi nào:** Customer không hài lòng và yêu cầu Admin can thiệp

**Ai làm được gì:**
- ✅ **Customer & Seller:** CHỈ XEM, không thể thay đổi
- ❌ **Admin:** Xem tất cả evidence, chat history, ra quyết định cuối

**Điều kiện chuyển đổi:**
- Admin close → **CLOSED_BY_ADMIN** (chưa có)

**SLA:**
- Admin phải phản hồi trong 3-5 ngày làm việc
- Thông báo cho cả Customer và Seller

**Cần implement:**
- Admin dashboard
- Admin assign system (round-robin hoặc manual)
- Evidence review UI

---

### 5️⃣ RESOLVED (Đã giải quyết)
**Khi nào:** 
- Customer chấp nhận giải pháp, HOẶC
- Tự động resolved sau timeout

**Ai làm được gì:**
- ✅ **Customer & Seller:** Xem lịch sử, Chat follow-up
- ❌ **Không thể:** Thay đổi status, Reopen complaint

**Business Logic:**
- Nếu có vấn đề mới → phải tạo complaint mới
- Keep history for audit

---

### 6️⃣ CLOSED_BY_ADMIN (Admin đóng)
**Khi nào:** Admin đã review và đưa ra quyết định cuối cùng

**Ai làm được gì:**
- ✅ **All:** CHỈ XEM
- ❌ **Không thể:** Appeal, Thay đổi

**Business Logic:**
- Quyết định là FINAL
- Admin decision notes phải rõ ràng
- Có thể có action items (refund, warning, ban...)

---

### 7️⃣ CANCELLED (Đã hủy)
**Khi nào:** Customer tự hủy khi còn ở status NEW

**Ai làm được gì:**
- ✅ **All:** CHỈ XEM
- ❌ **Không thể:** Reopen

**Business Logic:**
- Có thể tạo complaint mới nếu cần
- Log lý do cancel (optional)

---

## 🚨 VALIDATION RULES QUAN TRỌNG

### Khi tạo Complaint:
```
✓ Phải có transaction_id hợp lệ
✓ Description >= 10 characters
✓ CHỈ Customer mới tạo được
✓ KHÔNG được tạo duplicate complaint cho cùng transaction (nếu đã có active)
✓ Evidence phải là valid JSON array hoặc URL
```

### Khi Seller Response:
```
✓ CHỈ response được 1 LẦN
✓ Reason >= 10 characters
✓ CHỈ khi status = NEW
✓ Action phải là "APPROVE" hoặc "REJECT"
✓ Tự động tạo notification cho Customer
```

### Khi Customer Cancel:
```
✓ CHỈ khi status = NEW
✓ Confirmation required
✓ Optional: Lý do cancel
```

### Khi Request Admin:
```
✓ CHỈ khi status = IN_PROGRESS hoặc PENDING_CONFIRMATION
✓ Phải có lý do rõ ràng
✓ Assign admin handler
✓ Notify admin team
```

### Khi Customer Confirm:
```
✓ CHỈ khi status = PENDING_CONFIRMATION
✓ Action: ACCEPT hoặc REJECT
✓ Nếu ACCEPT → RESOLVED
✓ Nếu REJECT → ESCALATED
```

### Khi Admin Close:
```
✓ CHỈ khi status = ESCALATED
✓ Phải có decision notes chi tiết
✓ Decision type: FAVOR_CUSTOMER, FAVOR_SELLER, NEUTRAL
✓ Notify cả Customer và Seller
```

---

## 📬 NOTIFICATION RULES

| Event | Gửi cho | Nội dung |
|-------|---------|----------|
| Complaint Created | Seller | "Bạn có khiếu nại mới #ID" |
| Seller Response | Customer | "Seller đã phản hồi khiếu nại #ID" |
| Request Admin | Admin | "Khiếu nại #ID cần xem xét" |
| Admin Decision | Customer + Seller | "Admin đã có quyết định cho #ID" |
| Auto-resolved | Customer + Seller | "Khiếu nại #ID tự động resolved" |
| Timeout Warning | Customer | "Còn X ngày để phản hồi #ID" |

---

## 🔧 CẦN IMPLEMENT NGAY (Priority Order)

### 🔴 HIGH PRIORITY:

1. **Cancel Complaint API** (Customer)
   ```
   POST /account/complaints/{id}/cancel
   Body: { "reason": "optional" }
   → Update status to CANCELLED
   ```

2. **Request Admin Support API** (Customer)
   ```
   POST /account/complaints/{id}/escalate
   Body: { "reason": "why need admin" }
   → Update status to ESCALATED
   → Assign admin_handler_id
   → Notify admin
   ```

3. **Admin Complaint Dashboard**
   ```
   GET /admin/complaints (list with filters)
   GET /admin/complaints/{id} (detail view)
   POST /admin/complaints/{id}/close (make decision)
   ```

### 🟡 MEDIUM PRIORITY:

4. **Customer Confirmation API**
   ```
   POST /account/complaints/{id}/confirm
   Body: { "action": "ACCEPT/REJECT", "comment": "..." }
   → ACCEPT → RESOLVED
   → REJECT → ESCALATED
   ```

5. **Seller Propose Solution API**
   ```
   POST /seller/complaints/{id}/propose-solution
   Body: { "solution": "...", "evidence": [...] }
   → Update status to PENDING_CONFIRMATION
   → Notify Customer
   ```

### 🟢 LOW PRIORITY:

6. **Auto-resolve Scheduled Job**
   ```java
   @Scheduled(cron = "0 0 2 * * ?") // 2 AM daily
   public void autoResolveTimeoutComplaints() {
       // Find PENDING_CONFIRMATION > 3 days
       // Update to RESOLVED
       // Send notifications
   }
   ```

7. **Complaint Timeline/History**
   ```sql
   CREATE TABLE ComplaintHistory (
       id BIGINT PRIMARY KEY,
       complaint_id BIGINT,
       from_status ENUM(...),
       to_status ENUM(...),
       actor_id BIGINT,
       notes TEXT,
       created_at DATETIME
   );
   ```

---

## 📊 DATABASE INDEXES (Đã có)

```sql
INDEX idx_seller_status_type (seller_id, status, complaint_type)
INDEX idx_status_updated_at (status, updated_at)
INDEX idx_admin_handler_status (admin_handler_id, status)
```

---

## 🧪 TEST SCENARIOS CẦN COVER

### ✅ Scenario 1: Happy Path
```
NEW → IN_PROGRESS → PENDING_CONFIRMATION → RESOLVED
```

### ✅ Scenario 2: Escalation
```
NEW → PENDING_CONFIRMATION → ESCALATED → CLOSED_BY_ADMIN
```

### ✅ Scenario 3: Cancel
```
NEW → CANCELLED
```

### ✅ Scenario 4: Auto-resolve
```
NEW → PENDING_CONFIRMATION → (wait 3 days) → RESOLVED
```

### ❌ Scenario 5: Invalid Transitions (should fail)
```
RESOLVED → IN_PROGRESS (BLOCKED)
CLOSED_BY_ADMIN → anything (BLOCKED)
CANCELLED → anything (BLOCKED)
```

---

## 🎯 BUSINESS METRICS CẦN TRACK

1. **Resolution Time:**
   - Average time từ NEW → RESOLVED
   - Target: < 48 hours

2. **Escalation Rate:**
   - % complaints chuyển sang ESCALATED
   - Target: < 10%

3. **Auto-resolve Rate:**
   - % complaints auto-resolved do timeout
   - Target: < 5%

4. **Seller Response Time:**
   - Average time seller response từ NEW
   - Target: < 24 hours

5. **Admin Decision Time:**
   - Average time từ ESCALATED → CLOSED_BY_ADMIN
   - Target: < 3 business days

---

**📅 Ngày cập nhật:** 6 November 2025  
**👤 Người tạo:** System Analysis  
**📌 Version:** 1.0  
**✨ Status:** Documentation Complete, Implementation In Progress

