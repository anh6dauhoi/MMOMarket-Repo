package com.mmo.dto;

import java.util.Date;

public class SellerTransactionListItem {
    private final Long id;
    private final String productName;
    private final String variantName;
    private final Long quantity;
    private final Long coinSeller;
    private final String status;
    private final Date createdAt;

    public SellerTransactionListItem(Long id, String productName, String variantName, Long quantity, Long coinSeller, String status, Date createdAt) {
        this.id = id;
        this.productName = productName;
        this.variantName = variantName;
        this.quantity = quantity;
        this.coinSeller = coinSeller;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getProductName() { return productName; }
    public String getVariantName() { return variantName; }
    public Long getQuantity() { return quantity; }
    public Long getCoinSeller() { return coinSeller; }
    public String getStatus() { return status; }
    public Date getCreatedAt() { return createdAt; }
}

