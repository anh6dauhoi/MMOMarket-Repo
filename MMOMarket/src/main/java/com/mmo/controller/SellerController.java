package com.mmo.controller;

import com.mmo.dto.CreateWithdrawalRequest;
import com.mmo.dto.SellerRegistrationForm;
import com.mmo.dto.SellerWithdrawalResponse;
import com.mmo.dto.SellerTransactionListItem;
import com.mmo.dto.SellerComplaintListItem;
import com.mmo.entity.Complaint;
import com.mmo.entity.SellerBankInfo;
import com.mmo.entity.ShopInfo;
import com.mmo.entity.User;
import com.mmo.entity.Withdrawal;
import com.mmo.entity.Transaction;
import com.mmo.repository.UserRepository;
import com.mmo.service.EmailService;
import com.mmo.service.NotificationService;
import com.mmo.service.SellerBankInfoService;
import com.mmo.service.SystemConfigurationService;
import com.mmo.util.Bank;
import com.mmo.util.EmailTemplate;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.Calendar;

@Controller
@RequestMapping("/seller")
public class SellerController {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SellerBankInfoService sellerBankInfoService;

    @Autowired
    private EmailService emailService;
    @Autowired
    private NotificationService notificationService;

    // New: queue publisher for withdrawal creation
    @Autowired
    private com.mmo.mq.WithdrawalQueuePublisher withdrawalQueuePublisher;

    // New: queue publisher for seller registration
    @Autowired
    private com.mmo.mq.SellerRegistrationPublisher sellerRegistrationPublisher;

    // New: queue publisher for buy-points
    @Autowired
    private com.mmo.mq.BuyPointsPublisher buyPointsPublisher;

    @PersistenceContext
    private EntityManager entityManager;

    // Inject repositories used for delete-shop logic
    @Autowired private com.mmo.repository.ProductRepository productRepository;
    @Autowired private com.mmo.repository.ProductVariantRepository productVariantRepository;
    @Autowired private com.mmo.repository.ProductVariantAccountRepository productVariantAccountRepository;
    @Autowired private com.mmo.repository.ReviewRepository reviewRepository;
    @Autowired private com.mmo.repository.TransactionRepository transactionRepository;
    @Autowired private com.mmo.repository.ShopInfoRepository shopInfoRepository;

    // OTP dependencies
    @Autowired
    private com.mmo.service.AuthService authService;
    @Autowired
    private com.mmo.repository.EmailVerificationRepository emailVerificationRepository;

    @Autowired
    @Qualifier("emailExecutor")
    private Executor emailExecutor;

    // Inject system configuration service
    @Autowired
    private SystemConfigurationService systemConfigurationService;

    @Autowired
    private com.mmo.service.ComplaintService complaintService;

    private static final long REGISTRATION_FEE = 200_000L;

    @GetMapping("/register")
    public String showRegistrationForm(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = null;
        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof UserDetails) {
                email = ((UserDetails) principal).getUsername();
            } else if (principal instanceof OidcUser) {
                email = ((OidcUser) principal).getEmail();
            } else if (principal instanceof OAuth2User) {
                Object mailAttr = ((OAuth2User) principal).getAttributes().get("email");
                if (mailAttr != null) email = mailAttr.toString();
            } else {
                email = authentication.getName();
            }
        }

        if (email == null) {
            return "redirect:/login";
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return "redirect:/login";
        }

        // Load seller agreement URL from system configuration (fallback to static PDF)
        String agreementUrl = null;
        try {
            if (systemConfigurationService != null) {
                agreementUrl = systemConfigurationService.getStringValue(
                        com.mmo.constant.SystemConfigKeys.POLICY_SELLER_AGREEMENT_URL,
                        null
                );
            }
        } catch (Exception ignored) {}
        if (agreementUrl == null || agreementUrl.isBlank()) {
            agreementUrl = "/contracts/seller-contract.pdf"; // default static file
        }
        model.addAttribute("sellerAgreementUrl", agreementUrl);

        // Add user data to model for balance display
        model.addAttribute("user", user);

        // Prefer shopStatus over legacy SellerRegistration flow
        String shopStatus = user.getShopStatus();
        model.addAttribute("shopStatus", shopStatus);
        boolean active = shopStatus != null && shopStatus.equalsIgnoreCase("Active");
        if (active) {
            // Load ShopInfo and present as a synthetic 'registration' for view compatibility
            ShopInfo shop = entityManager.createQuery("SELECT s FROM ShopInfo s WHERE s.user = :u AND s.isDelete = false", ShopInfo.class)
                    .setParameter("u", user)
                    .getResultStream().findFirst().orElse(null);
            Map<String, Object> registration = new HashMap<>();
            registration.put("id", 0L); // sentinel id so template renders status fragment
            registration.put("status", "Active");
            registration.put("shopName", shop != null ? shop.getShopName() : (user.getFullName() != null ? user.getFullName() + "'s Shop" : "My Shop"));
            registration.put("description", shop != null ? shop.getDescription() : "");
            model.addAttribute("registration", registration);
            return "customer/account-setting";
        }

        // Inactive: show registration form
        if (!model.containsAttribute("sellerRegistration")) {
            model.addAttribute("sellerRegistration", new SellerRegistrationForm());
        }
        return "customer/account-setting";
    }

    // NEW: Send OTP for seller registration
    @PostMapping(path = "/register/send-otp")
    @ResponseBody
    public ResponseEntity<?> sendSellerRegistrationOtp(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        String email = authentication.getName();
        if (authentication.getPrincipal() instanceof OidcUser oidc) email = oidc.getEmail();
        else if (authentication.getPrincipal() instanceof OAuth2User ou) {
            Object mailAttr = ou.getAttributes().get("email");
            if (mailAttr != null) email = mailAttr.toString();
        }
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            return ResponseEntity.badRequest().body("User has no email configured. Cannot send OTP.");
        }
        // Cooldown: avoid spamming OTP sends (min 60 seconds between sends)
        try {
            Optional<com.mmo.entity.EmailVerification> latest = emailVerificationRepository.findTopByUserOrderByCreatedAtDesc(user);
            if (latest.isPresent() && latest.get().getCreatedAt() != null) {
                long seconds = (System.currentTimeMillis() - latest.get().getCreatedAt().getTime()) / 1000L;
                if (seconds < 60) {
                    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("Please wait " + (60 - seconds) + "s before requesting a new OTP.");
                }
            }
        } catch (Exception ignored) {}

        // Offload OTP creation + persistence + email to executor; return immediately
        try {
            User target = user;
            emailExecutor.execute(() -> {
                try {
                    String code = authService.generateVerificationCode();
                    com.mmo.entity.EmailVerification verification = new com.mmo.entity.EmailVerification();
                    verification.setUser(target);
                    verification.setVerificationCode(code);
                    verification.setExpiryDate(new Date(System.currentTimeMillis() + 5 * 60 * 1000)); // 5 minutes expiry
                    verification.setUsed(false);
                    emailVerificationRepository.save(verification);
                    String subject = "[MMOMarket] OTP Verify Seller Registration";
                    String html = EmailTemplate.sellerRegistrationOtpEmail(code);
                    emailService.sendEmailAsync(target.getEmail(), subject, html);
                } catch (Exception ignored) { }
            });
        } catch (Exception ignored) { }

        return ResponseEntity.ok("OTP has been sent to your email.");
    }

    // NEW: Seller registration with OTP (JSON endpoint)
    @PostMapping(path = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @Transactional
    public ResponseEntity<?> registerSellerJson(@RequestBody SellerRegistrationForm sellerRegistration,
                                                Authentication authentication,
                                                HttpSession session,
                                                HttpServletRequest request) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }
            String email = authentication.getName();
            if (authentication.getPrincipal() instanceof OidcUser oidc) email = oidc.getEmail();
            else if (authentication.getPrincipal() instanceof OAuth2User ou) {
                Object mailAttr = ou.getAttributes().get("email");
                if (mailAttr != null) email = mailAttr.toString();
            }
            User user = userRepository.findByEmail(email).orElse(null);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }

            // Validate required fields
            if (sellerRegistration.getShopName() == null || sellerRegistration.getShopName().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Shop name is required.");
            }
            if (sellerRegistration.getDescription() == null || sellerRegistration.getDescription().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Shop description is required.");
            }

            // Validate OTP
            if (sellerRegistration.getOtp() == null || !sellerRegistration.getOtp().matches("\\d{6}")) {
                return handleOtpFailForSellerRegister(session, request, authentication, user, user.getId(), "seller_register", "MSG_OTP_REQUIRED: Please enter the 6-digit OTP sent to your email.");
            }

            // Pre-validate OTP
            var optVerification = emailVerificationRepository.findTopByUserAndVerificationCodeAndIsUsedFalseOrderByCreatedAtDesc(user, sellerRegistration.getOtp());
            if (optVerification.isEmpty()) {
                return handleOtpFailForSellerRegister(session, request, authentication, user, user.getId(), "seller_register", "MSG_OTP_INVALID: OTP is incorrect or already used.");
            }
            var verification = optVerification.get();
            if (verification.getExpiryDate() == null || verification.getExpiryDate().before(new Date())) {
                return handleOtpFailForSellerRegister(session, request, authentication, user, user.getId(), "seller_register", "MSG_OTP_EXPIRED: OTP has expired. Please request a new OTP.");
            }

            // Success so far: clear attempts
            clearSellerOtpAttempts(session, user.getId(), "seller_register");

            // Validate coins balance
            Long currentCoins = user.getCoins() == null ? 0L : user.getCoins();
            if (currentCoins < REGISTRATION_FEE && (user.getShopStatus() == null || !user.getShopStatus().equalsIgnoreCase("Active"))) {
                notificationService.createNotificationForUser(
                        user.getId(),
                        "Seller registration failed",
                        "Insufficient balance. A fee of 200,000 coins is required to activate your seller account."
                );
                return ResponseEntity.badRequest().body("Insufficient balance. 200,000 coins are required for seller registration.");
            }

            // Mark OTP as used
            verification.setUsed(true);
            emailVerificationRepository.save(verification);

            // Enqueue registration request for idempotent, atomic processing
            String dedupeKey = UUID.randomUUID().toString();
            com.mmo.mq.dto.SellerRegistrationMessage msg = new com.mmo.mq.dto.SellerRegistrationMessage(
                    user.getId(),
                    sellerRegistration.getShopName(),
                    sellerRegistration.getDescription(),
                    dedupeKey
            );
            try {
                sellerRegistrationPublisher.publish(msg);
            } catch (Exception ex) {
                return ResponseEntity.status(500).body("Failed to submit registration. Please try again later.");
            }

            // Return success
            return ResponseEntity.ok("Registration successful! Redirecting to your shop...");
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }

    @PostMapping("/register")
    public String registerSeller(@Valid @ModelAttribute("sellerRegistration") SellerRegistrationForm sellerRegistration,
                                 BindingResult bindingResult,
                                 @RequestParam(value = "agree", required = false) Boolean agree,
                                 RedirectAttributes redirectAttributes,
                                 HttpSession session,
                                 HttpServletRequest request,
                                 Authentication authentication) {
        // Validate basic input
        if (agree == null || !agree) {
            redirectAttributes.addFlashAttribute("agreeError", "You must agree to the Terms and Policy to continue.");
        }
        if (bindingResult.hasErrors() || (agree == null || !agree)) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.sellerRegistration", bindingResult);
            redirectAttributes.addFlashAttribute("sellerRegistration", sellerRegistration);
            return "redirect:/seller/register";
        }

        // Resolve current user
        String email = null;
        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof UserDetails) {
                email = ((UserDetails) principal).getUsername();
            } else if (principal instanceof OidcUser) {
                email = ((OidcUser) principal).getEmail();
            } else if (principal instanceof OAuth2User) {
                Object mailAttr = ((OAuth2User) principal).getAttributes().get("email");
                if (mailAttr != null) email = mailAttr.toString();
            } else {
                email = authentication.getName();
            }
        }
        if (email == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please log in to continue.");
            return "redirect:/login";
        }
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "User not found.");
            return "redirect:/login";
        }

        // Validate OTP first
        if (sellerRegistration.getOtp() == null || !sellerRegistration.getOtp().matches("\\d{6}")) {
            int attempts = incSellerOtpAttempts(session, user.getId(), "seller_register");
            if (attempts >= 5) {
                try { new SecurityContextLogoutHandler().logout(request, null, authentication); } catch (Exception ignored) {}
                try { session.invalidate(); } catch (Exception ignored) {}
                redirectAttributes.addFlashAttribute("errorMessage", "Too many OTP failures. You have been logged out. Please sign in again.");
                return "redirect:/authen/login";
            } else if (attempts >= 3) {
                sendSellerRegistrationOtpForUser(user);
                redirectAttributes.addFlashAttribute("errorMessage", "Please enter the 6-digit OTP. A new OTP has been sent to your email.");
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Please enter the 6-digit OTP sent to your email.");
            }
            redirectAttributes.addFlashAttribute("sellerRegistration", sellerRegistration);
            return "redirect:/seller/register";
        }

        // Pre-validate OTP
        var optVerification = emailVerificationRepository.findTopByUserAndVerificationCodeAndIsUsedFalseOrderByCreatedAtDesc(user, sellerRegistration.getOtp());
        if (optVerification.isEmpty()) {
            int attempts = incSellerOtpAttempts(session, user.getId(), "seller_register");
            if (attempts >= 5) {
                try { new SecurityContextLogoutHandler().logout(request, null, authentication); } catch (Exception ignored) {}
                try { session.invalidate(); } catch (Exception ignored) {}
                redirectAttributes.addFlashAttribute("errorMessage", "Too many OTP failures. You have been logged out. Please sign in again.");
                return "redirect:/authen/login";
            } else if (attempts >= 3) {
                sendSellerRegistrationOtpForUser(user);
                redirectAttributes.addFlashAttribute("errorMessage", "Incorrect OTP. A new OTP has been sent to your email.");
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Incorrect or used OTP.");
            }
            redirectAttributes.addFlashAttribute("sellerRegistration", sellerRegistration);
            return "redirect:/seller/register";
        }
        var verification = optVerification.get();
        if (verification.getExpiryDate() == null || verification.getExpiryDate().before(new Date())) {
            int attempts = incSellerOtpAttempts(session, user.getId(), "seller_register");
            if (attempts >= 5) {
                try { new SecurityContextLogoutHandler().logout(request, null, authentication); } catch (Exception ignored) {}
                try { session.invalidate(); } catch (Exception ignored) {}
                redirectAttributes.addFlashAttribute("errorMessage", "Too many OTP failures. You have been logged out. Please sign in again.");
                return "redirect:/authen/login";
            } else if (attempts >= 3) {
                sendSellerRegistrationOtpForUser(user);
                redirectAttributes.addFlashAttribute("errorMessage", "OTP expired. A new OTP has been sent to your email.");
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "OTP has expired. Please request a new OTP.");
            }
            redirectAttributes.addFlashAttribute("sellerRegistration", sellerRegistration);
            return "redirect:/seller/register";
        }

        // Success: clear attempts
        clearSellerOtpAttempts(session, user.getId(), "seller_register");

        // Validate coins balance
        Long currentCoins = user.getCoins() == null ? 0L : user.getCoins();
        if (currentCoins < REGISTRATION_FEE && (user.getShopStatus() == null || !user.getShopStatus().equalsIgnoreCase("Active"))) {
            notificationService.createNotificationForUser(
                    user.getId(),
                    "Seller registration failed",
                    "Insufficient balance. A fee of 200,000 coins is required to activate your seller account."
            );
            redirectAttributes.addFlashAttribute("errorMessage", "Insufficient balance. 200,000 coins are required for seller registration.");
            redirectAttributes.addFlashAttribute("sellerRegistration", sellerRegistration);
            return "redirect:/seller/register";
        }

        // Mark OTP as used
        verification.setUsed(true);
        emailVerificationRepository.save(verification);

        // Enqueue registration request for idempotent, atomic processing
        String dedupeKey = UUID.randomUUID().toString();
        com.mmo.mq.dto.SellerRegistrationMessage msg = new com.mmo.mq.dto.SellerRegistrationMessage(
                user.getId(),
                sellerRegistration.getShopName(),
                sellerRegistration.getDescription(),
                dedupeKey
        );
        try {
            sellerRegistrationPublisher.publish(msg);
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to submit registration. Please try again later.");
            redirectAttributes.addFlashAttribute("sellerRegistration", sellerRegistration);
            return "redirect:/seller/register";
        }

        // Redirect to shop-info page on success
        redirectAttributes.addFlashAttribute("successMessage", "Your seller registration was successful! Welcome to your shop.");
        return "redirect:/seller/shop-info";
    }

    @GetMapping("/shop-info")
    public String showShopInfo(Model model, RedirectAttributes redirectAttributes) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = null;
        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof UserDetails) {
                email = ((UserDetails) principal).getUsername();
            } else if (principal instanceof OidcUser) {
                email = ((OidcUser) principal).getEmail();
            } else if (principal instanceof OAuth2User) {
                Object mailAttr = ((OAuth2User) principal).getAttributes().get("email");
                if (mailAttr != null) email = mailAttr.toString();
            } else {
                email = authentication.getName();
            }
        }
        if (email == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please login to continue.");
            return "redirect:/login";
        }
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "User not found.");
            return "redirect:/login";
        }
        boolean activeShop = user.getShopStatus() != null && user.getShopStatus().equalsIgnoreCase("Active");
        if (!activeShop) {
            redirectAttributes.addFlashAttribute("errorMessage", "Your shop is not Active. Please complete registration.");
            return "redirect:/seller/register";
        }

        ShopInfo shop = entityManager.createQuery("SELECT s FROM ShopInfo s WHERE s.user = :u AND s.isDelete = false", ShopInfo.class)
                .setParameter("u", user)
                .getResultStream().findFirst().orElse(null);

        if (shop == null) {
            // Fallback: create a lightweight view model when ShopInfo missing
            ShopInfo fallback = new ShopInfo();
            fallback.setShopName(user.getFullName() != null ? user.getFullName() + "'s Shop" : "My Shop");
            fallback.setDescription("");
            model.addAttribute("shop", fallback);
            model.addAttribute("totalFlags", 0);
            model.addAttribute("activeFlags", 0);
        } else {
            model.addAttribute("shop", shop);
            // Get flag statistics
            Long totalFlags = entityManager.createQuery(
                "SELECT COUNT(f) FROM ShopFlag f WHERE f.shop.id = :shopId", Long.class)
                .setParameter("shopId", shop.getId())
                .getSingleResult();
            Long activeFlags = entityManager.createQuery(
                "SELECT COUNT(f) FROM ShopFlag f WHERE f.shop.id = :shopId AND f.status = com.mmo.entity.ShopFlag$FlagStatus.ACTIVE", Long.class)
                .setParameter("shopId", shop.getId())
                .getSingleResult();
            model.addAttribute("totalFlags", totalFlags != null ? totalFlags : 0);
            model.addAttribute("activeFlags", activeFlags != null ? activeFlags : 0);
        }
        model.addAttribute("sellerEmail", user.getEmail());
        model.addAttribute("shopStatus", user.getShopStatus());
        return "seller/shop-info";
    }

    @GetMapping("/contract")
    public ResponseEntity<org.springframework.core.io.Resource> downloadContract(@RequestParam(name = "signed", defaultValue = "false") boolean signed) {
        // Legacy endpoint retained; not used in new auto-activation flow
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = (authentication != null) ? authentication.getName() : null;
        if (authentication != null && authentication.getPrincipal() instanceof OidcUser oidc) {
            email = oidc.getEmail();
        } else if (authentication != null && authentication.getPrincipal() instanceof OAuth2User ou) {
            Object mailAttr = ou.getAttributes().get("email");
            if (mailAttr != null) email = mailAttr.toString();
        }
        if (email == null) return ResponseEntity.status(401).build();
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) return ResponseEntity.status(401).build();
        // No contract in new flow
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    /**
     * Get shop flags for current seller
     */
    @GetMapping("/shop-flags")
    @ResponseBody
    public ResponseEntity<?> getShopFlags(Authentication auth) {
        try {
            if (auth == null || !auth.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized"));
            }

            String email = auth.getName();
            if (auth.getPrincipal() instanceof OidcUser oidc) {
                email = oidc.getEmail();
            } else if (auth.getPrincipal() instanceof OAuth2User ou) {
                Object mailAttr = ou.getAttributes().get("email");
                if (mailAttr != null) email = mailAttr.toString();
            }

            User user = userRepository.findByEmail(email).orElse(null);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "User not found"));
            }

            ShopInfo shop = entityManager.createQuery(
                "SELECT s FROM ShopInfo s WHERE s.user = :u AND s.isDelete = false", ShopInfo.class)
                .setParameter("u", user)
                .getResultStream().findFirst().orElse(null);

            if (shop == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Shop not found"));
            }

            // Get all flags for this shop
            List<com.mmo.entity.ShopFlag> flags = entityManager.createQuery(
                "SELECT f FROM ShopFlag f " +
                "LEFT JOIN FETCH f.admin " +
                "LEFT JOIN FETCH f.relatedComplaint " +
                "WHERE f.shop.id = :shopId " +
                "ORDER BY f.createdAt DESC",
                com.mmo.entity.ShopFlag.class)
                .setParameter("shopId", shop.getId())
                .getResultList();

            List<com.mmo.dto.ShopFlagResponse> flagResponses = flags.stream()
                .map(com.mmo.dto.ShopFlagResponse::from)
                .collect(java.util.stream.Collectors.toList());

            return ResponseEntity.ok(Map.of(
                "flags", flagResponses,
                "total", flags.size(),
                "active", flags.stream().filter(f -> f.getStatus() == com.mmo.entity.ShopFlag.FlagStatus.ACTIVE).count()
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to get shop flags: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/contract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String uploadSignedContract(@RequestParam("signed") MultipartFile file,
                                       RedirectAttributes redirectAttributes) {
        // Legacy endpoint retained; not used in new auto-activation flow
        redirectAttributes.addFlashAttribute("errorMessage", "Contract upload is not required. Your shop is already active.");
        return "redirect:/seller/register";
    }

    @GetMapping("/my-shop")
    public String showMyShop(Model model,
                             RedirectAttributes redirectAttributes,
                             @RequestParam(required = false, defaultValue = "month") String timeFilter) {
        // Get current logged-in user
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = null;
        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof UserDetails) {
                email = ((UserDetails) principal).getUsername();
            } else if (principal instanceof OidcUser) {
                email = ((OidcUser) principal).getEmail();
            } else if (principal instanceof OAuth2User) {
                Object mailAttr = ((OAuth2User) principal).getAttributes().get("email");
                if (mailAttr != null) email = mailAttr.toString();
            } else {
                email = authentication.getName();
            }
        }

        if (email == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please login to continue.");
            return "redirect:/login";
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "User not found.");
            return "redirect:/login";
        }

        // Check if shop is active
        boolean activeShop = user.getShopStatus() != null && user.getShopStatus().equalsIgnoreCase("Active");
        if (!activeShop) {
            redirectAttributes.addFlashAttribute("errorMessage", "Your shop is not Active. Please complete registration.");
            return "redirect:/seller/register";
        }

        // Calculate statistics
        Long sellerId = user.getId();

        // Determine time range based on filter
        Calendar cal = Calendar.getInstance();
        Calendar previousCal = Calendar.getInstance();

        switch (timeFilter.toLowerCase()) {
            case "day":
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                previousCal.setTime(cal.getTime());
                previousCal.add(Calendar.DAY_OF_MONTH, -1);
                break;
            case "year":
                cal.set(Calendar.DAY_OF_YEAR, 1);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                previousCal.setTime(cal.getTime());
                previousCal.add(Calendar.YEAR, -1);
                break;
            case "month":
            default:
                cal.set(Calendar.DAY_OF_MONTH, 1);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                previousCal.setTime(cal.getTime());
                previousCal.add(Calendar.MONTH, -1);
                break;
        }

        Date startDate = cal.getTime();
        Date previousStartDate = previousCal.getTime();

        // Total revenue for current period
        Long totalRevenue = transactionRepository.getRevenueBySellerIdAndDateAfter(sellerId, startDate);
        if (totalRevenue == null) totalRevenue = 0L;

        // Total orders for current period
        Long totalOrders = transactionRepository.getOrdersBySellerIdAndDateAfter(sellerId, startDate);
        if (totalOrders == null) totalOrders = 0L;

        // Total products (not filtered by time)
        Long totalProducts = productRepository.countBySellerId(sellerId);
        if (totalProducts == null) totalProducts = 0L;

        // Calculate previous period's statistics for comparison
        Long previousRevenue = transactionRepository.getRevenueBySellerIdBetweenDates(sellerId, previousStartDate, startDate);
        if (previousRevenue == null) previousRevenue = 0L;

        Long previousOrders = transactionRepository.getOrdersBySellerIdBetweenDates(sellerId, previousStartDate, startDate);
        if (previousOrders == null) previousOrders = 0L;

        // Calculate percentage changes
        double revenueChange = 0.0;
        double ordersChange = 0.0;

        if (previousRevenue > 0) {
            revenueChange = ((double)(totalRevenue - previousRevenue) / previousRevenue) * 100;
        } else if (totalRevenue > 0) {
            revenueChange = 100.0;
        }

        if (previousOrders > 0) {
            ordersChange = ((double)(totalOrders - previousOrders) / previousOrders) * 100;
        } else if (totalOrders > 0) {
            ordersChange = 100.0;
        }

        // Get recent transactions (limit to 10)
        List<com.mmo.entity.Transaction> recentTransactions = transactionRepository.findRecentTransactionsBySeller(sellerId);
        if (recentTransactions.size() > 10) {
            recentTransactions = recentTransactions.subList(0, 10);
        }

        // Get top selling products (limit to 5)
        List<com.mmo.entity.Product> topProducts = productRepository.findTopSellingProductsBySeller(
            sellerId,
            org.springframework.data.domain.PageRequest.of(0, 5)
        );

        // Calculate sales count and percentage for each product
        List<com.mmo.dto.TopProductDTO> topProductDTO = new ArrayList<>();
        long totalSales = topProducts.stream()
            .mapToLong(p -> productRepository.countSalesForProduct(p.getId()))
            .sum();

        for (com.mmo.entity.Product product : topProducts) {
            Long salesCount = productRepository.countSalesForProduct(product.getId());
            if (salesCount == null) salesCount = 0L;

            double percentage = 0.0;
            if (totalSales > 0) {
                percentage = ((double) salesCount / totalSales) * 100;
            }

           com.mmo.dto.TopProductDTO dto = new com.mmo.dto.TopProductDTO(
                product.getId(),
                product.getName(),
                product.getImage(),
                salesCount,
                percentage
            );
            topProductDTO.add(dto);
        }

        // Add statistics to model
        model.addAttribute("totalRevenue", totalRevenue);
        model.addAttribute("totalOrders", totalOrders);
        model.addAttribute("totalProducts", totalProducts);
        model.addAttribute("revenueChange", String.format("%.1f", Math.abs(revenueChange)));
        model.addAttribute("revenueChangePositive", revenueChange >= 0);
        model.addAttribute("ordersChange", String.format("%.1f", Math.abs(ordersChange)));
        model.addAttribute("ordersChangePositive", ordersChange >= 0);
        model.addAttribute("recentTransactions", recentTransactions);
        model.addAttribute("topProducts", topProductDTO);
        model.addAttribute("timeFilter", timeFilter);

        // Add comparison label based on filter
        String comparisonLabel;
        switch (timeFilter.toLowerCase()) {
            case "day":
                comparisonLabel = "vs yesterday";
                break;
            case "year":
                comparisonLabel = "vs last year";
                break;
            case "month":
            default:
                comparisonLabel = "vs last month";
                break;
        }
        model.addAttribute("comparisonLabel", comparisonLabel);

        return "seller/my-shop";
    }

    @GetMapping("/withdraw-money")
    public String showWithdrawMoneyPage(Model model, RedirectAttributes redirectAttributes) {
        // Require logged-in and Active shop
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = null;
        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof UserDetails) {
                email = ((UserDetails) principal).getUsername();
            } else if (principal instanceof OidcUser) {
                email = ((OidcUser) principal).getEmail();
            } else if (principal instanceof OAuth2User) {
                Object mailAttr = ((OAuth2User) principal).getAttributes().get("email");
                if (mailAttr != null) email = mailAttr.toString();
            } else {
                email = authentication.getName();
            }
        }
        if (email == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please login to continue.");
            return "redirect:/login";
        }
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "User not found.");
            return "redirect:/login";
        }
        boolean activeShop = user.getShopStatus() != null && user.getShopStatus().equalsIgnoreCase("Active");
        if (!activeShop) {
            redirectAttributes.addFlashAttribute("errorMessage", "Your shop is not Active. Please complete registration.");
            return "redirect:/seller/register";
        }

        // Load coins
        Long coins = user.getCoins() == null ? 0L : user.getCoins();
        model.addAttribute("coins", coins);

        // Load all bank infos for the user
        model.addAttribute("bankInfos", sellerBankInfoService.findAllByUser(user));
        // Supported banks list - use Bank.listAll() to get all 60 banks
        model.addAttribute("supportedBanks", Bank.listAll());
        // Load existing bank info (robust to different mappings)
        SellerBankInfo bankInfo = findBankInfoForOwner(user);
        Map<String, Object> bank = new HashMap<>();
        if (bankInfo != null) {
            bank.put("id", tryGetId(bankInfo));
            bank.put("bankName", tryGetString(bankInfo, "getBankName"));
            bank.put("accountNumber", tryGetString(bankInfo, "getAccountNumber"));
            bank.put("accountHolder", firstNonBlank(
                    tryGetString(bankInfo, "getAccountHolder"),
                    tryGetString(bankInfo, "getAccountName"),
                    tryGetString(bankInfo, "getHolderName"),
                    tryGetString(bankInfo, "getBeneficiaryName"),
                    tryGetString(bankInfo, "getOwnerName")
            ));
            bank.put("branch", tryGetString(bankInfo, "getBranch"));
        }
        model.addAttribute("bank", bank);
        var withdrawals = entityManager.createQuery(
                        "SELECT w FROM Withdrawal w WHERE w.seller = :user ORDER BY w.createdAt DESC", Withdrawal.class)
                .setParameter("user", user)
                .getResultList();
        model.addAttribute("withdrawals", withdrawals);
        return "seller/withdraw-money";
    }

    // NEW: Send OTP for withdrawal (asynchronous email via EmailService)
    @PostMapping(path = "/withdrawals/send-otp")
    @ResponseBody
    public ResponseEntity<?> sendWithdrawalOtp(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        String email = authentication.getName();
        if (authentication.getPrincipal() instanceof OidcUser oidc) email = oidc.getEmail();
        else if (authentication.getPrincipal() instanceof OAuth2User ou) {
            Object mailAttr = ou.getAttributes().get("email");
            if (mailAttr != null) email = mailAttr.toString();
        }
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            return ResponseEntity.badRequest().body("User has no email configured. Cannot send OTP.");
        }
        // Cooldown: avoid spamming OTP sends (min 60 seconds between sends)
        try {
            Optional<com.mmo.entity.EmailVerification> latest = emailVerificationRepository.findTopByUserOrderByCreatedAtDesc(user);
            if (latest.isPresent() && latest.get().getCreatedAt() != null) {
                long seconds = (System.currentTimeMillis() - latest.get().getCreatedAt().getTime()) / 1000L;
                if (seconds < 60) {
                    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("Please wait " + (60 - seconds) + "s before requesting a new OTP.");
                }
            }
        } catch (Exception ignored) {}

        // Offload OTP creation + persistence + email to executor; return immediately
        try {
            User target = user;
            emailExecutor.execute(() -> {
                try {
                    String code = authService.generateVerificationCode();
                    com.mmo.entity.EmailVerification verification = new com.mmo.entity.EmailVerification();
                    verification.setUser(target);
                    verification.setVerificationCode(code);
                    verification.setExpiryDate(new Date(System.currentTimeMillis() + 5 * 60 * 1000)); // 5 minutes expiry
                    verification.setUsed(false);
                    emailVerificationRepository.save(verification);
                    String subject = "[MMOMarket] OTP Xác minh rút tiền";
                    String html = EmailTemplate.withdrawalOtpEmail(code);
                    emailService.sendEmailAsync(target.getEmail(), subject, html);
                } catch (Exception ignored) { }
            });
        } catch (Exception ignored) { }

        return ResponseEntity.ok("OTP has been sent to your email.");
    }

    // NEW: Seller creates a withdrawal request
    @PostMapping(path = "/withdrawals", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @Transactional
    public ResponseEntity<?> createWithdrawal(@RequestBody CreateWithdrawalRequest req,
                                              Authentication authentication,
                                              HttpSession session,
                                              HttpServletRequest request) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }
            String email = authentication.getName();
            if (authentication.getPrincipal() instanceof OidcUser oidc) email = oidc.getEmail();
            else if (authentication.getPrincipal() instanceof OAuth2User ou) {
                Object mailAttr = ou.getAttributes().get("email");
                if (mailAttr != null) email = mailAttr.toString();
            }
            User seller = userRepository.findByEmail(email).orElse(null);
            if (seller == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }

            boolean sellerRole = seller.getRole() != null && seller.getRole().equalsIgnoreCase("SELLER");
            boolean activeShop = seller.getShopStatus() != null && seller.getShopStatus().equalsIgnoreCase("Active");
            if (!sellerRole && !activeShop) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");
            }

            if (req == null || req.getAmount() == null || req.getAmount() <= 0) {
                return ResponseEntity.badRequest().body("MSG16: Minimum withdrawal amount is 50000.");
            }
            if (req.getAmount() < 50_000L) {
                return ResponseEntity.badRequest().body("MSG16: Minimum withdrawal amount is 50000.");
            }
            if (req.getBankInfoId() == null) {
                return ResponseEntity.badRequest().body("MSG17: Bank information not found.");
            }
            if (req.getOtp() == null || !req.getOtp().matches("\\d{6}")) {
                return handleOtpFailForWithdraw(session, request, authentication, seller, seller.getId(), "withdraw_create", "MSG_OTP_REQUIRED: Please enter the 6-digit OTP sent to your email.");
            }

            // Pre-validate OTP to enforce attempts policy before enqueue
            var optVerification = emailVerificationRepository.findTopByUserAndVerificationCodeAndIsUsedFalseOrderByCreatedAtDesc(seller, req.getOtp());
            if (optVerification.isEmpty()) {
                return handleOtpFailForWithdraw(session, request, authentication, seller, seller.getId(), "withdraw_create", "MSG_OTP_INVALID: Code not found or already used.");
            }
            var verification = optVerification.get();
            if (verification.getExpiryDate() == null || verification.getExpiryDate().before(new Date())) {
                return handleOtpFailForWithdraw(session, request, authentication, seller, seller.getId(), "withdraw_create", "MSG_OTP_EXPIRED: The code has expired.");
            }

            // Success so far: clear attempts for this action
            clearSellerOtpAttempts(session, seller.getId(), "withdraw_create");

            // Enqueue creation message; worker will validate OTP, balance, and persist
            String dedupeKey = seller.getId() + ":" + req.getBankInfoId() + ":" + req.getAmount() + ":" + req.getOtp();
            com.mmo.mq.dto.WithdrawalCreateMessage msg = new com.mmo.mq.dto.WithdrawalCreateMessage(
                    seller.getId(),
                    req.getBankInfoId(),
                    req.getAmount(),
                    req.getBankName(),
                    req.getAccountNumber(),
                    req.getAccountHolder(),
                    req.getBranch(),
                    req.getOtp(),
                    dedupeKey
            );
            withdrawalQueuePublisher.publishCreate(msg);

            // Do not persist or deduct coins here
            return ResponseEntity.accepted().body("Withdrawal request has been queued and will appear shortly.");
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }

    // NEW: Seller creates a withdrawal request (form submission)
    @PostMapping(path = "/withdrawals", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Transactional
    public String createWithdrawalForm(CreateWithdrawalRequest req,
                                       Authentication authentication,
                                       RedirectAttributes redirectAttributes,
                                       HttpSession session,
                                       HttpServletRequest request) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Unauthorized");
                return "redirect:/seller/withdraw-money";
            }
            String email = authentication.getName();
            if (authentication.getPrincipal() instanceof OidcUser oidc) email = oidc.getEmail();
            else if (authentication.getPrincipal() instanceof OAuth2User ou) {
                Object mailAttr = ou.getAttributes().get("email");
                if (mailAttr != null) email = mailAttr.toString();
            }
            User seller = userRepository.findByEmail(email).orElse(null);
            if (seller == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Unauthorized");
                return "redirect:/seller/withdraw-money";
            }
            boolean sellerRole = seller.getRole() != null && seller.getRole().equalsIgnoreCase("SELLER");
            boolean activeShop = seller.getShopStatus() != null && seller.getShopStatus().equalsIgnoreCase("Active");
            if (!sellerRole && !activeShop) {
                redirectAttributes.addFlashAttribute("errorMessage", "Forbidden");
                return "redirect:/seller/withdraw-money";
            }

            if (req == null || req.getOtp() == null || !req.getOtp().matches("\\d{6}")) {
                int attempts = incSellerOtpAttempts(session, seller.getId(), "withdraw_create");
                if (attempts >= 5) {
                    try { new SecurityContextLogoutHandler().logout(request, null, authentication); } catch (Exception ignored) {}
                    try { session.invalidate(); } catch (Exception ignored) {}
                    redirectAttributes.addFlashAttribute("errorMessage", "Too many OTP failures. You have been logged out. Please sign in again.");
                    return "redirect:/authen/login";
                } else if (attempts >= 3) {
                    sendWithdrawalOtpForUser(seller);
                    redirectAttributes.addFlashAttribute("errorMessage", "Please enter the 6-digit OTP. A new OTP has been sent to your email.");
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", "Please enter the 6-digit OTP sent to your email.");
                }
                return "redirect:/seller/withdraw-money";
            }
            if (req.getAmount() == null || req.getAmount() < 50_000L) {
                redirectAttributes.addFlashAttribute("errorMessage", "MSG16: Minimum withdrawal amount is 50000.");
                return "redirect:/seller/withdraw-money";
            }
            if (req.getBankInfoId() == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "MSG17: Bank information not found.");
                return "redirect:/seller/withdraw-money";
            }

            // Pre-validate OTP
            var optVerification = emailVerificationRepository.findTopByUserAndVerificationCodeAndIsUsedFalseOrderByCreatedAtDesc(seller, req.getOtp());
            if (optVerification.isEmpty()) {
                int attempts = incSellerOtpAttempts(session, seller.getId(), "withdraw_create");
                if (attempts >= 5) {
                    try { new SecurityContextLogoutHandler().logout(request, null, authentication); } catch (Exception ignored) {}
                    try { session.invalidate(); } catch (Exception ignored) {}
                    redirectAttributes.addFlashAttribute("errorMessage", "Too many OTP failures. You have been logged out. Please sign in again.");
                    return "redirect:/authen/login";
                } else if (attempts >= 3) {
                    sendWithdrawalOtpForUser(seller);
                    redirectAttributes.addFlashAttribute("errorMessage", "Incorrect OTP. A new OTP has been sent to your email.");
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", "Incorrect or used OTP.");
                }
                return "redirect:/seller/withdraw-money";
            }
            var verification = optVerification.get();
            if (verification.getExpiryDate() == null || verification.getExpiryDate().before(new Date())) {
                int attempts = incSellerOtpAttempts(session, seller.getId(), "withdraw_create");
                if (attempts >= 5) {
                    try { new SecurityContextLogoutHandler().logout(request, null, authentication); } catch (Exception ignored) {}
                    try { session.invalidate(); } catch (Exception ignored) {}
                    redirectAttributes.addFlashAttribute("errorMessage", "Too many OTP failures. You have been logged out. Please sign in again.");
                    return "redirect:/authen/login";
                } else if (attempts >= 3) {
                    sendWithdrawalOtpForUser(seller);
                    redirectAttributes.addFlashAttribute("errorMessage", "OTP expired. A new OTP has been sent to your email.");
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", "OTP has expired. Please request a new OTP.");
                }
                return "redirect:/seller/withdraw-money";
            }

            // Success: clear attempts
            clearSellerOtpAttempts(session, seller.getId(), "withdraw_create");

            String dedupeKey = seller.getId() + ":" + req.getBankInfoId() + ":" + req.getAmount() + ":" + req.getOtp();
            com.mmo.mq.dto.WithdrawalCreateMessage msg = new com.mmo.mq.dto.WithdrawalCreateMessage(
                    seller.getId(),
                    req.getBankInfoId(),
                    req.getAmount(),
                    req.getBankName(),
                    req.getAccountNumber(),
                    req.getAccountHolder(),
                    req.getBranch(),
                    req.getOtp(),
                    dedupeKey
            );
            withdrawalQueuePublisher.publishCreate(msg);

            redirectAttributes.addFlashAttribute("successMessage", "Your withdrawal request has been queued and will appear shortly.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Internal error: " + ex.getMessage());
            return "redirect:/seller/withdraw-money";
        }
        return "redirect:/seller/withdraw-money";
    }

    @GetMapping("/withdrawals/{id}")
    public String viewWithdrawalDetail(@PathVariable Long id, Model model, Authentication authentication) {
        // Lấy user hiện tại
        String email = authentication.getName();
        User user = userRepository.findByEmailAndIsDelete(email, false);
        if (user == null) {
            return "redirect:/authen/login";
        }
        // Lấy withdrawal theo id
        Withdrawal withdrawal = entityManager.find(Withdrawal.class, id);
        if (withdrawal == null || withdrawal.getSeller() == null || !withdrawal.getSeller().getId().equals(user.getId())) {
            // Không tìm thấy hoặc không thuộc về user hiện tại
            return "redirect:/seller/withdraw-money?error=notfound";
        }
        model.addAttribute("withdrawal", withdrawal);
        return "seller/withdrawal-detail";
    }

    @GetMapping("/withdrawals/{id}/json")
    @ResponseBody
    public ResponseEntity<?> getWithdrawalDetailJson(@PathVariable Long id, Authentication authentication) {
        String email = authentication.getName();
        User user = userRepository.findByEmailAndIsDelete(email, false);
        if (user == null) return ResponseEntity.status(401).body("Unauthorized");
        Withdrawal withdrawal = entityManager.find(Withdrawal.class, id);
        if (withdrawal == null || withdrawal.getSeller() == null || !withdrawal.getSeller().getId().equals(user.getId())) {
            return ResponseEntity.status(404).body("Not found");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("id", withdrawal.getId());
        data.put("amount", withdrawal.getAmount());
        data.put("status", withdrawal.getStatus());
        data.put("bankName", withdrawal.getBankName());
        data.put("accountNumber", withdrawal.getAccountNumber());
        data.put("branch", withdrawal.getBranch());
        data.put("createdAt", withdrawal.getCreatedAt());
        return ResponseEntity.ok(data);
    }

    // NEW: Seller Transactions list page with search, filter, sort, pagination
    @GetMapping("/transactions")
    public String listSellerTransactions(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "sortBy", defaultValue = "date") String sortBy,
            @RequestParam(name = "sortDir", defaultValue = "desc") String sortDir,
            Model model,
            RedirectAttributes redirectAttributes) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = null;
        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof UserDetails) {
                email = ((UserDetails) principal).getUsername();
            } else if (principal instanceof OidcUser) {
                email = ((OidcUser) principal).getEmail();
            } else if (principal instanceof OAuth2User) {
                Object mailAttr = ((OAuth2User) principal).getAttributes().get("email");
                if (mailAttr != null) email = mailAttr.toString();
            } else {
                email = authentication.getName();
            }
        }
        if (email == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please login to continue.");
            return "redirect:/login";
        }
        User seller = userRepository.findByEmail(email).orElse(null);
        if (seller == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "User not found.");
            return "redirect:/login";
        }
        boolean activeShop = seller.getShopStatus() != null && seller.getShopStatus().equalsIgnoreCase("Active");
        if (!activeShop) {
            redirectAttributes.addFlashAttribute("errorMessage", "Your shop is not Active. Please complete registration.");
            return "redirect:/seller/register";
        }

        // Build dynamic query with filters
        StringBuilder jpql = new StringBuilder(
                "select new com.mmo.dto.SellerTransactionListItem(t.id, p.name, v.variantName, t.quantity, t.coinSeller, t.status, t.createdAt) " +
                "from Transaction t join t.product p join t.variant v " +
                "where t.seller.id = :sid and t.isDelete = false");

        // Add search filter
        if (search != null && !search.trim().isEmpty()) {
            jpql.append(" and (lower(p.name) like :search or lower(v.variantName) like :search)");
        }

        // Add status filter
        if (status != null && !status.trim().isEmpty() && !status.equalsIgnoreCase("ALL")) {
            jpql.append(" and upper(t.status) = :status");
        }

        // Add sorting
        if ("coins".equalsIgnoreCase(sortBy)) {
            jpql.append(" order by t.coinSeller ").append(sortDir.equalsIgnoreCase("asc") ? "asc" : "desc");
        } else {
            jpql.append(" order by t.createdAt ").append(sortDir.equalsIgnoreCase("asc") ? "asc" : "desc");
        }

        var query = entityManager.createQuery(jpql.toString(), SellerTransactionListItem.class)
            .setParameter("sid", seller.getId());

        if (search != null && !search.trim().isEmpty()) {
            query.setParameter("search", "%" + search.trim().toLowerCase() + "%");
        }

        if (status != null && !status.trim().isEmpty() && !status.equalsIgnoreCase("ALL")) {
            query.setParameter("status", status.toUpperCase());
        }

        // Get all items for stats calculation
        List<SellerTransactionListItem> allItems = query.getResultList();

        // Pagination: 5 items per page
        int pageSize = 5;
        int totalItems = allItems.size();
        int totalPages = (int) Math.ceil((double) totalItems / pageSize);
        page = Math.max(0, Math.min(page, totalPages - 1));

        int startIndex = page * pageSize;
        int endIndex = Math.min(startIndex + pageSize, totalItems);
        List<SellerTransactionListItem> items = allItems.subList(startIndex, endIndex);

        model.addAttribute("transactions", items);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalItems", totalItems);
        model.addAttribute("search", search);
        model.addAttribute("status", status);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("sortDir", sortDir);

        // Quick stats from all filtered items
        long totalEarned = 0L;
        long completedEarned = 0L;
        int escrow = 0, disputed = 0, completed = 0, cancelled = 0;
        for (SellerTransactionListItem it : allItems) {
            if (it.getCoinSeller() != null) totalEarned += it.getCoinSeller();
            String st = it.getStatus() == null ? "" : it.getStatus().toUpperCase();
            switch (st) {
                case "ESCROW" -> escrow++;
                case "DISPUTED" -> disputed++;
                case "COMPLETED" -> {
                    completed++;
                    if (it.getCoinSeller() != null) completedEarned += it.getCoinSeller();
                }
                case "CANCELLED", "REFUNDED" -> cancelled++;
                default -> {}
            }
        }
        model.addAttribute("totalEarned", totalEarned);
        model.addAttribute("completedEarned", completedEarned);
        model.addAttribute("escrowCount", escrow);
        model.addAttribute("disputedCount", disputed);
        model.addAttribute("completedCount", completed);
        model.addAttribute("cancelledCount", cancelled);
        return "seller/transactions";
    }

    // NEW: Transaction detail (JSON) for modal
    @GetMapping(path = "/transactions/{id}/json", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> getTransactionDetailJson(@PathVariable("id") Long id, Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }
            String email = authentication.getName();
            if (authentication.getPrincipal() instanceof OidcUser oidc) email = oidc.getEmail();
            else if (authentication.getPrincipal() instanceof OAuth2User ou) {
                Object mailAttr = ou.getAttributes().get("email");
                if (mailAttr != null) email = mailAttr.toString();
            }
            User seller = userRepository.findByEmail(email).orElse(null);
            if (seller == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }

            Transaction tx = entityManager.find(Transaction.class, id);
            if (tx == null || tx.isDelete() || tx.getSeller() == null || !tx.getSeller().getId().equals(seller.getId())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Not found");
            }

            Map<String, Object> data = new HashMap<>();
            data.put("id", tx.getId());
            data.put("status", safeString(tx.getStatus()));
            data.put("createdAt", tx.getCreatedAt());
            data.put("escrowReleaseDate", tx.getEscrowReleaseDate());
            data.put("quantity", tx.getQuantity());
            data.put("amount", tx.getAmount());
            data.put("commission", tx.getCommission());
            data.put("coinSeller", tx.getCoinSeller());

            String productName = null, categoryName = null, variantName = null; Long variantPrice = null; Long productId = null, variantId = null;
            if (tx.getProduct() != null) {
                productName = tx.getProduct().getName();
                productId = tx.getProduct().getId();
                if (tx.getProduct().getCategory() != null) {
                    categoryName = tx.getProduct().getCategory().getName();
                }
            }
            if (tx.getVariant() != null) {
                variantName = tx.getVariant().getVariantName();
                variantPrice = tx.getVariant().getPrice();
                variantId = tx.getVariant().getId();
            }
            data.put("productId", productId);
            data.put("productName", productName);
            data.put("categoryName", categoryName);
            data.put("variantId", variantId);
            data.put("variantName", variantName);
            data.put("variantPrice", variantPrice);

            // FIX: Query accounts from ProductVariantAccount instead of using deliveredAccount (always null)
            // Get all accounts associated with this transaction
            try {
                List<com.mmo.entity.ProductVariantAccount> accounts = entityManager
                    .createQuery("SELECT a FROM ProductVariantAccount a WHERE a.transaction.id = :txId AND a.isDelete = false",
                                com.mmo.entity.ProductVariantAccount.class)
                    .setParameter("txId", tx.getId())
                    .getResultList();

                if (accounts != null && !accounts.isEmpty()) {
                    // Build account data string with all accounts
                    StringBuilder accountDataBuilder = new StringBuilder();
                    for (int i = 0; i < accounts.size(); i++) {
                        com.mmo.entity.ProductVariantAccount acc = accounts.get(i);
                        if (i > 0) {
                            accountDataBuilder.append("\n\n");
                            accountDataBuilder.append("═══════════════════════════════════════\n\n");
                        }
                        accountDataBuilder.append("Account #").append(i + 1).append(":\n");
                        accountDataBuilder.append(acc.getAccountData() != null ? acc.getAccountData() : "No data available");
                    }
                    data.put("deliveredAccountData", accountDataBuilder.toString());
                } else {
                    data.put("deliveredAccountData", "No account data available");
                }
            } catch (Exception e) {
                data.put("deliveredAccountData", "Error loading account data: " + e.getMessage());
            }

            String status = safeString(tx.getStatus());
            String statusExplain;
            switch (status.toUpperCase()) {
                case "ESCROW" -> statusExplain = "Funds are held in escrow for 3 days to ensure transaction security.";
                case "DISPUTED" -> statusExplain = "Funds are frozen due to customer dispute. Please contact support.";
                case "COMPLETED" -> statusExplain = "Funds have been added to your wallet (coins).";
                case "CANCELLED", "REFUNDED" -> statusExplain = "Transaction has been cancelled or refunded.";
                default -> statusExplain = "";
            }
            data.put("statusExplain", statusExplain);

            boolean escrowReleased = false;
            if (tx.getEscrowReleaseDate() != null) {
                escrowReleased = new Date().after(tx.getEscrowReleaseDate());
            }
            data.put("escrowReleased", escrowReleased);

            return ResponseEntity.ok(data);
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }

    // NEW: Seller Complaints list page with filters and actions
    @GetMapping("/complaints")
    public String listSellerComplaints(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "status", required = false) Complaint.ComplaintStatus status,
            @RequestParam(name = "type", required = false) Complaint.ComplaintType type,
            Model model,
            RedirectAttributes redirectAttributes) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = null;
        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof UserDetails) {
                email = ((UserDetails) principal).getUsername();
            } else if (principal instanceof OidcUser) {
                email = ((OidcUser) principal).getEmail();
            } else if (principal instanceof OAuth2User) {
                Object mailAttr = ((OAuth2User) principal).getAttributes().get("email");
                if (mailAttr != null) email = mailAttr.toString();
            } else {
                email = authentication.getName();
            }
        }
        if (email == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please login to continue.");
            return "redirect:/login";
        }
        User seller = userRepository.findByEmail(email).orElse(null);
        if (seller == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "User not found.");
            return "redirect:/login";
        }
        boolean activeShop = seller.getShopStatus() != null && seller.getShopStatus().equalsIgnoreCase("Active");
        if (!activeShop) {
            redirectAttributes.addFlashAttribute("errorMessage", "Your shop is not Active. Please complete registration.");
            return "redirect:/seller/register";
        }

        StringBuilder jpql = new StringBuilder(
            "select new com.mmo.dto.SellerComplaintListItem(c.id, c.transactionId, c.complaintType, c.status, c.createdAt, c.updatedAt, c.respondedAt, c.customer.id, c.customer.fullName, c.description) " +
            "from Complaint c where c.seller.id = :sid and c.isDelete = false"
        );
        if (status != null) jpql.append(" and c.status = :status");
        if (type != null) jpql.append(" and c.complaintType = :type");
        jpql.append(" order by c.updatedAt desc");

        var query = entityManager.createQuery(jpql.toString(), SellerComplaintListItem.class)
                .setParameter("sid", seller.getId());
        if (status != null) query.setParameter("status", status);
        if (type != null) query.setParameter("type", type);

        var all = query.getResultList();
        int pageSize = 10;
        int totalItems = all.size();
        int totalPages = (int) Math.ceil((double) totalItems / pageSize);
        page = Math.max(0, Math.min(page, Math.max(totalPages - 1, 0)));
        int start = page * pageSize;
        int end = Math.min(start + pageSize, totalItems);
        var items = all.subList(Math.min(start, end), end);

        model.addAttribute("complaints", items);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalItems", totalItems);
        model.addAttribute("status", status);
        model.addAttribute("type", type);
        return "seller/complaints";
    }

    // NEW: Complaint Detail JSON for modal
    @GetMapping(path = "/complaints/{id}/json", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> getComplaintDetail(@PathVariable("id") Long id, Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }
            String email = authentication.getName();
            if (authentication.getPrincipal() instanceof OidcUser oidc) email = oidc.getEmail();
            else if (authentication.getPrincipal() instanceof OAuth2User ou) {
                Object mailAttr = ou.getAttributes().get("email");
                if (mailAttr != null) email = mailAttr.toString();
            }
            User seller = userRepository.findByEmail(email).orElse(null);
            if (seller == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }
            Complaint c = entityManager.find(Complaint.class, id);
            if (c == null || c.isDelete() || c.getSeller() == null || !c.getSeller().getId().equals(seller.getId())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Not found");
            }
            Map<String, Object> data = new HashMap<>();
            data.put("id", c.getId());
            data.put("transactionId", c.getTransactionId());
            data.put("type", c.getComplaintType());
            data.put("status", c.getStatus());
            data.put("description", c.getDescription());
            data.put("evidence", c.getEvidence());
            data.put("createdAt", c.getCreatedAt());
            data.put("updatedAt", c.getUpdatedAt());
            data.put("respondedAt", c.getRespondedAt());
            if (c.getCustomer() != null) {
                Map<String, Object> cus = new HashMap<>();
                cus.put("id", c.getCustomer().getId());
                cus.put("name", c.getCustomer().getFullName());
                data.put("customer", cus);
            }
            if (c.getAdminHandler() != null) {
                Map<String, Object> admin = new HashMap<>();
                admin.put("id", c.getAdminHandler().getId());
                admin.put("name", c.getAdminHandler().getFullName());
                data.put("adminHandler", admin);
            }
            data.put("sellerFinalResponse", c.getSellerFinalResponse());
            data.put("adminDecisionNotes", c.getAdminDecisionNotes());
            return ResponseEntity.ok(data);
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }

    // NEW: Seller responds to complaint (approve/reject)
    @PostMapping(path = "/complaints/{id}/respond", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @Transactional
    public ResponseEntity<?> respondToComplaint(@PathVariable("id") Long id,
                                                @RequestBody Map<String, String> request,
                                                Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }
            String email = authentication.getName();
            if (authentication.getPrincipal() instanceof OidcUser oidc) email = oidc.getEmail();
            else if (authentication.getPrincipal() instanceof OAuth2User ou) {
                Object mailAttr = ou.getAttributes().get("email");
                if (mailAttr != null) email = mailAttr.toString();
            }
            User seller = userRepository.findByEmail(email).orElse(null);
            if (seller == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }

            Complaint complaint = entityManager.find(Complaint.class, id);
            if (complaint == null || complaint.isDelete() || complaint.getSeller() == null || !complaint.getSeller().getId().equals(seller.getId())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Complaint not found");
            }

            String action = request.get("action");
            String reason = request.get("reason");

            if (action == null || (!action.equals("APPROVE") && !action.equals("REJECT"))) {
                return ResponseEntity.badRequest().body("Invalid action. Must be APPROVE or REJECT");
            }

            if (reason == null || reason.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Reason is required");
            }

            if (reason.trim().length() < 10) {
                return ResponseEntity.badRequest().body("Reason must be at least 10 characters");
            }

            // Check if already responded
            if (complaint.getSellerFinalResponse() != null && !complaint.getSellerFinalResponse().isEmpty()) {
                return ResponseEntity.badRequest().body("You have already responded to this complaint");
            }

            // Save seller response
            String response = action + ": " + reason.trim();
            complaint.setSellerFinalResponse(response);
            complaint.setRespondedAt(new Date()); // Record when seller responded
            complaint.setUpdatedAt(new Date());

            // Update status based on action
            if (action.equals("APPROVE")) {
                // Seller accepts the complaint - move to pending confirmation or resolved
                if (complaint.getStatus() == Complaint.ComplaintStatus.NEW) {
                    complaint.setStatus(Complaint.ComplaintStatus.IN_PROGRESS);
                }
            } else if (action.equals("REJECT")) {
                // Seller rejects the complaint - move to pending confirmation
                if (complaint.getStatus() == Complaint.ComplaintStatus.NEW) {
                    complaint.setStatus(Complaint.ComplaintStatus.PENDING_CONFIRMATION);
                }
            }

            entityManager.merge(complaint);

            // Create notification for customer
            try {
                String notifTitle = action.equals("APPROVE") ? "Seller Approved Your Complaint" : "Seller Responded to Your Complaint";
                String notifMessage = "The seller has responded to your complaint #" + complaint.getId() +
                                     " for transaction #" + complaint.getTransactionId() + ". Please check the details.";
                notificationService.createNotificationForUser(complaint.getCustomer().getId(), notifTitle, notifMessage);
            } catch (Exception e) {
                // Log but don't fail
                System.err.println("Failed to create notification: " + e.getMessage());
            }

            return ResponseEntity.ok("Response submitted successfully");
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }

    /**
     * Seller requests admin support (escalate complaint)
     */
    @PostMapping(path = "/complaints/{id}/escalate", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @Transactional
    public ResponseEntity<?> escalateComplaintSeller(@PathVariable("id") Long id,
                                                     @RequestBody Map<String, String> request,
                                                     Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }
            String email = authentication.getName();
            if (authentication.getPrincipal() instanceof OidcUser oidc) email = oidc.getEmail();
            else if (authentication.getPrincipal() instanceof OAuth2User ou) {
                Object mailAttr = ou.getAttributes().get("email");
                if (mailAttr != null) email = mailAttr.toString();
            }
            User seller = userRepository.findByEmail(email).orElse(null);
            if (seller == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }

            String reason = request.get("reason");
            if (reason == null || reason.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Reason is required");
            }

            complaintService.escalateToAdmin(id, seller.getId(), reason, true);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Complaint escalated to admin successfully. Admin team will review and respond within 3-5 business days."
            ));
        } catch (Exception ex) {
            return ResponseEntity.status(400).body(Map.of(
                "success", false,
                "error", ex.getMessage()
            ));
        }
    }

    // Helpers to tolerate different SellerBankInfo mappings without adding new dependencies
    private Long tryResolveOwnerId(SellerBankInfo bankInfo) {
        try {
            Object owner = bankInfo.getClass().getMethod("getSeller").invoke(bankInfo);
            if (owner != null) {
                Object id = owner.getClass().getMethod("getId").invoke(owner);
                return id instanceof Long ? (Long) id : null;
            }
        } catch (Exception ignored) {
        }
        try {
            Object owner = bankInfo.getClass().getMethod("getUser").invoke(bankInfo);
            if (owner != null) {
                Object id = owner.getClass().getMethod("getId").invoke(owner);
                return id instanceof Long ? (Long) id : null;
            }
        } catch (Exception ignored) {
        }
        try {
            Object id = bankInfo.getClass().getMethod("getUserId").invoke(bankInfo);
            return id instanceof Long ? (Long) id : null;
        } catch (Exception ignored) {
        }
        return null;
    }

    private void tryCopyBankDisplayFields(SellerBankInfo bankInfo, Withdrawal wd) {
        try {
            Object bankName = bankInfo.getClass().getMethod("getBankName").invoke(bankInfo);
            if (bankName instanceof String s) wd.setBankName(s);
        } catch (Exception ignored) {
        }
        try {
            Object acc = bankInfo.getClass().getMethod("getAccountNumber").invoke(bankInfo);
            if (acc instanceof String s) wd.setAccountNumber(s);
        } catch (Exception ignored) {
        }
        try {
            Object br = bankInfo.getClass().getMethod("getBranch").invoke(bankInfo);
            if (br instanceof String s) wd.setBranch(s);
        } catch (Exception ignored) {
        }
        // Try to copy account holder / beneficiary name using several possible getter names
        try {
            String[] possibleGetters = new String[]{"getAccountHolder", "getAccountName", "getHolderName", "getBeneficiaryName", "getOwnerName"};
            for (String g : possibleGetters) {
                try {
                    Object name = bankInfo.getClass().getMethod(g).invoke(bankInfo);
                    if (name instanceof String s && s != null && !s.isBlank()) {
                        wd.setAccountName(s);
                        break;
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    // Helper: find SellerBankInfo by owner using tolerant queries
    private SellerBankInfo findBankInfoForOwner(User owner) {
        Long uid = owner.getId();
        String[] possibleFields = {"seller.id", "user.id", "userId"};

        for (String field : possibleFields) {
            try {
                String hql = "select b from SellerBankInfo b where b." + field + " = :uid";
                return entityManager.createQuery(hql, SellerBankInfo.class)
                        .setParameter("uid", uid)
                        .setMaxResults(1)
                        .getSingleResult();
            } catch (Exception ignored) {
                // This query failed, try the next one
            }
        }
        return null;
    }

    // Helper: set owner relation via reflection
    private void setOwner(SellerBankInfo bankInfo, User owner) {
        try {
            bankInfo.getClass().getMethod("setSeller", User.class).invoke(bankInfo, owner);
            return;
        } catch (Exception ignored) {
        }
        try {
            bankInfo.getClass().getMethod("setUser", User.class).invoke(bankInfo, owner);
            return;
        } catch (Exception ignored) {
        }
        try {
            bankInfo.getClass().getMethod("setUserId", Long.class).invoke(bankInfo, owner.getId());
        } catch (Exception ignored) {
        }
    }

    private Long tryGetId(SellerBankInfo bankInfo) {
        try {
            Object id = bankInfo.getClass().getMethod("getId").invoke(bankInfo);
            if (id instanceof Long l) return l;
        } catch (Exception ignored) {
        }
        return null;
    }

    private boolean trySetString(Object target, String setterName, String value) {
        try {
            target.getClass().getMethod(setterName, String.class).invoke(target, value);
            return true;
        } catch (Exception ignored) {
        }
        return false;
    }

    private String tryGetString(Object target, String getterName) {
        try {
            Object v = target.getClass().getMethod(getterName).invoke(target);
            return v != null ? v.toString() : null;
        } catch (Exception ignored) {
        }
        return null;
    }

    private String firstNonBlank(String... vals) {
        if (vals == null) return null;
        for (String s : vals) {
            if (s != null && !s.isBlank()) return s;
        }
        return null;
    }

    // Danh sách tất cả bank info của user
    @GetMapping("/bank-infos")
    public String listBankInfos(Model model, RedirectAttributes redirectAttributes) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = null;
        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof UserDetails) {
                email = ((UserDetails) principal).getUsername();
            } else if (principal instanceof OidcUser) {
                email = ((OidcUser) principal).getEmail();
            } else if (principal instanceof OAuth2User) {
                Object mailAttr = ((OAuth2User) principal).getAttributes().get("email");
                if (mailAttr != null) email = mailAttr.toString();
            } else {
                email = authentication.getName();
            }
        }
        if (email == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please login to continue.");
            return "redirect:/login";
        }
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "User not found.");
            return "redirect:/login";
        }
        // Lấy tất cả bank info của user
        var bankInfos = entityManager.createQuery(
                        "SELECT b FROM SellerBankInfo b WHERE b.user = :user AND b.isDelete = false", SellerBankInfo.class)
                .setParameter("user", user)
                .getResultList();
        model.addAttribute("bankInfos", bankInfos);
        return "seller/bank-infos";
    }

    // Xem chi tiết 1 bank info
    @GetMapping("/bank-info/{id}")
    public String viewBankInfo(@PathVariable("id") Long id, Model model, RedirectAttributes redirectAttributes) {
        SellerBankInfo bankInfo = entityManager.find(SellerBankInfo.class, id);
        if (bankInfo == null || bankInfo.isDelete()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Bank info not found.");
            return "redirect:/seller/bank-infos";
        }
        model.addAttribute("bankInfo", bankInfo);
        return "seller/bank-info-detail";
    }

    // Sửa bank info
    @PostMapping("/bank-info/{id}/edit")
    @Transactional
    public String editBankInfo(@PathVariable("id") Long id,
                               @RequestParam("bankName") String bankName,
                               @RequestParam("accountNumber") String accountNumber,
                               @RequestParam(value = "accountHolder", required = false) String accountHolder,
                               @RequestParam(value = "branch", required = false) String branch,
                               RedirectAttributes redirectAttributes) {
        SellerBankInfo bankInfo = entityManager.find(SellerBankInfo.class, id);
        if (bankInfo == null || bankInfo.isDelete()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Bank info not found.");
            return "redirect:/seller/withdraw-money";
        }
        bankInfo.setBankName(bankName);
        bankInfo.setAccountNumber(accountNumber);
        bankInfo.setAccountHolder(accountHolder);
        bankInfo.setBranch(branch);
        entityManager.merge(bankInfo);
        redirectAttributes.addFlashAttribute("successMessage", "Bank info updated successfully.");
        return "redirect:/seller/withdraw-money";
    }

    // Xóa bank info (soft delete)
    @PostMapping("/bank-info/{id}/delete")
    @Transactional
    public String deleteBankInfo(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        SellerBankInfo bankInfo = entityManager.find(SellerBankInfo.class, id);
        if (bankInfo == null || bankInfo.isDelete()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Bank info not found.");
            return "redirect:/seller/withdraw-money";
        }
        bankInfo.setDelete(true);
        entityManager.merge(bankInfo);
        redirectAttributes.addFlashAttribute("successMessage", "Bank info deleted successfully.");
        return "redirect:/seller/withdraw-money";
    }

    // Thêm mới bank info
    @PostMapping("/bank-info/add")
    @Transactional
    public String addBankInfo(@RequestParam("bankName") String bankName,
                              @RequestParam("accountNumber") String accountNumber,
                              @RequestParam(value = "accountHolder", required = false) String accountHolder,
                              @RequestParam(value = "branch", required = false) String branch,
                              RedirectAttributes redirectAttributes) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String email = null;
            if (authentication != null && authentication.isAuthenticated()) {
                Object principal = authentication.getPrincipal();
                if (principal instanceof UserDetails) {
                    email = ((UserDetails) principal).getUsername();
                } else if (principal instanceof OidcUser) {
                    email = ((OidcUser) principal).getEmail();
                } else if (principal instanceof OAuth2User) {
                    Object mailAttr = ((OAuth2User) principal).getAttributes().get("email");
                    if (mailAttr != null) email = mailAttr.toString();
                } else {
                    email = authentication.getName();
                }
            }
            if (email == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Please login to continue.");
                return "redirect:/login";
            }
            User user = userRepository.findByEmail(email).orElse(null);
            if (user == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "User not found.");
                return "redirect:/login";
            }
            SellerBankInfo bankInfo = new SellerBankInfo();
            bankInfo.setUser(user);
            bankInfo.setBankName(bankName);
            bankInfo.setAccountNumber(accountNumber);
            bankInfo.setAccountHolder(accountHolder);
            bankInfo.setBranch(branch);
            bankInfo.setDelete(false);
            entityManager.persist(bankInfo);
            redirectAttributes.addFlashAttribute("successMessage", "Bank info added successfully.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to add bank info: " + ex.getMessage());
        }
        return "redirect:/seller/withdraw-money";
    }

    // Helper DTO for supported bank options (matches template usage: b.displayName)
    private static class BankOption {
        private final String displayName;
        BankOption(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }

    @PostMapping("/withdrawals/{id}/update-bank")
    @Transactional
    public String updateWithdrawalBankInfo(@PathVariable Long id,
                                           @RequestParam String bankName,
                                           @RequestParam String accountNumber,
                                           @RequestParam(name = "accountHolder") String accountHolder,
                                           @RequestParam(name = "branch", required = false) String branch,
                                           Authentication authentication,
                                           RedirectAttributes redirectAttributes) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Unauthorized");
                return "redirect:/seller/withdraw-money";
            }
            String email = authentication.getName();
            if (authentication.getPrincipal() instanceof org.springframework.security.oauth2.core.oidc.user.OidcUser oidc) email = oidc.getEmail();
            else if (authentication.getPrincipal() instanceof org.springframework.security.oauth2.core.user.OAuth2User ou) {
                Object mailAttr = ou.getAttributes().get("email");
                if (mailAttr != null) email = mailAttr.toString();
            }
            User user = userRepository.findByEmailAndIsDelete(email, false);
            if (user == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Unauthorized");
                return "redirect:/seller/withdraw-money";
            }
            Withdrawal withdrawal = entityManager.find(Withdrawal.class, id);
            if (withdrawal == null || withdrawal.getSeller() == null || !withdrawal.getSeller().getId().equals(user.getId())) {
                redirectAttributes.addFlashAttribute("errorMessage", "Withdrawal not found.");
                return "redirect:/seller/withdraw-money";
            }
            // Only allow when Pending and within 24 hours since createdAt
            Date createdAt = withdrawal.getCreatedAt();
            if (createdAt == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "This withdrawal cannot be edited at this time.");
                return "redirect:/seller/withdraw-money";
            }
            long diffMs = System.currentTimeMillis() - createdAt.getTime();
            boolean within24h = diffMs <= 24L * 60L * 60L * 1000L;
//            boolean within24h = diffMs <= 2L * 60L * 1000L;
            String status = withdrawal.getStatus();
            boolean isPending = status != null && status.equalsIgnoreCase("Pending");
            if (!isPending || !within24h) {
                redirectAttributes.addFlashAttribute("errorMessage", "You can only update bank info within 24h for pending withdrawals.");
                return "redirect:/seller/withdraw-money";
            }
            // Normalize and validate non-blank fields
            String bn = bankName == null ? null : bankName.trim();
            String an = accountNumber == null ? null : accountNumber.trim();
            String ah = accountHolder == null ? null : accountHolder.trim();
            String br = branch == null ? null : branch.trim();
            if (bn == null || bn.isEmpty() || an == null || an.isEmpty() || ah == null || ah.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Bank Name, Account Number, and Account Holder cannot be empty.");
                return "redirect:/seller/withdraw-money";
            }
            // Update allowed fields only
            withdrawal.setBankName(bn);
            withdrawal.setAccountNumber(an);
            withdrawal.setAccountName(ah);
            if (br != null) {
                withdrawal.setBranch(br);
            }
            entityManager.merge(withdrawal);
            redirectAttributes.addFlashAttribute("successMessage", "Bank info updated successfully.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to update bank info: " + ex.getMessage());
        }
        return "redirect:/seller/withdraw-money";
    }

    // NEW: Seller creates a withdrawal request (JSON payload)
    @PostMapping(path = "/withdrawals/{id}/update-bank", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @Transactional
    public ResponseEntity<?> updateWithdrawalBankInfoJson(@PathVariable Long id,
                                                          @RequestBody Map<String, Object> body,
                                                          Authentication authentication,
                                                          HttpSession session,
                                                          HttpServletRequest request) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }
            String email = authentication.getName();
            if (authentication.getPrincipal() instanceof org.springframework.security.oauth2.core.oidc.user.OidcUser oidc) email = oidc.getEmail();
            else if (authentication.getPrincipal() instanceof org.springframework.security.oauth2.core.user.OAuth2User ou) {
                Object mailAttr = ou.getAttributes().get("email");
                if (mailAttr != null) email = mailAttr.toString();
            }
            User user = userRepository.findByEmailAndIsDelete(email, false);
            if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");

            Withdrawal withdrawal = entityManager.find(Withdrawal.class, id);
            if (withdrawal == null || withdrawal.getSeller() == null || !withdrawal.getSeller().getId().equals(user.getId())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Withdrawal not found");
            }
            Date createdAt = withdrawal.getCreatedAt();
            if (createdAt == null) return ResponseEntity.badRequest().body("This withdrawal cannot be edited at this time.");
            long diffMs = System.currentTimeMillis() - createdAt.getTime();
            boolean within24h = diffMs <= 24L * 60L * 60L * 1000L;
            String status = withdrawal.getStatus();
            boolean isPending = status != null && status.equalsIgnoreCase("Pending");
            if (!isPending || !within24h) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("You can only update bank info within 24h for pending withdrawals.");
            }

            String bankName = body.get("bankName") != null ? body.get("bankName").toString() : null;
            String accountNumber = body.get("accountNumber") != null ? body.get("accountNumber").toString() : null;
            String accountHolder = body.get("accountHolder") != null ? body.get("accountHolder").toString() : null;
            String branch = body.get("branch") != null ? body.get("branch").toString() : null;
            String otp = body.get("otp") != null ? body.get("otp").toString() : null;
            if (bankName == null || bankName.isBlank() || accountNumber == null || accountNumber.isBlank() || accountHolder == null || accountHolder.isBlank()) {
                return ResponseEntity.badRequest().body("bankName, accountNumber, and accountHolder are required");
            }
            // Require and verify OTP (6 digits)
            if (otp == null || !otp.matches("\\d{6}")) {
                return handleOtpFailForWithdraw(session, request, authentication, user, user.getId(), "withdraw_update", "MSG_OTP_REQUIRED: Please enter the 6-digit OTP sent to your email.");
            }
            // Lookup latest unused OTP for this user/code
            var optVerification = emailVerificationRepository.findTopByUserAndVerificationCodeAndIsUsedFalseOrderByCreatedAtDesc(user, otp);
            if (optVerification.isEmpty()) {
                return handleOtpFailForWithdraw(session, request, authentication, user, user.getId(), "withdraw_update", "MSG_OTP_INVALID: Code not found or already used.");
            }
            var verification = optVerification.get();
            if (verification.getExpiryDate() == null || verification.getExpiryDate().before(new Date())) {
                return handleOtpFailForWithdraw(session, request, authentication, user, user.getId(), "withdraw_update", "MSG_OTP_EXPIRED: The code has expired.");
            }

            // Build old/new info for email
            String oldInfo = String.format("%s - %s (%s)%s",
                    safeString(withdrawal.getBankName()),
                    safeString(withdrawal.getAccountNumber()),
                    safeString(withdrawal.getAccountName()),
                    withdrawal.getBranch() != null && !withdrawal.getBranch().isBlank() ? (" - " + withdrawal.getBranch()) : "");
            String newInfo = String.format("%s - %s (%s)%s",
                    bankName.trim(),
                    accountNumber.trim(),
                    accountHolder.trim(),
                    branch != null && !branch.isBlank() ? (" - " + branch.trim()) : "");

            // Update allowed fields only
            withdrawal.setBankName(bankName.trim());
            withdrawal.setAccountNumber(accountNumber.trim());
            withdrawal.setAccountName(accountHolder.trim());
            if (branch != null) {
                withdrawal.setBranch(branch.trim());
            }
            entityManager.merge(withdrawal);

            // Mark OTP as used
            verification.setUsed(true);
            emailVerificationRepository.save(verification);

            // Clear attempts on success
            clearSellerOtpAttempts(session, user.getId(), "withdraw_update");

            // Send async email notify
            try {
                String userName = user.getFullName() != null ? user.getFullName() : (user.getEmail() != null ? user.getEmail() : "User");
                String updatedAt = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date());
                String html = EmailTemplate.withdrawalBankInfoUpdatedEmail(userName, oldInfo, newInfo, updatedAt);
                String subject = "[MMOMarket] Cập nhật thông tin ngân hàng rút tiền";
                if (user.getEmail() != null && !user.getEmail().isBlank()) {
                    emailService.sendEmailAsync(user.getEmail(), subject, html);
                }
            } catch (Exception ignored) {}

            Map<String, Object> res = new HashMap<>();
            res.put("message", "Bank info updated successfully");
            res.put("id", withdrawal.getId());
            res.put("bankName", withdrawal.getBankName());
            res.put("accountNumber", withdrawal.getAccountNumber());
            res.put("accountHolder", withdrawal.getAccountName());
            res.put("branch", withdrawal.getBranch());
            return ResponseEntity.ok(res);
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }

    // NEW: Send OTP for shop deletion verification
    @PostMapping(path = "/delete-shop/send-otp")
    @ResponseBody
    public ResponseEntity<?> sendDeleteShopOtp(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        String email = authentication.getName();
        if (authentication.getPrincipal() instanceof OidcUser oidc) email = oidc.getEmail();
        else if (authentication.getPrincipal() instanceof OAuth2User ou) {
            Object mailAttr = ou.getAttributes().get("email");
            if (mailAttr != null) email = mailAttr.toString();
        }
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            return ResponseEntity.badRequest().body("User has no email configured. Cannot send OTP.");
        }

        // Check if shop is active
        String shopStatus = user.getShopStatus();
        boolean active = shopStatus != null && shopStatus.equalsIgnoreCase("Active");
        if (!active) {
            return ResponseEntity.badRequest().body("Your shop is not in Active status.");
        }

        // Cooldown: avoid spamming OTP sends (min 60 seconds between sends)
        try {
            Optional<com.mmo.entity.EmailVerification> latest = emailVerificationRepository.findTopByUserOrderByCreatedAtDesc(user);
            if (latest.isPresent() && latest.get().getCreatedAt() != null) {
                long seconds = (System.currentTimeMillis() - latest.get().getCreatedAt().getTime()) / 1000L;
                if (seconds < 60) {
                    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("Please wait " + (60 - seconds) + "s before requesting a new OTP.");
                }
            }
        } catch (Exception ignored) {}

        // Offload OTP creation + persistence + email to executor; return immediately
        try {
            User target = user;
            emailExecutor.execute(() -> {
                try {
                    String code = authService.generateVerificationCode();
                    com.mmo.entity.EmailVerification verification = new com.mmo.entity.EmailVerification();
                    verification.setUser(target);
                    verification.setVerificationCode(code);
                    verification.setExpiryDate(new Date(System.currentTimeMillis() + 5 * 60 * 1000)); // 5 minutes expiry
                    verification.setUsed(false);
                    emailVerificationRepository.save(verification);
                    String subject = "[MMOMarket] Confirm Shop Cancellation (OTP)";
                    String html = EmailTemplate.deleteShopOtpEmail(code);
                    emailService.sendEmailAsync(target.getEmail(), subject, html);
                } catch (Exception ignored) { }
            });
        } catch (Exception ignored) { }

        return ResponseEntity.ok("OTP has been sent to your email.");
    }

    // NEW: Check delete-shop conditions before showing OTP modal
    @GetMapping(path = "/delete-shop/check")
    @ResponseBody
    public ResponseEntity<?> checkDeleteShopConditions(Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("ok", false, "message", "Unauthorized"));
            }

            String email = authentication.getName();
            if (authentication.getPrincipal() instanceof OidcUser oidc) email = oidc.getEmail();
            else if (authentication.getPrincipal() instanceof OAuth2User ou) {
                Object mailAttr = ou.getAttributes().get("email");
                if (mailAttr != null) email = mailAttr.toString();
            }

            User user = userRepository.findByEmail(email).orElse(null);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("ok", false, "message", "Unauthorized"));
            }

            // Check if shop is active
            String shopStatus = user.getShopStatus();
            boolean active = shopStatus != null && shopStatus.equalsIgnoreCase("Active");
            if (!active) {
                return ResponseEntity.ok(Map.of("ok", false, "message", "Your shop is not in Active status."));
            }

            // Business rule a.2: Coins must be 0
            long coins = user.getCoins() == null ? 0L : user.getCoins();
            if (coins != 0L) {
                return ResponseEntity.ok(Map.of("ok", false, "message", "You must withdraw all your Coins balance to 0 before deleting your shop."));
            }

            // Business rule a.1: No listed products
            Long remainingProducts = entityManager.createQuery(
                    "SELECT COUNT(p) FROM Product p WHERE p.seller.id = :sellerId AND p.isDelete = false", Long.class)
                .setParameter("sellerId", user.getId())
                .getSingleResult();
            if (remainingProducts != null && remainingProducts > 0) {
                return ResponseEntity.ok(Map.of("ok", false, "message", "You must remove all listed products before deleting your shop."));
            }

            // Business rule a.3: Not temporarily locked or unresolved policy violation
            if (shopStatus.equalsIgnoreCase("Suspended") || shopStatus.equalsIgnoreCase("Banned") || shopStatus.equalsIgnoreCase("Locked")) {
                return ResponseEntity.ok(Map.of("ok", false, "message", "Your account is temporarily locked or has policy violations. Cannot delete shop."));
            }

            // All conditions met
            return ResponseEntity.ok(Map.of("ok", true, "message", "All conditions met. You can proceed."));
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(Map.of("ok", false, "message", "Internal error: " + ex.getMessage()));
        }
    }

    // NEW: Delete shop with OTP verification (JSON endpoint)
    @PostMapping(path = "/delete-shop", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @Transactional
    public ResponseEntity<?> deleteShopWithOtp(@RequestBody Map<String, String> payload,
                                               Authentication authentication,
                                               HttpSession session,
                                               HttpServletRequest request) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }

            String email = authentication.getName();
            if (authentication.getPrincipal() instanceof OidcUser oidc) email = oidc.getEmail();
            else if (authentication.getPrincipal() instanceof OAuth2User ou) {
                Object mailAttr = ou.getAttributes().get("email");
                if (mailAttr != null) email = mailAttr.toString();
            }

            User user = userRepository.findByEmail(email).orElse(null);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }

            // Must have an active shop to delete
            String shopStatus = user.getShopStatus();
            boolean active = shopStatus != null && shopStatus.equalsIgnoreCase("Active");
            if (!active) {
                return ResponseEntity.badRequest().body("Your shop is not in Active status.");
            }

            // Validate OTP
            String otp = payload != null ? payload.get("otp") : null;
            if (otp == null || !otp.matches("\\d{6}")) {
                return handleOtpFailForWithdraw(session, request, authentication, user, user.getId(), "delete_shop", "MSG_OTP_REQUIRED");
            }

            // Verify OTP
            var optVerification = emailVerificationRepository.findTopByUserAndVerificationCodeAndIsUsedFalseOrderByCreatedAtDesc(user, otp);
            if (optVerification.isEmpty()) {
                return handleOtpFailForWithdraw(session, request, authentication, user, user.getId(), "delete_shop", "MSG_OTP_INVALID");
            }
            var verification = optVerification.get();
            if (verification.getExpiryDate() == null || verification.getExpiryDate().before(new Date())) {
                return handleOtpFailForWithdraw(session, request, authentication, user, user.getId(), "delete_shop", "MSG_OTP_EXPIRED");
            }

            // Mark OTP as used
            verification.setUsed(true);
            emailVerificationRepository.save(verification);

            // Clear OTP attempts
            clearSellerOtpAttempts(session, user.getId(), "delete_shop");

            // Business rule a.2: Coins must be 0
            long coins = user.getCoins() == null ? 0L : user.getCoins();
            if (coins != 0L) {
                return ResponseEntity.badRequest().body("You must withdraw all your Coins balance to 0 before deleting your shop.");
            }

            // Business rule a.1: No listed products — ensure no non-deleted products remain
            Long remainingProducts = entityManager.createQuery(
                    "SELECT COUNT(p) FROM Product p WHERE p.seller.id = :sellerId AND p.isDelete = false", Long.class)
                .setParameter("sellerId", user.getId())
                .getSingleResult();
            if (remainingProducts != null && remainingProducts > 0) {
                return ResponseEntity.badRequest().body("You must remove all listed products before deleting your shop.");
            }

            // Business rule a.3: Not temporarily locked or unresolved policy violation
            if (shopStatus != null && (shopStatus.equalsIgnoreCase("Suspended") || shopStatus.equalsIgnoreCase("Banned") || shopStatus.equalsIgnoreCase("Locked"))) {
                return ResponseEntity.badRequest().body("Your account is temporarily locked or has policy violations. Cannot delete shop.");
            }

            // All conditions satisfied -> perform soft delete cascade (immediate and irreversible)
            Long uid = user.getId();
            Long sellerId = user.getId();

            // Soft delete delivered accounts/inventory first to avoid FK constraints
            entityManager.createQuery(
                    "UPDATE ProductVariantAccount a SET a.isDelete = true, a.deletedBy = :uid " +
                            "WHERE a.isDelete = false AND a.variant.id IN (SELECT v.id FROM ProductVariant v WHERE v.product.seller.id = :sellerId)"
            ).setParameter("uid", uid).setParameter("sellerId", sellerId).executeUpdate();

            // Soft delete reviews of seller's products
            entityManager.createQuery(
                    "UPDATE Review r SET r.isDelete = true, r.deletedBy = :uid WHERE r.isDelete = false AND r.product.seller.id = :sellerId"
            ).setParameter("uid", uid).setParameter("sellerId", sellerId).executeUpdate();

            // Soft delete transactions of this seller
            entityManager.createQuery(
                    "UPDATE Transaction t SET t.isDelete = true, t.deletedBy = :uid WHERE t.isDelete = false AND t.seller.id = :sellerId"
            ).setParameter("uid", uid).setParameter("sellerId", sellerId).executeUpdate();

            // Soft delete product variants
            entityManager.createQuery(
                    "UPDATE ProductVariant v SET v.isDelete = true, v.deletedBy = :uid WHERE v.isDelete = false AND v.product.seller.id = :sellerId"
            ).setParameter("uid", uid).setParameter("sellerId", sellerId).executeUpdate();

            // Soft delete products
            entityManager.createQuery(
                    "UPDATE Product p SET p.isDelete = true, p.deletedBy = :uid WHERE p.isDelete = false AND p.seller.id = :sellerId"
            ).setParameter("uid", uid).setParameter("sellerId", sellerId).executeUpdate();

            // Soft delete seller bank info
            entityManager.createQuery(
                    "UPDATE SellerBankInfo s SET s.isDelete = true, s.deletedBy = :uid WHERE s.isDelete = false AND s.user.id = :sellerId"
            ).setParameter("uid", uid).setParameter("sellerId", sellerId).executeUpdate();

            // Soft delete ShopInfo - Note: ShopInfo.deletedBy is a User entity, not Long
            entityManager.createQuery(
                    "UPDATE ShopInfo s SET s.isDelete = true, s.deletedBy = :userEntity WHERE s.isDelete = false AND s.user.id = :sellerId"
            ).setParameter("userEntity", user).setParameter("sellerId", sellerId).executeUpdate();

            // Set shopStatus back to Inactive
            try {
                user.setShopStatus("Inactive");
                userRepository.save(user);
            } catch (Exception ignored) {}

            // Create notification for user
            try {
                notificationService.createNotificationForUser(
                    user.getId(),
                    "Shop Registration Cancelled",
                    "Your shop has been successfully cancelled. You can register a new shop again at any time."
                );
            } catch (Exception e) {
                // Log error but don't fail the operation
                System.err.println("Failed to create notification: " + e.getMessage());
            }

            return ResponseEntity.ok("Shop has been successfully cancelled.");
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }

    // NEW: Buy Points - send OTP
    @PostMapping(path = "/buy-points/send-otp")
    @ResponseBody
    public ResponseEntity<?> sendBuyPointsOtp(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        String email = authentication.getName();
        if (authentication.getPrincipal() instanceof OidcUser oidc) email = oidc.getEmail();
        else if (authentication.getPrincipal() instanceof OAuth2User ou) {
            Object mailAttr = ou.getAttributes().get("email");
            if (mailAttr != null) email = mailAttr.toString();
        }
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            return ResponseEntity.badRequest().body("User has no email configured. Cannot send OTP.");
        }
        // Cooldown
        try {
            Optional<com.mmo.entity.EmailVerification> latest = emailVerificationRepository.findTopByUserOrderByCreatedAtDesc(user);
            if (latest.isPresent() && latest.get().getCreatedAt() != null) {
                long seconds = (System.currentTimeMillis() - latest.get().getCreatedAt().getTime()) / 1000L;
                if (seconds < 60) {
                    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("Please wait " + (60 - seconds) + "s before requesting a new OTP.");
                }
            }
        } catch (Exception ignored) {}

        try {
            User target = user;
            emailExecutor.execute(() -> {
                try {
                    String code = authService.generateVerificationCode();
                    com.mmo.entity.EmailVerification verification = new com.mmo.entity.EmailVerification();
                    verification.setUser(target);
                    verification.setVerificationCode(code);
                    verification.setExpiryDate(new Date(System.currentTimeMillis() + 5 * 60 * 1000));
                    verification.setUsed(false);
                    emailVerificationRepository.save(verification);
                    String subject = "[MMOMarket] Confirm Points Purchase (OTP)";
                    String html = EmailTemplate.buyPointsOtpEmail(code);
                    emailService.sendEmailAsync(target.getEmail(), subject, html);
                } catch (Exception ignored) { }
            });
        } catch (Exception ignored) { }
        return ResponseEntity.ok("OTP has been sent to your email.");
    }

    // NEW: Buy Points - quote how many points/cost to reach a target level
    @GetMapping(path = "/buy-points/quote", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> quoteBuyPoints(@RequestParam(name = "targetLevel", required = false) Integer targetLevel,
                                            Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("ok", false, "message", "Unauthorized"));
        }
        String email = authentication.getName();
        if (authentication.getPrincipal() instanceof OidcUser oidc) email = oidc.getEmail();
        else if (authentication.getPrincipal() instanceof OAuth2User ou) {
            Object mailAttr = ou.getAttributes().get("email");
            if (mailAttr != null) email = mailAttr.toString();
        }
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("ok", false, "message", "Unauthorized"));
        }
        ShopInfo shop = shopInfoRepository.findByUserIdAndIsDeleteFalse(user.getId()).orElseGet(() -> shopInfoRepository.findByUser_Id(user.getId()).orElse(null));
        if (shop == null) {
            return ResponseEntity.ok(Map.of("ok", false, "message", "Shop not found."));
        }
        long currentPoints = shop.getPoints() == null ? 0L : shop.getPoints();
        short currentLevel = shop.getShopLevel() == null ? 0 : shop.getShopLevel();
        int desired = (targetLevel == null || targetLevel < currentLevel + 1) ? (currentLevel + 1) : Math.min(targetLevel, 7);
        long threshold = levelThreshold(desired);
        if (desired <= currentLevel) {
            return ResponseEntity.ok(Map.of("ok", false, "message", "You already meet or exceed the selected level.", "currentLevel", currentLevel));
        }
        long neededPoints = Math.max(0L, threshold - currentPoints);
        long costCoins = neededPoints; // 1 coin per point
        long coins = user.getCoins() == null ? 0L : user.getCoins();
        boolean enough = coins >= costCoins;
        long shortfall = enough ? 0L : (costCoins - coins);
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "currentLevel", currentLevel,
                "currentPoints", currentPoints,
                "targetLevel", desired,
                "targetThreshold", threshold,
                "neededPoints", neededPoints,
                "costCoins", costCoins,
                "coins", coins,
                "enough", enough,
                "shortfall", shortfall
        ));
    }

    private long levelThreshold(int level) {
        return switch (level) {
            case 1 -> 1_000_000L;
            case 2 -> 3_000_000L;
            case 3 -> 5_000_000L;
            case 4 -> 10_000_000L;
            case 5 -> 20_000_000L;
            case 6 -> 40_000_000L;
            case 7 -> 50_000_000L;
            default -> 0L;
        };
    }

    // NEW: Buy Points - submit purchase request with OTP
    @PostMapping(path = "/buy-points", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @Transactional
    public ResponseEntity<?> buyPoints(@RequestBody Map<String, Object> body,
                                       Authentication authentication,
                                       HttpSession session,
                                       HttpServletRequest request) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }
            String email = authentication.getName();
            if (authentication.getPrincipal() instanceof OidcUser oidc) email = oidc.getEmail();
            else if (authentication.getPrincipal() instanceof OAuth2User ou) {
                Object mailAttr = ou.getAttributes().get("email");
                if (mailAttr != null) email = mailAttr.toString();
            }
            User user = userRepository.findByEmail(email).orElse(null);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }
            ShopInfo shop = shopInfoRepository.findByUserIdAndIsDeleteFalse(user.getId()).orElseGet(() -> shopInfoRepository.findByUser_Id(user.getId()).orElse(null));
            if (shop == null) return ResponseEntity.badRequest().body("Shop not found.");

            String otp = body.get("otp") != null ? body.get("otp").toString() : null;
            if (otp == null || !otp.matches("\\d{6}")) {
                return handleOtpFailForWithdraw(session, request, authentication, user, user.getId(), "buy_points", "MSG_OTP_REQUIRED: Please enter the 6-digit OTP sent to your email.");
            }
            // Pre-validate OTP
            var optVerification = emailVerificationRepository.findTopByUserAndVerificationCodeAndIsUsedFalseOrderByCreatedAtDesc(user, otp);
            if (optVerification.isEmpty()) {
                return handleOtpFailForWithdraw(session, request, authentication, user, user.getId(), "buy_points", "MSG_OTP_INVALID: Code not found or already used.");
            }
            var verification = optVerification.get();
            if (verification.getExpiryDate() == null || verification.getExpiryDate().before(new Date())) {
                return handleOtpFailForWithdraw(session, request, authentication, user, user.getId(), "buy_points", "MSG_OTP_EXPIRED: The code has expired.");
            }

            // Parse pointsToBuy or targetLevel
            Long pointsToBuy = null;
            if (body.get("pointsToBuy") instanceof Number n) {
                pointsToBuy = n.longValue();
            } else if (body.get("targetLevel") instanceof Number lv) {
                int desired = Math.min(7, Math.max(shop.getShopLevel() == null ? 0 : (shop.getShopLevel() + 1), lv.intValue()));
                long threshold = levelThreshold(desired);
                long currentPoints = shop.getPoints() == null ? 0L : shop.getPoints();
                long need = Math.max(0L, threshold - currentPoints);
                pointsToBuy = need;
            }
            if (pointsToBuy == null || pointsToBuy <= 0) {
                return ResponseEntity.badRequest().body("Invalid pointsToBuy.");
            }

            // Pre-check sufficient coins to avoid unnecessary OTP/queue
            long coins = user.getCoins() == null ? 0L : user.getCoins();
            if (coins < pointsToBuy) {
                long shortfall = pointsToBuy - coins;
                return ResponseEntity.badRequest().body("Insufficient coins. Please top up at least " + String.format("%,d", shortfall) + " coins.");
            }

            // Success so far: clear attempts
            clearSellerOtpAttempts(session, user.getId(), "buy_points");

            String dedupeKey = user.getId() + ":" + pointsToBuy + ":" + otp;
            com.mmo.mq.dto.BuyPointsMessage msg = new com.mmo.mq.dto.BuyPointsMessage(
                    user.getId(),
                    pointsToBuy,
                    pointsToBuy, // cost 1:1
                    otp,
                    dedupeKey
            );
            buyPointsPublisher.publish(msg);
            return ResponseEntity.accepted().body("Your points purchase request has been queued. It will be applied shortly.");
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }

    private void sendWithdrawalOtpForUser(User user) {
        try {
            if (user == null || user.getEmail() == null || user.getEmail().isBlank()) return;
            User target = user;
            emailExecutor.execute(() -> {
                try {
                    String code = authService.generateVerificationCode();
                    com.mmo.entity.EmailVerification verification = new com.mmo.entity.EmailVerification();
                    verification.setUser(target);
                    verification.setVerificationCode(code);
                    verification.setExpiryDate(new Date(System.currentTimeMillis() + 5 * 60 * 1000));
                    verification.setUsed(false);
                    emailVerificationRepository.save(verification);
                    String subject = "[MMOMarket] OTP Xác minh rút tiền";
                    String html = EmailTemplate.withdrawalOtpEmail(code);
                    emailService.sendEmailAsync(target.getEmail(), subject, html);
                } catch (Exception ignored) { }
            });
        } catch (Exception ignored) { }
    }

    private void sendSellerRegistrationOtpForUser(User user) {
        try {
            if (user == null || user.getEmail() == null || user.getEmail().isBlank()) return;
            User target = user;
            emailExecutor.execute(() -> {
                try {
                    String code = authService.generateVerificationCode();
                    com.mmo.entity.EmailVerification verification = new com.mmo.entity.EmailVerification();
                    verification.setUser(target);
                    verification.setVerificationCode(code);
                    verification.setExpiryDate(new Date(System.currentTimeMillis() + 5 * 60 * 1000));
                    verification.setUsed(false);
                    emailVerificationRepository.save(verification);
                    String subject = "[MMOMarket] OTP Verify Seller Registration";
                    String html = EmailTemplate.sellerRegistrationOtpEmail(code);
                    emailService.sendEmailAsync(target.getEmail(), subject, html);
                } catch (Exception ignored) { }
            });
        } catch (Exception ignored) { }
    }

    private ResponseEntity<String> handleOtpFailForSellerRegister(HttpSession session, HttpServletRequest request, Authentication authentication, User user, Long uid, String action, String baseMessage) {
        int attempts = incSellerOtpAttempts(session, uid, action);
        if (attempts >= 5) {
            try {
                new SecurityContextLogoutHandler().logout(request, null, authentication);
            } catch (Exception ignored) { }
            try { session.invalidate(); } catch (Exception ignored) { }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Too many OTP failures. You have been logged out. Please sign in again.");
        } else if (attempts >= 3) {
            try {
                sendSellerRegistrationOtpForUser(user);
            } catch (Exception ignored) {}
            String warningMsg = "⚠️ Warning: You have entered an incorrect OTP " + attempts + " times. A new OTP has been sent to your email. Please check your inbox and use the latest OTP.";
            return ResponseEntity.badRequest().body(warningMsg);
        } else {
            return ResponseEntity.badRequest().body(baseMessage + " (Attempt " + attempts + "/5)");
        }
    }
    private void sendDeleteShopOtpForUser(User user) {
        try {
            if (user == null || user.getEmail() == null || user.getEmail().isBlank()) return;
            User target = user;
            emailExecutor.execute(() -> {
                try {
                    String code = authService.generateVerificationCode();
                    com.mmo.entity.EmailVerification verification = new com.mmo.entity.EmailVerification();
                    verification.setUser(target);
                    verification.setVerificationCode(code);
                    verification.setExpiryDate(new Date(System.currentTimeMillis() + 5 * 60 * 1000));
                    verification.setUsed(false);
                    emailVerificationRepository.save(verification);
                    String subject = "[MMOMarket] Confirm Shop Cancellation (OTP)";
                    String html = EmailTemplate.deleteShopOtpEmail(code);
                    emailService.sendEmailAsync(target.getEmail(), subject, html);
                } catch (Exception ignored) { }
            });
        } catch (Exception ignored) { }
    }
    private void sendBuyPointsOtpForUser(User user) {
        try {
            if (user == null || user.getEmail() == null || user.getEmail().isBlank()) return;
            User target = user;
            emailExecutor.execute(() -> {
                try {
                    String code = authService.generateVerificationCode();
                    com.mmo.entity.EmailVerification verification = new com.mmo.entity.EmailVerification();
                    verification.setUser(target);
                    verification.setVerificationCode(code);
                    verification.setExpiryDate(new Date(System.currentTimeMillis() + 5 * 60 * 1000));
                    verification.setUsed(false);
                    emailVerificationRepository.save(verification);
                    String subject = "[MMOMarket] Confirm Points Purchase (OTP)";
                    String html = EmailTemplate.buyPointsOtpEmail(code);
                    emailService.sendEmailAsync(target.getEmail(), subject, html);
                } catch (Exception ignored) { }
            });
        } catch (Exception ignored) { }
    }
    private ResponseEntity<String> handleOtpFailForWithdraw(HttpSession session, HttpServletRequest request, Authentication authentication, User user, Long uid, String action, String baseMessage) {
        int attempts = incSellerOtpAttempts(session, uid, action);
        if (attempts >= 5) {
            try {
                new SecurityContextLogoutHandler().logout(request, null, authentication);
            } catch (Exception ignored) { }
            try { session.invalidate(); } catch (Exception ignored) { }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Too many OTP failures. You have been logged out. Please sign in again.");
        } else if (attempts >= 3) {
            try {
                if (action != null && action.startsWith("withdraw")) {
                    sendWithdrawalOtpForUser(user);
                } else if (action != null && action.startsWith("delete_shop")) {
                    sendDeleteShopOtpForUser(user);
                } else if (action != null && action.startsWith("buy_points")) {
                    sendBuyPointsOtpForUser(user);
                } else {
                    sendWithdrawalOtpForUser(user);
                }
            } catch (Exception ignored) {}
            String warningMsg = "⚠️ Warning: You have entered an incorrect OTP " + attempts + " times. A new OTP has been sent to your email. Please check your inbox and use the latest OTP.";
            return ResponseEntity.badRequest().body(warningMsg);
        } else {
            return ResponseEntity.badRequest().body(baseMessage + " (Attempt " + attempts + "/5)");
        }
    }

    // Helpers for OTP attempt tracking per action
    private String sellerOtpAttemptsKey(Long uid, String action) {
        return "sellerOtpAttempts:" + uid + ":" + action;
    }
    private int incSellerOtpAttempts(HttpSession session, Long uid, String action) {
        String key = sellerOtpAttemptsKey(uid, action);
        Integer cur = (Integer) session.getAttribute(key);
        int next = (cur == null ? 0 : cur) + 1;
        session.setAttribute(key, next);
        return next;
    }
    private void clearSellerOtpAttempts(HttpSession session, Long uid, String action) {
        session.removeAttribute(sellerOtpAttemptsKey(uid, action));
    }
    private static String safeString(Object s) {
        return s == null ? "" : s.toString();
    }

    // ==================== REVIEWS MANAGEMENT ====================
    @GetMapping("/reviews")
    public String sellerReviews(@RequestParam(name = "page", defaultValue = "0") int page,
                                @RequestParam(name = "search", defaultValue = "") String search,
                                @RequestParam(name = "sort", defaultValue = "rating_desc") String sort,
                                Authentication authentication,
                                Model model) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/authen/login";
        }

        User seller = entityManager.createQuery("SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email)", User.class)
                .setParameter("email", authentication.getName())
                .getResultStream()
                .findFirst()
                .orElse(null);

        if (seller == null) {
            return "redirect:/authen/login";
        }

        // Build JPQL query to get reviews for seller's products
        StringBuilder sb = new StringBuilder(
            "SELECT r FROM Review r " +
            "LEFT JOIN FETCH r.product p " +
            "LEFT JOIN FETCH r.user u " +
            "WHERE r.isDelete = false AND p.seller.id = :sellerId"
        );

        if (search != null && !search.isBlank()) {
            sb.append(" AND (LOWER(p.name) LIKE LOWER(:search) OR CAST(p.id AS string) LIKE :search OR CAST(u.id AS string) LIKE :search OR CAST(r.id AS string) LIKE :search)");
        }

        // Determine ordering
        String orderField = "r.rating";
        String orderDir = "DESC";
        if (sort != null) {
            String s = sort.trim().toLowerCase();
            if ("rating_asc".equals(s)) {
                orderField = "r.rating";
                orderDir = "ASC";
            } else if ("rating_desc".equals(s)) {
                orderField = "r.rating";
                orderDir = "DESC";
            } else if ("date_asc".equals(s)) {
                orderField = "r.createdAt";
                orderDir = "ASC";
            } else if ("date_desc".equals(s)) {
                orderField = "r.createdAt";
                orderDir = "DESC";
            }
        }
        sb.append(" ORDER BY ").append(orderField).append(" ").append(orderDir);

        jakarta.persistence.TypedQuery<com.mmo.entity.Review> query = entityManager.createQuery(sb.toString(), com.mmo.entity.Review.class);
        query.setParameter("sellerId", seller.getId());

        if (search != null && !search.isBlank()) {
            query.setParameter("search", "%" + search + "%");
        }

        List<com.mmo.entity.Review> all = query.getResultList();
        int total = all.size();
        int totalPages = (int) Math.ceil((double) total / 10);
        List<com.mmo.entity.Review> pageList = all.stream()
                .skip((long) page * 10)
                .limit(10)
                .toList();

        model.addAttribute("reviews", pageList);
        model.addAttribute("currentPage", page);
        model.addAttribute("currentSearch", search);
        model.addAttribute("currentSort", sort);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageTitle", "Reviews Management");

        return "seller/reviews";
    }
}
