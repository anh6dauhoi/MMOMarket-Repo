package com.mmo.controller;

import com.mmo.entity.User;
import com.mmo.repository.UserRepository;
import com.mmo.service.SearchService;
import com.mmo.service.ShopService;
import com.mmo.repository.ProductRepository;
import com.mmo.repository.ReviewRepository; // add
import com.mmo.repository.ProductVariantRepository; // NEW
import com.mmo.entity.ProductVariant; // NEW
import com.mmo.repository.ShopInfoRepository; // NEW
import com.mmo.entity.ShopInfo; // NEW
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.*;

@Controller
public class SearchController {

    private final SearchService searchService;
    private final UserRepository userRepository;
    private final ShopService shopService;
    private final ProductRepository productRepository;
    private final ReviewRepository reviewRepository; // add
    private final ProductVariantRepository productVariantRepository; // NEW
    private final ShopInfoRepository shopInfoRepository; // NEW

    public SearchController(SearchService searchService,
                            UserRepository userRepository,
                            ShopService shopService,
                            ProductRepository productRepository,
                            ReviewRepository reviewRepository, // add
                            ProductVariantRepository productVariantRepository, // NEW
                            ShopInfoRepository shopInfoRepository) { // NEW
        this.searchService = searchService;
        this.userRepository = userRepository;
        this.shopService = shopService;
        this.productRepository = productRepository;
        this.reviewRepository = reviewRepository; // add
        this.productVariantRepository = productVariantRepository; // NEW
        this.shopInfoRepository = shopInfoRepository; // NEW
    }

    @GetMapping("/search")
    public String search(@RequestParam(name = "q", required = false) String q,
                         @RequestParam(name = "ratingSort", required = false) String ratingSort,
                         @RequestParam(name = "productSort", required = false) String productSort, // NEW
                         Model model,
                         Principal principal) {
        // try to resolve current user (if any)
        User user = null;
        if (principal != null) {
            user = userRepository.findByEmail(principal.getName()).orElse(null);
        } else {
            // fallback: attempt from security context (covers some oauth principal types)
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getName() != null && !"anonymousUser".equals(auth.getName())) {
                user = userRepository.findByEmail(auth.getName()).orElse(null);
            }
        }

        // NOTE: search history persistence removed — no-op

        // run search (products + shops)
        Map<String, List<?>> results = searchService.searchProductsAndShops(q, 30);

        // --- Products mapping ---
        List<?> rawProducts = results.get("products");
        List<Map<String, Object>> productsView = new ArrayList<>();
        if (rawProducts != null) {
            for (Object pObj : rawProducts) {
                Map<String, Object> map = new HashMap<>();
                // id
                Object idVal = tryInvoke(pObj, "getId", "getIdValue", "getProductId");
                map.put("id", idVal);

                Long pid = toLong(idVal);

                // name/title
                Object nameVal = tryInvoke(pObj, "getTitle", "getName", "getProductName", "getProductNameVn", "getLabel");
                map.put("name", nameVal != null ? nameVal.toString() : "");

                // image (fallback to default)
                Object imgVal = tryInvoke(pObj, "getImageUrl", "getImage", "getThumbnail");
                map.put("image", (imgVal != null && !String.valueOf(imgVal).isBlank()) ? imgVal : "/images/default.jpg");

                // Resolve sellerId early
                Object sellerIdVal = tryInvoke(pObj, "getSellerId");
                if (sellerIdVal == null) {
                    Object sellerObj = tryInvoke(pObj, "getSeller", "getUser", "getOwner");
                    if (sellerObj != null) {
                        sellerIdVal = tryInvoke(sellerObj, "getId", "getUserId");
                    }
                }
                Long sellerId = toLong(sellerIdVal);
                if (sellerId != null) map.put("sellerId", sellerId);

                // Resolve shopId: prefer direct getter, then product.shop.id, finally by seller via ShopInfo
                Object shopIdVal = tryInvoke(pObj, "getShopId");
                if (shopIdVal == null) {
                    Object shopObj = tryInvoke(pObj, "getShop");
                    if (shopObj != null) {
                        shopIdVal = tryInvoke(shopObj, "getId", "getShopId");
                    }
                }
                // NEW: resolve by seller via ShopInfo when still null
                if (shopIdVal == null && sellerId != null) {
                    try {
                        Optional<ShopInfo> siOpt = shopInfoRepository.findByUserIdAndIsDeleteFalse(sellerId);
                        if (siOpt == null || siOpt.isEmpty()) {
                            siOpt = shopInfoRepository.findByUser_Id(sellerId);
                        }
                        if (siOpt != null && siOpt.isPresent() && siOpt.get().getId() != null) {
                            shopIdVal = siOpt.get().getId();
                        }
                    } catch (Exception ignored) {}
                }
                if (shopIdVal != null) map.put("shopId", shopIdVal);

                // shopName (try product/shop getters)
                String shopName = null;
                Object shopObj = tryInvoke(pObj, "getShop");
                Object sname = tryInvoke(pObj, "getShopName");
                if (sname == null && shopObj != null) {
                    sname = tryInvoke(shopObj, "getShopName", "getName");
                }
                if (sname != null) shopName = String.valueOf(sname);

                // NEW: Prefer ShopInfo.shopName by seller if available
                if ((shopName == null || shopName.isBlank()) && sellerId != null) {
                    try {
                        Optional<ShopInfo> siOpt = shopInfoRepository.findByUserIdAndIsDeleteFalse(sellerId);
                        if (siOpt == null || siOpt.isEmpty()) {
                            siOpt = shopInfoRepository.findByUser_Id(sellerId);
                        }
                        if (siOpt != null && siOpt.isPresent()) {
                            String siName = siOpt.get().getShopName();
                            if (siName != null && !siName.isBlank()) {
                                shopName = siName;
                            }
                        }
                    } catch (Exception ignored) {}
                }

                // fallback to seller info only if still not found
                if (shopName == null || shopName.isBlank()) {
                    Object sellerObj = tryInvoke(pObj, "getSeller", "getUser", "getOwner");
                    Object fullName = (sellerObj != null) ? tryInvoke(sellerObj, "getFullName") : null;
                    Object email = (sellerObj != null && fullName == null) ? tryInvoke(sellerObj, "getEmail") : null;
                    shopName = fullName != null ? String.valueOf(fullName) : (email != null ? String.valueOf(email) : "Shop");
                }
                map.put("shopName", shopName);

                // Price: compute from variants (min price), fallback 0
                Long price = 0L;
                if (pid != null) {
                    try {
                        List<ProductVariant> variants = productVariantRepository.findByProductIdAndIsDeleteFalse(pid);
                        if (variants != null && !variants.isEmpty()) {
                            price = variants.stream()
                                    .map(ProductVariant::getPrice)
                                    .filter(Objects::nonNull)
                                    .min(Long::compareTo)
                                    .orElse(0L);
                        }
                    } catch (Exception ignored) {}
                }
                map.put("price", price);

                // totalSold and averageRating (always set rating, default 0.0)
                if (pid != null) {
                    Long sold = productRepository.getTotalSoldForProduct(pid);
                    map.put("totalSold", sold != null ? sold : 0L);

                    Double avg = reviewRepository.getAverageRatingByProduct(pid);
                    map.put("averageRating", (avg != null) ? Math.round(avg * 10.0) / 10.0 : 0.0);
                } else {
                    map.put("totalSold", 0L);
                    map.put("averageRating", 0.0);
                }

                productsView.add(map);
            }
        }

        // NEW: sort products by productSort
        if (productSort != null && !productSort.isBlank()) {
            String ps = productSort.trim().toLowerCase();
            Comparator<Map<String, Object>> byPrice =
                    Comparator.comparingLong(m -> ((Number) m.getOrDefault("price", 0L)).longValue());
            Comparator<Map<String, Object>> byRating =
                    Comparator.comparingDouble(m -> ((Number) m.getOrDefault("averageRating", 0.0)).doubleValue());

            if (ps.equals("priceasc") || ps.equals("price-low-to-high")) {
                productsView.sort(byPrice);
            } else if (ps.equals("pricedesc") || ps.equals("price-high-to-low")) {
                productsView.sort(byPrice.reversed());
            } else if (ps.equals("ratingdesc") || ps.equals("rating-high-to-low")) {
                productsView.sort(byRating.reversed());
            } else if (ps.equals("ratingasc") || ps.equals("rating-low-to-high")) {
                productsView.sort(byRating);
            }
        }

        // --- Shops mapping ---
        List<?> rawShops = results.get("shops");
        List<Map<String, Object>> shopsView = new ArrayList<>();
        if (rawShops != null) {
            for (Object sObj : rawShops) {
                Map<String, Object> sm = new HashMap<>();

                // Basic identifiers
                Object sid = tryInvoke(sObj, "getId", "getShopId");
                Object sname = tryInvoke(sObj, "getShopName", "getName", "getTitle");

                Object shopInfo = tryInvoke(sObj, "getShopInfo", "getShop");
                Object shopIdVal = tryInvoke(sObj, "getShopId");
                // Resolve sellerId
                Object sellerIdVal = tryInvoke(sObj, "getSellerId", "getUserId");
                if (sellerIdVal == null) {
                    Object sellerObj = tryInvoke(sObj, "getSeller", "getUser", "getOwner");
                    if (sellerObj != null) {
                        sellerIdVal = tryInvoke(sellerObj, "getId", "getUserId");
                    }
                }
                Long sellerId = toLong(sellerIdVal);

                if (shopIdVal == null && shopInfo != null) {
                    shopIdVal = tryInvoke(shopInfo, "getId", "getShopId");
                }

                // Avatar/logo (prefer explicit avatar, then logo/image; fallback default)
                Object avatarVal = tryInvoke(sObj, "getAvatar", "getLogo", "getImage", "getAvatarUrl");
                if (avatarVal == null && shopInfo != null) {
                    avatarVal = tryInvoke(shopInfo, "getAvatar", "getLogo", "getImage", "getAvatarUrl");
                }
                if (avatarVal == null) avatarVal = "/images/default-avatar.svg";

                // Try reflective values first
                Object ratingVal = tryInvoke(sObj, "getRatingAverage", "getAverageRating", "getAvgRating");
                Object soldVal = tryInvoke(sObj, "getTotalSold", "getTotalProductsSold", "getSoldCount");

                // Compute with services (preferred)
                double ratingComputed = (sellerId != null) ? shopService.getSellerAverageRating(sellerId) : 0.0;
                Long totalSoldComputed = (sellerId != null) ? productRepository.getTotalSoldForSeller(sellerId) : null;

                // Final values (prefer computed if available)
                double ratingFinal = (ratingComputed > 0.0)
                        ? Math.round(ratingComputed * 10.0) / 10.0
                        : (ratingVal instanceof Number ? ((Number) ratingVal).doubleValue() : 0.0);

                long soldFinal = (totalSoldComputed != null)
                        ? totalSoldComputed
                        : (soldVal instanceof Number ? ((Number) soldVal).longValue() : 0L);

                // Success rate derived like homepage
                double successFinal = ratingFinal > 4.0 ? 90.0
                        : ratingFinal > 3.0 ? 70.0
                        : ratingFinal > 2.0 ? 50.0 : 30.0;

                sm.put("id", sid);
                sm.put("shopName", sname != null ? sname.toString() : "");
                sm.put("shopId", shopIdVal);
                sm.put("sellerId", sellerId);
                sm.put("avatar", avatarVal);
                sm.put("ratingAverage", ratingFinal);
                sm.put("totalSold", soldFinal);
                sm.put("successRate", successFinal);

                shopsView.add(sm);
            }
        }

        // Sort shops by ratingAverage if requested
        if (ratingSort != null && !ratingSort.isBlank()) {
            shopsView.sort(java.util.Comparator.comparingDouble(m -> {
                Object v = m.get("ratingAverage");
                return (v instanceof Number) ? ((Number) v).doubleValue() : 0.0;
            }));
            if ("desc".equalsIgnoreCase(ratingSort)) {
                java.util.Collections.reverse(shopsView);
            }
        }

        model.addAttribute("products", productsView);
        model.addAttribute("shops", shopsView);
        model.addAttribute("q", q);
        model.addAttribute("ratingSort", ratingSort);
        model.addAttribute("productSort", productSort); // NEW

        // NOTE: recentSearches removed from model (UI no longer shows server-side history)

        return "customer/search";
    }

    // small reflection helper: try multiple getter names and return first non-null result
    private Object tryInvoke(Object target, String... methodNames) {
        if (target == null) return null;
        Class<?> cls = target.getClass();
        for (String m : methodNames) {
            try {
                Method method = cls.getMethod(m);
                Object val = method.invoke(target);
                if (val != null) return val;
            } catch (NoSuchMethodException ignored) {
                // try next
            } catch (Throwable ignored) {
                // ignore other reflection errors and try next
            }
        }
        return null;
    }


    // Parse any numeric object to Long safely
    private Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.parseLong(String.valueOf(v)); } catch (Exception e) { return null; }
    }
}
