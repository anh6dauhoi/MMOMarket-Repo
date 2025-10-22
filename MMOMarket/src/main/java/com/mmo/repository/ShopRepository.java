package com.mmo.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.mmo.entity.ShopInfo;

@Repository
public interface ShopRepository extends JpaRepository<ShopInfo, Long> {

    @Query("SELECT s FROM ShopInfo s " +
           "WHERE s.isDelete = false " +
           "ORDER BY s.createdAt DESC")
    Page<ShopInfo> findAllActiveShops(Pageable pageable);

    @Query("SELECT s FROM ShopInfo s " +
           "WHERE s.isDelete = false " +
           "AND (LOWER(s.shopName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(s.user.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(s.user.email) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY s.createdAt DESC")
    Page<ShopInfo> searchShops(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT s FROM ShopInfo s " +
           "WHERE s.isDelete = true " +
           "ORDER BY s.updatedAt DESC")
    Page<ShopInfo> findDeletedShops(Pageable pageable);
}
