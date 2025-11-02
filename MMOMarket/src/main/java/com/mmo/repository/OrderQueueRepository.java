package com.mmo.repository;

import com.mmo.entity.Orders;
import com.mmo.entity.Orders.QueueStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface OrderQueueRepository extends JpaRepository<Orders, Long> {

    // Lấy orders đang pending (để worker xử lý)
    List<Orders> findByStatusOrderByCreatedAtAsc(QueueStatus status);

    // Lấy orders của customer
    List<Orders> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    // Tìm order theo id và customerId (để check quyền)
    Orders findByIdAndCustomerId(Long id, Long customerId);
}