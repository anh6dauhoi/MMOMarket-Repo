package com.mmo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Entity
@Getter
@Setter
@Table(name = "Users", indexes = {@Index(name = "idx_email", columnList = "email")})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(length = 255)
    private String password;

    @Column(name = "full_name", length = 255)
    private String fullName;

    @Column(name = "role", nullable = false, length = 255)
    private String role;

    @Column(length = 20)
    private String phone;

    @Column(name = "shop_status", length = 50, columnDefinition = "VARCHAR(50) DEFAULT 'Inactive'")
    private String shopStatus = "Inactive";

    @Column(name = "coins", columnDefinition = "BIGINT DEFAULT 0")
    private Long coins = 0L;

    @Column(name = "depositCode", length = 50)
    private String depositCode;

    // DDL: isVerified TINYINT(1) DEFAULT 0
    @Column(name = "isVerified", columnDefinition = "TINYINT(1) DEFAULT 0")
    private boolean verified;

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

    // Optional readonly self-references for audit (keep insertable/updatable false)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", insertable = false, updatable = false)
    private User createdByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deleted_by", insertable = false, updatable = false)
    private User deletedByUser;
}
