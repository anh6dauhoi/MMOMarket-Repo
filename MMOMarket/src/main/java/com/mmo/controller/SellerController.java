package com.mmo.controller;

import com.mmo.dto.CreateWithdrawalRequest;
import com.mmo.dto.SellerRegistrationForm;
import com.mmo.dto.SellerWithdrawalResponse;
import com.mmo.entity.SellerBankInfo;
import com.mmo.entity.ShopInfo;
import com.mmo.entity.User;
import com.mmo.entity.Withdrawal;
import com.mmo.repository.UserRepository;
import com.mmo.service.EmailService;
import com.mmo.service.NotificationService;
import com.mmo.service.SellerBankInfoService;
import com.mmo.service.SystemConfigurationService;
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

    @PersistenceContext
    private EntityManager entityManager;

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

    @PostMapping("/register")
    @Transactional
    public String registerSeller(@Valid @ModelAttribute("sellerRegistration") SellerRegistrationForm sellerRegistration,
                                 BindingResult bindingResult,
                                 @RequestParam(value = "agree", required = false) Boolean agree,
                                 RedirectAttributes redirectAttributes) {
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
            redirectAttributes.addFlashAttribute("errorMessage", "Please log in to continue.");
            return "redirect:/login";
        }
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "User not found.");
            return "redirect:/login";
        }

        // Check balance and deduct registration fee
        Long currentCoins = user.getCoins() == null ? 0L : user.getCoins();
        if (currentCoins < REGISTRATION_FEE) {
            // Notify the user about insufficient balance
            notificationService.createNotificationForUser(
                    user.getId(),
                    "Seller registration failed",
                    "Insufficient balance. A fee of 200,000 coins is required to activate your seller account."
            );
            redirectAttributes.addFlashAttribute("errorMessage", "Insufficient balance. 200,000 coins are required for seller registration.");
            redirectAttributes.addFlashAttribute("sellerRegistration", sellerRegistration);
            return "redirect:/seller/register";
        }

        // Deduct fee and activate shop
        user.setCoins(currentCoins - REGISTRATION_FEE);
        user.setShopStatus("Active");
        // Optionally grant SELLER role if not present
        try {
            String role = user.getRole();
            if (role == null || !role.equalsIgnoreCase("SELLER")) {
                user.setRole("SELLER");
            }
        } catch (Exception ignored) {}
        userRepository.save(user);

        // Create or update ShopInfo
        ShopInfo shop = entityManager.createQuery("SELECT s FROM ShopInfo s WHERE s.user = :u AND s.isDelete = false", ShopInfo.class)
                .setParameter("u", user)
                .getResultStream().findFirst().orElse(null);
        if (shop == null) {
            shop = new ShopInfo();
            shop.setUser(user);
            shop.setShopName(sellerRegistration.getShopName());
            shop.setDescription(sellerRegistration.getDescription());
            // Set default commission from system configuration
            try {
                BigDecimal defCommission = systemConfigurationService != null ? systemConfigurationService.getDefaultCommissionPercentage() : new BigDecimal("5.00");
                if (defCommission != null) shop.setCommission(defCommission);
            } catch (Exception ignored) { }
            entityManager.persist(shop);
        } else {
            shop.setShopName(sellerRegistration.getShopName());
            shop.setDescription(sellerRegistration.getDescription());
            entityManager.merge(shop);
        }

        // Professional notification messages (English)
        notificationService.createNotificationForUser(
                user.getId(),
                "Seller account activated",
                "Your seller registration has been completed successfully. Your shop is now active. A fee of 200,000 coins has been deducted from your account."
        );

        redirectAttributes.addFlashAttribute("successMessage", "Your seller account is now active. 200,000 coins have been deducted from your balance.");
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
        } else {
            model.addAttribute("shop", shop);
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

    @PostMapping(value = "/contract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String uploadSignedContract(@RequestParam("signed") MultipartFile file,
                                       RedirectAttributes redirectAttributes) {
        // Legacy endpoint retained; not used in new auto-activation flow
        redirectAttributes.addFlashAttribute("errorMessage", "Contract upload is not required. Your shop is already active.");
        return "redirect:/seller/register";
    }

    @GetMapping("/my-shop")
    public String showMyShop(Model model) {
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
        // Supported banks list
        model.addAttribute("supportedBanks", defaultBanks());
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
    private List<BankOption> defaultBanks() {
        return Arrays.asList(
                new BankOption("Vietcombank"),
                new BankOption("Techcombank"),
                new BankOption("VietinBank"),
                new BankOption("BIDV"),
                new BankOption("Agribank"),
                new BankOption("MB Bank"),
                new BankOption("ACB"),
                new BankOption("Sacombank"),
                new BankOption("TPBank"),
                new BankOption("VPBank")
        );
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

    private static String safeString(Object s) {
        return s == null ? "" : s.toString();
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
    private ResponseEntity<String> handleOtpFailForWithdraw(HttpSession session, HttpServletRequest request, Authentication authentication, User user, Long uid, String action, String baseMessage) {
        int attempts = incSellerOtpAttempts(session, uid, action);
        if (attempts >= 5) {
            // Logout + invalidate session immediately (no blocking)
            try {
                new SecurityContextLogoutHandler().logout(request, null, authentication);
            } catch (Exception ignored) { }
            try { session.invalidate(); } catch (Exception ignored) { }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Too many OTP failures. You have been logged out. Please sign in again.");
        } else if (attempts >= 3) {
            // Warn and send new OTP asynchronously
            sendWithdrawalOtpForUser(user);
            return ResponseEntity.badRequest().body(baseMessage + " A new OTP has been sent to your email. Please use the latest OTP.");
        } else {
            return ResponseEntity.badRequest().body(baseMessage);
        }
    }
}
