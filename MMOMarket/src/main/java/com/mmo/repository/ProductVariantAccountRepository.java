package com.mmo.repository;

import com.mmo.entity.ProductVariantAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductVariantAccountRepository extends JpaRepository<ProductVariantAccount, Long> {

    /**
     * Đếm số lượng account Available cho một variant
     */
    long countByVariantIdAndStatusAndIsDeleteFalse(
            Long variantId,
            ProductVariantAccount.AccountStatus status
    );

    /**
     * Lấy danh sách account Available
     */
    List<ProductVariantAccount> findByVariantIdAndStatusAndIsDeleteFalseOrderByCreatedAtAsc(
            Long variantId,
            ProductVariantAccount.AccountStatus status
    );

    /**
     * Lấy account theo transaction
     */
    List<ProductVariantAccount> findByTransactionIdAndIsDeleteFalse(Long transactionId);

    /**
     * Lấy tất cả account của variant (bao gồm sold)
     */
    List<ProductVariantAccount> findByVariantIdAndIsDeleteFalse(Long variantId);
}