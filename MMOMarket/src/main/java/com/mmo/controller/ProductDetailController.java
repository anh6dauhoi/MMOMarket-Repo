package com.mmo.controller;

import com.mmo.service.ProductService;
import com.mmo.service.AuthService;
import com.mmo.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class ProductDetailController {

    private final ProductService productService;
    private final AuthService authService;

    public ProductDetailController(ProductService productService, AuthService authService) {
        this.productService = productService;
        this.authService = authService;
    }

    /**
     * Helper method to get current user from authentication
     * Handles both OAuth2 (Google) and form-based login
     */
    private User getCurrentUser(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }

        Object principal = auth.getPrincipal();

        // Case 1: Form-based login - principal is User object
        if (principal instanceof User) {
            return (User) principal;
        }

        // Case 2: OAuth2 login - principal is OAuth2User
        if (principal instanceof OAuth2User) {
            OAuth2User oauth2User = (OAuth2User) principal;
            String email = oauth2User.getAttribute("email");
            if (email != null) {
                return authService.findByEmail(email);
            }
        }

        // Case 3: Principal is string (username/email)
        if (principal instanceof String) {
            String identifier = (String) principal;
            return authService.findByEmail(identifier);
        }

        // Fallback: try to get email from authentication name
        return authService.findByEmail(auth.getName());
    }

    /**
     * Add current user information to model
     */
    private void addCurrentUserToModel(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = getCurrentUser(auth);

        if (currentUser != null) {
            model.addAttribute("currentUser", currentUser);
            model.addAttribute("currentUserId", currentUser.getId());
        }
    }

    // Legacy URL: /productdetail?id=123 (now id optional -> redirect 404 if absent)
    @GetMapping("/productdetail")
    public String legacy(@RequestParam(value = "id", required = false) Long id, Model model) {
        if (id == null) return "redirect:/404";
        var detail = productService.getProductDetail(id);
        if (detail == null) return "redirect:/404";
        model.addAllAttributes(detail);
        addCurrentUserToModel(model);
        return "customer/productdetail";
    }

    // New: key=value style /products?id=123
    @GetMapping(value = "/products", params = "id")
    public String productDetailByQuery(@RequestParam("id") Long id, Model model) {
        var detail = productService.getProductDetail(id);
        if (detail == null) return "redirect:/404";
        model.addAllAttributes(detail);
        addCurrentUserToModel(model);
        return "customer/productdetail";
    }

    @GetMapping("/products/{id}")
    public String productDetail(@PathVariable Long id, Model model) {
        var detail = productService.getProductDetail(id);
        if (detail == null) return "redirect:/404";
        model.addAllAttributes(detail);
        addCurrentUserToModel(model);
        return "customer/productdetail";
    }
}
