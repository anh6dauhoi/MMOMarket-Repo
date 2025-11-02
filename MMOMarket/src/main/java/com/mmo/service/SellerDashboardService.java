package com.mmo.service;

import com.mmo.repository.TransactionRepository;
import com.mmo.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SellerDashboardService {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private ProductRepository productRepository;

    public Map<String, Object> getDashboardStats(Long sellerId, int days) {
        Map<String, Object> stats = new HashMap<>();

        // Tính ngày bắt đầu
        Date startDate = Date.from(LocalDateTime.now()
                .minusDays(days)
                .atZone(ZoneId.systemDefault())
                .toInstant());

        // 1. REVENUE (tổng tiền - commission)
        Long totalRevenue = transactionRepository.findBySellerId(sellerId).stream()
                .filter(t -> t.getCreatedAt().after(startDate))
                .filter(t -> !t.isDelete())
                .mapToLong(t -> t.getAmount() - t.getCommission())
                .sum();

        // 2. TRANSACTIONS COUNT
        long totalTransactions = transactionRepository.findBySellerId(sellerId).stream()
                .filter(t -> t.getCreatedAt().after(startDate))
                .filter(t -> !t.isDelete())
                .count();

        // 3. GROWTH RATE (so với kỳ trước)
        Date previousStartDate = Date.from(LocalDateTime.now()
                .minusDays(days * 2)
                .atZone(ZoneId.systemDefault())
                .toInstant());

        Long previousRevenue = transactionRepository.findBySellerId(sellerId).stream()
                .filter(t -> t.getCreatedAt().after(previousStartDate) && t.getCreatedAt().before(startDate))
                .filter(t -> !t.isDelete())
                .mapToLong(t -> t.getAmount() - t.getCommission())
                .sum();

        double growthRate = 0;
        if (previousRevenue > 0) {
            growthRate = ((double)(totalRevenue - previousRevenue) / previousRevenue) * 100;
        }

        long previousTransactions = transactionRepository.findBySellerId(sellerId).stream()
                .filter(t -> t.getCreatedAt().after(previousStartDate) && t.getCreatedAt().before(startDate))
                .filter(t -> !t.isDelete())
                .count();

        double transactionGrowth = 0;
        if (previousTransactions > 0) {
            transactionGrowth = ((double)(totalTransactions - previousTransactions) / previousTransactions) * 100;
        }

        // 4. CHART DATA (revenue theo ngày)
        List<Map<String, Object>> chartData = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            Date dayStart = Date.from(LocalDateTime.now()
                    .minusDays(i)
                    .withHour(0).withMinute(0).withSecond(0)
                    .atZone(ZoneId.systemDefault())
                    .toInstant());

            Date dayEnd = Date.from(LocalDateTime.now()
                    .minusDays(i)
                    .withHour(23).withMinute(59).withSecond(59)
                    .atZone(ZoneId.systemDefault())
                    .toInstant());

            long dayRevenue = transactionRepository.findBySellerId(sellerId).stream()
                    .filter(t -> !t.isDelete())
                    .filter(t -> t.getCreatedAt().after(dayStart) && t.getCreatedAt().before(dayEnd))
                    .mapToLong(t -> t.getAmount() - t.getCommission())
                    .sum();

            Map<String, Object> dayData = new HashMap<>();
            dayData.put("date", dayStart);
            dayData.put("revenue", dayRevenue);
            chartData.add(dayData);
        }

        // 5. TOP SELLING PRODUCTS
        Map<Long, Map<String, Object>> productSales = new HashMap<>();

        transactionRepository.findBySellerId(sellerId).stream()
                .filter(t -> t.getCreatedAt().after(startDate))
                .filter(t -> !t.isDelete())
                .forEach(t -> {
                    Long productId = t.getProductId();
                    productSales.putIfAbsent(productId, new HashMap<>());
                    Map<String, Object> productData = productSales.get(productId);

                    // Đếm số lượng bán (1 transaction = 1 quantity, cần adjust nếu có quantity field)
                    int currentQty = (int) productData.getOrDefault("quantity", 0);
                    productData.put("quantity", currentQty + 1);
                    productData.put("productId", productId);
                });

        List<Map<String, Object>> topProducts = productSales.values().stream()
                .sorted((a, b) -> Integer.compare((int)b.get("quantity"), (int)a.get("quantity")))
                .limit(5)
                .map(data -> {
                    Long productId = (Long) data.get("productId");
                    String productName = productRepository.findById(productId)
                            .map(p -> p.getName())
                            .orElse("Unknown Product");
                    data.put("name", productName);
                    return data;
                })
                .collect(Collectors.toList());

        // Đưa vào stats
        stats.put("totalRevenue", totalRevenue);
        stats.put("totalTransactions", totalTransactions);
        stats.put("growthRate", String.format("%.1f", growthRate));
        stats.put("transactionGrowth", String.format("%.1f", transactionGrowth));
        stats.put("chartData", chartData);
        stats.put("topProducts", topProducts);

        return stats;
    }
}