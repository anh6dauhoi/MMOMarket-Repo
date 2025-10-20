package com.mmo.controller;

import com.mmo.entity.Product;
import com.mmo.entity.User;
import com.mmo.entity.SellerRegistration;
import com.mmo.repository.ReviewRepository;
import com.mmo.repository.UserRepository;
import com.mmo.repository.SellerRegistrationRepository;
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
    private SellerRegistrationRepository sellerRegistrationRepository; // <-- thêm repo

    @GetMapping({"/", "/homepage"})
    public String home(Model model) {

        // --- Lấy danh mục sản phẩm ---
        model.addAttribute("categories", categoryService.getPopularCategories());

        // --- Lấy top sản phẩm (Best Seller) ---
        List<Map<String, Object>> bestSellers = productService.getTopSellingProducts(4);
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

            // Unwrap Optional<SellerRegistration> safely
            String shopName = null;
            if (userId != null) {
                Optional<SellerRegistration> srOpt = sellerRegistrationRepository.findByUserId(userId);
                SellerRegistration sr = srOpt != null ? srOpt.orElse(null) : null;
                if (sr != null) {
                    shopName = sr.getShopName();
                }
            }

            // Fallback: nếu không có shopName thì dùng fullName hoặc email
            if (shopName == null || shopName.isBlank()) {
                shopName = fullName != null && !fullName.isBlank() ? fullName : email;
            }
            m.put("id", userId);
            m.put("fullName", fullName);
            m.put("shopName", shopName); // <-- put shopName
            m.put("email", email); // vẫn có nếu cần
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