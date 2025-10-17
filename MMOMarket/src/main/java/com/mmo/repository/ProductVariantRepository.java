package com.mmo.repository;

import com.mmo.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    List<ProductVariant> findByIsDeleteFalse();

    List<ProductVariant> findByProductIdAndIsDeleteFalse(Long productId);

    List<ProductVariant> findByProductIdAndStatusAndIsDeleteFalse(Long productId, String status);

    Optional<ProductVariant> findByIdAndIsDeleteFalse(Long id);

    long countByProductIdAndIsDeleteFalse(Long productId);
}