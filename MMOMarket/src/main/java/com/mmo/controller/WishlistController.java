package com.mmo.controller;

import com.mmo.entity.User;
import com.mmo.entity.ShopInfo;
import com.mmo.repository.UserRepository;
import com.mmo.service.WishlistService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.persistence.EntityManager;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import com.mmo.service.AuthService;

@Controller
public class WishlistController {

    private final WishlistService wishlistService;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final EntityManager entityManager;

    public WishlistController(WishlistService wishlistService, UserRepository userRepository, AuthService authService, EntityManager entityManager) {
        this.wishlistService = wishlistService;
        this.userRepository = userRepository;
        this.authService = authService;
        this.entityManager = entityManager;
    }

    @PostMapping("/wishlist/add")
    public String add(@RequestParam("productId") Long productId) {
        User user = currentUser();
        if (user == null) return "redirect:/authen/login";
        boolean added = wishlistService.addToWishlist(user, productId);
        return "redirect:/products?id=" + productId + "&wishlistSuccess=" + (added ? "1" : "0");
    }

    @PostMapping("/wishlist/remove")
    public String remove(@RequestParam("productId") Long productId) {
        User user = currentUser();
        if (user == null) return "redirect:/authen/login";
        wishlistService.remove(user, productId);
        return "redirect:/account/wishlist?removed=1";
    }

    @GetMapping("/account/wishlist")
    public String view(Model model, @RequestParam(value = "page", required = false, defaultValue = "1") int page) {
        User user = currentUser();
        if (user == null) return "redirect:/authen/login";

        int size = 6; // fixed: 6 products per page
        var paged = wishlistService.getWishlistItemsPaged(user, page, size);
        model.addAllAttributes(paged);

        model.addAttribute("displayName", user.getFullName() != null ? user.getFullName() : user.getEmail());
        model.addAttribute("activeTab", "wishlist"); // activate wishlist tab in account-setting

        // Add registration info to hide "Register Seller" when shop status is Active
        boolean active = user.getShopStatus() != null && user.getShopStatus().equalsIgnoreCase("Active");
        if (active) {
            ShopInfo shop = entityManager.createQuery(
                            "SELECT s FROM ShopInfo s WHERE s.user = :u AND s.isDelete = false",
                            ShopInfo.class)
                    .setParameter("u", user)
                    .getResultStream().findFirst().orElse(null);
            Map<String, Object> registration = new HashMap<>();
            registration.put("id", 0L);
            registration.put("status", "Active");
            registration.put("shopName", shop != null ? shop.getShopName() : (user.getFullName() != null ? user.getFullName() + "'s Shop" : "My Shop"));
            registration.put("description", shop != null ? shop.getDescription() : "");
            model.addAttribute("registration", registration);
        }

        return "customer/account-setting";
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;

        // Explicitly treat anonymous authentication as not-logged-in
        if (auth instanceof AnonymousAuthenticationToken) return null;
        if (!auth.isAuthenticated()) return null;

        Object principal = auth.getPrincipal();
        // Ignore Spring Security's anonymous principal
        if (principal instanceof String str && "anonymousUser".equalsIgnoreCase(str)) return null;

        String emailOrUsername = null;

        // 1) UsernamePassword authentication (form login)
        if (principal instanceof UserDetails ud) {
            emailOrUsername = ud.getUsername();
        }
        // 2) OAuth2 login (Google, etc.)
        else if (principal instanceof OAuth2User oauth2) {
            Object emailAttr = oauth2.getAttribute("email");
            if (emailAttr != null) emailOrUsername = String.valueOf(emailAttr);
        }

        // 3) Fallback to Authentication#getName()
        if (emailOrUsername == null || emailOrUsername.isBlank()) {
            emailOrUsername = auth.getName();
        }

        // Try resolve by email first
        try {
            User byEmail = authService.findByEmail(emailOrUsername);
            if (byEmail != null) return byEmail;
        } catch (Exception ignored) {}

        // Fallback: repository by username if available (reflection safe)
        try {
            var byUsernameMethod = userRepository.getClass().getMethod("findByUsername", String.class);
            Optional<?> opt = (Optional<?>) byUsernameMethod.invoke(userRepository, emailOrUsername);
            if (opt != null && opt.isPresent()) return (User) opt.get();
        } catch (NoSuchMethodException nsme) {
        } catch (Exception ignored) {}

        return null;
    }
}
