package com.mmo.controller;

import com.mmo.service.ProductService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class ProductDetailController {

    private final ProductService productService;

    public ProductDetailController(ProductService productService) {
        this.productService = productService;
    }

    // Legacy URL: /productdetail?id=123 (now id optional -> redirect 404 if absent)
    @GetMapping("/productdetail")
    public String legacy(@RequestParam(value = "id", required = false) Long id, Model model) {
        if (id == null) return "redirect:/404";
        var detail = productService.getProductDetail(id);
        if (detail == null) return "redirect:/404";
        model.addAllAttributes(detail);
        return "customer/productdetail";
    }

    // New: key=value style /products?id=123
    @GetMapping(value = "/products", params = "id")
    public String productDetailByQuery(@RequestParam("id") Long id, Model model) {
        var detail = productService.getProductDetail(id);
        if (detail == null) return "redirect:/404";
        model.addAllAttributes(detail);
        return "customer/productdetail";
    }

    @GetMapping("/products/{id}")
    public String productDetail(@PathVariable Long id, Model model) {
        var detail = productService.getProductDetail(id);
        if (detail == null) return "redirect:/404";
        model.addAllAttributes(detail);
        return "customer/productdetail";
    }
}
