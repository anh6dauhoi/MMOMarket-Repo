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
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Controller
@SessionAttributes("user")
public class AuthController {
    @Autowired
    private AuthService authService;

    @Autowired
    private EmailVerificationRepository emailVerificationRepository;

    @Autowired
    private CoinDepositRepository coinDepositRepository;

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
            model.addAttribute("message", errorMessage != null ? errorMessage : "Email hoặc mật khẩu không đúng. Vui lòng thử lại hoặc xác thực tài khoản.");
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
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8));
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
    public String welcome(Model model, org.springframework.security.core.Authentication authentication) {
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
                user.setRole("ROLE_CUSTOMER");
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
            model.addAttribute("message", "Chào mừng " + displayName + " đến với MMOMarket!");
        }
        return "customer/welcome";
    }

    @GetMapping("/customer/topup")
    public String topupPage(Model model, org.springframework.security.core.Authentication authentication) {
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
            String bankAccount = json.path("to_account").asText("0813302283");

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
}
