package com.mmo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Entity
@Getter
@Setter
@Table(name = "ShopFlags", indexes = {
    @Index(name = "idx_shop_status", columnList = "shop_id, status")
})
public class ShopFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "shop_id", nullable = true,
        foreignKey = @ForeignKey(name = "fk_shopflag_shop", value = ConstraintMode.CONSTRAINT))
    private ShopInfo shop;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_shopflag_admin", value = ConstraintMode.NO_CONSTRAINT))
    private User admin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_complaint_id",
        foreignKey = @ForeignKey(name = "fk_shopflag_complaint",
            foreignKeyDefinition = "FOREIGN KEY (related_complaint_id) REFERENCES Complaints(id) ON DELETE SET NULL"))
    private Complaint relatedComplaint;

    @Lob
    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "flag_level", nullable = false,
        columnDefinition = "ENUM('WARNING', 'SEVERE', 'BANNED') DEFAULT 'WARNING'")
    private FlagLevel flagLevel = FlagLevel.WARNING;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false,
        columnDefinition = "ENUM('ACTIVE', 'RESOLVED') DEFAULT 'ACTIVE'")
    private FlagStatus status = FlagStatus.ACTIVE;

    @Lob
    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP",
        insertable = false, updatable = false)
    private Date createdAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_at", columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP",
        insertable = false, updatable = false)
    private Date updatedAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "resolved_at")
    private Date resolvedAt;

    public enum FlagLevel {
        WARNING,
        SEVERE,
        BANNED
    }

    public enum FlagStatus {
        ACTIVE,
        RESOLVED
    }
}

