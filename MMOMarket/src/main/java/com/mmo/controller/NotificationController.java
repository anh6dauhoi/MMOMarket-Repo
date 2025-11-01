package com.mmo.controller;

import com.mmo.entity.User;
import com.mmo.repository.NotificationRepository;
import com.mmo.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class NotificationController {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private AuthService authService;

    @PostMapping("/notifications/mark-all-read")
    @Transactional
    public String markAllRead(Authentication authentication, HttpServletRequest request) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/authen/login";
        }
        String email = authentication.getName();
        if (authentication instanceof OAuth2AuthenticationToken oauth2Token) {
            OAuth2User oauthUser = oauth2Token.getPrincipal();
            String mail = oauthUser.getAttribute("email");
            if (mail != null) email = mail;
        }
        User user = email != null ? authService.findByEmail(email) : null;
        if (user != null) {
            int updated = notificationRepository.updateStatusForUserId(user.getId(), "Unread", "Readed");
            if (updated == 0) {
                notificationRepository.updateStatusForUserEmail(user.getEmail(), "Unread", "Readed");
            }
        }
        String referer = request.getHeader("Referer");
        return "redirect:" + (referer != null ? referer : "/homepage");
    }

    @PostMapping("/notifications/mark-as-read")
    @Transactional
    public String markSelectedAsRead(@RequestParam(name = "ids", required = false) List<Long> ids,
                                     Authentication authentication,
                                     HttpServletRequest request) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/authen/login";
        }
        if (ids == null || ids.isEmpty()) {
            String referer = request.getHeader("Referer");
            return "redirect:" + (referer != null ? referer : "/account/notifications");
        }
        String email = authentication.getName();
        if (authentication instanceof OAuth2AuthenticationToken oauth2Token) {
            OAuth2User oauthUser = oauth2Token.getPrincipal();
            String mail = oauthUser.getAttribute("email");
            if (mail != null) email = mail;
        }
        notificationRepository.updateStatusForIdsAndEmail(email, ids, "Unread", "Readed");
        String referer = request.getHeader("Referer");
        return "redirect:" + (referer != null ? referer : "/account/notifications");
    }
}
