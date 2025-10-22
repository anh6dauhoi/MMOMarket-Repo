package com.mmo.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmo.entity.CoinDeposit;
import com.mmo.entity.EmailVerification;
import com.mmo.entity.User;
import com.mmo.repository.CoinDepositRepository;
import com.mmo.repository.EmailVerificationRepository;
import com.mmo.service.AuthService;
import jakarta.mail.MessagingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

@Controller
@SessionAttributes("user")
public class AuthController {
    @Autowired
    private AuthService authService;

    @Autowired
    private EmailVerificationRepository emailVerificationRepository;

    @Autowired
    private CoinDepositRepository coinDepositRepository;

    @Autowired
    @Qualifier("emailExecutor")
    private Executor emailExecutor;

    @GetMapping("/authen/register")
    public String showRegisterForm() {
        return "authen/register";
    }

    @PostMapping("/authen/register")
    public String register(@RequestParam String email, @RequestParam String password, @RequestParam("confirm-password") String confirmPassword, @RequestParam(required = false) String fullName, Model model) {
        try {
            // Kiểm tra mật khẩu xác nhận
            if (!password.equals(confirmPassword)) {
                model.addAttribute("message", "Password confirmation does not match.");
                model.addAttribute("email", email);
                return "authen/register";
            }
            // Kiểm tra độ mạnh mật khẩu
            if (password == null || password.length() < 8 || !password.matches("^(?=.*[A-Za-z])(?=.*\\d).{8,}$")) {
                model.addAttribute("message", "Password must be at least 8 characters and include letters and numbers.");
                model.addAttribute("email", email);
                return "authen/register";
            }

            // Kiểm tra email đã tồn tại
            if (authService.findByEmail(email) != null) {
                model.addAttribute("message", "Email is already in use. Please choose another email.");
                model.addAttribute("email", email);
                return "authen/register";
            }

            // Đăng ký user (đồng bộ)
            User user = authService.register(email, password, fullName != null ? fullName : "Unknown");

            // Tạo OTP + gửi email hoàn toàn bất đồng bộ để điều hướng nhanh
            emailExecutor.execute(() -> {
                try {
                    String code = authService.generateVerificationCode();
                    EmailVerification verification = new EmailVerification();
                    verification.setUser(user);
                    verification.setVerificationCode(code);
                    verification.setExpiryDate(new Date(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(10)));
                    verification.setUsed(false);
                    emailVerificationRepository.save(verification);
                    authService.sendVerificationCodeEmail(email, code);
                } catch (Exception ignored) { }
            });

            // Điều hướng ngay sang trang verify
            model.addAttribute("email", email);
            return "authen/verify";
        } catch (Exception e) {
            model.addAttribute("message", "Unable to process registration at the moment. Please try again later.");
            model.addAttribute("email", email);
            return "authen/register";
        }
    }

    @GetMapping("/authen/login")
    public String login(Model model, @RequestParam(value = "error", required = false) String error) {
        if (error != null) {
            String errorMessage = (String) model.getAttribute("message");
            model.addAttribute("message", errorMessage != null ? errorMessage : "Incorrect email or password. Please try again or verify your account.");
        }
        return "authen/login";
    }

    @GetMapping("/authen/verify")
    public String showVerifyForm(@RequestParam String email, @RequestParam(required = false) Boolean reset, Model model) {
        model.addAttribute("email", email);
        if (Boolean.TRUE.equals(reset)) {
            model.addAttribute("reset", true);
        }
        return "authen/verify";
    }

    @PostMapping("/authen/verify")
    public String verify(@RequestParam String email, @RequestParam String code, Model model,
                         @RequestParam(required = false) Boolean reset) {
        User user = authService.findByEmail(email);
        if (user == null) {
            model.addAttribute("message", "Verification code is incorrect or already used.");
            model.addAttribute("email", email);
            if (Boolean.TRUE.equals(reset)) model.addAttribute("reset", true);
            return "authen/verify";
        }

        // Validate OTP định dạng numeric 6 ký tự để chống sửa F12
        if (code == null || !code.matches("^\\d{6}$")) {
            model.addAttribute("message", "Invalid OTP code.");
            model.addAttribute("email", email);
            return "authen/verify";
        }

        // Lấy bản ghi OTP mới nhất, chưa dùng
        Optional<EmailVerification> optionalVerification = emailVerificationRepository
                .findTopByUserAndVerificationCodeAndIsUsedFalseOrderByCreatedAtDesc(user, code);
        EmailVerification verification = optionalVerification.orElse(null);
        if (verification == null) {
            model.addAttribute("message", "Verification code is incorrect or already used.");
            model.addAttribute("email", email);
            return "authen/verify";
        }

        if (verification.getExpiryDate().before(new Date())) {
            model.addAttribute("message", "Verification code has expired. Please request a new code.");
            model.addAttribute("email", email);
            return "authen/verify";
        }

        if (Boolean.TRUE.equals(reset)) {
            // Flow reset mật khẩu: KHÔNG đánh dấu used tại đây để dùng lại ở bước đặt mật khẩu mới
            model.addAttribute("email", email);
            model.addAttribute("code", code);
            return "authen/new-password";
        } else {
            // Flow đăng ký: đánh dấu used và xác thực tài khoản
            verification.setUsed(true);
            emailVerificationRepository.save(verification);

            user.setVerified(true);
            authService.saveUser(user);
            model.addAttribute("message", "Verification successful! You can log in now.");
            return "authen/login";
        }
    }

    @GetMapping("/authen/forgot-password")
    public String showForgotPasswordForm() {
        return "authen/forgot-password";
    }

    @PostMapping("/authen/forgot-password")
    public String forgotPassword(@RequestParam String email, Model model) {
        // Kiểm tra tài khoản tồn tại; nếu không, báo lỗi và ở lại trang quên mật khẩu
        User user = authService.findByEmail(email);
        if (user == null) {
            model.addAttribute("error", "Email does not exist.");
            return "authen/forgot-password";
        }

        // Có tài khoản -> chuyển ngay sang trang nhập OTP, đồng thời tạo OTP + gửi email bất đồng bộ
        emailExecutor.execute(() -> {
            try {
                String code = authService.generateVerificationCode();
                EmailVerification verification = new EmailVerification();
                verification.setUser(user);
                verification.setVerificationCode(code);
                verification.setExpiryDate(new Date(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(10)));
                verification.setUsed(false);
                emailVerificationRepository.save(verification);
                authService.sendVerificationCodeEmail(email, code);
            } catch (Exception ignored) { }
        });

        model.addAttribute("email", email);
        model.addAttribute("reset", true);
        return "authen/verify";
    }

    @PostMapping("/authen/new-password")
    public String newPassword(@RequestParam String email, @RequestParam String password, @RequestParam String confirmPassword, @RequestParam String code, Model model) {
        // Backend validation để chống sửa tham số
        if (password == null || password.length() < 8 || !password.matches("^(?=.*[A-Za-z])(?=.*\\d).{8,}$")) {
            model.addAttribute("error", "Password must be at least 8 characters and include letters and numbers.");
            model.addAttribute("email", email);
            model.addAttribute("code", code);
            return "authen/new-password";
        }
        if (!password.equals(confirmPassword)) {
            model.addAttribute("error", "Password confirmation does not match.");
            model.addAttribute("email", email);
            model.addAttribute("code", code);
            return "authen/new-password";
        }

        User user = authService.findByEmail(email);
        if (user == null) {
            model.addAttribute("error", "Email does not exist.");
            model.addAttribute("email", email);
            model.addAttribute("code", code);
            return "authen/new-password";
        }

        // Chỉ chấp nhận OTP mới nhất, chưa used
        Optional<EmailVerification> optionalVerification = emailVerificationRepository
                .findTopByUserAndVerificationCodeAndIsUsedFalseOrderByCreatedAtDesc(user, code);
        EmailVerification verification = optionalVerification.orElse(null);
        if (verification == null || verification.getExpiryDate().before(new Date())) {
            model.addAttribute("error", "Verification code is invalid or has expired.");
            model.addAttribute("email", email);
            model.addAttribute("code", code);
            return "authen/new-password";
        }

        // Cập nhật mật khẩu bằng encoder
        authService.updatePassword(user, password);
        verification.setUsed(true);
        emailVerificationRepository.save(verification);

        model.asMap().clear();
        model.addAttribute("message", "Password updated successfully! You can log in.");
        return "authen/login";
    }

    private String generateDepositCode(User user) {
        // Format: Prefix MMO + 2-5 alphanumeric (from base36 userId) + 3-10 digits (deterministic checksum 6 digits)
        long id = user.getId() != null ? user.getId() : new Random().nextLong();
        String mid = Long.toString(Math.abs(id), 36).toUpperCase();
        if (mid.length() < 2) {
            mid = (mid + "XX").substring(0, 2);
        }
        if (mid.length() > 5) {
            mid = mid.substring(mid.length() - 5);
        }
        // Deterministic 6-digit numeric suffix based on email+id
        String seed = (user.getEmail() != null ? user.getEmail() : "") + id;
        CRC32 crc = new CRC32();
        crc.update(seed.getBytes(StandardCharsets.UTF_8));
        long val = Math.abs(crc.getValue() % 1_000_000L);
        String suffix = String.format("%06d", val);
        return "MMO" + mid + suffix;
    }

    private void ensureDepositCode(User user) {
        if (user.getDepositCode() == null || user.getDepositCode().isBlank()) {
            // Ensure we have an id before generating code for base36
            if (user.getId() == null) {
                user = authService.saveUser(user);
            }
            String code = generateDepositCode(user);
            user.setDepositCode(code);
            authService.saveUser(user);
        }
    }

    @RequestMapping("/welcome")
    public String welcome(Model model, Authentication authentication) {
        User user = null;
        String displayName = null;

        if (authentication instanceof OAuth2AuthenticationToken oauth2Token) {
            OAuth2User oauthUser = oauth2Token.getPrincipal();
            String email = oauthUser.getAttribute("email");
            String name = oauthUser.getAttribute("name");
            user = authService.findByEmail(email);
            if (user == null) {
                // Chưa tồn tại -> tạo tài khoản mới (đã liên kết với Google qua email)
                user = new User();
                user.setEmail(email);
                user.setFullName(name);
                user.setRole("CUSTOMER");
                user.setVerified(true);
                user = authService.saveUser(user);
                ensureDepositCode(user);
            } else {
                // Đã tồn tại -> liên kết phương thức đăng nhập bằng cách dùng chính tài khoản hiện có
                // Nếu tài khoản chưa verify, coi như đã xác thực qua Google
                if (!user.isVerified()) {
                    user.setVerified(true);
                }
                // Cập nhật tên nếu đang trống/null
                if ((user.getFullName() == null || user.getFullName().trim().isEmpty()) && name != null) {
                    user.setFullName(name);
                }
                user = authService.saveUser(user);
                ensureDepositCode(user);
            }
            displayName = (user.getFullName() != null ? user.getFullName() : name);
        } else if (authentication != null && authentication.isAuthenticated()) {
            // Handle traditional form login (UsernamePasswordAuthenticationToken)
            String email = authentication.getName();
            user = authService.findByEmail(email);
            if (user != null) {
                ensureDepositCode(user);
                displayName = (user.getFullName() != null && !user.getFullName().trim().isEmpty()) ? user.getFullName() : user.getEmail();
            } else {
                displayName = email; // fallback
            }
        }

        if (user != null) {
            model.addAttribute("user", user);
        }
        if (displayName != null) {
            model.addAttribute("message", "Welcome " + displayName + " to MMOMarket!");
        }
        return "customer/welcome";
    }

    @GetMapping("/customer/topup")
    public String topupPage(Model model, Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            User user = null;
            if (authentication instanceof OAuth2AuthenticationToken oauth2Token) {
                OAuth2User oauthUser = oauth2Token.getPrincipal();
                String email = oauthUser.getAttribute("email");
                user = authService.findByEmail(email);
            } else {
                String email = authentication.getName();
                user = authService.findByEmail(email);
            }
            if (user != null) {
                ensureDepositCode(user);
                model.addAttribute("user", user);
                model.addAttribute("depositCode", user.getDepositCode());
                String accountName = URLEncoder.encode("TRAN VAN TUAN ANH", StandardCharsets.UTF_8);
                String addInfo = URLEncoder.encode(user.getDepositCode(), StandardCharsets.UTF_8);
                String qr = "https://api.vietqr.io/image/970422-0813302283-jYp8Yod.jpg?accountName=" + accountName + "&addInfo=" + addInfo;
                model.addAttribute("vietQrUrl", qr);
            }
        }
        return "customer/topup";
    }

    @PostMapping("/customer/topup")
    @ResponseBody
    public String sepayWebhook(@RequestBody String body) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode json = mapper.readTree(body);
            // Lấy các field phổ biến từ SePay (tùy biến theo cấu trúc thực tế)
            String description = json.path("description").asText(json.path("content").asText(""));
            long amount = json.path("amount").asLong(0);
            json.path("to_account").asText("0813302283");

            // Tìm code theo cấu trúc MMO...
            Pattern p = Pattern.compile("MMO[A-Z0-9]{2,5}[0-9]{3,10}");
            Matcher m = p.matcher(description != null ? description.toUpperCase() : "");
            if (!m.find()) {
                return "IGNORED: no code";
            }
            String code = m.group();

            User user = authService.findByDepositCode(code);
            if (user == null) {
                return "IGNORED: user not found";
            }

            // Lưu giao dịch coin + cộng coin (1 VND = 1 coin)
            CoinDeposit cd = new CoinDeposit();
            cd.setUser(user);
            cd.setAmount(amount);
            cd.setCoinsAdded(amount);
            cd.setStatus("Success");
            cd.setCreatedAt(new Date());
            cd.setUpdatedAt(new Date());
            coinDepositRepository.save(cd);

            if (user.getCoins() == null) user.setCoins(0L);
            user.setCoins(user.getCoins() + Math.max(0, amount));
            authService.saveUser(user);

            return "OK";
        } catch (Exception e) {
            return "ERROR";
        }
    }

    @PostMapping("/authen/resend-otp")
    @ResponseBody
    public String resendOtp(@RequestParam String email) {
        try {
            User user = authService.findByEmail(email);
            if (user != null) {
                String code = authService.generateVerificationCode();
                EmailVerification verification = new EmailVerification();
                verification.setUser(user);
                verification.setVerificationCode(code);
                verification.setExpiryDate(new Date(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(10)));
                verification.setUsed(false);
                emailVerificationRepository.save(verification);
                authService.sendVerificationCodeEmail(email, code);
            }
        } catch (Exception ignored) { }
        // Luôn trả về thông báo chung để tránh lộ thông tin email tồn tại
        return "If the email exists, a new OTP has been sent.";
    }
}
