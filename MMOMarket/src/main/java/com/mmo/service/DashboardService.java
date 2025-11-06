package com.mmo.service;

import com.mmo.dto.*;
import com.mmo.entity.ShopPointPurchase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Get dashboard totals (transactions, revenue, users, point revenue)
     */
    public DashboardTotalsDTO getDashboardTotals(Date from, Date to) {
        try {
            Long totalTransactions;
            Long totalRevenue;
            Long pointRevenue;

            if (from != null && to != null) {
                totalTransactions = entityManager.createQuery(
                        "SELECT COUNT(t) FROM Transaction t WHERE t.isDelete = false " +
                                "AND t.createdAt >= :from AND t.createdAt < :to", Long.class)
                        .setParameter("from", from)
                        .setParameter("to", to)
                        .getSingleResult();

                totalRevenue = entityManager.createQuery(
                        "SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.isDelete = false " +
                                "AND t.status != 'CREATED' AND t.createdAt >= :from AND t.createdAt < :to", Long.class)
                        .setParameter("from", from)
                        .setParameter("to", to)
                        .getSingleResult();

                pointRevenue = entityManager.createQuery(
                        "SELECT COALESCE(SUM(p.coinsSpent), 0) FROM ShopPointPurchase p " +
                                "WHERE p.createdAt >= :from AND p.createdAt < :to", Long.class)
                        .setParameter("from", from)
                        .setParameter("to", to)
                        .getSingleResult();
            } else {
                totalTransactions = entityManager.createQuery(
                        "SELECT COUNT(t) FROM Transaction t WHERE t.isDelete = false", Long.class)
                        .getSingleResult();

                totalRevenue = entityManager.createQuery(
                        "SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.isDelete = false AND t.status != 'CREATED'", Long.class)
                        .getSingleResult();

                pointRevenue = entityManager.createQuery(
                        "SELECT COALESCE(SUM(p.coinsSpent), 0) FROM ShopPointPurchase p", Long.class)
                        .getSingleResult();
            }

            Long totalUsers = entityManager.createQuery(
                    "SELECT COUNT(u) FROM User u WHERE u.isDelete = false", Long.class)
                    .getSingleResult();

            String lastUpdated = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

            return new DashboardTotalsDTO(
                    totalTransactions != null ? totalTransactions : 0L,
                    totalRevenue != null ? totalRevenue : 0L,
                    totalUsers != null ? totalUsers : 0L,
                    pointRevenue != null ? pointRevenue : 0L,
                    lastUpdated
            );
        } catch (Exception e) {
            // Return default values on error
            String lastUpdated = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            return new DashboardTotalsDTO(0L, 0L, 0L, 0L, lastUpdated);
        }
    }

    /**
     * Get top 5 sellers by revenue and avg rating
     */
    public List<TopSellerDTO> getTopSellers(Date from, Date to) {
        try {
            String query;
            if (from != null && to != null) {
                query = "SELECT t.seller.id as sellerId, t.seller.fullName as sellerName, " +
                        "COALESCE(SUM(t.coinSeller), 0) as revenue, COUNT(t) as transactionCount " +
                        "FROM Transaction t " +
                        "WHERE t.isDelete = false AND t.status != 'CREATED' " +
                        "AND t.seller.shopStatus = 'active' AND t.seller.role = 'customer' " +
                        "AND t.createdAt >= :from AND t.createdAt < :to " +
                        "GROUP BY t.seller.id, t.seller.fullName " +
                        "ORDER BY revenue DESC";
            } else {
                query = "SELECT t.seller.id as sellerId, t.seller.fullName as sellerName, " +
                        "COALESCE(SUM(t.coinSeller), 0) as revenue, COUNT(t) as transactionCount " +
                        "FROM Transaction t " +
                        "WHERE t.isDelete = false AND t.status != 'CREATED' " +
                        "AND t.seller.shopStatus = 'active' AND t.seller.role = 'customer' " +
                        "GROUP BY t.seller.id, t.seller.fullName " +
                        "ORDER BY revenue DESC";
            }

            List<Object[]> results;
            if (from != null && to != null) {
                results = entityManager.createQuery(query, Object[].class)
                        .setParameter("from", from)
                        .setParameter("to", to)
                        .setMaxResults(5)
                        .getResultList();
            } else {
                results = entityManager.createQuery(query, Object[].class)
                        .setMaxResults(5)
                        .getResultList();
            }

            List<TopSellerDTO> topSellers = new ArrayList<>();
            for (Object[] row : results) {
                Long sellerId = (Long) row[0];
                String sellerName = row[1] != null ? (String) row[1] : "Unknown Seller";
                Long revenue = row[2] != null ? (Long) row[2] : 0L;
                Long transactionCount = row[3] != null ? (Long) row[3] : 0L;

                // Get average rating from reviews
                Double avgRating = getAvgRatingForSeller(sellerId);

                topSellers.add(new TopSellerDTO(sellerName, revenue, avgRating, transactionCount));
            }

            return topSellers;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * Get average rating for a seller's products
     */
    private Double getAvgRatingForSeller(Long sellerId) {
        Double avgRating = entityManager.createQuery(
                "SELECT AVG(r.rating) FROM Review r " +
                        "WHERE r.product.seller.id = :sellerId AND r.isDelete = false", Double.class)
                .setParameter("sellerId", sellerId)
                .getSingleResult();
        return avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : 0.0;
    }

    /**
     * Get top 5 products by sold count
     */
    public List<TopProductDTO> getTopProducts(Date from, Date to) {
        try {
            String query;
            if (from != null && to != null) {
                query = "SELECT t.product.id, t.product.name, COALESCE(SUM(t.quantity), 0) as soldCount, " +
                        "COALESCE(SUM(t.amount), 0) as revenue " +
                        "FROM Transaction t " +
                        "WHERE t.isDelete = false AND t.status != 'CREATED' " +
                        "AND t.createdAt >= :from AND t.createdAt < :to " +
                        "GROUP BY t.product.id, t.product.name " +
                        "ORDER BY soldCount DESC";
            } else {
                query = "SELECT t.product.id, t.product.name, COALESCE(SUM(t.quantity), 0) as soldCount, " +
                        "COALESCE(SUM(t.amount), 0) as revenue " +
                        "FROM Transaction t " +
                        "WHERE t.isDelete = false AND t.status != 'CREATED' " +
                        "GROUP BY t.product.id, t.product.name " +
                        "ORDER BY soldCount DESC";
            }

            List<Object[]> results;
            if (from != null && to != null) {
                results = entityManager.createQuery(query, Object[].class)
                        .setParameter("from", from)
                        .setParameter("to", to)
                        .setMaxResults(5)
                        .getResultList();
            } else {
                results = entityManager.createQuery(query, Object[].class)
                        .setMaxResults(5)
                        .getResultList();
            }

            return results.stream()
                    .map(row -> new TopProductDTO(
                            row[1] != null ? (String) row[1] : "Unknown Product",
                            row[2] != null ? (Long) row[2] : 0L,
                            row[3] != null ? (Long) row[3] : 0L
                    ))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * Get top 5 point purchases by amount
     */
    public List<TopPointPurchaseDTO> getTopPointPurchases(Date from, Date to) {
        try {
            String query;
            if (from != null && to != null) {
                query = "SELECT p FROM ShopPointPurchase p " +
                        "WHERE p.createdAt >= :from AND p.createdAt < :to " +
                        "ORDER BY p.coinsSpent DESC";
            } else {
                query = "SELECT p FROM ShopPointPurchase p " +
                        "ORDER BY p.coinsSpent DESC";
            }

            List<ShopPointPurchase> results;
            if (from != null && to != null) {
                results = entityManager.createQuery(query, ShopPointPurchase.class)
                        .setParameter("from", from)
                        .setParameter("to", to)
                        .setMaxResults(5)
                        .getResultList();
            } else {
                results = entityManager.createQuery(query, ShopPointPurchase.class)
                        .setMaxResults(5)
                        .getResultList();
            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            return results.stream()
                    .filter(p -> p.getUser() != null) // Null safety check
                    .map(p -> new TopPointPurchaseDTO(
                            p.getUser().getFullName() != null ? p.getUser().getFullName() : "Unknown",
                            p.getCoinsSpent() != null ? p.getCoinsSpent() : 0L,
                            p.getCreatedAt() != null ? sdf.format(p.getCreatedAt()) : "N/A"
                    ))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * Get top 5 buyers by total spent
     */
    public List<TopBuyerDTO> getTopBuyers(Date from, Date to) {
        try {
            String query;
            if (from != null && to != null) {
                query = "SELECT t.customer.fullName, COALESCE(SUM(t.amount), 0) as totalSpent, COUNT(t) as transactionCount " +
                        "FROM Transaction t " +
                        "WHERE t.isDelete = false AND t.status != 'CREATED' " +
                        "AND t.createdAt >= :from AND t.createdAt < :to " +
                        "GROUP BY t.customer.id, t.customer.fullName " +
                        "ORDER BY totalSpent DESC";
            } else {
                query = "SELECT t.customer.fullName, COALESCE(SUM(t.amount), 0) as totalSpent, COUNT(t) as transactionCount " +
                        "FROM Transaction t " +
                        "WHERE t.isDelete = false AND t.status != 'CREATED' " +
                        "GROUP BY t.customer.id, t.customer.fullName " +
                        "ORDER BY totalSpent DESC";
            }

            List<Object[]> results;
            if (from != null && to != null) {
                results = entityManager.createQuery(query, Object[].class)
                        .setParameter("from", from)
                        .setParameter("to", to)
                        .setMaxResults(5)
                        .getResultList();
            } else {
                results = entityManager.createQuery(query, Object[].class)
                        .setMaxResults(5)
                        .getResultList();
            }

            return results.stream()
                    .map(row -> new TopBuyerDTO(
                            row[0] != null ? (String) row[0] : "Unknown Buyer",
                            row[1] != null ? (Long) row[1] : 0L,
                            row[2] != null ? (Long) row[2] : 0L
                    ))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * Get revenue time series (daily aggregation)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getRevenueTimeSeries(Date from, Date to) {
        try {
            // Default to last 30 days if no dates provided
            if (from == null || to == null) {
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.DAY_OF_MONTH, -30);
                from = cal.getTime();
                to = new Date();
            }

            // Use native SQL query since DATE() function is not supported in JPQL
            String sql = "SELECT DATE(created_at) as date, COALESCE(SUM(amount), 0) as revenue " +
                    "FROM Transactions " +
                    "WHERE isDelete = 0 AND status != 'CREATED' " +
                    "AND created_at >= :from AND created_at < :to " +
                    "GROUP BY DATE(created_at) " +
                    "ORDER BY date";

            List<Object[]> results = entityManager.createNativeQuery(sql)
                    .setParameter("from", from)
                    .setParameter("to", to)
                    .getResultList();

            List<String> labels = new ArrayList<>();
            List<Long> series = new ArrayList<>();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

            for (Object[] row : results) {
                try {
                    // Skip if date is null
                    if (row[0] == null) {
                        continue;
                    }

                    // Handle date - could be java.sql.Date or java.util.Date
                    Date date;
                    if (row[0] instanceof java.sql.Date) {
                        date = new Date(((java.sql.Date) row[0]).getTime());
                    } else if (row[0] instanceof java.util.Date) {
                        date = (java.util.Date) row[0];
                    } else {
                        // Skip invalid date type
                        continue;
                    }

                    // Handle revenue - could be BigDecimal, Long, Integer, etc.
                    long revenue = 0L;
                    if (row[1] != null) {
                        if (row[1] instanceof java.math.BigDecimal) {
                            revenue = ((java.math.BigDecimal) row[1]).longValue();
                        } else if (row[1] instanceof Number) {
                            revenue = ((Number) row[1]).longValue();
                        }
                    }

                    // Add to results
                    labels.add(sdf.format(date));
                    series.add(revenue);
                } catch (Exception e) {
                    // Skip problematic rows
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("labels", labels);
            result.put("series", series);
            return result;
        } catch (Exception e) {
            // Return empty data on error
            Map<String, Object> result = new HashMap<>();
            result.put("labels", new ArrayList<>());
            result.put("series", new ArrayList<>());
            return result;
        }
    }
}

