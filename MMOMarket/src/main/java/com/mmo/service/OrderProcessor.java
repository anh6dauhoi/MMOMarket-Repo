package com.mmo.service;

import com.mmo.entity.Orders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class OrderProcessor {

    @Autowired
    private OrderQueueService orderQueueService;

    /**
     * Worker chạy mỗi 3 giây để xử lý pending orders
     * @Scheduled cần @EnableScheduling trong Application class
     */
    @Scheduled(fixedDelay = 3000) // 3 giây
    public void processOrderQueue() {
        // Lấy danh sách pending orders
        List<Orders> pendingOrders = orderQueueService.getPendingOrders();

        if (pendingOrders.isEmpty()) {
            return; // Không có order nào cần xử lý
        }

        System.out.println("Processing " + pendingOrders.size() + " pending orders...");

        // Xử lý từng order
        for (Orders order : pendingOrders) {
            try {
                orderQueueService.processOrder(order);
                System.out.println("✓ Order #" + order.getId() + " completed");
            } catch (Exception e) {
                System.err.println("✗ Order #" + order.getId() + " failed: " + e.getMessage());
            }
        }
    }
}