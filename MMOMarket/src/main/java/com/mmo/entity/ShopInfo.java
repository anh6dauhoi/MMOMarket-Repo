package com.mmo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.Date;

@Entity
@Getter
@Setter
@Table(name = "ShopInfo", uniqueConstraints = {
    @UniqueConstraint(name = "unique_active_shop", columnNames = {"user_id", "isDelete"})
})
public class ShopInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_shopinfo_user", value = ConstraintMode.CONSTRAINT))
    private User user;

    @Column(name = "shop_name", nullable = false, length = 255)
    private String shopName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "shop_level", columnDefinition = "TINYINT UNSIGNED DEFAULT 0")
    private Short shopLevel = 0;

    @Column(name = "commission", precision = 5, scale = 2, nullable = false)
    private BigDecimal commission;

    @Column(name = "points", nullable = false, columnDefinition = "BIGINT DEFAULT 0")
    private Long points = 0L;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP", insertable = false, updatable = false)
    private Date createdAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_at", columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", insertable = false, updatable = false)
    private Date updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", foreignKey = @ForeignKey(name = "fk_shopinfo_createdby", value = ConstraintMode.CONSTRAINT))
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deleted_by", foreignKey = @ForeignKey(name = "fk_shopinfo_deletedby", value = ConstraintMode.CONSTRAINT))
    private User deletedBy;

    @Column(name = "isDelete", columnDefinition = "TINYINT(1) DEFAULT 0")
    private boolean isDelete;
}
