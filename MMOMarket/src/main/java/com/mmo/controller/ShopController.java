// java
package com.mmo.controller;

import com.mmo.entity.SellerRegistration;
import com.mmo.entity.User;
import com.mmo.service.ProductService;
import com.mmo.repository.ProductRepository;
import com.mmo.repository.ReviewRepository;
import com.mmo.repository.SellerRegistrationRepository;
import com.mmo.repository.UserRepository;
import com.mmo.repository.CategoryRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.*;

@Controller
public class ShopController {

    private final ProductService productService;
    private final UserRepository userRepository;
    private final SellerRegistrationRepository sellerRegistrationRepository;
    private final ProductRepository productRepository;
    private final ReviewRepository reviewRepository;
    private final CategoryRepository categoryRepository;

    public ShopController(ProductService productService,
                          UserRepository userRepository,
                          SellerRegistrationRepository sellerRegistrationRepository,
                          ProductRepository productRepository,
                          ReviewRepository reviewRepository,
                          CategoryRepository categoryRepository) {
        this.productService = productService;
        this.userRepository = userRepository;
        this.sellerRegistrationRepository = sellerRegistrationRepository;
        this.productRepository = productRepository;
        this.reviewRepository = reviewRepository;
        this.categoryRepository = categoryRepository;
    }

    @GetMapping("/shop/{sellerId}")
    public String shop(@PathVariable Long sellerId,
                       @RequestParam(value = "categoryId", required = false) Long categoryId,
                       @RequestParam(value = "minPrice", required = false) Long minPrice,
                       @RequestParam(value = "maxPrice", required = false) Long maxPrice,
                       @RequestParam(value = "sort", required = false) String sort,
                       @RequestParam(value = "minRating", required = false) Integer minRating,
                       @RequestParam(value = "q", required = false) String q,
                       Model model) {
        Optional<User> sellerOpt = userRepository.findById(sellerId);
        if (sellerOpt.isEmpty()) {
            return "redirect:/";
        }
        User seller = sellerOpt.get();

        // Unwrap Optional<SellerRegistration> safely
        Optional<SellerRegistration> shopOpt = sellerRegistrationRepository.findByUserId(sellerId);
        SellerRegistration shop = shopOpt.orElse(null);

        String shopName = (shop != null && shop.getShopName() != null && !shop.getShopName().isEmpty())
                ? shop.getShopName()
                : (seller.getFullName() != null && !seller.getFullName().isEmpty() ? seller.getFullName() : "Shop");

        Long totalSold = productRepository.getTotalSoldForSeller(sellerId);
        if (totalSold == null) totalSold = 0L;

        Double avgRating = reviewRepository.getAverageRatingBySeller(sellerId);
        if (avgRating == null) avgRating = 0.0;

        Double successRate = 30.0;
        if (avgRating > 4.0) successRate = 90.0;
        else if (avgRating > 3.0) successRate = 70.0;
        else if (avgRating > 2.0) successRate = 50.0;

        List<Map<String, Object>> products = productService.getSellerProductsWithFilters(
                sellerId, categoryId, minPrice, maxPrice, sort, minRating, q
        );

        model.addAttribute("pageTitle", shopName);
        model.addAttribute("seller", seller);
        model.addAttribute("shopName", shopName);
        model.addAttribute("shop", shop);
        model.addAttribute("totalSold", totalSold);
        model.addAttribute("averageRating", Math.round(avgRating * 10.0) / 10.0);
        model.addAttribute("successRate", successRate);
        model.addAttribute("products", products);

        model.addAttribute("sellerId", sellerId);

        // Filters data
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("minPrice", minPrice);
        model.addAttribute("maxPrice", maxPrice);
        model.addAttribute("sort", sort);
        model.addAttribute("minRating", minRating);

        // expose search query back to view
        model.addAttribute("q", q);

        return "customer/shop";
    }

}
