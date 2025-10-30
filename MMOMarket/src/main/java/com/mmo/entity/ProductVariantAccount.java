package com.mmo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Entity
@Getter
@Setter
@Table(name = "ProductVariantAccounts", indexes = {
        @Index(name = "idx_variant_status", columnList = "variant_id, status")
})
public class ProductVariantAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @ManyToOne
    @JoinColumn(name = "variant_id", insertable = false, updatable = false)
    private ProductVariant variant;

    @Column(name = "account_data", nullable = false, columnDefinition = "TEXT")
    private String accountData;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ENUM('Available', 'Sold') DEFAULT 'Available'")
    private AccountStatus status = AccountStatus.Available;

    @Column(name = "transaction_id")
    private Long transactionId;

    @ManyToOne
    @JoinColumn(name = "transaction_id", insertable = false, updatable = false)
    private Transaction transaction;

    @Column(name = "is_activated", columnDefinition = "TINYINT(1) DEFAULT 0")
    private boolean isActivated = false;

    @Column(name = "activated_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date activatedAt;

    @Column(name = "created_at", columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;

    @Column(name = "updated_at", columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Column(name = "isDelete", columnDefinition = "TINYINT(1) DEFAULT 0")
    private boolean isDelete = false;

    @ManyToOne
    @JoinColumn(name = "created_by", insertable = false, updatable = false)
    private User createdByUser;

    @ManyToOne
    @JoinColumn(name = "deleted_by", insertable = false, updatable = false)
    private User deletedByUser;

    public enum AccountStatus {
        Available,
        Sold
    }

    @PrePersist
    protected void onCreate() {
        createdAt = new Date();
        updatedAt = new Date();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = new Date();
    }
}