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
                double sum = 0.0;
                int count = 0;
                for (Product p : productRepository.findBySellerId(seller.getId())) {
                    Double r = reviewRepository.getAverageRatingByProduct(p.getId());
                    if (r != null && r > 0) { sum += r; count++; }
                }
                double avgRating = count > 0 ? (sum / count) : 0.0;

                Long totalSold = productRepository.getTotalSoldForSeller(seller.getId());
                if (totalSold == null) totalSold = 0L;

                double successRate = avgRating > 4.0 ? 90.0 : avgRating > 3.0 ? 70.0 : avgRating > 2.0 ? 50.0 : 30.0;

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
                data.put("ratingAverage", Math.round(avgRating * 10.0) / 10.0);
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
