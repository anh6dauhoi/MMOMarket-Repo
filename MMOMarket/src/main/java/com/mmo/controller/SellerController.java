package com.mmo.controller;

import com.mmo.dto.CreateWithdrawalRequest;
import com.mmo.dto.SellerRegistrationForm;
import com.mmo.dto.SellerWithdrawalResponse;
import com.mmo.entity.*;
import com.mmo.repository.UserRepository;
import com.mmo.service.EmailService;
import com.mmo.service.NotificationService;
import com.mmo.service.SellerBankInfoService;
import com.mmo.service.SystemConfigurationService;
import com.mmo.service.ProductVariantAccountService;
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
    // Add missing CategoryRepository bean
    @Autowired private com.mmo.repository.CategoryRepository categoryRepository;

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

    // New: injected field for ProductVariantAccountService
    @Autowired
    private ProductVariantAccountService productVariantAccountService;

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

        // Optional soft check to provide faster feedback; final guard happens in the queue consumer
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

        // Inform the user; actual activation will be applied shortly by the consumer
        redirectAttributes.addFlashAttribute("successMessage", "Your registration request has been submitted. Your shop will be activated shortly if requirements are met.");
        return "redirect:/seller/register";
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

    // Upload product image and return public URL
    @PostMapping(path = "/products/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public ResponseEntity<?> uploadProductImage(@RequestParam("file") MultipartFile file,
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
            User user = userRepository.findByEmail(email).orElse(null);
            if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");

            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body("No file uploaded");
            }
            String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
            if (!(contentType.equals("image/png") || contentType.equals("image/jpeg") || contentType.equals("image/jpg") || contentType.equals("image/webp"))) {
                return ResponseEntity.badRequest().body("Only PNG, JPG, JPEG, WEBP are allowed");
            }
            if (file.getSize() > 10L * 1024 * 1024) { // 10MB safety
                return ResponseEntity.badRequest().body("File too large (max 10MB)");
            }

            // Build absolute upload directory under project root: <user.dir>/uploads/products/{sellerId}
            String rootDir = System.getProperty("user.dir");
            java.nio.file.Path uploadDir = java.nio.file.Paths
                    .get(rootDir, "uploads", "products", String.valueOf(user.getId()))
                    .toAbsolutePath()
                    .normalize();
            java.nio.file.Files.createDirectories(uploadDir);

            // Create safe filename with timestamp + uuid + proper extension
            String original = file.getOriginalFilename();
            String ext = ".png";
            if (original != null && original.contains(".")) {
                String e = original.substring(original.lastIndexOf('.')).toLowerCase();
                if (e.matches("\u002E(jpe?g|png|webp)")) ext = e;
            } else {
                if (contentType.contains("jpeg")) ext = ".jpg";
                else if (contentType.contains("png")) ext = ".png";
                else if (contentType.contains("webp")) ext = ".webp";
            }
            String filename = System.currentTimeMillis() + "-" + java.util.UUID.randomUUID() + ext;

            // Resolve absolute destination and copy stream (avoid Servlet Part.write relative path behavior)
            java.nio.file.Path dest = uploadDir.resolve(filename);
            try (java.io.InputStream is = file.getInputStream()) {
                java.nio.file.Files.copy(is, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            String publicUrl = "/uploads/products/" + user.getId() + "/" + filename;
            return ResponseEntity.ok(java.util.Map.of(
                    "url", publicUrl,
                    "name", filename,
                    "size", file.getSize(),
                    "contentType", contentType
            ));
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }

    // NEW: Product management page
    @GetMapping("/product-management")
    public String showProductManagement(Model model, RedirectAttributes redirectAttributes,
                                        @RequestParam(value = "sortPrice", required = false) String sortPrice,
                                        @RequestParam(value = "status", required = false) String status,
                                        @RequestParam(value = "page", required = false) Integer pageParam) {
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

        // Normalize status: default 'all' to show all products
        String statusNorm = status == null ? "all" : status.trim().toLowerCase();
        if (!(statusNorm.equals("active") || statusNorm.equals("hidden") || statusNorm.equals("all"))) {
            statusNorm = "all";
        }

        // Pagination defaults
        final int pageSize = 10; // 10 products per page
        int page = (pageParam == null || pageParam < 1) ? 1 : pageParam;

        // Build where clause according to status
        String baseWhere = "p.seller.id = :sid";
        if (statusNorm.equals("active")) {
            baseWhere += " AND p.isDelete = false";
        } else if (statusNorm.equals("hidden")) {
            baseWhere += " AND p.isDelete = true";
        }

        // Count total items matching filter
        long totalItems = 0L;
        try {
            String countQ = "SELECT COUNT(p) FROM Product p WHERE " + baseWhere;
            jakarta.persistence.TypedQuery<Long> tq = entityManager.createQuery(countQ, Long.class).setParameter("sid", user.getId());
            totalItems = tq.getSingleResult();
        } catch (Exception e) {
            totalItems = 0L;
        }
        int totalPages = (int) Math.max(1, (totalItems + pageSize - 1) / pageSize);
        if (page > totalPages) page = totalPages;
        int offset = (page - 1) * pageSize;

        // Fetch paged products
        java.util.List<com.mmo.entity.Product> products = new java.util.ArrayList<>();
        try {
            String selectQ = "SELECT p FROM Product p WHERE " + baseWhere + " ORDER BY p.createdAt DESC";
            jakarta.persistence.TypedQuery<com.mmo.entity.Product> pq = entityManager.createQuery(selectQ, com.mmo.entity.Product.class)
                    .setParameter("sid", user.getId())
                    .setFirstResult(offset)
                    .setMaxResults(pageSize);
            products = pq.getResultList();
        } catch (Exception e) {
            products = java.util.Collections.emptyList();
        }

        java.util.List<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
        for (com.mmo.entity.Product p : products) {
            java.util.Map<String, Object> row = new java.util.HashMap<>();
            row.put("id", p.getId());
            row.put("name", p.getName());
            row.put("category", p.getCategory());
            row.put("isDelete", p.isDelete());
            row.put("createdAt", p.getCreatedAt());
            row.put("updatedAt", p.getUpdatedAt());

            // Resolve image for thumbnail
            String displayImage = null;
            for (String getter : List.of("getImageUrl", "getImage", "getThumbnail")) {
                try {
                    var m = p.getClass().getMethod(getter);
                    Object v = m.invoke(p);
                    if (v instanceof String s && s != null && !s.isBlank()) { displayImage = s; break; }
                } catch (Exception ignored) {}
            }
            row.put("image", displayImage);

            // Variants + lowest price (include ALL variants including hidden ones for seller management)
            java.util.List<com.mmo.entity.ProductVariant> variants = productVariantRepository.findByProductId(p.getId());
            row.put("variantCount", variants != null ? variants.size() : 0);
            Long lowest = (variants == null || variants.isEmpty()) ? 0L : variants.stream().map(com.mmo.entity.ProductVariant::getPrice).filter(java.util.Objects::nonNull).min(Long::compareTo).orElse(0L);
            row.put("lowestPrice", lowest);

            // Total sold from repository
            Long totalSold = productRepository.getTotalSoldForProduct(p.getId());
            row.put("totalSold", totalSold != null ? totalSold : 0L);

            rows.add(row);
        }

        // Optional sorting by lowestPrice (applied in-memory to current page results)
        if (sortPrice != null) {
            String sp = sortPrice.trim().toLowerCase();
            if (sp.equals("asc") || sp.equals("desc")) {
                rows.sort((a, b) -> {
                    Long la = (Long) a.getOrDefault("lowestPrice", 0L);
                    Long lb = (Long) b.getOrDefault("lowestPrice", 0L);
                    int cmp = Long.compare(la, lb);
                    return sp.equals("asc") ? cmp : -cmp;
                });
            }
        }
        model.addAttribute("sortPrice", sortPrice == null ? "" : sortPrice.toLowerCase());
        model.addAttribute("status", statusNorm);
        model.addAttribute("products", rows);

        // Categories for edit dropdown (filter not deleted)
        java.util.List<com.mmo.entity.Category> cats = categoryRepository.findAll();
        if (cats != null) {
            cats.removeIf(c -> c == null || c.isDelete());
        }
        model.addAttribute("categories", cats);

        // Pagination model attributes
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalItems", totalItems);
        model.addAttribute("pageSize", pageSize);
        java.util.List<Integer> pageNumbers = new java.util.ArrayList<>();
        for (int i = 1; i <= totalPages; i++) pageNumbers.add(i);
        model.addAttribute("pageNumbers", pageNumbers);
        model.addAttribute("prevPage", page > 1 ? page - 1 : null);
        model.addAttribute("nextPage", page < totalPages ? page + 1 : null);

        // Calculate 1-based start/end indices for the current page (for "Showing X to Y of Z results")
        long startItem = totalItems == 0 ? 0 : (long) offset + 1L;
        long endItem = totalItems == 0 ? 0 : (long) offset + rows.size();
        if (endItem > totalItems) endItem = totalItems;
        model.addAttribute("startItem", startItem);
        model.addAttribute("endItem", endItem);

        return "seller/product-management";
    }

    // Get product JSON for edit modal prefill
    @GetMapping(path = "/products/{id}/json", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> getProductJson(@PathVariable Long id, Authentication authentication) {
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
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");

        java.util.Optional<com.mmo.entity.Product> opt = productRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Not found");
        com.mmo.entity.Product p = opt.get();
        if (p.getSeller() == null || !Objects.equals(p.getSeller().getId(), user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("id", p.getId());
        data.put("name", p.getName());
        data.put("description", p.getDescription());
        data.put("categoryId", p.getCategory() != null ? p.getCategory().getId() : null);
        // resolve image
        String displayImage = null;
        for (String getter : List.of("getImageUrl", "getImage", "getThumbnail")) {
            try {
                var m = p.getClass().getMethod(getter);
                Object v = m.invoke(p);
                if (v instanceof String s && !s.isBlank()) { displayImage = s; break; }
            } catch (Exception ignored) {}
        }
        data.put("image", displayImage);
        data.put("isDelete", p.isDelete());

        // Fetch ShopInfo via HQL like shop-info to ensure correct level
        try {
            ShopInfo shop = entityManager.createQuery(
                    "SELECT s FROM ShopInfo s WHERE s.user = :u AND s.isDelete = false", ShopInfo.class)
                .setParameter("u", user)
                .getResultStream().findFirst().orElse(null);
            if (shop != null) {
                try { entityManager.refresh(shop); } catch (Exception ignored) {}
                data.put("shopLevel", shop.getShopLevel() != null ? shop.getShopLevel() : 0);
                data.put("shopPoints", shop.getPoints() != null ? shop.getPoints() : 0);
            }
        } catch (Exception ignored) {}

        return ResponseEntity.ok(data);
    }

    // Render seller product detail page (full page view)
    @GetMapping(path = "/products/{id}/detail")
    public String showProductDetailPage(@PathVariable Long id, Model model, Authentication authentication, RedirectAttributes redirectAttributes) {
        if (authentication == null || !authentication.isAuthenticated()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please login to continue.");
            return "redirect:/login";
        }
        String email = authentication.getName();
        if (authentication.getPrincipal() instanceof OidcUser oidc) email = oidc.getEmail();
        else if (authentication.getPrincipal() instanceof OAuth2User ou) {
            Object mailAttr = ou.getAttributes().get("email"); if (mailAttr != null) email = mailAttr.toString();
        }
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "User not found.");
            return "redirect:/login";
        }
        java.util.Optional<com.mmo.entity.Product> opt = productRepository.findById(id);
        if (opt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Product not found.");
            return "redirect:/seller/product-management";
        }
        com.mmo.entity.Product p = opt.get();
        if (p.getSeller() == null || !Objects.equals(p.getSeller().getId(), user.getId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "You are not authorized to view this product.");
            return "redirect:/seller/product-management";
        }
        model.addAttribute("productId", p.getId());
        model.addAttribute("productName", p.getName());
        // Fetch ShopInfo via HQL like shop-info to ensure correct level
        try {
            ShopInfo shop = entityManager.createQuery(
                    "SELECT s FROM ShopInfo s WHERE s.user = :u AND s.isDelete = false", ShopInfo.class)
                .setParameter("u", user)
                .getResultStream().findFirst().orElse(null);
            if (shop != null) {
                try { entityManager.refresh(shop); } catch (Exception ignored) {}
                model.addAttribute("shop", shop);
            }
        } catch (Exception ignored) {}
        return "seller/product-detail";
    }

    // Toggle hide/show product
    @PostMapping(path = "/products/{id}/toggle-hide", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @Transactional
    public ResponseEntity<?> toggleHideProduct(@PathVariable Long id, Authentication authentication) {
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
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");

        com.mmo.entity.Product p = entityManager.find(com.mmo.entity.Product.class, id);
        if (p == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Not found");
        if (p.getSeller() == null || !Objects.equals(p.getSeller().getId(), user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");
        }
        boolean next = !p.isDelete();
        p.setDelete(next);
        p.setDeletedBy(next ? user.getId() : null);
        entityManager.merge(p);
        return ResponseEntity.ok(Map.of("id", p.getId(), "hidden", next));
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
            return ResponseEntity.ok(Map.of(
                "level", 0,
                "maxPrice", 50_000L,
                "message", "Shop not found. Default limit applies."
            ));
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

    // Get maximum price limit based on shop level
    private long getMaxPriceByLevel(short level) {
        return switch (level) {
            case 0 -> 50_000L;        // Level 0: max 50k
            case 1 -> 100_000L;       // Level 1: max 100k
            case 2 -> 300_000L;       // Level 2: max 300k
            case 3 -> 500_000L;       // Level 3: max 500k
            case 4 -> 1_000_000L;     // Level 4: max 1M
            case 5 -> 2_000_000L;     // Level 5: max 2M
            case 6 -> 4_000_000L;     // Level 6: max 4M
            case 7 -> Long.MAX_VALUE; // Level 7: unlimited
            default -> 50_000L;       // Default: max 50k
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

    // Get shop price limit info
    @GetMapping(path = "/shop/price-limit", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> getShopPriceLimit(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Unauthorized"));
        }
        String email = authentication.getName();
        if (authentication.getPrincipal() instanceof OidcUser oidc) email = oidc.getEmail();
        else if (authentication.getPrincipal() instanceof OAuth2User ou) {
            Object mailAttr = ou.getAttributes().get("email");
            if (mailAttr != null) email = mailAttr.toString();
        }
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Unauthorized"));
        }

        // Fetch ShopInfo via HQL to ensure consistent lookup
        ShopInfo shop = null;
        try {
            shop = entityManager.createQuery(
                    "SELECT s FROM ShopInfo s WHERE s.user = :u AND s.isDelete = false", ShopInfo.class)
                .setParameter("u", user)
                .getResultStream().findFirst().orElse(null);
        } catch (Exception ignored) {}
        if (shop == null) {
            return ResponseEntity.ok(Map.of(
                "level", 0,
                "maxPrice", 50_000L,
                "message", "Shop not found. Default limit applies."
            ));
        }

        // Refresh from database to get latest shop_level (updated by DB trigger)
        try { entityManager.refresh(shop); } catch (Exception ignored) {}
        short shopLevel = shop.getShopLevel() == null ? 0 : shop.getShopLevel();
        long maxPrice = getMaxPriceByLevel(shopLevel);
        long currentPoints = shop.getPoints() == null ? 0L : shop.getPoints();
        long nextLevelThreshold = shopLevel < 7 ? levelThreshold(shopLevel + 1) : 0L;
        long nextLevelMaxPrice = shopLevel < 7 ? getMaxPriceByLevel((short) (shopLevel + 1)) : Long.MAX_VALUE;

        return ResponseEntity.ok(Map.of(
            "level", shopLevel,
            "maxPrice", maxPrice,
            "currentPoints", currentPoints,
            "nextLevel", shopLevel < 7 ? (shopLevel + 1) : 7,
            "nextLevelThreshold", nextLevelThreshold,
            "nextLevelMaxPrice", nextLevelMaxPrice,
            "pointsToNextLevel", Math.max(0L, nextLevelThreshold - currentPoints)
        ));
    }

    // List variants for a product (JSON)
    @GetMapping(path = "/products/{id}/variants", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> listVariants(@PathVariable Long id, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        String email = authentication.getName();
        if (authentication.getPrincipal() instanceof OidcUser oidc) email = oidc.getEmail();
        else if (authentication.getPrincipal() instanceof OAuth2User ou) {
            Object mailAttr = ou.getAttributes().get("email"); if (mailAttr != null) email = mailAttr.toString();
        }
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        java.util.Optional<com.mmo.entity.Product> opt = productRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Not found");
        com.mmo.entity.Product p = opt.get();
        if (p.getSeller() == null || !Objects.equals(p.getSeller().getId(), user.getId())) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");

        // Get ALL variants including hidden ones (seller should see all their variants)
        java.util.List<com.mmo.entity.ProductVariant> variants = productVariantRepository.findByProductId(p.getId());

        java.util.List<com.mmo.dto.ProductVariantDto> dtos = new java.util.ArrayList<>();
        for (com.mmo.entity.ProductVariant v : variants) {
            long stock = 0L;
            long sold = 0L;
            try {
                // available accounts = status 'Available'
                stock = productVariantAccountRepository.countByVariant_IdAndIsDeleteFalseAndStatus(v.getId(), "Available");
            } catch (Exception ignored) {}
            try {
                // sold accounts = status 'Sold'
                sold = productVariantAccountRepository.countByVariant_IdAndIsDeleteFalseAndStatus(v.getId(), "Sold");
            } catch (Exception ignored) {}
            dtos.add(new com.mmo.dto.ProductVariantDto(v.getId(), v.getVariantName(), v.getPrice(), stock,sold, v.isDelete()));
        }
        return ResponseEntity.ok(dtos);
    }

    // Create a new variant for a product
    @PostMapping(path = "/products/{id}/variants", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @Transactional
    public ResponseEntity<?> createVariant(@PathVariable Long id, @RequestBody Map<String, Object> body, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        String email = authentication.getName();
        if (authentication.getPrincipal() instanceof OidcUser oidc) email = oidc.getEmail();
        else if (authentication.getPrincipal() instanceof OAuth2User ou) {
            Object mailAttr = ou.getAttributes().get("email"); if (mailAttr != null) email = mailAttr.toString();
        }
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");

        // Get shop info to check level
        ShopInfo shop = shopInfoRepository.findByUserIdAndIsDeleteFalse(user.getId()).orElseGet(() -> shopInfoRepository.findByUser_Id(user.getId()).orElse(null));
        if (shop == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Shop not found"));
        }
        // Refresh from database to get latest shop_level (updated by DB trigger)
        entityManager.refresh(shop);
        short shopLevel = shop.getShopLevel() == null ? 0 : shop.getShopLevel();
        long maxPrice = getMaxPriceByLevel(shopLevel);

        java.util.Optional<com.mmo.entity.Product> opt = productRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Not found"));
        com.mmo.entity.Product p = opt.get();
        if (p.getSeller() == null || !Objects.equals(p.getSeller().getId(), user.getId())) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Forbidden"));

        String variantName = body.get("variantName") != null ? body.get("variantName").toString().trim() : null;
        Long price = null;
        try { if (body.get("price") instanceof Number n) price = n.longValue(); else if (body.get("price") != null) price = Long.parseLong(body.get("price").toString()); } catch (Exception ignored) {}

        Map<String, String> fieldErrors = new HashMap<>();
        if (variantName == null || variantName.isBlank()) fieldErrors.put("variantName", "Variant name is required");
        if (price == null) fieldErrors.put("price", "Price is required");
        else if (price < 0) fieldErrors.put("price", "Invalid price");
        if (!fieldErrors.isEmpty()) return ResponseEntity.badRequest().body(Map.of("message", "Validation failed", "fieldErrors", fieldErrors));

        // Check price limit based on shop level
        if (price > maxPrice) {
            String formattedMax = String.format("%,d", maxPrice);
            return ResponseEntity.badRequest().body(Map.of(
                "message", "Price exceeds your shop level limit. Your level " + shopLevel + " allows max price: " + formattedMax + " Coins",
                "fieldErrors", Map.of("price", "Price exceeds your shop level limit. Max: " + formattedMax + " Coins"),
                "maxPrice", maxPrice,
                "currentLevel", shopLevel
            ));
        }

        com.mmo.entity.ProductVariant v = new com.mmo.entity.ProductVariant();
        v.setProduct(p);
        v.setVariantName(variantName);
        v.setPrice(price);
        v.setDelete(false);
        v.setCreatedBy(user.getId());
        entityManager.persist(v);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Created", "id", v.getId()));
    }

    // Update a variant
    @PutMapping(path = "/products/variants/{variantId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @Transactional
    public ResponseEntity<?> updateVariant(@PathVariable Long variantId, @RequestBody Map<String, Object> body, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        String email = authentication.getName();
        if (authentication.getPrincipal() instanceof OidcUser oidc) email = oidc.getEmail();
        else if (authentication.getPrincipal() instanceof OAuth2User ou) {
            Object mailAttr = ou.getAttributes().get("email"); if (mailAttr != null) email = mailAttr.toString();
        }
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");

        // Get shop info to check level
        ShopInfo shop = shopInfoRepository.findByUserIdAndIsDeleteFalse(user.getId()).orElseGet(() -> shopInfoRepository.findByUser_Id(user.getId()).orElse(null));
        if (shop == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Shop not found"));
        }
        // Refresh from database to get latest shop_level (updated by DB trigger)
        entityManager.refresh(shop);
        short shopLevel = shop.getShopLevel() == null ? 0 : shop.getShopLevel();
        long maxPrice = getMaxPriceByLevel(shopLevel);

        com.mmo.entity.ProductVariant v = entityManager.find(com.mmo.entity.ProductVariant.class, variantId);
        if (v == null || v.isDelete()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Not found"));
        if (v.getProduct() == null || v.getProduct().getSeller() == null || !Objects.equals(v.getProduct().getSeller().getId(), user.getId())) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Forbidden"));

        String variantName = body.get("variantName") != null ? body.get("variantName").toString().trim() : null;
        Long price = null;
        try { if (body.get("price") instanceof Number n) price = n.longValue(); else if (body.get("price") != null) price = Long.parseLong(body.get("price").toString()); } catch (Exception ignored) {}

        Map<String, String> fieldErrors = new HashMap<>();
        if (variantName == null || variantName.isBlank()) fieldErrors.put("variantName", "Variant name is required");
        if (price == null) fieldErrors.put("price", "Price is required");
        else if (price < 0) fieldErrors.put("price", "Invalid price");
        if (!fieldErrors.isEmpty()) return ResponseEntity.badRequest().body(Map.of("message", "Validation failed", "fieldErrors", fieldErrors));

        // Check price limit
        if (price > maxPrice) {
            String formattedMax = String.format("%,d", maxPrice);
            return ResponseEntity.badRequest().body(Map.of(
                "message", "Price exceeds your shop level limit. Your level " + shopLevel + " allows max price: " + formattedMax + " Coins",
                "fieldErrors", Map.of("price", "Price exceeds your shop level limit. Max: " + formattedMax + " Coins"),
                "maxPrice", maxPrice,
                "currentLevel", shopLevel
            ));
        }

        v.setVariantName(variantName);
        v.setPrice(price);
        entityManager.merge(v);
        return ResponseEntity.ok(Map.of("message", "Updated"));
    }


    // Toggle hide/show variant (soft delete toggle)
    @PostMapping(path = "/products/variants/{variantId}/toggle-hide", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @Transactional
    public ResponseEntity<?> toggleHideVariant(@PathVariable Long variantId, Authentication authentication) {
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
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");

        com.mmo.entity.ProductVariant v = entityManager.find(com.mmo.entity.ProductVariant.class, variantId);
        if (v == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Variant not found");
        if (v.getProduct() == null || v.getProduct().getSeller() == null ||
            !Objects.equals(v.getProduct().getSeller().getId(), user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");
        }
        boolean next = !v.isDelete();
        v.setDelete(next);
        v.setDeletedBy(next ? user.getId() : null);
        entityManager.merge(v);
        return ResponseEntity.ok(Map.of("id", v.getId(), "hidden", next));
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
            return ResponseEntity.badRequest().body(baseMessage + " A new OTP has been sent to your email. Please use the latest OTP.");
        } else {
            return ResponseEntity.badRequest().body(baseMessage);
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


    // Create product via JSON (used by Product Management modal)
    @PostMapping(path = "/products", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @Transactional
    public ResponseEntity<?> createProductJson(@RequestBody Map<String, Object> body,
                                               Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Unauthorized"));
            }
            String email = authentication.getName();
            if (authentication.getPrincipal() instanceof OidcUser oidc) email = oidc.getEmail();
            else if (authentication.getPrincipal() instanceof OAuth2User ou) {
                Object mailAttr = ou.getAttributes().get("email");
                if (mailAttr != null) email = mailAttr.toString();
            }
            User seller = userRepository.findByEmail(email).orElse(null);
            if (seller == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Unauthorized"));
            }
            boolean activeShop = seller.getShopStatus() != null && seller.getShopStatus().equalsIgnoreCase("Active");
            if (!activeShop) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Your shop is not Active."));
            }

            String name = body.get("name") != null ? body.get("name").toString().trim() : null;
            String description = body.get("description") != null ? body.get("description").toString().trim() : null;
            String image = body.get("image") != null ? body.get("image").toString().trim() : null;
            Long categoryId = null;
            if (body.get("categoryId") instanceof Number n) categoryId = n.longValue();
            else if (body.get("categoryId") != null) { try { categoryId = Long.parseLong(body.get("categoryId").toString()); } catch (Exception ignored) {} }

            Map<String, String> fieldErrors = new HashMap<>();
            if (name == null || name.isBlank()) fieldErrors.put("name", "Name is required");
            else if (name.length() < 3) fieldErrors.put("name", "Name must be at least 3 characters");
            else if (name.length() > 255) fieldErrors.put("name", "Name must be at most 255 characters");
            Category cat = null;
            if (categoryId == null) fieldErrors.put("categoryId", "Category is required");
            else {
                cat = entityManager.find(Category.class, categoryId);
                if (cat == null || cat.isDelete()) fieldErrors.put("categoryId", "Invalid category");
            }
            if (description != null && description.length() > 5000) fieldErrors.put("description", "Description must be at most 5000 characters");
            if (image != null && !image.isBlank()) {
                if (image.length() > 255) fieldErrors.put("image", "Image URL must be at most 255 characters");
                String lower = image.toLowerCase();
                boolean okPrefix = lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("/");
                boolean okExt = lower.matches(".*\\.(png|jpe?g|webp)(\\?.*)?$");
                if (!okPrefix || !okExt) fieldErrors.put("image", "Image URL must be http(s) or site-relative and end with .png/.jpg/.jpeg/.webp");
            }
            if (!fieldErrors.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Validation failed", "fieldErrors", fieldErrors));
            }

            com.mmo.entity.Product p = new com.mmo.entity.Product();
            p.setSeller(seller);
            p.setCategory(cat);
            p.setName(name);
            p.setDescription(description);
            p.setDelete(false);
            p.setCreatedBy(seller.getId());
            if (image != null && !image.isBlank()) {
                try { p.getClass().getMethod("setImage", String.class).invoke(p, image); } catch (Exception ignored) { p.setImage(image); }
            }
            entityManager.persist(p);
            entityManager.flush(); // Force immediate database insert

            Map<String, Object> res = new HashMap<>();
            res.put("message", "Created successfully");
            res.put("id", p.getId());
            res.put("name", p.getName());
            res.put("categoryName", p.getCategory() != null ? p.getCategory().getName() : null);
            res.put("image", image);
            res.put("description", p.getDescription());
            return ResponseEntity.status(HttpStatus.CREATED).body(res);
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(Map.of("message", "Internal error: " + ex.getMessage()));
        }
    }

    // Update product via JSON (used by Product Management modal)
    @PutMapping(path = "/products/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @Transactional
    public ResponseEntity<?> updateProductJson(@PathVariable Long id,
                                               @RequestBody Map<String, Object> body,
                                               Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Unauthorized"));
            }
            String email = authentication.getName();
            if (authentication.getPrincipal() instanceof OidcUser oidc) email = oidc.getEmail();
            else if (authentication.getPrincipal() instanceof OAuth2User ou) {
                Object mailAttr = ou.getAttributes().get("email");
                if (mailAttr != null) email = mailAttr.toString();
            }
            User seller = userRepository.findByEmail(email).orElse(null);
            if (seller == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Unauthorized"));
            }

            com.mmo.entity.Product p = entityManager.find(com.mmo.entity.Product.class, id);
            if (p == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Product not found"));
            }
            if (p.getSeller() == null || !Objects.equals(p.getSeller().getId(), seller.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "You are not authorized to edit this product"));
            }

            String name = body.get("name") != null ? body.get("name").toString().trim() : null;
            String description = body.get("description") != null ? body.get("description").toString().trim() : null;
            String image = body.get("image") != null ? body.get("image").toString().trim() : null;
            Long categoryId = null;
            if (body.get("categoryId") instanceof Number n) categoryId = n.longValue();
            else if (body.get("categoryId") != null) { try { categoryId = Long.parseLong(body.get("categoryId").toString()); } catch (Exception ignored) {} }

            Map<String, String> fieldErrors = new HashMap<>();
            if (name == null || name.isBlank()) fieldErrors.put("name", "Name is required");
            else if (name.length() < 3) fieldErrors.put("name", "Name must be at least 3 characters");
            else if (name.length() > 255) fieldErrors.put("name", "Name must be at most 255 characters");
            Category cat = null;
            if (categoryId == null) fieldErrors.put("categoryId", "Category is required");
            else {
                cat = entityManager.find(Category.class, categoryId);
                if (cat == null || cat.isDelete()) fieldErrors.put("categoryId", "Invalid category");
            }
            if (description != null && description.length() > 5000) fieldErrors.put("description", "Description must be at most 5000 characters");
            if (image != null && !image.isBlank()) {
                if (image.length() > 255) fieldErrors.put("image", "Image URL must be at most 255 characters");
                String lower = image.toLowerCase();
                boolean okPrefix = lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("/");
                boolean okExt = lower.matches(".*\\.(png|jpe?g|webp)(\\?.*)?$");
                if (!okPrefix || !okExt) fieldErrors.put("image", "Image URL must be http(s) or site-relative and end with .png/.jpg/.jpeg/.webp");
            }
            if (!fieldErrors.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Validation failed", "fieldErrors", fieldErrors));
            }

            // Update fields
            p.setName(name);
            p.setDescription(description);
            p.setCategory(cat);

            // Update image: if key exists in body, update it (even if null/empty to clear)
            // If key doesn't exist, keep existing image
            if (body.containsKey("image")) {
                p.setImage(image != null && !image.isBlank() ? image : null);
            }

            // Persist changes
            entityManager.merge(p);
            entityManager.flush();

            Map<String, Object> res = new HashMap<>();
            res.put("message", "Updated successfully");
            res.put("id", p.getId());
            res.put("name", p.getName());
            res.put("categoryName", p.getCategory() != null ? p.getCategory().getName() : null);
            res.put("image", p.getImage());
            res.put("description", p.getDescription());
            return ResponseEntity.ok(res);
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(Map.of("message", "Internal error: " + ex.getMessage()));
        }
    }

    // Download Excel template for uploading accounts to a variant (username,password)
    @GetMapping(path = "/products/variants/{variantId}/template")
    public ResponseEntity<byte[]> downloadVariantTemplate(@PathVariable Long variantId, Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            String email = authentication.getName();
            if (authentication.getPrincipal() instanceof OidcUser oidc) email = oidc.getEmail();
            else if (authentication.getPrincipal() instanceof OAuth2User ou) {
                Object mailAttr = ou.getAttributes().get("email"); if (mailAttr != null) email = mailAttr.toString();
            }
            User user = userRepository.findByEmail(email).orElse(null);
            if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            com.mmo.entity.ProductVariant v = entityManager.find(com.mmo.entity.ProductVariant.class, variantId);
            if (v == null || v.isDelete()) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            if (v.getProduct() == null || v.getProduct().getSeller() == null || !Objects.equals(v.getProduct().getSeller().getId(), user.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            byte[] bytes = productVariantAccountService.buildTemplateCsv();
            String filename = "variant-" + variantId + "-template.csv";
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .contentType(org.springframework.http.MediaType.parseMediaType("text/csv"))
                    .body(bytes);
        } catch (Exception ex) {
            return ResponseEntity.status(500).build();
        }
    }

    // Preview or confirm upload of accounts for a variant via Excel/CSV
    @PostMapping(path = "/products/variants/{variantId}/upload-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @Transactional
    public ResponseEntity<?> uploadVariantAccounts(@PathVariable Long variantId,
                                                   @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
                                                   @RequestParam(name = "preview", required = false, defaultValue = "false") boolean preview,
                                                   @RequestParam(name = "dedupe", required = false, defaultValue = "true") boolean dedupe,
                                                   Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Unauthorized"));
            }
            String email = authentication.getName();
            if (authentication.getPrincipal() instanceof OidcUser oidc) email = oidc.getEmail();
            else if (authentication.getPrincipal() instanceof OAuth2User ou) {
                Object mailAttr = ou.getAttributes().get("email"); if (mailAttr != null) email = mailAttr.toString();
            }
            User user = userRepository.findByEmail(email).orElse(null);
            if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Unauthorized"));
            com.mmo.entity.ProductVariant v = entityManager.find(com.mmo.entity.ProductVariant.class, variantId);
            if (v == null || v.isDelete()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Variant not found"));
            if (v.getProduct() == null || v.getProduct().getSeller() == null || !Objects.equals(v.getProduct().getSeller().getId(), user.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Forbidden"));
            }
            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "No file uploaded"));
            }

            if (preview) {
                var pr = productVariantAccountService.previewUpload(user, v, file, dedupe);
                java.util.List<java.util.Map<String,Object>> rows = new java.util.ArrayList<>();
                for (var r : pr.getRows()) rows.add(Map.of(
                        "username", r.getUsername(),
                        "password", r.getPassword(),
                        "duplicate", r.isDuplicate(),
                        "invalid", r.isInvalid(),
                        "rowIndex", r.getRowIndex()
                ));
                return ResponseEntity.ok(Map.of(
                        "count", pr.getCount(),
                        "duplicateCount", pr.getDuplicateCount(),
                        "invalidCount", pr.getInvalidCount(),
                        "rows", rows
                ));
            } else {
                try {
                    var ur = productVariantAccountService.confirmUpload(user, v, file, dedupe);
                    return ResponseEntity.ok(Map.of("created", ur.getCreated(), "skipped", ur.getSkipped()));
                } catch (IllegalArgumentException iae) {
                    // Invalid rows: respond 400 with message
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", iae.getMessage()));
                }
            }
        } catch (org.springframework.web.multipart.MultipartException mex) {
            return ResponseEntity.badRequest().body(Map.of("message", "Upload error: " + mex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(Map.of("message", "Internal error: " + ex.getMessage()));
        }
    }
}
