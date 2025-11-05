package com.mmo.repository;

import com.mmo.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Date;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByCustomer_Id(Long customerId);

    List<Transaction> findBySeller_Id(Long sellerId);

    List<Transaction> findByProduct_Id(Long productId);

    List<Transaction> findByVariant_Id(Long variantId);

    List<Transaction> findByStatus(String status);

    // New: find escrow transactions whose release date passed
    List<Transaction> findByStatusAndEscrowReleaseDateBefore(String status, Date before);
}
