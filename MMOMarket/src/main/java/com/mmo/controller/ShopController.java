// java
package com.mmo.controller;

import com.mmo.entity.ShopInfo;
import com.mmo.entity.User;
import com.mmo.service.ProductService;
import com.mmo.service.ShopService;
import com.mmo.repository.ProductRepository;
import com.mmo.repository.ReviewRepository;
import com.mmo.repository.ShopInfoRepository;
import com.mmo.repository.UserRepository;
import com.mmo.repository.CategoryRepository;
import com.mmo.util.TierNameUtil;
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
    private final ShopInfoRepository shopInfoRepository;
    private final ProductRepository productRepository;
    private final ReviewRepository reviewRepository;
    private final CategoryRepository categoryRepository;
    private final ShopService shopService;

    public ShopController(ProductService productService,
                          UserRepository userRepository,
                          ShopInfoRepository shopInfoRepository,
                          ProductRepository productRepository,
                          ReviewRepository reviewRepository,
                          CategoryRepository categoryRepository,
                          ShopService shopService) {
        this.productService = productService;
        this.userRepository = userRepository;
        this.shopInfoRepository = shopInfoRepository;
        this.productRepository = productRepository;
        this.reviewRepository = reviewRepository;
        this.categoryRepository = categoryRepository;
        this.shopService = shopService;
    }

    // Redirect helper: resolve ShopInfo by sellerId and redirect to /shop?id={shopId}
    @GetMapping("/shop/by-seller/{sellerId}")
    public String redirectBySeller(@PathVariable Long sellerId) {
        if (sellerId == null) return "redirect:/";
        try {
            Optional<ShopInfo> active = shopInfoRepository.findByUserIdAndIsDeleteFalse(sellerId);
            if (active != null && active.isPresent()) {
                return "redirect:/shop?id=" + active.get().getId();
            }
            Optional<ShopInfo> any = shopInfoRepository.findByUser_Id(sellerId);
            if (any != null && any.isPresent()) {
                return "redirect:/shop?id=" + any.get().getId();
            }
        } catch (Exception ignored) {}
        return "redirect:/";
    }

    // New: support key=value query: /shop?id=123
    @GetMapping("/shop")
    public String shopByQuery(@RequestParam(value = "id", required = false) Long shopId,
                             @RequestParam(value = "categoryId", required = false) Long categoryId,
                             @RequestParam(value = "sort", required = false) String sort,
                             @RequestParam(value = "minRating", required = false) Integer minRating,
                             @RequestParam(value = "q", required = false) String q,
                             @RequestParam(value = "page", defaultValue = "1") int page,
                             Model model) {
        // If no shopId provided, redirect to homepage or show error
        if (shopId == null) {
            return "redirect:/homepage";
        }
        return shop(shopId, categoryId, sort, minRating, q, page, model);
    }

    @GetMapping("/shop/{shopId}")
    public String shop(@PathVariable Long shopId,
                       @RequestParam(value = "categoryId", required = false) Long categoryId,
                       @RequestParam(value = "sort", required = false) String sort,
                       @RequestParam(value = "minRating", required = false) Integer minRating,
                       @RequestParam(value = "q", required = false) String q,
                       @RequestParam(value = "page", defaultValue = "1") int page,
                       Model model) {
        // Convert from 1-based to 0-based page index for internal use
        int pageIndex = Math.max(0, page - 1);

        // Resolve ShopInfo strictly by id; reject deleted or missing
        Optional<ShopInfo> byId = shopInfoRepository.findById(shopId);
        if (byId.isEmpty() || byId.get().isDelete() || byId.get().getUser() == null) {
            return "redirect:/";
        }
        ShopInfo shop = byId.get();
        User seller = shop.getUser();
        Long sellerId = seller.getId();

        String shopName = (shop != null && shop.getShopName() != null && !shop.getShopName().isEmpty())
                ? shop.getShopName()
                : (seller.getFullName() != null && !seller.getFullName().isEmpty() ? seller.getFullName() : "Shop");

        Long totalSold = productRepository.getTotalSoldForSeller(sellerId);
        if (totalSold == null) totalSold = 0L;

        Double avgRating = shopService.getSellerAverageRating(sellerId);
        if (avgRating == null) avgRating = 0.0;

        Double successRate = 30.0;
        if (avgRating > 4.0) successRate = 90.0;
        else if (avgRating > 3.0) successRate = 70.0;
        else if (avgRating > 2.0) successRate = 50.0;

        // Get products with pagination (12 items per page) - use 0-based pageIndex
        Map<String, Object> productsData = productService.getSellerProductsWithFilters(
                sellerId, categoryId, 0L, 999999999L, sort, minRating, q, pageIndex, 12
        );

        model.addAttribute("pageTitle", shopName);
        model.addAttribute("seller", seller);
        model.addAttribute("shopName", shopName);
        model.addAttribute("shop", shop);
        model.addAttribute("totalSold", totalSold);
        model.addAttribute("averageRating", Math.round(avgRating * 10.0) / 10.0);
        model.addAttribute("successRate", successRate);
        model.addAttribute("products", productsData.get("products"));

        // Pagination info - pass 1-based page to view
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", productsData.get("totalPages"));
        model.addAttribute("totalItems", productsData.get("totalItems"));
        model.addAttribute("pageSize", productsData.get("pageSize"));

        // expose shop identifier for links/forms (use ShopInfo.id)
        model.addAttribute("sellerId", sellerId);
        model.addAttribute("shopIdentifier", shop.getId());

        // Add shop level and tier name for display
        short level = shop.getShopLevel() == null ? (short) 0 : shop.getShopLevel();
        model.addAttribute("shopLevel", level);
        model.addAttribute("tierName", TierNameUtil.getTierName(level));

        // Filters data
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("sort", sort);
        model.addAttribute("minRating", minRating);

        // expose search query back to view
        model.addAttribute("q", q);

        return "customer/shop";
    }

}
