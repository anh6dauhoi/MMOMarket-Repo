package com.mmo.repository;

import com.mmo.entity.Orders;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrdersRepository extends JpaRepository<Orders, Long> {

    @Query("SELECT o FROM Orders o JOIN FETCH o.product WHERE o.customerId = :customerId")
    Page<Orders> findByCustomerIdOrderByCreatedAtDesc(@Param("customerId") Long customerId, Pageable pageable);

    @Query("SELECT o FROM Orders o JOIN FETCH o.product WHERE o.id = :id")
    Orders findWithProductById(@Param("id") Long id);

    @Query("SELECT o FROM Orders o JOIN FETCH o.product p WHERE o.customerId = :customerId AND LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<Orders> findByCustomerIdAndProductNameContaining(@Param("customerId") Long customerId, @Param("search") String search, Pageable pageable);

    // NEW: count completed purchases for a customer-product pair (to enforce 1 review per purchase)
    long countByCustomerIdAndProductIdAndStatus(Long customerId, Long productId, Orders.QueueStatus status);
}
