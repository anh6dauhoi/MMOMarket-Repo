package com.mmo.repository;

import com.mmo.entity.ProductVariantAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductVariantAccountRepository extends JpaRepository<ProductVariantAccount, Long> {

    // count available accounts for a variant (isDelete = false and status = 'Available')
    long countByVariant_IdAndIsDeleteFalseAndStatus(Long variantId, String status);

    // find delivered accounts for a transaction (exclude soft-deleted)
    List<ProductVariantAccount> findByTransaction_IdAndIsDeleteFalse(Long transactionId);

    // find single delivered account by id and transaction id (exclude soft-deleted)
    Optional<ProductVariantAccount> findByIdAndTransaction_IdAndIsDeleteFalse(Long id, Long transactionId);
}
