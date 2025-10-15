package com.mmo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Entity
@Getter
@Setter
@Table(name = "Transactions", indexes = {
        @Index(name = "idx_tx_customer_id", columnList = "customer_id"),
        @Index(name = "idx_tx_seller_id", columnList = "seller_id"),
        @Index(name = "idx_tx_product_id", columnList = "product_id"),
        @Index(name = "idx_tx_variant_id", columnList = "variant_id"),
        @Index(name = "idx_tx_status", columnList = "status")
})
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // FKs
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    // Amount fields
    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "commission", nullable = false)
    private Long commission;

    @Column(name = "coins_used", nullable = false)
    private Long coinsUsed;

    // Status
    @Column(name = "status", length = 20, columnDefinition = "VARCHAR(20) DEFAULT 'Pending'")
    private String status = "Pending";

    // Escrow release
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "escrow_release_date")
    private Date escrowReleaseDate;

    // Audit timestamps (DB-managed)
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP", insertable = false, updatable = false)
    private Date createdAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_at", columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", insertable = false, updatable = false)
    private Date updatedAt;

    // Audit users
    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Column(name = "isDelete", columnDefinition = "TINYINT(1) DEFAULT 0")
    private boolean isDelete;

    // Optional readonly navigations for audit
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", insertable = false, updatable = false)
    private User createdByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deleted_by", insertable = false, updatable = false)
    private User deletedByUser;
}