package com.mmo.repository;

import com.mmo.entity.Complaint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

    // Tìm complaints theo transaction
    List<Complaint> findByTransactionIdAndIsDeleteFalse(Long transactionId);

    // Tìm complaints đang mở (Open) của một transaction
    List<Complaint> findByTransactionIdAndStatusAndIsDeleteFalse(
            Long transactionId, String status);

    // Tìm complaints của customer
    List<Complaint> findByCustomerIdAndIsDeleteFalseOrderByCreatedAtDesc(Long customerId);

    // Tìm complaints của seller
    List<Complaint> findBySellerIdAndIsDeleteFalseOrderByCreatedAtDesc(Long sellerId);

    // Tìm complaints theo status
    List<Complaint> findByStatusAndIsDeleteFalse(String status);

    // Đếm số complaints đang mở của một transaction
    long countByTransactionIdAndStatusAndIsDeleteFalse(Long transactionId, String status);
}