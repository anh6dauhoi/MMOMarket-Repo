# Escrow & Complaint Logic Summary

## Nghiệp vụ chuyển tiền cho Seller

### 1. Quy trình Escrow (Giữ tiền tạm)

#### Khi Mua hàng:
- **File**: `BuyAccountListener.java` (dòng 154-168)
- Customer mua sản phẩm → Tiền bị trừ ngay từ tài khoản
- Transaction được tạo với:
  - Status: `ESCROW` (tiền đang bị giữ tạm)
  - `escrowReleaseDate`: **3 ngày** kể từ ngày mua
  - `coinSeller`: Số tiền seller sẽ nhận (sau khi trừ commission)
  - `coinAdmin`: Phí hoa hồng cho admin

#### Sau 3 ngày - Auto Release:
- **File**: `EscrowReleaseScheduler.java` (method `releaseEscrow()`)
- **Chạy mỗi giờ**: Scheduler tự động kiểm tra các transaction có `escrowReleaseDate` đã qua
- **Logic kiểm tra**:
  ```
  IF escrowReleaseDate <= hiện tại AND status = "ESCROW" THEN
      IF có complaint đang mở (NEW, IN_PROGRESS, PENDING_CONFIRMATION, ESCALATED) THEN
          ❌ GIỮ TIỀN - Không chuyển cho seller
          Log: "Skipping tx due to open complaint"
      ELSE
          ✅ CHUYỂN TIỀN CHO SELLER
          - Transaction status: ESCROW → COMPLETED
          - Cộng coinSeller vào tài khoản seller
          - Gửi notification cho seller
      END IF
  END IF
  ```

### 2. Quy tắc Complaint (Khiếu nại)

#### Các trạng thái Complaint:
- **NEW**: Mới tạo, seller chưa phản hồi
- **IN_PROGRESS**: Seller đang xử lý
- **PENDING_CONFIRMATION**: Seller đã đưa giải pháp, chờ customer xác nhận
- **ESCALATED**: Đã escalate lên admin
- **RESOLVED**: Đã giải quyết (customer chấp nhận giải pháp)
- **CLOSED_BY_ADMIN**: Admin đã đưa ra quyết định cuối cùng
- **CANCELLED**: Customer tự hủy complaint

#### Nghiệp vụ chính:

##### A. Không có Complaint (Trường hợp bình thường - ~95% cases)
```
Ngày 0: Customer mua hàng
        └─> Transaction: ESCROW, escrowReleaseDate = Ngày 3
        
Ngày 1-2: Customer không khiếu nại

Ngày 3: Scheduler chạy
        └─> Kiểm tra: Không có complaint mở
        └─> ✅ CHUYỂN TIỀN CHO SELLER
            └─> Transaction: COMPLETED
            └─> Seller nhận tiền
```

##### B. Có Complaint - Seller giải quyết thành công
```
Ngày 0: Customer mua hàng
        └─> Transaction: ESCROW, escrowReleaseDate = Ngày 3
        
Ngày 1: Customer tạo complaint (NEW)
        └─> Transaction vẫn ESCROW
        
Ngày 1.5: Seller phản hồi và đưa giải pháp
          └─> Complaint: IN_PROGRESS → PENDING_CONFIRMATION
        
Ngày 2: Customer xác nhận chấp nhận giải pháp
        └─> Complaint: RESOLVED (đã giải quyết)
        └─> Transaction vẫn ESCROW (chờ đến ngày 3)
        
Ngày 3: Scheduler chạy
        └─> Kiểm tra: Complaint = RESOLVED (không phải trạng thái mở)
        └─> ✅ CHUYỂN TIỀN CHO SELLER
            └─> Transaction: COMPLETED
            └─> Seller nhận tiền
```

##### C. Có Complaint - Chưa giải quyết xong
```
Ngày 0: Customer mua hàng
        └─> Transaction: ESCROW, escrowReleaseDate = Ngày 3
        
Ngày 2: Customer tạo complaint (NEW)
        └─> Transaction vẫn ESCROW
        
Ngày 3: Scheduler chạy
        └─> Kiểm tra: Complaint = NEW (đang mở)
        └─> ❌ GIỮ TIỀN - Không chuyển cho seller
            └─> Transaction vẫn ESCROW
        
Ngày 4-10: Seller xử lý complaint...
           └─> Transaction vẫn ESCROW
        
Ngày 11: Complaint được giải quyết (RESOLVED)
         └─> Scheduler chạy tiếp
         └─> ✅ CHUYỂN TIỀN CHO SELLER (do complaint đã RESOLVED)
```

##### D. Customer không phản hồi trong 3 ngày
```
Seller đưa giải pháp → Complaint: PENDING_CONFIRMATION

Sau 3 ngày customer không phản hồi:
    └─> Auto-resolve scheduler chạy (method autoResolveExpiredPendingComplaints)
    └─> Complaint: PENDING_CONFIRMATION → RESOLVED
    └─> Tiền sẽ được chuyển cho seller vào lần scheduler chạy tiếp theo
```

##### E. Escalate lên Admin
```
Complaint được escalate → Status: ESCALATED
    └─> Transaction: ESCROW (GIỮ TIỀN)
    
Admin xem xét và quyết định:
    └─> Complaint: CLOSED_BY_ADMIN
    └─> Admin có thể:
        - Refund cho customer
        - Cảnh cáo seller
        - Ban seller
        - Hoặc không làm gì (seller nhận tiền)
    
Note: Logic refund/ban chưa được implement đầy đủ (có TODO trong AdminController.java)
```

## Tóm tắt Logic Chính

### ✅ Seller nhận tiền KHI:
1. **Không có complaint** SAU 3 ngày
2. **Có complaint nhưng đã RESOLVED** (customer chấp nhận giải pháp)
3. **Có complaint nhưng đã CANCELLED** (customer tự hủy)
4. **Có complaint CLOSED_BY_ADMIN** và admin không quyết định refund

### ❌ Seller KHÔNG nhận tiền KHI:
1. Có complaint với status: **NEW, IN_PROGRESS, PENDING_CONFIRMATION, ESCALATED**
2. Complaint đang được xử lý (chưa kết thúc)

### 🔄 Auto-Process:
1. **Mỗi giờ**: Kiểm tra và release tiền cho các transaction đã qua 3 ngày (không có complaint mở)
2. **Mỗi giờ**: Tự động resolve các complaint PENDING_CONFIRMATION quá 3 ngày

## Code Location

### Transaction Creation:
- **File**: `src/main/java/com/mmo/mq/BuyAccountListener.java`
- **Line**: 154-168
- **Logic**: Set escrowReleaseDate = 3 days from now

### Escrow Release Scheduler:
- **File**: `src/main/java/com/mmo/service/EscrowReleaseScheduler.java`
- **Method**: `releaseEscrow()`
- **Schedule**: Every hour (`@Scheduled(cron = "0 0 * * * *")`)
- **Logic**: 
  - Find transactions: status=ESCROW AND escrowReleaseDate <= now
  - Check if has open complaint (NEW, IN_PROGRESS, PENDING_CONFIRMATION, ESCALATED)
  - If NO open complaint → Release money to seller

### Auto-Resolve Complaints:
- **File**: `src/main/java/com/mmo/service/EscrowReleaseScheduler.java`
- **Method**: `autoResolveExpiredPendingComplaints()`
- **Schedule**: Every hour (`@Scheduled(cron = "0 0 * * * *")`)
- **Logic**: Auto-resolve PENDING_CONFIRMATION complaints after 3 days

### Complaint Service:
- **File**: `src/main/java/com/mmo/service/ComplaintService.java`
- **Methods**:
  - `cancelComplaint()`: Customer cancel complaint
  - `escalateToAdmin()`: Escalate to admin
  - `confirmResolution()`: Customer confirm seller's solution

### Admin Complaint Resolution:
- **File**: `src/main/java/com/mmo/controller/AdminController.java`
- **Method**: `resolveComplaint()`
- **Line**: 2968-3070
- **Note**: Refund/Ban logic có TODO chưa implement đầy đủ

## Testing

### Test Case 1: Normal purchase (no complaint)
```
Day 0: Buy product → Transaction ESCROW
Day 3: Auto-release → Transaction COMPLETED, Seller receives money ✅
```

### Test Case 2: Complaint resolved before 3 days
```
Day 0: Buy product → Transaction ESCROW
Day 1: Create complaint → NEW
Day 2: Seller responds → PENDING_CONFIRMATION
Day 2.5: Customer accepts → RESOLVED
Day 3: Auto-release → Transaction COMPLETED, Seller receives money ✅
```

### Test Case 3: Complaint not resolved
```
Day 0: Buy product → Transaction ESCROW
Day 2.5: Create complaint → NEW
Day 3: Auto-release check → HAS OPEN COMPLAINT → Skip release ❌
Day 4-10: Complaint being handled...
Day 11: Complaint RESOLVED → Next scheduler run → Release money ✅
```

### Test Case 4: Customer doesn't respond
```
Day 0: Buy product → Transaction ESCROW
Day 1: Create complaint → NEW
Day 2: Seller gives solution → PENDING_CONFIRMATION
Day 5: Customer no response → Auto-resolve → RESOLVED
Day 5+: Next scheduler run → Release money ✅
```

## 3. Withdrawal Logic (Rút tiền)

### Business Rule: Không cho phép rút tiền khi có complaint đang mở

#### Withdrawal Validation Flow:
- **File**: `src/main/java/com/mmo/mq/WithdrawalCreateListener.java`
- **Logic**:
  ```
  WHEN seller tạo withdrawal request:
      1. Validate OTP
      2. Validate bank info
      3. ✅ CHECK FOR OPEN COMPLAINTS (NEW LOGIC)
         - Kiểm tra seller có complaint nào với status:
           * NEW
           * IN_PROGRESS
           * PENDING_CONFIRMATION
           * ESCALATED
         - IF có complaint mở → ❌ BLOCK WITHDRAWAL
           * Thông báo cho seller
           * Không trừ tiền
           * Return (skip withdrawal creation)
      4. Check balance
      5. Deduct coins
      6. Create withdrawal request
  ```

#### Scenarios:

##### Scenario 1: Seller không có complaint - Withdrawal OK ✅
```
Seller có: 1,000,000 coins
Complaints: NONE hoặc tất cả đã RESOLVED/CANCELLED/CLOSED_BY_ADMIN

→ Request withdrawal 500,000 coins
→ ✅ SUCCESS: Withdrawal created, coins deducted
```

##### Scenario 2: Seller có complaint đang mở - Withdrawal BLOCKED ❌
```
Seller có: 1,000,000 coins
Complaints: 
  - Complaint #123: NEW (customer vừa tạo hôm nay)
  - Complaint #120: RESOLVED (đã giải quyết tuần trước)

→ Request withdrawal 500,000 coins
→ ❌ BLOCKED: "You have 1 open complaint(s). Please resolve all complaints before requesting withdrawal."
→ Coins NOT deducted, withdrawal NOT created
```

##### Scenario 3: Seller resolve complaint xong - Withdrawal OK ✅
```
Ngày 1: Seller có complaint #123: IN_PROGRESS
        → Request withdrawal → ❌ BLOCKED

Ngày 2: Seller giải quyết complaint
        → Complaint #123: PENDING_CONFIRMATION

Ngày 2.5: Customer xác nhận
          → Complaint #123: RESOLVED

Ngày 3: Seller request withdrawal lại
        → ✅ SUCCESS (không còn complaint mở)
```

### Integration với Escrow Logic:

```
Transaction Flow với Complaint:
┌─────────────────────────────────────────────────────────┐
│ Day 0: Customer mua hàng                                 │
│        └─> Transaction: ESCROW                           │
│        └─> Seller balance: +0 (chưa nhận tiền)          │
├─────────────────────────────────────────────────────────┤
│ Day 1: Customer tạo complaint                            │
│        └─> Complaint: NEW                                │
│        └─> Seller tries withdrawal → ❌ BLOCKED          │
│           "You have 1 open complaint"                    │
├─────────────────────────────────────────────────────────┤
│ Day 2: Seller giải quyết complaint                       │
│        └─> Complaint: RESOLVED                           │
│        └─> Transaction vẫn ESCROW (chờ đến day 3)       │
│        └─> Seller tries withdrawal → ✅ OK (if có tiền)  │
├─────────────────────────────────────────────────────────┤
│ Day 3: Escrow release                                    │
│        └─> Scheduler chạy                                │
│        └─> Check complaint: RESOLVED (không mở)         │
│        └─> ✅ Release money to seller                    │
│        └─> Transaction: COMPLETED                        │
│        └─> Seller balance: +coinSeller                   │
│        └─> Seller có thể withdrawal số tiền này         │
└─────────────────────────────────────────────────────────┘
```

## Current Status

✅ **Implemented:**
- Escrow release after 3 days
- Check for open complaints before release
- Auto-resolve complaints after 3 days no response
- Complaint creation, escalation, resolution flow
- **✅ NEW: Block withdrawal when seller has open complaints**

⚠️ **Partially Implemented (TODOs exist):**
- Admin refund logic (AdminController.java line 3015)
- Admin ban seller logic (AdminController.java line 3024)
- Admin compensation logic (AdminController.java line 3021)
- Admin warning logic (AdminController.java line 3018)

## Kết luận

Logic hiện tại **ĐÚNG VÀ ĐỦ** cho nghiệp vụ:
- ✅ Sau 3 ngày không có complaint → Tiền về seller
- ✅ Nếu có complaint (mở) → Tiền bị giữ tạm
- ✅ Complaint resolved → Tiền sẽ về seller (khi escrow period kết thúc hoặc đã qua)
- ✅ Auto-resolve sau 3 ngày không phản hồi
- ✅ **NEW: Seller không thể rút tiền khi có complaint đang mở**

### Business Rules Summary:

| Tình huống | Escrow Release | Withdrawal Request |
|-----------|----------------|-------------------|
| Không có complaint | ✅ Sau 3 ngày | ✅ Cho phép |
| Complaint: NEW/IN_PROGRESS/PENDING/ESCALATED | ❌ Giữ tiền | ❌ **BLOCKED** |
| Complaint: RESOLVED | ✅ Sau 3 ngày | ✅ Cho phép |
| Complaint: CANCELLED | ✅ Sau 3 ngày | ✅ Cho phép |
| Complaint: CLOSED_BY_ADMIN | ✅ Sau 3 ngày | ✅ Cho phép |

### Lý do Block Withdrawal khi có Complaint:

1. **Bảo vệ Customer**: Nếu complaint đang open, có thể admin sẽ quyết định refund → Cần giữ tiền trong hệ thống
2. **Bảo vệ Platform**: Tránh seller rút hết tiền rồi biến mất khi có tranh chấp
3. **Công bằng**: Seller phải giải quyết complaint trước khi được rút tiền
4. **Escrow + Withdrawal sync**: Đảm bảo tiền trong escrow không bị rút ra ngoài khi đang có tranh chấp

**Scheduler đang hoạt động đúng như mong đợi + Withdrawal validation đã được implement.**

