package com.mmo.repository;

import com.mmo.entity.ShopPointPurchase;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopPointPurchaseRepository extends JpaRepository<ShopPointPurchase, Long> {
    // Additional query methods can be added if idempotency/dedupe is required in the future.
}

