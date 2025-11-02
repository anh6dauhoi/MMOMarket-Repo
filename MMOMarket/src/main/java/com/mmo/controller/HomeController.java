package com.mmo.controller;

import com.mmo.entity.Product;
import com.mmo.entity.User;
import com.mmo.entity.ShopInfo;
import com.mmo.repository.ReviewRepository;
import com.mmo.repository.UserRepository;
import com.mmo.repository.ShopInfoRepository;
import com.mmo.service.CategoryService;
import com.mmo.service.ProductService;
import com.mmo.service.ShopService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.*;

@Controller
public class HomeController {

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private ProductService productService;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShopService shopService; // dùng service tập trung xử lý seller

    @Autowired
    private ShopInfoRepository shopInfoRepository; // replaced SellerRegistrationRepository

    @GetMapping({"/", "/homepage"})
    public String home(Model model) {

        // --- Lấy danh mục sản phẩm ---
        model.addAttribute("categories", categoryService.getPopularCategories());

        // --- Lấy top sản phẩm (Best Seller) ---
        List<Map<String, Object>> bestSellers = productService.getTopSellingProducts(4);
        // Add shopId for each bestSeller if missing (resolve via product.seller.id)
        if (bestSellers != null) {
            for (Map<String, Object> it : bestSellers) {
                if (it == null || it.get("shopId") != null) continue;
                Object prodObj = it.get("product");
                if (prodObj instanceof Product) {
                    Product p = (Product) prodObj;
                    User seller = p.getSeller();
                    if (seller != null && seller.getId() != null) {
                        try {
                          Optional<ShopInfo> si = shopInfoRepository.findByUserIdAndIsDeleteFalse(seller.getId());
                          if (si == null || si.isEmpty()) si = shopInfoRepository.findByUser_Id(seller.getId());
                          if (si != null && si.isPresent()) {
                            it.put("shopId", si.get().getId());
                            // also backfill shopName if absent
                            if (it.get("shopName") == null || String.valueOf(it.get("shopName")).isBlank()) {
                              String fallbackName = si.get().getShopName();
                              if (fallbackName == null || fallbackName.isBlank()) {
                                fallbackName = seller.getFullName() != null && !seller.getFullName().isBlank()
                                        ? seller.getFullName()
                                        : seller.getEmail();
                              }
                              it.put("shopName", fallbackName != null ? fallbackName : "Shop");
                            }
                          }
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
        model.addAttribute("bestSellers", bestSellers);

        // --- Lấy danh sách Reputable Sellers từ SellerService ---
        List<Map<String, Object>> sellersFromService = shopService.getReputableSellers();

        // Chuyển về dạng flat map phù hợp với template (keys: avatar, fullName, shopName, ratingAverage, totalSold, successRate)
        List<Map<String, Object>> sellersForView = new ArrayList<>();
        for (Map<String, Object> s : sellersFromService) {
            Map<String, Object> m = new HashMap<>();
            Object sellerObj = s.get("seller");

            Long userId = null;
            String fullName = (String) s.getOrDefault("fullName", "Seller");
            String email = (String) s.getOrDefault("email", "");

            if (sellerObj instanceof User) {
                User user = (User) sellerObj;
                userId = user.getId();
                fullName = user.getFullName();
                email = user.getEmail();
            } else {
                // nếu SellerService đã trả về sellerId trực tiếp
                Object sid = s.get("sellerId");
                if (sid instanceof Number) userId = ((Number) sid).longValue();
                // Fallback to generic "id" key provided by SellerService
                if (userId == null) {
                    Object idObj = s.get("id");
                    if (idObj instanceof Number) userId = ((Number) idObj).longValue();
                }
            }


            String shopName = null;
            if (userId != null) {
                try {
                    Optional<ShopInfo> siOpt = shopInfoRepository.findByUserIdAndIsDeleteFalse(userId);
                    if (siOpt == null || siOpt.isEmpty()) {
                        siOpt = shopInfoRepository.findByUser_Id(userId);
                    }
                    if (siOpt != null && siOpt.isPresent()) {
                        ShopInfo si = siOpt.get();
                        if (si.getShopName() != null && !si.getShopName().isBlank()) {
                            shopName = si.getShopName();
                        }
                    }
                } catch (Exception ignored) {}
            }
            
            if (shopName == null || shopName.isBlank()) {
                shopName = fullName != null && !fullName.isBlank() ? fullName : email;
            }
            m.put("id", userId);
            m.put("fullName", fullName);
            m.put("shopName", shopName);
            // Preserve shopId returned by service (used by Thymeleaf links)
            if (s.containsKey("shopId")) {
                m.put("shopId", s.get("shopId"));
            }
            m.put("email", email);
            m.put("avatar", s.getOrDefault("avatar", "/images/default-avatar.svg"));
            m.put("ratingAverage", s.getOrDefault("averageRating", s.getOrDefault("ratingAverage", 0)));
            m.put("totalSold", s.getOrDefault("totalSold", 0));
            m.put("successRate", s.getOrDefault("successRate", 0));
            sellersForView.add(m);
        }

        model.addAttribute("reputableSellers", sellersForView);

        return "customer/homepage";
    }
}