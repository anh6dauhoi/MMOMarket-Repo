package com.mmo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.Date;

@Entity
@Getter
@Setter
@Table(name = "ProductVariants", indexes = {
        @Index(name = "idx_product_id", columnList = "product_id")
})
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // FK: product_id -> Products(id)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Product product;

    @Column(name = "variant_name", nullable = false, length = 255)
    private String variantName;

    @Column(name = "price", nullable = false)
    private Long price;


    @Column(name = "status", length = 20, columnDefinition = "VARCHAR(20) DEFAULT 'Pending'")
    private String status = "Pending";

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
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", insertable = false, updatable = false)
    private User createdByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deleted_by", insertable = false, updatable = false)
    private User deletedByUser;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = new Date();
    }

    // THÊM: Transient field để hiển thị stock
    @Transient
    private Long stock;

    public Long getStock() {
        return stock;
    }

    public void setStock(Long stock) {
        this.stock = stock;
    }
}