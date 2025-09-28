package com.mmo.controller;

import com.mmo.entity.User;
import com.mmo.service.AuthService;
import jakarta.mail.MessagingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

    @Autowired
    private AuthService authService;

    @GetMapping("/authen/register")
    public String showRegisterForm() {
        return "authen/register";
    }

    @PostMapping("/authen/register")
    public String register(@RequestParam String email, @RequestParam String password, @RequestParam String fullName, Model model) {
        try {
            User user = authService.register(email, password, fullName);
            String code = authService.generateVerificationCode();
            model.addAttribute("email", email);
            model.addAttribute("code", code);
            return "authen/verify";
        } catch (MessagingException e) {
            model.addAttribute("message", "Failed to send verification code.");
            return "authen/register";
        }
    }

    @GetMapping("/authen/login")
    public String login(Model model, @RequestParam(value = "error", required = false) String error) {
        if (error != null) {
            model.addAttribute("message", "Invalid email or password. Please try again or verify your account.");
        }
        return "authen/login";
    }

    @GetMapping("/authen/verify")
    public String showVerifyForm(@RequestParam String email, @RequestParam String code, Model model) {
        model.addAttribute("email", email);
        model.addAttribute("code", code);
        return "authen/verify";
    }

    @PostMapping("/authen/verify")
    public String verify(@RequestParam String email, @RequestParam String code, Model model) {
        User user = authService.findByEmail(email);
        if (user != null && code.equals(model.getAttribute("code"))) {
            user.setVerified(true);
            model.addAttribute("message", "Verification successful! Please log in.");
            return "redirect:/authen/login";
        }
        model.addAttribute("message", "Invalid verification code.");
        return "authen/verify";
    }

    @GetMapping("/authen/forgot-password")
    public String showForgotPasswordForm() {
        return "authen/forgot-password";
    }

    @PostMapping("/authen/forgot-password")
    public String forgotPassword(@RequestParam String email, Model model) {
        User user = authService.findByEmail(email);
        if (user != null) {
            String code = authService.generateVerificationCode();
            model.addAttribute("email", email);
            model.addAttribute("code", code);
            return "authen/new-password";
        }
        model.addAttribute("error", "Email not found.");
        return "authen/forgot-password";
    }

    @PostMapping("/authen/new-password")
    public String newPassword(@RequestParam String email, @RequestParam String password, @RequestParam String confirmPassword, Model model) {
        if (password.equals(confirmPassword)) {
            User user = authService.findByEmail(email);
            user.setPassword(password); // Cần encoder trong thực tế
            model.addAttribute("message", "Password updated successfully! Please log in.");
            return "redirect:/authen/login";
        }
        model.addAttribute("error", "Passwords do not match.");
        return "authen/new-password";
    }

    @GetMapping("/welcome")
    public String welcome(Model model) {
        model.addAttribute("message", "Welcome to MMOMarket!");
        return "customer/welcome";
    }
}