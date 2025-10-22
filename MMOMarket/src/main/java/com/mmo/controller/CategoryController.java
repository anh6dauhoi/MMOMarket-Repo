package com.mmo.controller;

import com.mmo.entity.Category;
import com.mmo.service.CategoryService;
import com.mmo.service.ProductService;
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

    @GetMapping("/category/{id}")
    public String category(
            @PathVariable("id") Long categoryId,
            @RequestParam(value = "maxPrice", required = false) Long maxPrice,
            @RequestParam(value = "minRating", required = false) Integer minRating,
            @RequestParam(value = "sort", required = false) String sort,
            Model model) {
        // Defaults
        if (maxPrice == null) maxPrice = 500000L;
        if (minRating == null) minRating = 0;
        if (sort == null || sort.isBlank()) sort = "newest";

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

        // Fetch products by category with filters
        List<Map<String, Object>> products = productService.getProductsByCategory(effectiveCategoryId, 0L, maxPrice, sort, minRating);
        model.addAttribute("products", products != null ? products : Collections.emptyList());

        // Add filter state
        model.addAttribute("maxPrice", maxPrice);
        model.addAttribute("minRating", minRating);
        model.addAttribute("sort", sort);
        model.addAttribute("categoryId", effectiveCategoryId == null ? 0L : effectiveCategoryId);

        return "customer/category";
    }

    @GetMapping("/category")
    public String allProducts(
            @RequestParam(value = "maxPrice", required = false) Long maxPrice,
            @RequestParam(value = "minRating", required = false) Integer minRating,
            @RequestParam(value = "sort", required = false) String sort,
            Model model) {
        // Defaults
        if (maxPrice == null) maxPrice = 500000L;
        if (minRating == null) minRating = 0;
        if (sort == null || sort.isBlank()) sort = "newest";

        // Fetch all categories for sidebar
        List<Category> categories = categoryService.findAll();
        model.addAttribute("categories", categories);

        // Set selected category to "All Products"
        model.addAttribute("selectedCategory", "All Products");

        // Fetch all products
        List<Map<String, Object>> products = productService.getProductsByCategory(null, 0L, maxPrice, sort, minRating);
        model.addAttribute("products", products != null ? products : Collections.emptyList());

        // Add filter state
        model.addAttribute("maxPrice", maxPrice);
        model.addAttribute("minRating", minRating);
        model.addAttribute("sort", sort);
        model.addAttribute("categoryId", 0L);

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