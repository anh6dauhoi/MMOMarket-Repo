package com.mmo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Entity
@Getter
@Setter
@Table(name = "Complaints", indexes = {
        @Index(name = "idx_complaint_transaction_id", columnList = "transaction_id"),
        @Index(name = "idx_complaint_customer_id", columnList = "customer_id"),
        @Index(name = "idx_complaint_seller_id", columnList = "seller_id"),
        @Index(name = "idx_complaint_status", columnList = "status")
})
public class Complaint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id")
    private Long transactionId;

    @ManyToOne
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @Lob
    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Lob
    @Column(name = "evidence", columnDefinition = "TEXT")
    private String evidence;

    @Column(name = "status", length = 20, columnDefinition = "VARCHAR(20) DEFAULT 'Open'")
    private String status = "Open"; // Open, Resolved, Rejected

    @Lob
    @Column(name = "resolution", columnDefinition = "TEXT")
    private String resolution;

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

    // Optional readonly audit associations
    @ManyToOne
    @JoinColumn(name = "created_by", insertable = false, updatable = false)
    private User createdByUser;

    @ManyToOne
    @JoinColumn(name = "deleted_by", insertable = false, updatable = false)
    private User deletedByUser;

    // ← THÊM lifecycle callbacks để tự động set timestamps
    @PrePersist
    protected void onCreate() {
        createdAt = new Date();
        updatedAt = new Date();
        if (status == null) {
            status = "Open";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = new Date();
    }
}