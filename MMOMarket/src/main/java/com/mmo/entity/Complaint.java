package com.mmo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Entity
@Getter
@Setter
@Table(name = "Complaints", indexes = {
        @Index(name = "idx_seller_status_type", columnList = "seller_id, status, complaint_type"),
        @Index(name = "idx_status_updated_at", columnList = "status, updated_at"),
        @Index(name = "idx_admin_handler_status", columnList = "admin_handler_id, status")
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

    @Enumerated(EnumType.STRING)
    @Column(
        name = "complaint_type",
        nullable = false,
        columnDefinition = "ENUM('ITEM_NOT_WORKING','ITEM_NOT_AS_DESCRIBED','FRAUD_SUSPICION','OTHER') DEFAULT 'ITEM_NOT_WORKING'"
    )
    private ComplaintType complaintType = ComplaintType.ITEM_NOT_WORKING;

    @Lob
    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "evidence", columnDefinition = "JSON")
    private String evidence;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "status",
        nullable = false,
        columnDefinition = "ENUM('NEW','IN_PROGRESS','PENDING_CONFIRMATION','ESCALATED','RESOLVED','CLOSED_BY_ADMIN','CANCELLED') DEFAULT 'NEW'"
    )
    private ComplaintStatus status = ComplaintStatus.NEW;

    @Column(name = "seller_final_response", columnDefinition = "TEXT")
    private String sellerFinalResponse;

    @Column(name = "admin_decision_notes", columnDefinition = "TEXT")
    private String adminDecisionNotes;

    @ManyToOne
    @JoinColumn(name = "admin_handler_id")
    private User adminHandler;

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

    // Enums aligned with DB
    public enum ComplaintType {
        ITEM_NOT_WORKING,
        ITEM_NOT_AS_DESCRIBED,
        FRAUD_SUSPICION,
        OTHER
    }

    public enum ComplaintStatus {
        NEW,
        IN_PROGRESS,
        PENDING_CONFIRMATION,
        ESCALATED,
        RESOLVED,
        CLOSED_BY_ADMIN,
        CANCELLED
    }
}