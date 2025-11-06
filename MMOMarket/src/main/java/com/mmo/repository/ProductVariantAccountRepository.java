package com.mmo.repository;

import com.mmo.entity.ProductVariantAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
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

    // Locked fetch: pick first N available accounts for update (prevents races during allocation)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProductVariantAccount p WHERE p.variant.id = :variantId AND p.isDelete = false AND p.status = 'Available' ORDER BY p.id ASC")
    List<ProductVariantAccount> findAvailableForUpdate(@Param("variantId") Long variantId, Pageable pageable);
}
