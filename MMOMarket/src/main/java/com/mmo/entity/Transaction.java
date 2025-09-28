package com.mmo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;
    @ManyToOne
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;
    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
    @ManyToOne
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;
    @Column(nullable = false)
    private Long amount;
    @Column(nullable = false)
    private Long commission;
    @Column(nullable = false)
    private Long coinsUsed;
    private String status;
    private java.util.Date escrowReleaseDate;
    private java.util.Date createdAt;
    private java.util.Date updatedAt;
    private Long createdBy;
    private Long deletedBy;
    private boolean isDelete;
}