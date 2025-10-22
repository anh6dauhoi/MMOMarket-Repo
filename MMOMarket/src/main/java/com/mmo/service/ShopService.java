package com.mmo.service;

import com.mmo.entity.User;
import com.mmo.entity.SellerRegistration;
import com.mmo.entity.Product;
import com.mmo.repository.UserRepository;
import com.mmo.repository.ReviewRepository;
import com.mmo.repository.ProductRepository;
import com.mmo.repository.SellerRegistrationRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.lang.reflect.Method;

@Service
public class ShopService {

    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final SellerRegistrationRepository sellerRegistrationRepository;

    public ShopService(UserRepository userRepository, ReviewRepository reviewRepository, ProductRepository productRepository, SellerRegistrationRepository sellerRegistrationRepository) {
        this.userRepository = userRepository;
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.sellerRegistrationRepository = sellerRegistrationRepository;
    }

    // Try to obtain review count for a product using common repository method names (safe reflection).
    private long safeCountReviews(Long productId) {
        if (productId == null) return 0L;
        String[] names = new String[] {
                "getReviewCountByProduct",
                "getReviewCountByProductId",
                "countByProductId",
                "countByProduct_Id",
                "countByProductIdAndIsDeleteFalse",
                "countByProduct_IdAndIsDeleteFalse"
        };
        for (String name : names) {
            try {
                // try both Long and long parameter types
                Method m = null;
                try { m = reviewRepository.getClass().getMethod(name, Long.class); } catch (NoSuchMethodException ignored) {}
                if (m == null) {
                    try { m = reviewRepository.getClass().getMethod(name, long.class); } catch (NoSuchMethodException ignored) {}
                }
                if (m != null) {
                    Object res = m.invoke(reviewRepository, productId);
                    if (res instanceof Number) return ((Number) res).longValue();
                }
            } catch (Exception ignored) {}
        }
        return 0L;
    }

    // Try to obtain sum of ratings for a product using common repository method names (safe reflection).
    // Returns null if not available.
    private Double safeSumRatings(Long productId) {
        if (productId == null) return null;
        String[] names = new String[] {
                "getSumRatingByProduct",
                "sumRatingByProduct",
                "getTotalRatingByProduct",
                "sumRatingsByProductId",
                "getSumOfRatingsForProduct"
        };
        for (String name : names) {
            try {
                Method m = null;
                try { m = reviewRepository.getClass().getMethod(name, Long.class); } catch (NoSuchMethodException ignored) {}
                if (m == null) {
                    try { m = reviewRepository.getClass().getMethod(name, long.class); } catch (NoSuchMethodException ignored) {}
                }
                if (m != null) {
                    Object res = m.invoke(reviewRepository, productId);
                    if (res instanceof Number) return ((Number) res).doubleValue();
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    // Public helper: average rating across ALL reviews of all products of a seller.
    public double getSellerAverageRating(Long sellerId) {
        if (sellerId == null) return 0.0;

        // Primary: direct DB AVG over all reviews for this seller
        try {
            Double avg = reviewRepository.getAverageRatingBySeller(sellerId);
            if (avg != null) return avg;
        } catch (Exception ignored) {}

        // Fallback: aggregate across seller's products (sum of ratings / total review count)
        List<Product> products = productRepository.findBySellerId(sellerId);
        if (products == null || products.isEmpty()) return 0.0;

        long totalCount = 0L;
        double totalSum = 0.0;

        for (Product p : products) {
            if (p == null || p.getId() == null) continue;

            Long count = safeCountReviews(p.getId());
            Double sum = safeSumRatings(p.getId());

            // Try to reconstruct sum from per-product average if needed
            if ((sum == null || sum <= 0.0) && (count == null || count <= 0)) {
                Double avgP = null;
                try { avgP = reviewRepository.getAverageRatingByProduct(p.getId()); } catch (Exception ignored) {}
                if (avgP != null) {
                    long c = (count != null && count > 0) ? count : 0L;
                    if (c > 0) {
                        sum = avgP * c;
                    } else {
                        // Last resort: treat as a single review worth avgP
                        sum = avgP;
                        c = 1L;
                    }
                    count = c;
                }
            }

            if (count != null && count > 0 && sum != null && sum > 0.0) {
                totalCount += count;
                totalSum += sum;
            }
        }

        return totalCount > 0 ? (totalSum / totalCount) : 0.0;
    }

    public List<Map<String, Object>> getReputableSellers() {
        // 1) Primary source: role + status (per your DB: role=customer, shop_status=active)
        List<User> sellers = userRepository.findByRoleAndShopStatus("customer", "active");
        if (sellers == null || sellers.isEmpty()) {
            // Fallbacks for different casings if any record mismatches
            for (String role : java.util.Arrays.asList("Customer", "CUSTOMER")) {
                for (String st : java.util.Arrays.asList("active", "Active", "ACTIVE")) {
                    List<User> tmp = userRepository.findByRoleAndShopStatus(role, st);
                    if (tmp != null && !tmp.isEmpty()) { sellers = tmp; break; }
                }
                if (sellers != null && !sellers.isEmpty()) break;
            }
        }

        List<Map<String, Object>> sellerData = new ArrayList<Map<String, Object>>();
        if (sellers != null && !sellers.isEmpty()) {
            for (User seller : sellers) {
                // Use centralized seller-level average across ALL reviews
                double shopAvgRating = getSellerAverageRating(seller.getId());

                Long totalSold = productRepository.getTotalSoldForSeller(seller.getId());
                if (totalSold == null) totalSold = 0L;
                double successRate = shopAvgRating > 4.0 ? 90.0 : shopAvgRating > 3.0 ? 70.0 : shopAvgRating > 2.0 ? 50.0 : 30.0;
                Optional<SellerRegistration> shopOpt = sellerRegistrationRepository.findByUserId(seller.getId());
                SellerRegistration shop = shopOpt.orElse(null);
                String shopName =
                    (shop != null && shop.getShopName() != null && !shop.getShopName().isEmpty())
                        ? shop.getShopName()
                        : (seller.getFullName() != null && !seller.getFullName().isEmpty())
                        ? seller.getFullName()
                        : (seller.getEmail() != null && !seller.getEmail().isEmpty())
                        ? seller.getEmail()
                        : "Shop";

                Map<String, Object> data = new HashMap<>();
                data.put("id", seller.getId());
                data.put("seller", seller);
                data.put("shopName", shopName);
                data.put("ratingAverage", Math.round(shopAvgRating * 10.0) / 10.0);
                data.put("totalSold", totalSold);
                data.put("successRate", successRate);
                data.put("avatar", "/images/default-avatar.svg");
                sellerData.add(data);
            }
        }

        // 2) Fallback: native reputable sellers ranking if above returns nothing
        if (sellerData.isEmpty()) {
            List<Object[]> rows = userRepository.findReputableSellers();
            for (Object[] row : rows) {
                Long userId = ((Number) row[0]).longValue();
                // row[2]=totalProductsSold, row[3]=averageRating from native query
                long totalSoldCount = row[2] != null ? ((Number) row[2]).longValue() : 0L;
                double avgRating = row[3] != null ? ((Number) row[3]).doubleValue() : 0.0;

                Optional<User> sellerOpt = userRepository.findById(userId);
                if (!sellerOpt.isPresent()) continue;
                User seller = sellerOpt.get();

                // Prefer sum amount if available; otherwise use count
                Long totalSold = productRepository.getTotalSoldForSeller(userId);
                if (totalSold == null) totalSold = totalSoldCount;

                double successRate = avgRating > 4.0 ? 90.0 : avgRating > 3.0 ? 70.0 : avgRating > 2.0 ? 50.0 : 30.0;

                Optional<SellerRegistration> shopOpt2 = sellerRegistrationRepository.findByUserId(userId);
                SellerRegistration shop2 = shopOpt2.orElse(null);

                String shopName2 =
                        (shop2 != null && shop2.getShopName() != null && !shop2.getShopName().isEmpty())
                                ? shop2.getShopName()
                                : (seller.getFullName() != null && !seller.getFullName().isEmpty())
                                ? seller.getFullName()
                                : (seller.getEmail() != null && !seller.getEmail().isEmpty())
                                ? seller.getEmail()
                                : "Shop";

                Map<String, Object> data = new HashMap<>();
                data.put("id", userId);
                data.put("seller", seller);
                data.put("shopName", shopName2);
                data.put("ratingAverage", Math.round(avgRating * 10.0) / 10.0);
                data.put("totalSold", totalSold);
                data.put("successRate", successRate);
                data.put("avatar", "/images/default-avatar.svg");
                sellerData.add(data);
            }
        }

        // Sort by totalSold desc, then ratingAverage desc
        sellerData.sort((a, b) -> {
            int bySold = Long.compare(((Number) b.get("totalSold")).longValue(), ((Number) a.get("totalSold")).longValue());
            if (bySold != 0) return bySold;
            return Double.compare((Double) b.get("ratingAverage"), (Double) a.get("ratingAverage"));
        });

        // Keep only top 5
        if (sellerData.size() > 3) {
            sellerData = new ArrayList<>(sellerData.subList(0, 3));
        }

        return sellerData;
    }
}
