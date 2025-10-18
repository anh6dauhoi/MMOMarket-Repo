package com.mmo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.Date;

@Entity
@Getter
@Setter
@Table(name = "ProductVariantAccounts", indexes = {
    @Index(name = "idx_variant_id_status", columnList = "variant_id, status")
})
public class ProductVariantAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @Column(name = "account_data", columnDefinition = "TEXT", nullable = false)
    private String accountData;

    @Column(name = "status", columnDefinition = "ENUM('Available', 'Sold') NOT NULL DEFAULT 'Available'")
    private String status = "Available";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = true)
    private Transaction transaction;

    @Column(name = "is_activated", columnDefinition = "TINYINT(1) DEFAULT 0")
    private boolean isActivated;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "activated_at")
    private Date activatedAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP", insertable = false, updatable = false)
    private Date createdAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_at", columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", insertable = false, updatable = false)
    private Date updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Column(name = "isDelete", columnDefinition = "TINYINT(1) DEFAULT 0")
    private boolean isDelete;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", insertable = false, updatable = false)
    private User createdByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deleted_by", insertable = false, updatable = false)
    private User deletedByUser;
}

