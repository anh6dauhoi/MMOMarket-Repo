package com.mmo.repository;

import com.mmo.entity.ProductVariantAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductVariantAccountRepository extends JpaRepository<ProductVariantAccount, Long> {

    // count available accounts for a variant (isDelete = false and status = 'Available')
    long countByVariant_IdAndIsDeleteFalseAndStatus(Long variantId, String status);
}

