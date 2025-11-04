package com.mmo.dto;

public class TransactionDetailResponse {
    private Long id;

    private Long customerId;
    private String customerName;
    private String customerEmail;

    private Long sellerId;
    private String sellerName;
    private String sellerEmail;

    private Long productId;
    private String productTitle;
    private Long variantId;
    private String variantName;

    private Long amount;
    private Long commission;
    private Long coinAdmin;
    private Long coinSeller;
    private String status;
    private String escrowReleaseDate;
    private Long deliveredAccountId;

    private String createdAt;
    private String updatedAt;

    public TransactionDetailResponse(Long id, Long customerId, String customerName, String customerEmail,
                                     Long sellerId, String sellerName, String sellerEmail,
                                     Long productId, String productTitle, Long variantId, String variantName,
                                     Long amount, Long commission, Long coinAdmin, Long coinSeller, String status, String escrowReleaseDate,
                                     Long deliveredAccountId, String createdAt, String updatedAt) {
        this.id = id;
        this.customerId = customerId;
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.sellerId = sellerId;
        this.sellerName = sellerName;
        this.sellerEmail = sellerEmail;
        this.productId = productId;
        this.productTitle = productTitle;
        this.variantId = variantId;
        this.variantName = variantName;
        this.amount = amount;
        this.commission = commission;
        this.coinAdmin = coinAdmin;
        this.coinSeller = coinSeller;
        this.status = status;
        this.escrowReleaseDate = escrowReleaseDate;
        this.deliveredAccountId = deliveredAccountId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Getters
    public Long getId() { return id; }
    public Long getCustomerId() { return customerId; }
    public String getCustomerName() { return customerName; }
    public String getCustomerEmail() { return customerEmail; }
    public Long getSellerId() { return sellerId; }
    public String getSellerName() { return sellerName; }
    public String getSellerEmail() { return sellerEmail; }
    public Long getProductId() { return productId; }
    public String getProductTitle() { return productTitle; }
    public Long getVariantId() { return variantId; }
    public String getVariantName() { return variantName; }
    public Long getAmount() { return amount; }
    public Long getCommission() { return commission; }
    public Long getCoinAdmin() { return coinAdmin; }
    public Long getCoinSeller() { return coinSeller; }
    public String getStatus() { return status; }
    public String getEscrowReleaseDate() { return escrowReleaseDate; }
    public Long getDeliveredAccountId() { return deliveredAccountId; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }
}

