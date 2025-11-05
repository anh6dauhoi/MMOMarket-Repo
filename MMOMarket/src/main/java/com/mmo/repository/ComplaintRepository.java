package com.mmo.repository;

import com.mmo.entity.Complaint;
import com.mmo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {
    List<Complaint> findByCustomer(User customer);

    List<Complaint> findBySeller(User seller);

    List<Complaint> findByStatus(Complaint.ComplaintStatus status);

    // Check for complaints by transaction id and status (no IgnoreCase needed for Enum)
    boolean existsByTransactionIdAndStatus(Long transactionId, Complaint.ComplaintStatus status);
}
