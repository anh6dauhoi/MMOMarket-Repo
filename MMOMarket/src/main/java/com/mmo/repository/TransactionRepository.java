package com.mmo.repository;

import com.mmo.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByCustomer_Id(Long customerId);

    List<Transaction> findBySeller_Id(Long sellerId);

    List<Transaction> findByProduct_Id(Long productId);

    List<Transaction> findByVariant_Id(Long variantId);

    List<Transaction> findByStatus(String status);

    // Seller statistics queries
    @Query("SELECT COALESCE(SUM(t.coinSeller), 0) FROM Transaction t WHERE t.seller.id = :sellerId AND t.isDelete = false AND t.status != 'CREATED'")
    Long getTotalRevenueBySellerId(@Param("sellerId") Long sellerId);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.seller.id = :sellerId AND t.isDelete = false")
    Long getTotalOrdersBySellerId(@Param("sellerId") Long sellerId);

    @Query("SELECT COALESCE(SUM(t.coinSeller), 0) FROM Transaction t WHERE t.seller.id = :sellerId AND t.isDelete = false AND t.status != 'CREATED' AND t.createdAt >= :startDate")
    Long getRevenueBySellerIdAndDate(@Param("sellerId") Long sellerId, @Param("startDate") Date startDate);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.seller.id = :sellerId AND t.isDelete = false AND t.createdAt >= :startDate")
    Long getOrdersBySellerIdAndDate(@Param("sellerId") Long sellerId, @Param("startDate") Date startDate);

    @Query("SELECT t FROM Transaction t WHERE t.seller.id = :sellerId AND t.isDelete = false ORDER BY t.createdAt DESC")
    List<Transaction> findRecentTransactionsBySeller(@Param("sellerId") Long sellerId);

    // New queries for time-based filtering
    @Query("SELECT COALESCE(SUM(t.coinSeller), 0) FROM Transaction t WHERE t.seller.id = :sellerId AND t.isDelete = false AND t.status != 'CREATED' AND t.createdAt >= :startDate")
    Long getRevenueBySellerIdAndDateAfter(@Param("sellerId") Long sellerId, @Param("startDate") Date startDate);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.seller.id = :sellerId AND t.isDelete = false AND t.createdAt >= :startDate")
    Long getOrdersBySellerIdAndDateAfter(@Param("sellerId") Long sellerId, @Param("startDate") Date startDate);

    @Query("SELECT COALESCE(SUM(t.coinSeller), 0) FROM Transaction t WHERE t.seller.id = :sellerId AND t.isDelete = false AND t.status != 'CREATED' AND t.createdAt >= :startDate AND t.createdAt < :endDate")
    Long getRevenueBySellerIdBetweenDates(@Param("sellerId") Long sellerId, @Param("startDate") Date startDate, @Param("endDate") Date endDate);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.seller.id = :sellerId AND t.isDelete = false AND t.createdAt >= :startDate AND t.createdAt < :endDate")
    Long getOrdersBySellerIdBetweenDates(@Param("sellerId") Long sellerId, @Param("startDate") Date startDate, @Param("endDate") Date endDate);
}

    // New: find escrow transactions whose release date passed
    List<Transaction> findByStatusAndEscrowReleaseDateBefore(String status, Date before);
}
