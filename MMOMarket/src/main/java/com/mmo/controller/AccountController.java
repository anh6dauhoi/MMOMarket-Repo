package com.mmo.controller;

import com.mmo.entity.ShopInfo;
import com.mmo.entity.User;
import com.mmo.repository.NotificationRepository;
import com.mmo.repository.UserRepository;
import com.mmo.service.NotificationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import com.mmo.dto.SellerRegistrationForm;

@Controller
public class AccountController {
    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @GetMapping("/account/settings")
    public String accountSettings(Model model, Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            String email = authentication.getName();
            if (authentication instanceof OAuth2AuthenticationToken oauth2Token) {
                OAuth2User oauthUser = oauth2Token.getPrincipal();
                String mail = oauthUser.getAttribute("email");
                if (mail != null) email = mail;
            }
            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
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
            }
        }
        if (!model.containsAttribute("sellerRegistration") && !model.containsAttribute("registration")) {
            model.addAttribute("sellerRegistration", new SellerRegistrationForm());
        }
        return "customer/account-setting";
    }

    @GetMapping("/account/notifications")
    public String myNotifications(Model model, Authentication authentication, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size, @RequestParam(required = false) String status, @RequestParam(required = false) String search) {
        if (authentication != null && authentication.isAuthenticated()) {
            String email = authentication.getName();
            if (authentication instanceof OAuth2AuthenticationToken oauth2Token) {
                OAuth2User oauthUser = oauth2Token.getPrincipal();
                String mail = oauthUser.getAttribute("email");
                if (mail != null) email = mail;
            }
            Pageable pageable = PageRequest.of(page, size);
            String effectiveStatus = (status != null && status.equalsIgnoreCase("Read")) ? "Readed" : status;
            Page<com.mmo.entity.Notification> notificationPage = notificationService.getNotificationsForUser(email, effectiveStatus, search, pageable);
            model.addAttribute("pageNotifications", notificationPage.getContent());
            model.addAttribute("currentPage", notificationPage.getNumber());
            model.addAttribute("totalPages", notificationPage.getTotalPages());
            model.addAttribute("status", status);
            model.addAttribute("search", search);

            // Provide synthetic registration for sidebar label (Active vs Register)
            userRepository.findByEmail(email).ifPresent(user -> {
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
            });
        }
        return "customer/my-notification";
    }

    @GetMapping("/customer/my-notification")
    public String redirectOldMyNotification() {
        return "redirect:/account/notifications";
    }

    @GetMapping("/api/notifications/unread-count")
    @ResponseBody
    public long getUnreadCount(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return 0;
        }
        String email = authentication.getName();
        if (authentication instanceof OAuth2AuthenticationToken oauth2Token) {
            OAuth2User oauthUser = oauth2Token.getPrincipal();
            String mail = oauthUser.getAttribute("email");
            if (mail != null) email = mail;
        }
        return notificationRepository.countByUser_EmailAndStatusAndIsDelete(email, "Unread", false);
    }

    // Update user profile (Name, Phone)
    @PostMapping("/account/update-profile")
    public String updateProfile(@ModelAttribute UpdateProfileRequest request,
                               Authentication authentication,
                               RedirectAttributes redirectAttributes) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to update profile");
                return "redirect:/authen/login";
            }

            String email = authentication.getName();
            if (authentication instanceof OAuth2AuthenticationToken oauth2Token) {
                OAuth2User oauthUser = oauth2Token.getPrincipal();
                String mail = oauthUser.getAttribute("email");
                if (mail != null) email = mail;
            }

            accountService.updateProfile(email, request);
            redirectAttributes.addFlashAttribute("successMessage", "Profile updated successfully!");
            return "redirect:/account/settings?tab=profile";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/account/settings?tab=profile";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to update profile. Please try again.");
            return "redirect:/account/settings?tab=profile";
        }
    }

    // Change password
    @PostMapping("/account/change-password")
    public String changePassword(@ModelAttribute ChangePasswordRequest request,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to change password");
                return "redirect:/authen/login";
            }

            String email = authentication.getName();
            if (authentication instanceof OAuth2AuthenticationToken oauth2Token) {
                OAuth2User oauthUser = oauth2Token.getPrincipal();
                String mail = oauthUser.getAttribute("email");
                if (mail != null) email = mail;
            }

            accountService.changePassword(email, request);
            redirectAttributes.addFlashAttribute("successMessage", "Password changed successfully!");
            return "redirect:/account/settings?tab=password";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/account/settings?tab=password";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to change password. Please try again.");
            return "redirect:/account/settings?tab=password";
        }
    }
}
