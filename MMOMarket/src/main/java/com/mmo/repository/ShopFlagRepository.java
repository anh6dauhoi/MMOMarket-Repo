package com.mmo.repository;

import com.mmo.entity.ShopFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ShopFlagRepository extends JpaRepository<ShopFlag, Long> {

    // Find all active flags for a specific shop
    List<ShopFlag> findByShopIdAndStatus(Long shopId, ShopFlag.FlagStatus status);

    // Find all flags for a specific shop (both active and resolved)
    List<ShopFlag> findByShopIdOrderByCreatedAtDesc(Long shopId);

    // Find active flags by shop and flag level
    List<ShopFlag> findByShopIdAndStatusAndFlagLevel(Long shopId, ShopFlag.FlagStatus status, ShopFlag.FlagLevel flagLevel);

    // Check if shop has any active flags
    boolean existsByShopIdAndStatus(Long shopId, ShopFlag.FlagStatus status);

    // Find flags by complaint
    List<ShopFlag> findByRelatedComplaintId(Long complaintId);

    // Find all active flags ordered by severity
    @Query("SELECT sf FROM ShopFlag sf WHERE sf.status = :status ORDER BY " +
           "CASE sf.flagLevel " +
           "WHEN 'BANNED' THEN 1 " +
           "WHEN 'SEVERE' THEN 2 " +
           "WHEN 'WARNING' THEN 3 " +
           "END, sf.createdAt DESC")
    List<ShopFlag> findByStatusOrderByFlagLevelDesc(@Param("status") ShopFlag.FlagStatus status);

    // Count active flags by shop
    long countByShopIdAndStatus(Long shopId, ShopFlag.FlagStatus status);
}

