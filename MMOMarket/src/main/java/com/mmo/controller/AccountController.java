package com.mmo.controller;

import com.mmo.service.NotificationService;
import com.mmo.entity.SellerRegistration;
import com.mmo.repository.SellerRegistrationRepository;
import com.mmo.repository.UserRepository;
import com.mmo.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.stereotype.Controller;

import java.util.Optional;

@Controller
public class AccountController {
    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SellerRegistrationRepository sellerRegistrationRepository;

    @GetMapping("/account/settings")
    public String accountSettings(Model model, Authentication authentication) {
        // If already registered, show status. Else, show form with backing model
        if (authentication != null && authentication.isAuthenticated()) {
            String email = authentication.getName();
            if (authentication instanceof OAuth2AuthenticationToken oauth2Token) {
                OAuth2User oauthUser = oauth2Token.getPrincipal();
                String mail = oauthUser.getAttribute("email");
                if (mail != null) email = mail;
            }
            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isPresent()) {
                Optional<SellerRegistration> regOpt = sellerRegistrationRepository.findByUserId(userOpt.get().getId());
                regOpt.ifPresent(reg -> model.addAttribute("registration", reg));
            }
        }
        if (!model.containsAttribute("sellerRegistration") && !model.containsAttribute("registration")) {
            model.addAttribute("sellerRegistration", new SellerRegistration());
        }
        return "customer/account-setting";
    }

    @GetMapping("/account/notifications")
    public String myNotifications(Model model, Authentication authentication,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "10") int size,
                                  @RequestParam(required = false) String status) {
        if (authentication != null && authentication.isAuthenticated()) {
            String email = authentication.getName();
            if (authentication instanceof OAuth2AuthenticationToken oauth2Token) {
                OAuth2User oauthUser = oauth2Token.getPrincipal();
                String mail = oauthUser.getAttribute("email");
                if (mail != null) email = mail;
            }
            Pageable pageable = PageRequest.of(page, size);
            // Normalize status for DB: map 'Read' (UI) -> 'Readed' (DB)
            String effectiveStatus = (status != null && status.equalsIgnoreCase("Read")) ? "Readed" : status;
            Page<com.mmo.entity.Notification> notificationPage = notificationService.getNotificationsForUser(email, effectiveStatus, pageable);
            model.addAttribute("notifications", notificationPage.getContent());
            model.addAttribute("currentPage", notificationPage.getNumber());
            model.addAttribute("totalPages", notificationPage.getTotalPages());
            model.addAttribute("status", status);
        }
        return "customer/my-notification";
    }

    @GetMapping("/customer/my-notification")
    public String redirectOldMyNotification() {
        return "redirect:/account/notifications";
    }
}
