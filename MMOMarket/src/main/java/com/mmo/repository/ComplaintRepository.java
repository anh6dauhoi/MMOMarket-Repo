package com.mmo.repository;

import com.mmo.entity.Complaint;
import com.mmo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Date;
import java.util.List;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {
    List<Complaint> findByCustomer(User customer);

    List<Complaint> findBySeller(User seller);

    List<Complaint> findByStatus(Complaint.ComplaintStatus status);

    // Find complaints by transaction ID
    List<Complaint> findByTransactionId(Long transactionId);

    // Check for complaints by transaction id and status (no IgnoreCase needed for Enum)
    boolean existsByTransactionIdAndStatus(Long transactionId, Complaint.ComplaintStatus status);

    // Check if transaction has any active complaint (not resolved/cancelled/closed)
    boolean existsByTransactionIdAndStatusIn(Long transactionId, List<Complaint.ComplaintStatus> statuses);

    // Find active complaint for transaction
    Complaint findFirstByTransactionIdAndStatusIn(Long transactionId, List<Complaint.ComplaintStatus> statuses);

    // Find complaints by status and updatedAt before a certain date (for auto-resolve after 3 days)
    List<Complaint> findByStatusAndUpdatedAtBefore(Complaint.ComplaintStatus status, Date updatedAt);
}
