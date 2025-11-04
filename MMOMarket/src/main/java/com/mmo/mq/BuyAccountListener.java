package com.mmo.mq;

import com.mmo.entity.*;
import com.mmo.mq.dto.BuyAccountMessage;
import com.mmo.repository.*;
import com.mmo.service.NotificationService;
import com.mmo.service.SystemConfigurationService;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

@Component
public class BuyAccountListener {
    private static final Logger log = LoggerFactory.getLogger(BuyAccountListener.class);

    private final OrdersRepository ordersRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductVariantAccountRepository productVariantAccountRepository;
    private final TransactionRepository transactionRepository;
    private final NotificationService notificationService;
    private final SystemConfigurationService systemConfigurationService;
    private final ShopInfoRepository shopInfoRepository;
    private final RabbitAdmin rabbitAdmin;

    public BuyAccountListener(OrdersRepository ordersRepository,
                              UserRepository userRepository,
                              ProductRepository productRepository,
                              ProductVariantRepository productVariantRepository,
                              ProductVariantAccountRepository productVariantAccountRepository,
                              TransactionRepository transactionRepository,
                              NotificationService notificationService,
                              SystemConfigurationService systemConfigurationService,
                              ShopInfoRepository shopInfoRepository,
                              RabbitAdmin rabbitAdmin) {
        this.ordersRepository = ordersRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.productVariantAccountRepository = productVariantAccountRepository;
        this.transactionRepository = transactionRepository;
        this.notificationService = notificationService;
        this.systemConfigurationService = systemConfigurationService;
        this.shopInfoRepository = shopInfoRepository;
        this.rabbitAdmin = rabbitAdmin;
    }

    @Transactional
    @RabbitListener(queues = RabbitConfig.BUY_ACCOUNT_QUEUE, containerFactory = "buyAccountListenerContainerFactory")
    public void handle(BuyAccountMessage msg) {
        long t0 = System.nanoTime();
        // Log queue depth and consumer count if available
        try {
            Properties props = rabbitAdmin.getQueueProperties(RabbitConfig.BUY_ACCOUNT_QUEUE);
            if (props != null) {
                Object depth = props.get("QUEUE_MESSAGE_COUNT");
                Object consumers = props.get("QUEUE_CONSUMER_COUNT");
                log.info("[Metrics] Queue={} depth={} consumers={}", RabbitConfig.BUY_ACCOUNT_QUEUE, depth, consumers);
            }
        } catch (Exception ignored) {}

        if (msg == null || msg.orderId() == null) return;
        Long orderId = msg.orderId();
        log.info("Processing buy-account orderId={}", orderId);

        Orders order = ordersRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("Order {} not found", orderId);
            return;
        }
        if (order.getStatus() == Orders.QueueStatus.COMPLETED || order.getStatus() == Orders.QueueStatus.FAILED) {
            log.info("Order {} already in terminal status {}", orderId, order.getStatus());
            return;
        }
        order.setStatus(Orders.QueueStatus.PROCESSING);
        order.setProcessedAt(new Date());
        ordersRepository.save(order);

        try {
            // Load entities
            Optional<User> customerOpt = userRepository.findById(order.getCustomerId());
            Optional<ProductVariant> variantOpt = productVariantRepository.findById(order.getVariantId());
            Optional<Product> productOpt = productRepository.findById(order.getProductId());
            if (customerOpt.isEmpty() || variantOpt.isEmpty() || productOpt.isEmpty()) {
                failOrder(order, "Reference not found: customer/product/variant");
                return;
            }
            User customer = customerOpt.get();
            ProductVariant variant = variantOpt.get();
            Product product = productOpt.get();
            long quantity = order.getQuantity() == null ? 1L : order.getQuantity();
            long unitPrice = variant.getPrice() == null ? 0L : variant.getPrice();
            long total = unitPrice * quantity;

            // Stock revalidation
            long stock = productVariantAccountRepository.countByVariant_IdAndIsDeleteFalseAndStatus(variant.getId(), "Available");
            if (stock < quantity) {
                failOrder(order, "Out of stock. Available: " + stock);
                return;
            }

            // Deduct coins atomically
            int updated = userRepository.deductCoinsIfEnough(customer.getId(), total);
            if (updated == 0) {
                failOrder(order, "Insufficient coins");
                try { notificationService.createNotificationForUser(customer.getId(), "Purchase failed", "You don't have enough coins to complete this order."); } catch (Exception ignored) {}
                return;
            }
            // New: Notify customer about coin deduction
            try {
                notificationService.createNotificationForUser(
                        customer.getId(),
                        "Payment authorized",
                        "We have deducted " + String.format("%,d", total) + " coins from your balance for order #" + order.getId() +
                        " (" + (product.getName() != null ? product.getName() : ("Product #" + product.getId())) + ")."
                );
            } catch (Exception ignored) {}

            // Commission percent from seller's ShopInfo if available; fallback to system default
            BigDecimal commissionPercent = null;
            try {
                var shopOpt = shopInfoRepository.findByUserIdAndIsDeleteFalse(product.getSeller().getId())
                        .or(() -> shopInfoRepository.findByUser_Id(product.getSeller().getId()));
                if (shopOpt.isPresent() && shopOpt.get().getCommission() != null) {
                    commissionPercent = shopOpt.get().getCommission();
                }
            } catch (Exception ex) {
                log.warn("Could not read ShopInfo commission for seller {}: {}", product.getSeller().getId(), ex.getMessage());
            }
            if (commissionPercent == null) commissionPercent = systemConfigurationService.getDefaultCommissionPercentage();
            if (commissionPercent == null) commissionPercent = new BigDecimal("5.00");

            // Calculate fee and seller amount
            BigDecimal totalBd = new BigDecimal(total);
            long fee = totalBd.multiply(commissionPercent).divide(new BigDecimal("100"), 0, RoundingMode.HALF_UP).longValue();
            long sellerCoins = total - fee;
            if (fee < 0) fee = 0;
            if (sellerCoins < 0) sellerCoins = 0;

            // Create Transaction in ESCROW
            Transaction tx = new Transaction();
            tx.setCustomer(customer);
            tx.setSeller(product.getSeller());
            tx.setProduct(product);
            tx.setVariant(variant);
            tx.setQuantity(quantity);
            tx.setAmount(total);
            tx.setCommission(fee);
            tx.setCoinAdmin(fee);
            tx.setCoinSeller(sellerCoins);
            tx.setStatus("ESCROW");
            Calendar cal = Calendar.getInstance();
            cal.setTime(new Date());
            cal.add(Calendar.DAY_OF_MONTH, 3);
            tx.setEscrowReleaseDate(cal.getTime());
            tx.setCreatedBy(customer.getId());
            transactionRepository.save(tx);

            // Allocate accounts with lock
            List<ProductVariantAccount> accounts = productVariantAccountRepository.findAvailableForUpdate(variant.getId(), PageRequest.of(0, Math.toIntExact(quantity)));
            if (accounts.size() < quantity) {
                // Refund coins and fail
                userRepository.addCoins(customer.getId(), total);
                failOrder(order, "Insufficient stock during allocation");
                return;
            }
            for (int i = 0; i < quantity; i++) {
                ProductVariantAccount acc = accounts.get(i);
                acc.setStatus("Sold");
                acc.setTransaction(tx);
                acc.setActivated(false);
                acc.setUpdatedAt(new Date());
            }
            productVariantAccountRepository.saveAll(accounts.subList(0, Math.toIntExact(quantity)));

            // Update order to completed
            order.setTransactionId(tx.getId());
            order.setStatus(Orders.QueueStatus.COMPLETED);
            order.setProcessedAt(new Date());
            ordersRepository.save(order);

            // Refined: purchase success notification in English
            try {
                notificationService.createNotificationForUser(
                        customer.getId(),
                        "Purchase successful",
                        "Your accounts are ready. You can view and activate them in My Orders (Order #" + order.getId() + ")."
                );
            } catch (Exception ignored) {}

            long t1 = System.nanoTime();
            log.info("[Metrics] Order {} done in {} ms (tx={})", order.getId(), Math.round((t1 - t0)/1_000_000.0), tx.getId());
        } catch (Exception ex) {
            log.error("Error processing order {}: {}", orderId, ex.getMessage(), ex);
            order.setStatus(Orders.QueueStatus.FAILED);
            order.setErrorMessage(ex.getMessage());
            order.setProcessedAt(new Date());
            ordersRepository.save(order);
        }
    }

    private void failOrder(Orders order, String message) {
        order.setStatus(Orders.QueueStatus.FAILED);
        order.setErrorMessage(message);
        order.setProcessedAt(new Date());
        ordersRepository.save(order);
        try {
            notificationService.createNotificationForUser(order.getCustomerId(), "Purchase failed", message);
        } catch (Exception ignored) {}
        log.warn("Order {} failed: {}", order.getId(), message);
    }
}
