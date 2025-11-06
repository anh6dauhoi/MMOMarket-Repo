package com.mmo.controller;

import com.mmo.entity.Category;
import com.mmo.service.CategoryService;
import com.mmo.service.ProductService;
import com.mmo.repository.ShopInfoRepository;
import com.mmo.entity.Product;
import com.mmo.entity.User;
import com.mmo.entity.ShopInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private ProductService productService;

    @Autowired
    private ShopInfoRepository shopInfoRepository;

    // Support key=value style: /category?id=... (with same filters)
    @GetMapping(value = "/category", params = "id")
    public String categoryByQuery(
            @RequestParam("id") Long id,
            @RequestParam(value = "maxPrice", required = false) Long maxPrice,
            @RequestParam(value = "minRating", required = false) Integer minRating,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            Model model) {
        return category(id, maxPrice, minRating, sort, page, model);
    }

    @GetMapping("/category/{id}")
    public String category(
            @PathVariable("id") Long categoryId,
            @RequestParam(value = "maxPrice", required = false) Long maxPrice,
            @RequestParam(value = "minRating", required = false) Integer minRating,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            Model model) {
        // Defaults
        if (maxPrice == null) maxPrice = 500000L;
        if (minRating == null) minRating = 0;
        if (sort == null || sort.isBlank()) sort = "newest";

        // Convert page from 1-based to 0-based for internal use
        int pageIndex = Math.max(0, page - 1);

        // Fetch all categories for sidebar
        List<Category> categories = categoryService.findAll();
        model.addAttribute("categories", categories);

        // Resolve selected category
        String selectedCategoryName = "All Products";
        Long effectiveCategoryId = (categoryId != null && categoryId > 0) ? categoryId : null;
        if (effectiveCategoryId != null) {
            Optional<Category> selectedCategoryOpt = categoryService.findById(effectiveCategoryId);
            if (selectedCategoryOpt.isPresent()) {
                selectedCategoryName = selectedCategoryOpt.get().getName();
            }
        }
        model.addAttribute("selectedCategory", selectedCategoryName);

        // Fetch products by category with pagination (use 0-based pageIndex internally)
        int pageSize = 12; // 12 products per page
        Map<String, Object> pageData = productService.getProductsByCategoryWithPagination(
            effectiveCategoryId, 0L, maxPrice, sort, minRating, pageIndex, pageSize);

        List<Map<String, Object>> products = (List<Map<String, Object>>) pageData.get("content");

        // Enrich each product map with shopId if missing
        if (products != null) {
            for (Map<String, Object> it : products) {
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
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
        model.addAttribute("products", products != null ? products : Collections.emptyList());

        // Add pagination info (convert back to 1-based for display)
        model.addAttribute("currentPage", page);  // 1-based page number for display
        model.addAttribute("totalPages", pageData.get("totalPages"));
        model.addAttribute("totalElements", pageData.get("totalElements"));

        // Debug logging
        System.out.println("=== CATEGORY CONTROLLER DEBUG ===");
        System.out.println("Current Page (1-based): " + page);
        System.out.println("Page Index (0-based): " + pageIndex);
        System.out.println("Total Pages: " + pageData.get("totalPages"));
        System.out.println("Total Elements: " + pageData.get("totalElements"));
        System.out.println("Products count: " + (products != null ? products.size() : 0));
        System.out.println("=================================");

        // Add filter state
        model.addAttribute("maxPrice", maxPrice);
        model.addAttribute("minRating", minRating);
        model.addAttribute("sort", sort);
        // Use null to omit id param when building /category links
        model.addAttribute("categoryId", effectiveCategoryId);

        return "customer/category";
    }

    @GetMapping("/category")
    public String allProducts(
            @RequestParam(value = "maxPrice", required = false) Long maxPrice,
            @RequestParam(value = "minRating", required = false) Integer minRating,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            Model model) {
        // Defaults
        if (maxPrice == null) maxPrice = 500000L;
        if (minRating == null) minRating = 0;
        if (sort == null || sort.isBlank()) sort = "newest";

        // Convert page from 1-based to 0-based for internal use
        int pageIndex = Math.max(0, page - 1);

        // Fetch all categories for sidebar
        List<Category> categories = categoryService.findAll();
        model.addAttribute("categories", categories);

        // Set selected category to "All Products"
        model.addAttribute("selectedCategory", "All Products");

        // Fetch all products with pagination (use 0-based pageIndex internally)
        int pageSize = 12; // 12 products per page
        Map<String, Object> pageData = productService.getProductsByCategoryWithPagination(
            null, 0L, maxPrice, sort, minRating, pageIndex, pageSize);

        List<Map<String, Object>> products = (List<Map<String, Object>>) pageData.get("content");

        // Enrich with shopId
        if (products != null) {
            for (Map<String, Object> it : products) {
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
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
        model.addAttribute("products", products != null ? products : Collections.emptyList());

        // Add pagination info (1-based for display)
        model.addAttribute("currentPage", page);  // 1-based page number for display
        model.addAttribute("totalPages", pageData.get("totalPages"));
        model.addAttribute("totalElements", pageData.get("totalElements"));

        // Debug logging
        System.out.println("=== ALL PRODUCTS CONTROLLER DEBUG ===");
        System.out.println("Current Page (1-based): " + page);
        System.out.println("Page Index (0-based): " + pageIndex);
        System.out.println("Total Pages: " + pageData.get("totalPages"));
        System.out.println("Total Elements: " + pageData.get("totalElements"));
        System.out.println("Products count: " + (products != null ? products.size() : 0));
        System.out.println("=====================================");

        // Add filter state
        model.addAttribute("maxPrice", maxPrice);
        model.addAttribute("minRating", minRating);
        model.addAttribute("sort", sort);
        model.addAttribute("categoryId", null);

        return "customer/category";
    }

    // new API for header dropdown
    @GetMapping("/api/categories")
    @ResponseBody
    public List<Map<String, Object>> apiCategories() {
        List<Category> categories = categoryService.findAll();
        return categories.stream()
                .map(c -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", c.getId());
                    m.put("name", c.getName());
                    return m;
                })
                .collect(Collectors.toList());
    }
}