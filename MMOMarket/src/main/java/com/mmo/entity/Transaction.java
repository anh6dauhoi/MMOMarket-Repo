package com.mmo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.Date;

@Entity
@Getter
@Setter
@Table(name = "Transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false)
    private Long commission;

    @Column(name = "coins_used", nullable = false)
    private Long coinsUsed;

    @Column(columnDefinition = "VARCHAR(20) DEFAULT 'Pending'")
    private String status;

    @Column(name = "escrow_release_date")
    @Temporal(TemporalType.TIMESTAMP)
    private Date escrowReleaseDate;

    @Column(name = "created_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;

    @Column(name = "updated_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Column(name = "isDelete", columnDefinition = "TINYINT(1) DEFAULT 0")
    private boolean isDelete;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = new Date();
        }
    }
}