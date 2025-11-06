package com.mmo.dto;

import java.util.Date;

public class AdminTransactionListItem {
    private final Long id;
    private final Long orderId;
    private final String orderRequestId;
    private final String orderStatus;
    private final String status;
    private final Long amount;
    private final Long coinSeller;
    private final Long coinAdmin;
    private final Long quantity;
    private final String productName;
    private final String variantName;
    private final String customerName;
    private final String customerEmail;
    private final String sellerName;
    private final String sellerEmail;
    private final Date createdAt;
    private final Date processedAt;

    public AdminTransactionListItem(Long id,
                                    Long orderId,
                                    String orderRequestId,
                                    String orderStatus,
                                    String status,
                                    Long amount,
                                    Long coinSeller,
                                    Long coinAdmin,
                                    Long quantity,
                                    String productName,
                                    String variantName,
                                    String customerName,
                                    String customerEmail,
                                    String sellerName,
                                    String sellerEmail,
                                    Date createdAt,
                                    Date processedAt) {
        this.id = id;
        this.orderId = orderId;
        this.orderRequestId = orderRequestId;
        this.orderStatus = orderStatus;
        this.status = status;
        this.amount = amount;
        this.coinSeller = coinSeller;
        this.coinAdmin = coinAdmin;
        this.quantity = quantity;
        this.productName = productName;
        this.variantName = variantName;
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.sellerName = sellerName;
        this.sellerEmail = sellerEmail;
        this.createdAt = createdAt;
        this.processedAt = processedAt;
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public String getOrderRequestId() { return orderRequestId; }
    public String getOrderStatus() { return orderStatus; }
    public String getStatus() { return status; }
    public Long getAmount() { return amount; }
    public Long getCoinSeller() { return coinSeller; }
    public Long getCoinAdmin() { return coinAdmin; }
    public Long getQuantity() { return quantity; }
    public String getProductName() { return productName; }
    public String getVariantName() { return variantName; }
    public String getCustomerName() { return customerName; }
    public String getCustomerEmail() { return customerEmail; }
    public String getSellerName() { return sellerName; }
    public String getSellerEmail() { return sellerEmail; }
    public Date getCreatedAt() { return createdAt; }
    public Date getProcessedAt() { return processedAt; }
}

