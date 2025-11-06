package com.mmo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "Categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(name = "description", length = 500)
    private String description;
    // Allowed values: Common, Warning (kept as String to match DB ENUM values)
    @Column(name = "type", length = 20, nullable = false, columnDefinition = "ENUM('Common','Warning') DEFAULT 'Common'")
    private String type = "Common";
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
    @Column(name = "status", columnDefinition = "TINYINT(1) DEFAULT 1")
    private boolean status = true; // true = Active (1), false = Inactive (0)
    // Products relationship
    @OneToMany(mappedBy = "category", fetch = FetchType.LAZY)
    private List<Product> products;
    // Optional readonly audit relations
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", insertable = false, updatable = false)
    private User createdByUser;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deleted_by", insertable = false, updatable = false)
    private User deletedByUser;

    // Transient field for product count (use productCountCache when available)
    @Transient
    private Integer productCountCache;
    @Transient
    public Integer getProductCount() {
        // Use cached count if available to avoid loading all products
        if (productCountCache != null) {
            return productCountCache;
        }
        return products != null ? products.size() : 0;
    }
    @Transient
    public void setProductCountCache(Integer count) {
        this.productCountCache = count;
    }
    /**
     * Category type enum representing the possible values for the type column
     */
    public enum CategoryType {
        Common, Warning
    }
}