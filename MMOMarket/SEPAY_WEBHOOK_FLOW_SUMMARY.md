# 📊 Tóm Tắt Luồng Xử Lý Webhook SePay

## 🎯 Tổng Quan

Tài liệu này mô tả chi tiết luồng xử lý nạp tiền qua SePay từ A-Z trong hệ thống MMOMarket.

---

## 🔄 Sơ Đồ Luồng (Sequence Diagram)

```
┌─────────┐         ┌────────┐         ┌──────────┐         ┌──────────┐
│ Khách   │         │ SePay  │         │ MMOMarket│         │ Database │
│ Hàng    │         │Gateway │         │ Backend  │         │          │
└────┬────┘         └───┬────┘         └────┬─────┘         └────┬─────┘
     │                  │                   │                    │
     │ 1. Chuyển khoản  │                   │                    │
     │  (Nội dung: USER_DEPOSIT_CODE)       │                    │
     │─────────────────>│                   │                    │
     │                  │                   │                    │
     │                  │ 2. Phát hiện giao dịch mới             │
     │                  │   (Bank webhook -> SePay)              │
     │                  │                   │                    │
     │                  │ 3. POST /api/webhook/sepay             │
     │                  │   Header: Authorization: Apikey XXX    │
     │                  │   Body: {id, amount, code, ...}        │
     │                  │──────────────────>│                    │
     │                  │                   │                    │
     │                  │                   │ 4. Xác thực API Key│
     │                  │                   │ (SepayWebhookController)
     │                  │                   │                    │
     │                  │                   │ 5. Check duplicate │
     │                  │                   │────────────────────>│
     │                  │                   │<────────────────────│
     │                  │                   │  (existsBySepayTransactionId)
     │                  │                   │                    │
     │                  │                   │ 6. Tìm User        │
     │                  │                   │────────────────────>│
     │                  │                   │<────────────────────│
     │                  │                   │  (findByDepositCode)
     │                  │                   │                    │
     │                  │                   │ 7. Lưu CoinDeposit │
     │                  │                   │────────────────────>│
     │                  │                   │<────────────────────│
     │                  │                   │                    │
     │                  │                   │ 8. Cộng coins User │
     │                  │                   │────────────────────>│
     │                  │                   │<────────────────────│
     │                  │                   │                    │
     │                  │                   │ 9. Tạo Notification│
     │                  │                   │ (Transaction mới)  │
     │                  │                   │────────────────────>│
     │                  │                   │<────────────────────│
     │                  │                   │                    │
     │                  │ 10. Response:     │                    │
     │                  │   200 OK + {"success": true}           │
     │                  │<──────────────────│                    │
     │                  │                   │                    │
     │ 11. Nhận thông báo nạp tiền thành công (Email/In-app)    │
     │<──────────────────────────────────────────────────────────│
```

---

## 📝 Chi Tiết Từng Bước

### **Bước 1: Khách Hàng Chuyển Khoản**

**Hành động:**
- Khách hàng vào trang `/topup` trên web MMOMarket
- Hệ thống hiển thị thông tin tài khoản SePay và **Mã nạp tiền cá nhân** (depositCode)
- Khách hàng mở app ngân hàng, chuyển khoản với nội dung: `USER_DEPOSIT_CODE`

**Ví dụ:**
```
Số tài khoản: 0123456789 (SePay)
Nội dung CK: ABCD1234
Số tiền: 100,000 VND
```

**Lưu ý:**
- ⚠️ Nội dung chuyển khoản phải chính xác (không dấu, đúng mã)
- ✅ Mỗi user có 1 depositCode duy nhất (lưu trong bảng `users`)

---

### **Bước 2: SePay Phát Hiện Giao Dịch**

**Hành động:**
- Ngân hàng gửi thông báo giao dịch cho SePay (bank webhook)
- SePay xử lý và tạo 1 record giao dịch với `sepayTransactionId` duy nhất

**Thời gian:** ~5-30 giây sau khi chuyển khoản thành công

---

### **Bước 3: SePay Gọi Webhook MMOMarket**

**Request từ SePay:**
```http
POST https://mmomarket.com/api/webhook/sepay
Content-Type: application/json
Authorization: Apikey AWPIPVVNYZXHIBK82QOM4DARK1YW6XMBLFUDUSF03XH7QECSS8OTUVU2HWNZJLYD

{
  "id": 1234567890,
  "gateway": "MBBank",
  "transactionDate": "2025-11-03 14:30:00",
  "accountNumber": "0123456789",
  "code": "ABCD1234",
  "content": "ABCD1234 Nap tien",
  "transferType": "in",
  "transferAmount": 100000,
  "accumulated": 500000,
  "subAccount": null,
  "referenceCode": "FT25310712345678",
  "description": "Chuyen khoan tu Nguyen Van A"
}
```

**Các trường quan trọng:**
- `id`: SePay Transaction ID (dùng để chống duplicate)
- `code`: Mã depositCode của user
- `transferType`: "in" (tiền vào) hoặc "out" (tiền ra)
- `transferAmount`: Số tiền thực tế (VND)
- `referenceCode`: Mã tham chiếu ngân hàng

---

### **Bước 4-10: MMOMarket Backend Xử Lý**

#### **Controller: `SepayWebhookController.java`**

```java
@PostMapping("/api/webhook/sepay")
public ResponseEntity<Map<String, Object>> receiveDepositWebhook(
    @RequestHeader("Authorization") String authorization,
    @RequestBody SepayWebhookPayload payload) {
    
    // BƯỚC 4: Xác thực API Key
    if (!authorization.startsWith("Apikey ") || !apiKey.equals(sepayApiKey)) {
        return 401/403; // ❌ Từ chối
    }
    
    try {
        // BƯỚC 5-9: Gọi Service xử lý
        sepayWebhookService.processSepayDepositWebhook(payload);
        
        // BƯỚC 10: Phản hồi thành công
        return 200 + {"success": true}; // ✅ SePay sẽ không retry
        
    } catch (IllegalArgumentException ex) {
        // Lỗi nghiệp vụ (duplicate, user không tồn tại)
        return 200 + {"success": false, "error": "..."}; // ✅ Không retry
        
    } catch (Exception ex) {
        // Lỗi hệ thống (DB timeout)
        return 500; // ⚠️ SePay sẽ retry
    }
}
```

#### **Service: `SepayWebhookService.java`**

**BƯỚC 5: Kiểm tra transferType**
```java
if (!"in".equalsIgnoreCase(payload.getTransferType())) {
    throw new IllegalArgumentException("Không phải tiền vào");
}
```
- Chỉ xử lý giao dịch "in" (tiền vào)
- Bỏ qua "out" (tiền ra)

**BƯỚC 6: Chống Trùng Lặp (QUAN TRỌNG NHẤT)**
```java
if (coinDepositRepository.existsBySepayTransactionId(payload.getId())) {
    throw new IllegalArgumentException("Duplicate transaction");
}
```
- Kiểm tra `sepayTransactionId` đã tồn tại trong DB chưa
- Đây là **cơ chế bảo mật chính** chống:
  - ✅ Replay attack
  - ✅ SePay retry nhiều lần
  - ✅ Network duplicate request

**BƯỚC 7: Tìm User**
```java
User user = userRepository.findByDepositCodeAndIsDelete(payload.getCode(), false);
if (user == null) {
    throw new IllegalArgumentException("Không tìm thấy user");
}
```
- Tìm user dựa trên `depositCode` trong payload
- Nếu không tìm thấy → từ chối (có thể user nhập sai mã)

**BƯỚC 8: Lưu CoinDeposit**
```java
CoinDeposit deposit = new CoinDeposit();
deposit.setSepayTransactionId(payload.getId()); // ← Unique constraint
deposit.setAmount(payload.getTransferAmount());
deposit.setStatus("Completed");
coinDepositRepository.save(deposit);
```
- Lưu lịch sử nạp tiền
- `sepayTransactionId` có **UNIQUE constraint** trong DB → đảm bảo không trùng

**BƯỚC 9: Cộng Coins Cho User**
```java
@Transactional
Long oldBalance = user.getCoins();
user.setCoins(oldBalance + payload.getTransferAmount());
userRepository.save(user);
```
- Cộng coins vào tài khoản user
- Toàn bộ logic từ Bước 8-9 nằm trong **1 transaction**
- Nếu có lỗi → rollback (không mất tiền)

**BƯỚC 10: Gửi Notification (Transaction Riêng)**
```java
@Transactional(propagation = REQUIRES_NEW)
public void sendNotificationInNewTransaction(...) {
    Notification notification = new Notification();
    notification.setTitle("Coin deposit successful");
    notification.setContent("You have just been added 100000 coins...");
    notificationRepository.save(notification);
}
```
- Gửi thông báo trong **transaction riêng**
- Nếu notification lỗi → **KHÔNG rollback tiền** (quan trọng!)

---

### **Bước 11: Phản Hồi Cho SePay**

**Response từ MMOMarket:**

✅ **Thành công:**
```http
HTTP/1.1 200 OK
Content-Type: application/json

{"success": true}
```
→ SePay nhận được, đánh dấu "Đã xử lý", **KHÔNG retry**

❌ **Lỗi nghiệp vụ (duplicate, user không tồn tại):**
```http
HTTP/1.1 200 OK
Content-Type: application/json

{"success": false, "error": "Giao dịch đã được xử lý"}
```
→ SePay nhận được, **KHÔNG retry** (vì retry cũng lỗi tương tự)

🔴 **Lỗi hệ thống (DB timeout):**
```http
HTTP/1.1 500 Internal Server Error
Content-Type: application/json

{"success": false, "error": "Lỗi hệ thống"}
```
→ SePay sẽ **RETRY** sau 5 phút, 15 phút, 30 phút, ... (exponential backoff)

---

## 🛡️ Cơ Chế Bảo Mật

### **1. Xác Thực API Key (Chiều 1: Web Xác Thực SePay)**

```java
// Controller
String apiKey = authorization.substring(7); // "Apikey ABCD..." → "ABCD..."
if (!sepayApiKey.equals(apiKey)) {
    return 403; // Từ chối
}
```

**Tại sao an toàn?**
- ✅ API Key dài 50+ ký tự, random
- ✅ HTTPS mã hóa header (hacker không nghe lén được)
- ✅ Lưu trong environment variable (không commit vào Git)

### **2. Chống Trùng Lặp (Chống Replay Attack)**

```java
// Service
if (coinDepositRepository.existsBySepayTransactionId(payload.getId())) {
    throw new IllegalArgumentException("Duplicate");
}
```

**Tại sao an toàn?**
- ✅ Mỗi `sepayTransactionId` là duy nhất toàn cầu
- ✅ Database có UNIQUE constraint
- ✅ `@Transactional` đảm bảo không race condition

**Test case:**
```
Request 1: sepayId=123 → Lưu DB → Cộng tiền → 200 OK
Request 2: sepayId=123 → Check DB → Đã tồn tại → 200 + "Duplicate" (KHÔNG cộng tiền)
```

### **3. Transaction Atomic (All or Nothing)**

```java
@Transactional
public void processSepayDepositWebhook(...) {
    // Bước 1: Lưu CoinDeposit
    coinDepositRepository.save(deposit);
    
    // Bước 2: Cộng coins
    user.setCoins(newBalance);
    userRepository.save(user);
    
    // Nếu có lỗi ở bất kỳ bước nào → ROLLBACK cả 2
}
```

**Tại sao an toàn?**
- ✅ Không thể xảy ra trường hợp: "Lưu CoinDeposit xong nhưng không cộng coins"
- ✅ Hoặc ngược lại: "Cộng coins xong nhưng không lưu CoinDeposit"

### **4. Notification Không Rollback Tiền**

```java
@Transactional
public void processSepayDepositWebhook(...) {
    // Transaction 1 (chính): Lưu CoinDeposit + Cộng coins
    coinDepositRepository.save(deposit);
    userRepository.save(user);
    // ← Transaction 1 commit ở đây
    
    try {
        // Transaction 2 (phụ): Gửi notification
        sendNotificationInNewTransaction(...); // REQUIRES_NEW
    } catch (Exception e) {
        log.error("Notification lỗi nhưng KHÔNG ảnh hưởng tiền");
    }
}
```

**Tại sao quan trọng?**
- 💰 **Tiền là tối thượng**: Notification lỗi không được làm mất tiền khách hàng
- ✅ Transaction 1 đã commit → coins đã vào tài khoản
- ⚠️ Transaction 2 lỗi → chỉ mất notification, user vẫn có tiền

---

## 📊 Luồng Xử Lý Exception

```
┌─────────────────┐
│ Request đến     │
└────────┬────────┘
         │
    ┌────▼────────────────────┐
    │ Kiểm tra API Key        │
    └────┬────────────────────┘
         │
         ├─── ❌ Sai → 401/403
         │
    ┌────▼────────────────────┐
    │ Service xử lý           │
    └────┬────────────────────┘
         │
         ├─── ❌ IllegalArgumentException (transferType sai, duplicate, user không tồn tại)
         │       → 200 + {"success": false, "error": "..."} 
         │       → SePay KHÔNG retry
         │
         ├─── ❌ RuntimeException (DB timeout, network lỗi)
         │       → 500 + {"success": false, "error": "Lỗi hệ thống"}
         │       → SePay SẼ retry
         │
         └─── ✅ Thành công
                → 200 + {"success": true}
                → SePay đánh dấu "Hoàn tất"
```

---

## 🔍 Các Trường Hợp Đặc Biệt

### **Case 1: User Nhập Sai Mã Nạp Tiền**

**Tình huống:**
```
Khách hàng chuyển khoản với nội dung: "WRONG_CODE"
→ SePay gọi webhook với code="WRONG_CODE"
→ Backend không tìm thấy user
```

**Xử lý:**
```java
User user = userRepository.findByDepositCode("WRONG_CODE");
if (user == null) {
    throw new IllegalArgumentException("Không tìm thấy user");
}
// Controller catch → 200 + {"success": false, "error": "..."}
```

**Kết quả:**
- ❌ Khách hàng không được cộng tiền
- ⚠️ Admin cần vào SePay dashboard xem lại giao dịch thủ công
- 💡 **Cải tiến:** Có thể tạo bảng `pending_deposits` để admin xử lý sau

---

### **Case 2: SePay Retry Do Network Timeout**

**Tình huống:**
```
Request 1: sepayId=123 → Backend xử lý xong → 200 OK
          Nhưng response bị mất (network timeout)
→ SePay không nhận được 200 → coi là lỗi → retry

Request 2 (retry): sepayId=123 → Backend check DB → Đã tồn tại
```

**Xử lý:**
```java
if (coinDepositRepository.existsBySepayTransactionId(123)) {
    throw new IllegalArgumentException("Duplicate");
}
// Controller catch → 200 + {"success": false, "error": "Duplicate"}
```

**Kết quả:**
- ✅ User chỉ được cộng tiền 1 lần (an toàn)
- ✅ SePay nhận 200 OK lần 2 → đánh dấu hoàn tất

---

### **Case 3: Database Deadlock**

**Tình huống:**
```
Request 1 và Request 2 cùng xử lý 2 giao dịch khác nhau của cùng 1 user
→ 2 transaction cùng update bảng `users` (cột `coins`)
→ Deadlock
```

**Xử lý:**
```java
try {
    user.setCoins(newBalance);
    userRepository.save(user);
} catch (Exception e) {
    throw new RuntimeException("Lỗi DB"); // → Controller trả 500
}
```

**Kết quả:**
- 🔴 Transaction lỗi → rollback (không mất tiền)
- ⚠️ SePay nhận 500 → retry sau 5 phút
- ✅ Retry lần sau sẽ thành công (deadlock tạm thời)

---

## 📋 Checklist Kiểm Tra

### **Trước Khi Deploy Production**

- [ ] **API Key:**
  - [ ] Đã move ra biến môi trường (`.env`)
  - [ ] KHÔNG commit vào Git
  - [ ] Rotate key mỗi 3-6 tháng

- [ ] **Database:**
  - [ ] Đã thêm UNIQUE constraint: `coin_deposit.sepay_transaction_id`
  - [ ] Đã test deadlock scenario
  - [ ] Đã setup connection pool (HikariCP)

- [ ] **HTTPS:**
  - [ ] Webhook URL là `https://...` (KHÔNG phải `http://`)
  - [ ] SSL certificate hợp lệ (Let's Encrypt)
  - [ ] Test với `curl` hoặc Postman

- [ ] **Logging:**
  - [ ] Log đầy đủ: sepayId, userId, amount
  - [ ] Log level: INFO cho success, ERROR cho system error
  - [ ] Log rotation setup (tránh đầy disk)

- [ ] **Monitoring:**
  - [ ] Setup alert khi có >10 lỗi 500 trong 5 phút
  - [ ] Dashboard hiển thị: success rate, latency, total amount
  - [ ] Health check endpoint: `/actuator/health`

- [ ] **Testing:**
  - [ ] Test API Key sai → 403
  - [ ] Test duplicate transaction → không cộng tiền 2 lần
  - [ ] Test notification lỗi → vẫn cộng tiền
  - [ ] Load test: 100 requests/giây

---

## 🎓 Best Practices

### **1. Idempotency is King**
```java
// ✅ ĐÚNG: Kiểm tra duplicate TRƯỚC KHI xử lý
if (exists(sepayId)) {
    return "Already processed";
}
process();

// ❌ SAI: Xử lý trước, kiểm tra sau
process();
if (exists(sepayId)) {
    rollback(); // ← Race condition!
}
```

### **2. Separate Critical vs Non-Critical**
```java
// ✅ ĐÚNG: Tiền và notification tách transaction
@Transactional
void processPayment() {
    saveDeposit();
    updateBalance();
} // ← Commit ở đây

sendNotificationAsync(); // ← Transaction riêng

// ❌ SAI: Notification lỗi rollback cả tiền
@Transactional
void processPayment() {
    saveDeposit();
    updateBalance();
    sendNotification(); // ← Lỗi ở đây rollback hết!
}
```

### **3. Log Everything**
```java
// ✅ ĐÚNG: Log với context đầy đủ
log.info("[SePay] Xử lý webhook: sepayId={}, userId={}, amount={}, gateway={}", 
    payload.getId(), user.getId(), amount, gateway);

// ❌ SAI: Log thiếu context
log.info("Processing webhook");
```

### **4. Fail Fast**
```java
// ✅ ĐÚNG: Kiểm tra điều kiện ngay từ đầu
if (!"in".equals(type)) throw new IllegalArgumentException();
if (exists(sepayId)) throw new IllegalArgumentException();
if (user == null) throw new IllegalArgumentException();
// ... tiếp tục xử lý

// ❌ SAI: Kiểm tra sau khi đã xử lý 1 phần
saveDeposit();
if (user == null) throw ...; // ← Đã lưu deposit rồi!
```

---

## 📞 Troubleshooting

### **Vấn đề: User không nhận được tiền**

**Các bước debug:**
1. Check logs: tìm `sepayId` trong logs
   ```bash
   grep "sepayId=123456" /var/log/mmomarket/app.log
   ```

2. Check database:
   ```sql
   SELECT * FROM coin_deposit WHERE sepay_transaction_id = 123456;
   SELECT coins FROM users WHERE deposit_code = 'ABCD1234';
   ```

3. Check SePay dashboard: giao dịch có status gì?

**Nguyên nhân thường gặp:**
- ⚠️ User nhập sai mã nạp tiền → Check logs: "Không tìm thấy user"
- ⚠️ Webhook chưa được config → SePay không gọi
- ⚠️ API Key sai → Logs: "API Key không hợp lệ"

---

### **Vấn đề: User bị cộng tiền 2 lần**

**Không thể xảy ra nếu:**
- ✅ Đã có UNIQUE constraint trên `sepay_transaction_id`
- ✅ Service kiểm tra `existsBySepayTransactionId()`

**Nếu vẫn xảy ra → nguy hiểm!**
```sql
-- Check duplicate trong DB
SELECT sepay_transaction_id, COUNT(*) 
FROM coin_deposit 
GROUP BY sepay_transaction_id 
HAVING COUNT(*) > 1;
```

**Fix ngay:**
```sql
-- Thêm constraint (nếu chưa có)
ALTER TABLE coin_deposit 
ADD CONSTRAINT uk_sepay_transaction_id 
UNIQUE (sepay_transaction_id);
```

---

## 🎯 Kết Luận

### **Luồng Hoàn Chỉnh (1 Câu)**

> Khách hàng chuyển khoản → SePay phát hiện → Gọi webhook với API Key → Backend kiểm tra duplicate → Lưu CoinDeposit + Cộng coins (1 transaction) → Gửi notification (transaction riêng) → Trả 200 OK cho SePay.

### **3 Trụ Cột Bảo Mật**

1. **API Key + HTTPS**: Xác thực request từ SePay
2. **Chống Duplicate**: Không cộng tiền 2 lần
3. **Transaction Atomic**: Không mất tiền khi lỗi

### **Key Takeaways**

✅ **Idempotency > Signature**: Với fintech, xử lý an toàn khi retry quan trọng hơn xác thực chữ ký  
✅ **HTTPS là bắt buộc**: Không có HTTPS = API Key lộ = game over  
✅ **Logging is gold**: Khi có bug production, logs giúp debug nhanh gấp 1000 lần  
✅ **Separate concerns**: Tiền (critical) và notification (non-critical) phải tách transaction  

---

**Tài liệu được tạo bởi GitHub Copilot**  
**Version:** 1.0  
**Last updated:** November 3, 2025

