package com.mmo.repository;

import com.mmo.entity.ShopInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ShopInfoRepository extends JpaRepository<ShopInfo, Long> {
    // prefer returned shop that is not deleted
    Optional<ShopInfo> findByUserIdAndIsDeleteFalse(Long userId);

    // fallback convenience
    Optional<ShopInfo> findByUser_Id(Long userId);

    // NEW: find by shop name (case-insensitive) for slug support
    Optional<ShopInfo> findFirstByShopNameIgnoreCaseAndIsDeleteFalse(String shopName);
}
