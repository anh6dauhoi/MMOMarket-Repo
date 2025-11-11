# Quy trình quản lý sản phẩm (MMOMarket)

Tài liệu này mô tả luồng nghiệp vụ đầy đủ cho việc quản lý sản phẩm, biến thể (variant) và tài khoản hàng tồn (variant accounts) trong hệ thống MMOMarket từ góc nhìn Seller, Customer và Admin.

---
## 1. Khái niệm chính
- Product (Bảng `Products`): thông tin cơ bản: seller, category, name, description, image, trạng thái hiển thị (qua cờ `isDelete` để ẩn/hiện), thời gian tạo/cập nhật.
- Product Variant (Bảng `ProductVariants`): mỗi biến thể thuộc một sản phẩm với các thuộc tính: tên biến thể (`variantName`), giá (`price`), trạng thái workflow nội bộ (`status` mặc định 'Pending'), cờ `isDelete` để ẩn/soft-delete.
- Product Variant Account (Bảng `ProductVariantAccounts`): đơn vị hàng tồn kho dạng “tài khoản” (username:password hoặc dữ liệu khác) được mã hoá; trạng thái: `Available` hoặc `Sold`; cờ `isDelete`.
- Transaction: ghi nhận giao dịch mua (đếm số lượng bán Completed để tính `totalSold`).
- Review: đánh giá sản phẩm dùng để tính rating trung bình.
- ShopInfo: thông tin cấp độ cửa hàng, dùng để hiển thị tier.

---
## 2. Vai trò & Mục tiêu
- Seller: tạo/sửa/ẩn sản phẩm, quản lý biến thể, cung cấp inventory (variant accounts), theo dõi bán hàng & đánh giá, cuối cùng có thể xóa shop.
- Customer: xem chi tiết sản phẩm, chọn biến thể, mua và nhận tài khoản đã mua, đánh giá.
- Admin: duyệt hoặc kiểm tra các trạng thái đặc thù (nếu mở rộng: duyệt biến thể, kiểm tra vi phạm), xử lý báo cáo / complaint.

---
## 3. Trạng thái & Quy tắc hiển thị
### 3.1 Product
- Hiển thị công khai khi `isDelete = false` (được coi là Active/Listed).
- Bị ẩn khỏi marketplace khi `isDelete = true` (Hidden). Vẫn còn trong quản lý nội bộ seller.
- Xóa shop hoặc quy trình cleanup sẽ set `isDelete = true` hàng loạt.

### 3.2 Product Variant
- Thuộc về một Product (cascade delete khi Product xóa).
- Cờ `isDelete` dùng để ẩn biến thể khỏi luồng khách hàng; seller vẫn có thể thấy trong quản lý.
- Field `status` (mặc định 'Pending') có thể dùng mở rộng cho workflow phê duyệt (ví dụ: Pending -> Approved -> Rejected). Hiện chưa thấy controller thay đổi nên coi là reserved.

### 3.3 Product Variant Account (Inventory Unit)
- `status = 'Available'`: có thể được bán, sửa thông tin (username/password).
- `status = 'Sold'`: đã gắn với `Transaction`, không sửa được nữa.
- Quy tắc sửa: endpoint `/products/variants/accounts/{accountId}` chỉ cho phép sửa khi trạng thái Available.
- Soft delete (`isDelete = true`) sử dụng khi xóa shop hoặc dọn dẹp.

---
## 4. Chu trình sống (Lifecycle) chi tiết
### 4.1 Tạo sản phẩm mới
1. Seller vào trang quản lý `/seller/product-management` (yêu cầu shop `Active`).
2. Seller upload ảnh qua endpoint: `POST /seller/products/upload-image` (multipart `file`).
3. Seller gửi form tạo Product (controller phần đầu chưa được trích nhưng suy luận từ cấu trúc):
   - Fields: name, description, categoryId, image URL.
   - Mặc định `isDelete = false` (Listed).
4. Hệ thống tạo bản ghi Product; Seller có thể thêm biến thể ngay sau đó.

### 4.2 Thêm biến thể (Variant)
1. Seller thêm variant: `variantName`, `price`.
2. Trạng thái khởi tạo: `status = 'Pending'` (có thể mở rộng thành duyệt giá hoặc nội dung).
3. Sau khi duyệt (nếu có logic Admin thêm ở tương lai) -> `status` có thể chuyển sang 'Approved'. Nếu 'Rejected' thì seller sửa lại.
4. Seller có thể ẩn biến thể bằng cờ `isDelete` nếu muốn tạm ngưng bán.

### 4.3 Quản lý hàng tồn (Variant Accounts)
1. Seller nạp inventory cho từng variant bằng cách tạo `ProductVariantAccount` với `plainAccountData` (format chuẩn: `username:password`). Hệ thống mã hóa sang `accountData` khi `@PrePersist` hoặc `@PreUpdate`.
2. Liệt kê inventory: `GET /seller/products/variants/{variantId}/accounts` với query param `status=all|available|sold`, phân trang (`page`, `size`), tìm kiếm `search` theo username.
3. Sửa một đơn vị inventory (username/password): `PUT /seller/products/variants/accounts/{accountId}` (JSON body). Chỉ khi `status='Available'`.
4. Khi khách hàng mua số lượng N của một variant, backend chọn N bản ghi `Available` (truy vấn khoá PESSIMISTIC_WRITE `findAvailableForUpdate`) -> gắn transaction -> chuyển sang `Sold`.

### 4.4 Hiển thị sản phẩm cho khách hàng
1. Khách xem chi tiết qua: `/products/{id}` hoặc `/productdetail?id={id}` (legacy)
2. Service `ProductService.getProductDetail` tổng hợp:
   - Product base info
   - Danh sách biến thể (DTO `ProductVariantDto`: id, name, price, stock = số account Available, sold = số account Sold).
   - Giá hiển thị mặc định: biến thể có `price` thấp nhất.
   - Tổng đã bán (`totalSold`), rating trung bình (`avgRating`), sellerRating, shopLevel/tier.
   - Related products tính bằng similarity tên & cùng category.

### 4.5 Ẩn/Hiện sản phẩm
- Endpoint: `POST /seller/products/{id}/toggle-hide`
- Logic: Đảo `isDelete`. Nếu chuyển sang ẩn -> set `deletedBy` = sellerId; hiện lại -> `deletedBy` null.
- Ẩn sẽ loại khỏi các truy vấn marketplace (repository luôn lọc `p.isDelete = false`).

### 4.6 Sửa thông tin sản phẩm
- Lấy JSON prefill: `GET /seller/products/{id}/json` (kiểm tra sở hữu).
- Seller cập nhật: (endpoint thực tế không trong đoạn trích nhưng suy luận) gửi name, description, category, image.
- Thay đổi không ảnh hưởng transaction đã hoàn tất.

### 4.7 Xoá sản phẩm / Xoá shop
- Xoá riêng một sản phẩm: sử dụng toggle ẩn (soft delete). Toàn bộ variant và accounts vẫn tồn tại nhưng variant còn cần được ẩn hoặc không bán nữa.
- Xoá shop (OTP bảo vệ): `POST /seller/delete-shop`:
  1. Kiểm tra điều kiện: shop Active, số dư coins = 0, không còn sản phẩm Active, không bị Suspended/Banned/Locked.
  2. Xác thực OTP (`/seller/delete-shop/send-otp`).
  3. Soft delete hàng loạt: VariantAccount, Review, Transaction, ProductVariant, Product, SellerBankInfo, ShopInfo. 
  4. Chuyển `shopStatus` về `Inactive`.

### 4.8 Rating & Review ảnh hưởng hiển thị
- Khi khách mua xong và để lại review -> `ReviewRepository.getAverageRatingByProduct` dùng trong listing.
- Rating seller tổng hợp qua `shopService.getSellerAverageRating` rồi hiển thị trong chi tiết sản phẩm, giúp khách đánh giá uy tín.

### 4.9 Thống kê & Sắp xếp
- Top Selling Products: `ProductRepository.findTopSellingProducts` dựa trên COUNT Transaction Completed.
- Bộ lọc category, price range, minRating, sort (price-low-to-high, price-high-to-low, rating, newest) xử lý in-memory sau tổng hợp.
- Seller view áp dụng phân trang tự tính (offset, limit) và optional sort theo lowest variant price.

---
## 5. Dữ liệu & Truy vấn quan trọng
- Tồn kho (stock) = `countByVariant_IdAndIsDeleteFalseAndStatus(variantId, 'Available')`
- Số đã bán của variant = `countByVariant_IdAndIsDeleteFalseAndStatus(variantId, 'Sold')`
- Tổng đã bán sản phẩm = `getTotalSoldForProduct(productId)` (COUNT Transaction Completed)
- Tổng đã bán cả shop = `getTotalSoldForSeller(sellerId)`

---
## 6. Bảo mật & Toàn vẹn dữ liệu
- Mã hoá dữ liệu account: tự động trong entity bằng `EncryptionUtil` ở lifecycle events.
- Cập nhật inventory dùng khoá pessimistic khi phân phối cho Transaction tránh race condition.
- Kiểm tra quyền sở hữu ở mọi endpoint Seller (`p.getSeller().getId() == user.getId()`).
- OTP bảo vệ các thao tác nhạy cảm: cập nhật thông tin rút tiền, xoá shop.

---
## 7. Edge Cases & Khuyến nghị
- Product không có biến thể: hiển thị giá = 0; nên ép seller tạo ít nhất 1 variant.
- Variant giá bằng 0: cần rule validation để ngăn nếu business không cho phép.
- Inventory trống: khách không thể mua; cần hiển thị "Hết hàng" ngay ở UI (dựa vào stock).
- Concurrent purchase: rely on `findAvailableForUpdate` + transaction isolation để tránh bán trùng.
- Xoá shop khi còn Transaction Pending: hiện tại batch soft delete sẽ set tất cả Transaction isDelete=true; nên xem xét nghiệp vụ hoàn tiền trước khi cho xoá.

---
## 8. Gợi ý cải tiến tương lai
- Thêm workflow duyệt biến thể: Pending -> Approved/Rejected với audit trail.
- Thêm trạng thái sản phẩm riêng (Active/Hidden) thay vì tái sử dụng `isDelete`.
- Cơ chế bulk upload inventory (CSV) kèm kiểm tra trùng lặp trước khi persist.
- Cache layer cho top selling / rating để giảm chi phí truy vấn lặp lại.
- Thêm chỉ số "stockAvailable" trên listing để khách biết còn hàng.
- Thêm soft delete timestamp & reason fields để audit rõ ràng.

---
## 9. Sơ đồ tóm tắt (dạng văn bản)
1. Seller tạo Product -> Thêm Variants -> Nạp Inventory Accounts.
2. Customer xem Product -> Chọn Variant -> Tạo Transaction -> Hệ thống phân phối N accounts (Available -> Sold).
3. Customer review -> Rating cập nhật.
4. Seller ẩn/sửa sản phẩm hoặc thêm inventory mới.
5. Seller có thể xoá shop (OTP) khi đạt điều kiện -> cascade soft delete.

---
## 10. Tham chiếu Endpoint tiêu biểu
- GET `/products/{id}`: trang chi tiết khách.
- GET `/seller/product-management`: trang quản lý seller.
- POST `/seller/products/upload-image`: upload ảnh.
- GET `/seller/products/{id}/json`: lấy JSON cho form edit.
- POST `/seller/products/{id}/toggle-hide`: ẩn/hiện.
- GET `/seller/products/variants/{variantId}/accounts`: liệt kê inventory.
- PUT `/seller/products/variants/accounts/{accountId}`: cập nhật inventory unit.
- POST `/seller/delete-shop/send-otp` + POST `/seller/delete-shop`: quy trình xoá shop.

---
## 11. Kiểm thử nhanh (Smoke Checklist)
- Tạo product -> thấy trong `/seller/product-management` với isDelete=false.
- Thêm variant -> hiển thị trên trang khách với đúng giá thấp nhất.
- Thêm 3 accounts -> stock hiển thị =3; mua 2 -> stock=1, sold=2.
- Ẩn product -> biến mất khỏi listing khách nhưng vẫn thấy trong seller (status=hidden).
- Xoá shop (thoả điều kiện) -> tất cả sản phẩm, biến thể, accounts soft delete.

---
## 12. Mapping sang Code
- Tổng hợp chi tiết: `ProductService.getProductDetail`.
- Thống kê top selling: `ProductRepository.findTopSellingProducts`.
- Inventory count: `ProductVariantAccountRepository.countByVariant_IdAndIsDeleteFalseAndStatus`.
- Quản lý seller view: logic trong `SellerController.showProductManagement`.
- Xoá shop cascade: `SellerController.deleteShopWithOtp`.

Tài liệu này phản ánh code hiện tại (snapshot ngày 2025-11-09). Nếu thay đổi logic, cần cập nhật đồng bộ.

