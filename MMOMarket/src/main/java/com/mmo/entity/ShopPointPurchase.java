package com.mmo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Entity
@Getter
@Setter
@Table(name = "ShopPointPurchases", indexes = {
        @Index(name = "idx_user_id", columnList = "user_id")
})
public class ShopPointPurchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // FK: user_id -> Users(id)
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "points_bought", nullable = false)
    private Long pointsBought;

    @Column(name = "coins_spent", nullable = false)
    private Long coinsSpent;

    @Column(name = "points_before", nullable = false)
    private Long pointsBefore;

    @Column(name = "points_after", nullable = false)
    private Long pointsAfter;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP", insertable = false, updatable = false)
    private Date createdAt;
}

