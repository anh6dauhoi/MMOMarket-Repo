package com.mmo.repository;

import com.mmo.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    // ...existing code...
    List<Transaction> findByCustomer_Id(Long customerId);
    List<Transaction> findBySeller_Id(Long sellerId);
    List<Transaction> findByProduct_Id(Long productId);
    List<Transaction> findByVariant_Id(Long variantId);
    List<Transaction> findByStatus(String status);
}

