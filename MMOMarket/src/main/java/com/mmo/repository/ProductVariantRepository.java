package com.mmo.repository;

import com.mmo.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    // Use a derived query so JPA maps columns to the ProductVariant entity automatically.
    List<ProductVariant> findByProductIdAndIsDeleteFalse(Long productId);

    // Get all variants including hidden ones (for seller management)
    List<ProductVariant> findByProductId(Long productId);

    // Count active (non-deleted) variants across all products of a seller
    long countByProduct_Seller_IdAndIsDeleteFalse(Long sellerId);

    // Optionally add an ordered variant lookup if needed by views/controllers:
    // List<ProductVariant> findByProductIdAndIsDeleteFalseOrderByPriceAsc(Long productId);
}
