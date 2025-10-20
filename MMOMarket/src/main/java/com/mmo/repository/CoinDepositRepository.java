package com.mmo.repository;

import com.mmo.entity.CoinDeposit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoinDepositRepository extends JpaRepository<CoinDeposit, Long> {
    // CRUD repository for CoinDeposits.
    boolean existsBySepayTransactionId(Long sepayTransactionId);
}
