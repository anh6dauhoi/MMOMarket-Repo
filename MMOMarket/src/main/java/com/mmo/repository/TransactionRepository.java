package com.mmo.repository;

import com.mmo.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // Existing methods
    List<Transaction> findByCustomerIdAndIsDeleteFalse(Long customerId);
    List<Transaction> findBySellerIdAndIsDeleteFalse(Long sellerId);
    List<Transaction> findByCustomerIdAndIsDeleteFalseOrderByCreatedAtDesc(Long customerId);
    List<Transaction> findBySellerId(Long sellerId);

    // New methods cho EscrowService
    List<Transaction> findByStatusAndEscrowReleaseDateBeforeAndIsDeleteFalse(
            String status, Date date);

    List<Transaction> findBySellerIdAndStatusAndIsDeleteFalse(
            Long sellerId, String status);

    List<Transaction> findByCustomerIdAndStatusAndIsDeleteFalse(
            Long customerId, String status);

    // ============ THÊM CHO DASHBOARD - MYSQL ============

    // Tính tổng doanh thu theo seller và khoảng thời gian
    @Query(value = "SELECT COALESCE(SUM(t.amount - t.commission), 0) FROM Transactions t " +
            "WHERE t.seller_id = :sellerId " +
            "AND t.created_at BETWEEN :startDate AND :endDate " +
            "AND t.isDelete = 0 " +
            "AND t.status = 'Completed'",
            nativeQuery = true)
    Long sumRevenueBySellerAndDateRange(@Param("sellerId") Long sellerId,
                                        @Param("startDate") Date startDate,
                                        @Param("endDate") Date endDate);

    // Đếm số giao dịch theo seller và khoảng thời gian
    Long countBySellerIdAndCreatedAtBetweenAndIsDeleteFalse(
            Long sellerId, Date startDate, Date endDate);

    // Top sản phẩm bán chạy
    @Query(value = "SELECT p.name, COUNT(t.id) as sales " +
            "FROM Transactions t " +
            "JOIN Products p ON t.product_id = p.id " +
            "WHERE t.seller_id = :sellerId " +
            "AND t.isDelete = 0 " +
            "GROUP BY p.id, p.name " +
            "ORDER BY sales DESC " +
            "LIMIT :limit",
            nativeQuery = true)
    List<Object[]> findTopSellingProducts(@Param("sellerId") Long sellerId,
                                          @Param("limit") int limit);

    // Doanh thu theo tuần
    @Query(value = "SELECT WEEK(t.created_at) as week, " +
            "COALESCE(SUM(t.amount - t.commission), 0) as revenue " +
            "FROM Transactions t " +
            "WHERE t.seller_id = :sellerId " +
            "AND t.created_at >= DATE_SUB(NOW(), INTERVAL :days DAY) " +
            "AND t.isDelete = 0 " +
            "AND t.status = 'Completed' " +
            "GROUP BY WEEK(t.created_at) " +
            "ORDER BY week",
            nativeQuery = true)
    List<Object[]> getWeeklyRevenue(@Param("sellerId") Long sellerId,
                                    @Param("days") int days);
}