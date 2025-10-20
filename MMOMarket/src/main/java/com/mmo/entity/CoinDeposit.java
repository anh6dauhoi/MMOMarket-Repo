package com.mmo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Entity
@Getter
@Setter
@Table(name = "CoinDeposits", indexes = {
        @Index(name = "idx_user_id", columnList = "user_id"),
        @Index(name = "idx_sepay_ref_code", columnList = "sepay_reference_code")
})
public class CoinDeposit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // FK: user_id -> Users(id)
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "coins_added", nullable = false)
    private Long coinsAdded;

    @Column(name = "status", length = 20, columnDefinition = "VARCHAR(20) DEFAULT 'Pending'")
    private String status = "Pending";

    @Column(name = "sepay_transaction_id", unique = true)
    private Long sepayTransactionId;

    @Column(name = "sepay_reference_code", length = 255)
    private String sepayReferenceCode;

    @Column(name = "gateway", length = 100)
    private String gateway;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "transaction_date")
    private Date transactionDate;

    @Lob
    @Column(name = "content")
    private String content;

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

    // Optional readonly audit relations
    @ManyToOne
    @JoinColumn(name = "created_by", insertable = false, updatable = false)
    private User createdByUser;

    @ManyToOne
    @JoinColumn(name = "deleted_by", insertable = false, updatable = false)
    private User deletedByUser;
}