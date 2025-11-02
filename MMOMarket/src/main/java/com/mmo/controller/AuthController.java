package com.mmo.controller;

import com.mmo.entity.EmailVerification;
import com.mmo.entity.User;
import com.mmo.repository.EmailVerificationRepository;
import com.mmo.service.AuthService;
import jakarta.mail.MessagingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Controller
@SessionAttributes("user")
public class AuthController {
    @Autowired
    private AuthService authService;

    @Autowired
    private EmailVerificationRepository emailVerificationRepository;

    @GetMapping("/authen/register")
    public String showRegisterForm() {
        return "authen/register";
    }

    @PostMapping("/authen/register")
    public String register(@RequestParam String email, @RequestParam String password, @RequestParam("confirm-password") String confirmPassword, @RequestParam(required = false) String fullName, Model model) {
        try {
            // Kiểm tra mật khẩu xác nhận
            if (!password.equals(confirmPassword)) {
                model.addAttribute("message", "Mật khẩu xác nhận không khớp.");
                model.addAttribute("email", email);
                return "authen/register";
            }

            // Kiểm tra email đã tồn tại
            if (authService.findByEmail(email) != null) {
                model.addAttribute("message", "Email đã được sử dụng. Vui lòng chọn email khác.");
                model.addAttribute("email", email);
                return "authen/register";
            }

            // Đăng ký user
            User user = authService.register(email, password, fullName != null ? fullName : "Unknown");
            String code = authService.generateVerificationCode();

            EmailVerification verification = new EmailVerification();
            verification.setUser(user);
            verification.setVerificationCode(code);
            verification.setExpiryDate(new Date(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(10)));
            verification.setUsed(false);
            emailVerificationRepository.save(verification);

            // Gửi mã xác thực qua email (ném exception nếu lỗi)
            authService.sendVerificationCodeEmail(email, code);

            model.addAttribute("email", email);
            return "authen/verify";
        } catch (MessagingException e) {
            model.addAttribute("message", "Không thể gửi mã xác thực. Vui lòng kiểm tra lại cấu hình email hoặc thử lại sau.");
            model.addAttribute("email", email);
            return "authen/register";
        }
    }

    @GetMapping("/authen/login")
    public String login(Model model, @RequestParam(value = "error", required = false) String error) {
        if (error != null) {
            String errorMessage = (String) model.getAttribute("message");
            model.addAttribute("message", errorMessage != null ? errorMessage : "Email hoặc mật kh��u không đúng. Vui lòng thử lại hoặc xác thực tài khoản.");
        }
        return "authen/login";
    }

    @GetMapping("/authen/verify")
    public String showVerifyForm(@RequestParam String email, Model model) {
        model.addAttribute("email", email);
        return "authen/verify";
    }

    @PostMapping("/authen/verify")
    public String verify(@RequestParam String email, @RequestParam String code, Model model,
                        @RequestParam(required = false) Boolean reset) {
        User user = authService.findByEmail(email);
        if (user == null) {
            model.addAttribute("message", "Email không tồn t��i.");
            model.addAttribute("email", email);
            return "authen/verify";
        }

        EmailVerification verification = emailVerificationRepository.findByUserAndVerificationCode(user, code);
        if (verification == null) {
            model.addAttribute("message", "Mã xác thực không đúng.");
            model.addAttribute("email", email);
            return "authen/verify";
        }

        if (verification.isUsed()) {
            model.addAttribute("message", "Mã xác thực đã được sử dụng.");
            model.addAttribute("email", email);
            return "authen/verify";
        }

        if (verification.getExpiryDate().before(new Date())) {
            model.addAttribute("message", "Mã xác thực đã hết hạn. Vui lòng yêu cầu mã mới.");
            model.addAttribute("email", email);
            return "authen/verify";
        }

        verification.setUsed(true);
        emailVerificationRepository.save(verification);

        if (reset != null && reset) {
            // Nếu là flow reset password, chuyển qua trang đổi mật khẩu mới
            model.addAttribute("email", email);
            model.addAttribute("code", code);
            return "authen/new-password";
        } else {
            // Đăng ký tài khoản
            user.setVerified(true);
            authService.saveUser(user);
            model.addAttribute("message", "Xác thực thành công! Bạn có thể đăng nhập.");
            return "authen/login";
        }
    }

    @GetMapping("/authen/forgot-password")
    public String showForgotPasswordForm() {
        return "authen/forgot-password";
    }

    @PostMapping("/authen/forgot-password")
    public String forgotPassword(@RequestParam String email, Model model) {
        User user = authService.findByEmail(email);
        if (user == null) {
            model.addAttribute("error", "Email không tồn tại.");
            return "authen/forgot-password";
        }

        try {
            String code = authService.generateVerificationCode();
            EmailVerification verification = new EmailVerification();
            verification.setUser(user);
            verification.setVerificationCode(code);
            verification.setExpiryDate(new Date(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(10)));
            verification.setUsed(false);
            emailVerificationRepository.save(verification);

            // Gửi mã xác thực qua email
            authService.sendVerificationCodeEmail(email, code);

            model.addAttribute("email", email);
            model.addAttribute("reset", true); // Đánh dấu là flow reset password
            return "authen/verify";
        } catch (Exception e) {
            model.addAttribute("error", "Không thể gửi mã xác thực. Vui lòng thử lại.");
            return "authen/forgot-password";
        }
    }

    @PostMapping("/authen/new-password")
    public String newPassword(@RequestParam String email, @RequestParam String password, @RequestParam String confirmPassword, @RequestParam String code, Model model) {
        if (!password.equals(confirmPassword)) {
            model.addAttribute("error", "Mật khẩu xác nhận không khớp.");
            model.addAttribute("email", email);
            return "authen/new-password";
        }

        User user = authService.findByEmail(email);
        if (user == null) {
            model.addAttribute("error", "Email không tồn tại.");
            model.addAttribute("email", email);
            return "authen/new-password";
        }

        EmailVerification verification = emailVerificationRepository.findByUserAndVerificationCode(user, code);
        if (verification == null || verification.isUsed() || verification.getExpiryDate().before(new Date())) {
            model.addAttribute("error", "Mã xác thực không hợp lệ hoặc đã hết hạn.");
            model.addAttribute("email", email);
            return "authen/new-password";
        }

        user.setPassword(password); // Lưu mật khẩu plaintext
        verification.setUsed(true);
        authService.saveUser(user);
        emailVerificationRepository.save(verification);

        // Chỉ set message một l��n, tránh double message
        model.asMap().clear();
        model.addAttribute("message", "Cập nhật mật khẩu thành công! Bạn có th��� đăng nhập.");
        return "authen/login";
    }

    @RequestMapping("/welcome")
    public String welcome(Model model, Authentication authentication) {
        if (authentication != null) {
            User user = null;

            // Xử lý OAuth2 login (Google, Facebook, etc.)
            if (authentication instanceof OAuth2AuthenticationToken) {
                OAuth2AuthenticationToken oauth2Token = (OAuth2AuthenticationToken) authentication;
                OAuth2User oauthUser = oauth2Token.getPrincipal();
                String email = oauthUser.getAttribute("email");
                String name = oauthUser.getAttribute("name");

                user = authService.findByEmail(email);
                if (user == null) {
                    user = new User();
                    user.setEmail(email);
                    user.setFullName(name);
                    user.setRole("ROLE_CUSTOMER");
                    user.setVerified(true);
                    authService.saveUser(user);
                }
                model.addAttribute("message", "Chào mừng " + name + " đến với MMOMarket!");
            }
            // Xử lý login thông thường (username/password)
            else {
                String email = authentication.getName();
                user = authService.findByEmail(email);
                if (user != null) {
                    model.addAttribute("message", "Chào mừng " + user.getFullName() + " đến với MMOMarket!");
                }
            }

            model.addAttribute("user", user);
        }
        return "customer/welcome";
    }

    @PostMapping("/authen/resend-otp")
    @ResponseBody
    public String resendOtp(@RequestParam String email) {
        User user = authService.findByEmail(email);
        if (user == null) {
            return "Email không tồn tại";
        }
        String code = authService.generateVerificationCode();
        EmailVerification verification = new EmailVerification();
        verification.setUser(user);
        verification.setVerificationCode(code);
        verification.setExpiryDate(new Date(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(10)));
        verification.setUsed(false);
        emailVerificationRepository.save(verification);
        // Gửi email mã xác thực mới cho user
        try {
            authService.sendVerificationCodeEmail(email, code);
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
        return "Đã gửi lại mã OTP thành công!";
    }

    // Thêm vào cuối class AuthController
    @GetMapping("/api/user/{id}")
    @ResponseBody
    public Map<String, Object> getUserInfo(@PathVariable Long id) {
        User user = authService.findById(id);
        if (user == null) {
            return Map.of("error", "User not found");
        }

        return Map.of(
                "id", user.getId(),
                "fullName", user.getFullName(),
                "email", user.getEmail()
        );
    }
}
