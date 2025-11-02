package com.mmo.controller.customer;

import com.mmo.entity.*;
import com.mmo.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/customer")
public class CustomerProductController {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductVariantService productVariantService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private UserService userService;

    // Danh sách sản phẩm
    @GetMapping("/products")
    public String viewProducts(Model model,
                               @RequestParam(required = false) String category,
                               @RequestParam(required = false) String search) {
        List<Product> products;

        if (search != null && !search.isEmpty()) {
            products = productService.searchProducts(search);
        } else if (category != null && !category.isEmpty()) {
            products = productService.findByCategory(Long.parseLong(category));
        } else {
            products = productService.findAll();
        }

        List<Category> categories = categoryService.findAll();

        model.addAttribute("products", products);
        model.addAttribute("categories", categories);

        return "customer/view-products";
    }

    // Chi tiết sản phẩm
    @GetMapping("/products/{id}")
    public String viewProductDetails(@PathVariable Long id, Model model, Authentication authentication) {
        User currentUser = userService.findByEmail(authentication.getName());
        Product product = productService.findById(id);

        // THAY ĐỔI: Variants giờ có stock được tính từ ProductVariantAccounts
        List<ProductVariant> variants = productVariantService.findByProductId(id);
        // Stock đã được set trong productVariantService.findByProductId()

        User seller = product.getSeller();

        // Reviews
        List<Review> reviews = reviewService.findByProductId(id);
        double avgRating = reviewService.getAverageRating(id);
        long totalReviews = reviewService.getTotalReviews(id);
        Map<Integer, Long> reviewStats = reviewService.getReviewStatsByRating(id);

        boolean hasReviewed = reviewService.hasUserReviewedProduct(id, currentUser.getId());
        Review userReview = reviewService.getUserReviewForProduct(id, currentUser.getId());

        model.addAttribute("product", product);
        model.addAttribute("variants", variants);
        model.addAttribute("seller", seller);
        model.addAttribute("reviews", reviews);
        model.addAttribute("avgRating", avgRating);
        model.addAttribute("totalReviews", totalReviews);
        model.addAttribute("reviewStats", reviewStats);
        model.addAttribute("hasReviewed", hasReviewed);
        model.addAttribute("userReview", userReview);
        model.addAttribute("currentUser", currentUser);

        return "customer/view-product-details";
    }
}