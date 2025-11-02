package com.mmo.service;

import com.mmo.entity.*;
import com.mmo.repository.OrdersRepository;
import com.mmo.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class OrdersService {

    @Autowired
    private OrdersRepository orderQueueRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductVariantService productVariantService;

    @Autowired
    private ProductVariantAccountService accountService;

    @Autowired
    private TransactionRepository transactionRepository;

    /**
     * Tạo order queue mới (gọi từ Controller)
     */
    public Orders createOrderQueue(Long customerId, Long productId, Long variantId,
                                   Integer quantity, Long totalPrice) {
        Orders order = new Orders();
        order.setCustomerId(customerId);
        order.setProductId(productId);
        order.setVariantId(variantId);
        order.setQuantity(quantity);
        order.setTotalPrice(totalPrice);
        order.setStatus(Orders.QueueStatus.PENDING);

        return orderQueueRepository.save(order);
    }

    /**
     * Lấy order theo ID
     */
    public Orders getOrder(Long orderId) {
        return orderQueueRepository.findById(orderId).orElse(null);
    }

    /**
     * Lấy order của customer (để check quyền)
     */
    public Orders getOrderByCustomer(Long orderId, Long customerId) {
        return orderQueueRepository.findByIdAndCustomerId(orderId, customerId);
    }

    /**
     * Xử lý 1 order (gọi từ Worker)
     */
    @Transactional
    public void processOrder(Orders order) {
        try {
            // 1. Đánh dấu đang xử lý
            order.setStatus(Orders.QueueStatus.PROCESSING);
            orderQueueRepository.save(order);

            // 2. Lấy thông tin
            User customer = userService.findById(order.getCustomerId());
            Product product = productService.findById(order.getProductId());
            ProductVariant variant = productVariantService.findById(order.getVariantId());

            // 3. Validation
            if (customer == null || product == null || variant == null) {
                throw new RuntimeException("Không tìm thấy thông tin đơn hàng");
            }

            // 4. Kiểm tra stock
            long availableStock = accountService.countAvailableAccounts(order.getVariantId());
            if (availableStock < order.getQuantity()) {
                throw new RuntimeException("Không đủ hàng! Chỉ còn " + availableStock + " sản phẩm");
            }

            // 5. Kiểm tra số dư
            if (customer.getCoins() < order.getTotalPrice()) {
                throw new RuntimeException("Số dư Coin không đủ");
            }

            // 6. Lấy accounts
            List<ProductVariantAccount> accountsToBuy = accountService
                    .getAvailableAccounts(order.getVariantId(), order.getQuantity());

            if (accountsToBuy.size() < order.getQuantity()) {
                throw new RuntimeException("Không đủ account khả dụng");
            }

            // 7. Trừ coins
            customer.setCoins(customer.getCoins() - order.getTotalPrice());
            userService.save(customer);

            // 8. Tạo transaction
            long commission = productService.calculateCommission(product.getSeller().getId(), order.getTotalPrice());

            Transaction transaction = new Transaction();
            transaction.setCustomerId(customer.getId());
            transaction.setSellerId(product.getSeller().getId());
            transaction.setProductId(order.getProductId());
            transaction.setVariantId(order.getVariantId());
            transaction.setAmount(order.getTotalPrice());
            transaction.setCommission(commission);
            transaction.setCoinsUsed(order.getTotalPrice());
            transaction.setStatus("Held");
            transaction.setCreatedBy(customer.getId());
            transaction.setDelete(false);

            // Set escrow release date (3 ngày)
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_MONTH, 3);
            transaction.setEscrowReleaseDate(cal.getTime());

            Transaction savedTransaction = transactionRepository.save(transaction);

            // 9. Cập nhật accounts thành Sold
            for (ProductVariantAccount account : accountsToBuy) {
                account.setStatus(ProductVariantAccount.AccountStatus.Sold);
                account.setTransactionId(savedTransaction.getId());
                accountService.save(account);
            }

            // 10. Cập nhật variant status nếu hết hàng
            long remainingStock = accountService.countAvailableAccounts(order.getVariantId());
            if (remainingStock == 0) {
                variant.setStatus("Inactive");
                productVariantService.save(variant);
            }

            // 11. Đánh dấu order hoàn thành
            order.setStatus(Orders.QueueStatus.COMPLETED);
            order.setTransactionId(savedTransaction.getId());
            order.setProcessedAt(new Date());
            orderQueueRepository.save(order);

        } catch (Exception e) {
            // Đánh dấu thất bại
            order.setStatus(Orders.QueueStatus.FAILED);
            order.setErrorMessage(e.getMessage());
            order.setProcessedAt(new Date());
            orderQueueRepository.save(order);

            e.printStackTrace();
        }
    }

    /**
     * Lấy danh sách pending orders (cho Worker)
     */
    public List<Orders> getPendingOrders() {
        return orderQueueRepository.findByStatusOrderByCreatedAtAsc(Orders.QueueStatus.PENDING);
    }
}