package com.mmo.controller;

import com.mmo.entity.User;
import com.mmo.service.AuthService;
import com.mmo.repository.NotificationRepository;
import com.mmo.entity.Notification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ControllerAdvice
public class GlobalModelAttributes {

    private static final Logger log = LoggerFactory.getLogger(GlobalModelAttributes.class);

    @Autowired
    private AuthService authService;

    @Autowired
    private NotificationRepository notificationRepository;

    @ModelAttribute
    public void addCurrentUser(Model model, Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return;
            }
            User user = null;
            if (authentication instanceof OAuth2AuthenticationToken oauth2Token) {
                OAuth2User oauthUser = oauth2Token.getPrincipal();
                String email = oauthUser.getAttribute("email");
                if (email != null) {
                    user = authService.findByEmail(email);
                    if (user == null) {
                        // Nếu người dùng không tồn tại, tạo mới
                        User newUser = new User();
                        newUser.setEmail(email);
                        newUser.setFullName(oauthUser.getAttribute("name"));
                        newUser.setRole("CUSTOMER");
                        newUser.setVerified(true); // Mặc định là đã xác thực
                        newUser.setCoins(0L); // Khởi tạo coins là 0
                        user = authService.saveUser(newUser);
                    }
                }
            } else {
                String email = authentication.getName();
                if (email != null) {
                    user = authService.findByEmail(email);
                }
            }
            if (user != null) {
                model.addAttribute("currentUser", user);
                model.addAttribute("displayName", shortenName(resolvePreferredName(user)));
                // Load latest UNREAD notifications only (with safe fallbacks)
                List<Notification> notifications = notificationRepository
                        .findTop20ByUser_IdAndStatusAndIsDeleteOrderByCreatedAtDesc(user.getId(), "Unread", false);
                if (notifications == null || notifications.isEmpty()) {
                    notifications = notificationRepository
                            .findTop20ByUser_IdAndStatusOrderByCreatedAtDesc(user.getId(), "Unread");
                }
                if (notifications == null || notifications.isEmpty()) {
                    notifications = notificationRepository
                            .findTop20ByUser_EmailAndStatusAndIsDeleteOrderByCreatedAtDesc(user.getEmail(), "Unread", false);
                }
                if (notifications == null || notifications.isEmpty()) {
                    notifications = notificationRepository
                            .findTop20ByUser_EmailAndStatusOrderByCreatedAtDesc(user.getEmail(), "Unread");
                }
                model.addAttribute("notifications", notifications);

                // Count unread notifications
                long unreadCount = notificationRepository
                        .countByUser_IdAndStatusAndIsDelete(user.getId(), "Unread", false);
                if (unreadCount == 0) {
                    unreadCount = notificationRepository.countByUser_IdAndStatus(user.getId(), "Unread");
                }
                if (unreadCount == 0) {
                    unreadCount = notificationRepository
                            .countByUser_EmailAndStatusAndIsDelete(user.getEmail(), "Unread", false);
                }
                if (unreadCount == 0) {
                    unreadCount = notificationRepository
                            .countByUser_EmailAndStatus(user.getEmail(), "Unread");
                }
                model.addAttribute("unreadCount", unreadCount);
                log.debug("Loaded {} UNREAD notifications for user {}", notifications != null ? notifications.size() : 0, user.getEmail());
            }
        } catch (Exception ignored) {
            // Avoid breaking views if anything unexpected happens
        }
    }

    private String resolvePreferredName(User user) {
        if (user == null) return "customer";
        if (user.getFullName() != null && !user.getFullName().isBlank()) {
            return user.getFullName();
        }
        return "customer";
    }

    private String shortenName(String name) {
        if (name == null || name.isBlank()) {
            return "customer";
        }
        if (name.equalsIgnoreCase("customer")) {
            return "customer";
        }
        if (name.length() > 12) {
            return name.substring(0, 10) + "...";
        }
        return name;
    }
}
