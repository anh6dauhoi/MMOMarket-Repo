package com.mmo.repository;

import com.mmo.entity.Complaint;
import com.mmo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {
    List<Complaint> findByCustomer(User customer);

    List<Complaint> findBySeller(User seller);

    List<Complaint> findByStatus(String status);

    // New: quick existence check for open complaints by transaction id
    boolean existsByTransactionIdAndStatusIgnoreCase(Long transactionId, String status);
}
