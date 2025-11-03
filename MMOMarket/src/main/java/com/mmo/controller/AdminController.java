package com.mmo.controller;

import com.mmo.dto.ProcessWithdrawalRequest;
import com.mmo.dto.AdminWithdrawalResponse;
import com.mmo.dto.WithdrawalDetailResponse;
import com.mmo.dto.CoinDepositDetailResponse;
import com.mmo.entity.User;
import com.mmo.entity.Withdrawal;
import com.mmo.entity.CoinDeposit;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.mmo.dto.AdminWithdrawalResponse;
import com.mmo.dto.CoinDepositDetailResponse;
import com.mmo.dto.CreateCategoryRequest;
import com.mmo.dto.ProcessWithdrawalRequest;
import com.mmo.dto.UpdateCategoryRequest;
import com.mmo.dto.WithdrawalDetailResponse;
import com.mmo.entity.Category;
import com.mmo.entity.CoinDeposit;
import com.mmo.entity.ShopInfo;
import com.mmo.entity.User;
import com.mmo.entity.Withdrawal;
import com.mmo.service.CategoryService;
import com.mmo.util.Bank;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Controller
@RequestMapping("/admin")
@SuppressWarnings("unchecked")
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    @Autowired
    private com.mmo.service.NotificationService notificationService;

    @Autowired
    private com.mmo.service.WithdrawalService withdrawalService;

    @Autowired
    private com.mmo.service.EmailService emailService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private com.mmo.service.BlogService blogService;

    @Autowired
    private com.mmo.service.ShopService shopService;

    @Autowired
    private com.mmo.repository.UserRepository userRepository;

    @Autowired
    private com.mmo.service.SystemConfigurationService systemConfigurationService;

    @PersistenceContext
    private EntityManager entityManager;

    // NEW: Admin Dashboard route
    @GetMapping({"", "/"})
    public String dashboard(Model model) {
        model.addAttribute("pageTitle", "Admin Dashboard");
        model.addAttribute("body", "admin/dashboard");
        return "admin/layout";
    }

    @GetMapping("/withdraw-management")
    public String withdrawManagement(@RequestParam(name = "status", defaultValue = "All") String status,
                                     @RequestParam(name = "page", defaultValue = "0") int page,
                                     @RequestParam(name = "search", defaultValue = "") String search,
                                     @RequestParam(name = "sort", defaultValue = "date_desc") String sort,
                                     Model model) {
        // Build base JPQL
        StringBuilder sb = new StringBuilder("SELECT w FROM Withdrawal w LEFT JOIN FETCH w.seller s WHERE 1=1");
        if (!"All".equalsIgnoreCase(status)) {
            sb.append(" AND LOWER(w.status) = LOWER(:status)");
        }
        if (search != null && !search.isBlank()) {
            sb.append(" AND (LOWER(s.fullName) LIKE LOWER(:search) OR LOWER(s.email) LIKE LOWER(:search) OR CAST(w.id AS string) LIKE :search)");
        }

        // Determine ordering based on sort param
        String orderField = "w.createdAt";
        String orderDir = "DESC";
        if (sort != null) {
            String s = sort.trim().toLowerCase();
            if ("date_asc".equals(s) || "created_at_asc".equals(s)) orderDir = "ASC";
            else orderDir = "DESC";
        }
        sb.append(" ORDER BY ").append(orderField).append(" ").append(orderDir);

        jakarta.persistence.Query query = entityManager.createQuery(sb.toString(), Withdrawal.class);
        if (!"All".equalsIgnoreCase(status)) {
            query.setParameter("status", status);
        }
        if (search != null && !search.isBlank()) {
            query.setParameter("search", "%" + search + "%");
        }

        List<Withdrawal> all = query.getResultList();
        int total = all.size();
        int totalPages = (int) Math.ceil((double) total / 10);
        List<Withdrawal> pageList = all.stream()
                .skip((long) page * 10)
                .limit(10)
                .toList();

        model.addAttribute("withdrawals", pageList);
        model.addAttribute("currentStatus", status);
        model.addAttribute("currentPage", page);
        model.addAttribute("currentSearch", search);
        model.addAttribute("currentSort", sort); // expose current sort to template
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageTitle", "Withdraw Management");
        model.addAttribute("body", "admin/withdraw-management");
        return "admin/layout";
    }


    @PostMapping(path = "/withdrawals/{id}/process", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @Transactional
    public ResponseEntity<?> processWithdrawal(@PathVariable Long id,
                                               @RequestBody ProcessWithdrawalRequest req,
                                               Authentication auth) {
        try {
            // AuthN/AuthZ: must be ADMIN
            if (auth == null || !auth.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }
            User admin = entityManager.createQuery("select u from User u where lower(u.email)=lower(:e)", User.class)
                    .setParameter("e", auth.getName())
                    .getResultStream().findFirst().orElse(null);
            if (admin == null || admin.getRole() == null || !admin.getRole().equalsIgnoreCase("ADMIN")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");
            }

            Withdrawal wd = entityManager.find(Withdrawal.class, id);
            if (wd == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Withdrawal not found");
            }
            String current = wd.getStatus() == null ? "" : wd.getStatus().trim();
            if (!"Pending".equalsIgnoreCase(current)) {
                return ResponseEntity.badRequest().body("Only pending withdrawals can be processed.");
            }

            String action = req.getStatus() == null ? "" : req.getStatus().trim();
            boolean approve = "Approved".equalsIgnoreCase(action);
            boolean reject = "Rejected".equalsIgnoreCase(action);
            if (!approve && !reject) {
                return ResponseEntity.badRequest().body("Status must be 'Approved' or 'Rejected'.");
            }

            // Process via service (handles emails and refund logic)
            Withdrawal processed = withdrawalService.processWithdrawal(
                    id,
                    action,
                    req.getProofFile(),
                    req.getReason(),
                    reject // refund only when rejected
            );

            User seller = processed.getSeller();
            Long amount = processed.getAmount() == null ? 0L : processed.getAmount();
            if (approve) {
                notificationService.createNotificationForUser(seller.getId(), "Withdrawal Approved", "Your withdrawal request of " + amount + " VND has been approved.");
            } else {
                notificationService.createNotificationForUser(seller.getId(), "Withdrawal Rejected", "Your withdrawal request of " + amount + " VND has been rejected. Reason: " + req.getReason() + ". 95% of the amount has been refunded to your account.");
            }
            return ResponseEntity.ok(AdminWithdrawalResponse.from(processed));
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }

    // NEW: Admin processes a withdrawal (Approved) - multipart variant (allows file upload)
    @PostMapping(path = "/withdrawals/{id}/process", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    @Transactional
    public ResponseEntity<?> processWithdrawalMultipart(@PathVariable Long id,
                                                         @RequestParam(required = false, name = "status") String status,
                                                         @RequestPart(value = "proof", required = false) MultipartFile proof,
                                                         Authentication auth) {
        try {
            if (auth == null || !auth.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }
            User admin = entityManager.createQuery("select u from User u where lower(u.email)=lower(:e)", User.class)
                    .setParameter("e", auth.getName())
                    .getResultStream().findFirst().orElse(null);
            if (admin == null || admin.getRole() == null || !admin.getRole().equalsIgnoreCase("ADMIN")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");
            }

            Withdrawal wd = entityManager.find(Withdrawal.class, id);
            if (wd == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Withdrawal not found");
            }
            String current = wd.getStatus() == null ? "" : wd.getStatus().trim();
            if (!"Pending".equalsIgnoreCase(current)) {
                return ResponseEntity.badRequest().body("Only pending withdrawals can be processed.");
            }

            if (status == null || !"Approved".equalsIgnoreCase(status)) {
                return ResponseEntity.badRequest().body("This endpoint only supports Approved via multipart (file upload).");
            }

            String proofFile = null;
            try {
                if (proof != null && !proof.isEmpty()) {
                    Path dir = Paths.get("uploads", "withdrawals");
                    Files.createDirectories(dir);
                    String filename = "proof-" + id + "-" + System.currentTimeMillis() + "-" + proof.getOriginalFilename();
                    Path target = dir.resolve(filename);
                    Files.copy(proof.getInputStream(), target);
                    proofFile = target.toString().replace('\\', '/');
                }
            } catch (Exception ignored) {}

            Withdrawal processed = withdrawalService.processWithdrawal(id, "Approved", proofFile, null, false);
            User seller = processed.getSeller();
            Long amount = processed.getAmount() == null ? 0L : processed.getAmount();
            notificationService.createNotificationForUser(seller.getId(), "Withdrawal Approved", "Your withdrawal request of " + amount + " VND has been approved.");
            return ResponseEntity.ok(AdminWithdrawalResponse.from(processed));
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }


    // NEW: Get withdrawal details for admin view detail modal
    @GetMapping(value = "/withdrawals/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> getWithdrawalDetail(@PathVariable Long id, Authentication auth) {
        try {
            // Attempt to obtain Authentication from param or SecurityContextHolder
            Authentication authentication = auth;
            if (authentication == null || !authentication.isAuthenticated()) {
                authentication = SecurityContextHolder.getContext().getAuthentication();
            }
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }

            // First, check authorities (in case roles are provided via Spring Security)
            boolean hasAdminAuthority = false;
            try {
                if (authentication.getAuthorities() != null) {
                    for (GrantedAuthority ga : authentication.getAuthorities()) {
                        String a = ga == null || ga.getAuthority() == null ? "" : ga.getAuthority().trim();
                        if ("ADMIN".equalsIgnoreCase(a) || "ROLE_ADMIN".equalsIgnoreCase(a)) {
                            hasAdminAuthority = true;
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {
            }

            // Lookup user record (if possible) to validate role; fall back to authority check
            User admin = null;
            try {
                admin = entityManager.createQuery("select u from User u where lower(u.email)=lower(:e)", User.class)
                        .setParameter("e", authentication.getName())
                        .getResultStream().findFirst().orElse(null);
            } catch (Exception ignored) {
            }

            // Validate admin: either authority says ADMIN OR DB user role indicates ADMIN
            boolean okAdmin = hasAdminAuthority;
            if (!okAdmin && admin != null && admin.getRole() != null) {
                String role = admin.getRole().trim();
                if (role.toUpperCase().startsWith("ROLE_")) role = role.substring(5);
                okAdmin = "ADMIN".equalsIgnoreCase(role);
            }
            if (!okAdmin) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");
            }

            // Fetch withdrawal with seller joined to avoid lazy loading issue
            Withdrawal wd = entityManager.createQuery("SELECT w FROM Withdrawal w LEFT JOIN FETCH w.seller WHERE w.id = :id", Withdrawal.class)
                    .setParameter("id", id)
                    .getResultStream().findFirst().orElse(null);
            if (wd == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Withdrawal not found");
            }

            User seller = wd.getSeller();
            String sellerName = seller != null ? seller.getFullName() : "";
            String sellerEmail = seller != null ? seller.getEmail() : "";

            // Generate server-side VietQR URL (fallback null if mapping not found)
            String vietQrUrl = null;
            try {
                String bankName = wd.getBankName() == null ? "" : wd.getBankName();
                String accountNumber = wd.getAccountNumber();
                String token = "jYp8Yod"; // same placeholder token used in top-up
                String code = Bank.findCodeForBankName(bankName);
                if (code != null && accountNumber != null && !accountNumber.isBlank()) {
                    String filename = code + "-" + accountNumber + "-" + token + ".jpg";
                    String accountName = URLEncoder.encode(sellerName == null ? "" : sellerName, StandardCharsets.UTF_8);
                    String addInfo = URLEncoder.encode((wd.getId() != null ? ("WD#" + wd.getId()) : ("WD:" + accountNumber)), StandardCharsets.UTF_8);
                    vietQrUrl = "https://api.vietqr.io/image/" + filename + "?accountName=" + accountName + "&addInfo=" + addInfo;
                }
            } catch (Exception ignored) {}

            WithdrawalDetailResponse response = new WithdrawalDetailResponse(
                    sellerName,
                    sellerEmail,
                    wd.getAmount(),
                    wd.getBankName(),
                    wd.getAccountNumber(),
                    wd.getBranch(),
                    wd.getStatus(),
                    wd.getProofFile(),
                    vietQrUrl,
                    wd.getCreatedAt() != null ? wd.getCreatedAt().toString() : ""
            );

            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }

    @GetMapping("/my-notification")
    public String adminMyNotifications(Model model, Authentication authentication,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "10") int size,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(required = false) String search) {
        if (authentication != null && authentication.isAuthenticated()) {
            String email = authentication.getName();
            if (authentication instanceof OAuth2AuthenticationToken oauth2Token) {
                OAuth2User oauthUser = oauth2Token.getPrincipal();
                String mail = oauthUser.getAttribute("email");
                if (mail != null) email = mail;
            }
            Pageable pageable = PageRequest.of(page, size);
            String effectiveStatus = (status != null && status.equalsIgnoreCase("Read")) ? "Readed" : status;
            Page<com.mmo.entity.Notification> notificationPage = notificationService.getNotificationsForUser(email, effectiveStatus, search, pageable);
            model.addAttribute("pageNotifications", notificationPage.getContent());
            model.addAttribute("currentPage", notificationPage.getNumber());
            model.addAttribute("totalPages", notificationPage.getTotalPages());
            model.addAttribute("status", status);
            model.addAttribute("search", search);
        }
        model.addAttribute("pageTitle", "Notifications");
        model.addAttribute("body", "admin/my-notification");
        return "admin/layout";
    }

    @GetMapping("/topup-management")
    public String topupManagement(@RequestParam(name = "status", defaultValue = "All") String status,
                                  @RequestParam(name = "page", defaultValue = "0") int page,
                                  @RequestParam(name = "search", defaultValue = "") String search,
                                  Model model) {
        String queryStr = "SELECT c FROM CoinDeposit c WHERE 1=1";
        if (!"All".equalsIgnoreCase(status)) {
            queryStr += " AND LOWER(c.status) = LOWER(:status)";
        }
        if (!search.isBlank()) {
            queryStr += " AND (LOWER(c.user.fullName) LIKE LOWER(:search) OR LOWER(c.user.email) LIKE LOWER(:search) OR CAST(c.id AS string) LIKE :search OR LOWER(c.sepayReferenceCode) LIKE LOWER(:search))";
        }
        queryStr += " ORDER BY c.createdAt DESC";
        jakarta.persistence.Query query = entityManager.createQuery(queryStr, CoinDeposit.class);
        if (!"All".equalsIgnoreCase(status)) {
            query.setParameter("status", status);
        }
        if (!search.isBlank()) {
            query.setParameter("search", "%" + search + "%");
        }
        List<CoinDeposit> all = query.getResultList();
        int total = all.size();
        int totalPages = (int) Math.ceil((double) total / 10);
        List<CoinDeposit> pageList = all.stream()
                .skip((long) page * 10)
                .limit(10)
                .toList();

        model.addAttribute("coinDeposits", pageList);
        model.addAttribute("currentStatus", status);
        model.addAttribute("currentPage", page);
        model.addAttribute("currentSearch", search);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageTitle", "Top-up Management");
        model.addAttribute("body", "admin/topup-management");
        return "admin/layout";
    }

    // NEW: System configuration management

    @GetMapping("/system-config")
    public String systemConfig(Model model, Authentication authentication,
                               @RequestParam(name = "success", required = false) String success) {
        // Ensure default keys exist
        systemConfigurationService.ensureDefaults();
        // Load all
        model.addAttribute("configMap", systemConfigurationService.getAllAsMap());
        model.addAttribute("defaults", com.mmo.service.SystemConfigurationService.DEFAULTS);
        // Provide bank options from util.Bank
        model.addAttribute("bankOptions", Bank.listAll());
        if (success != null) {
            model.addAttribute("successMessage", "System configuration updated successfully.");
        }
        // Build SePay QR preview from current config values
        try {
            String bankName = systemConfigurationService.getStringValue(com.mmo.constant.SystemConfigKeys.SYSTEM_BANK_NAME, "MBBank");
            String accountNumber = systemConfigurationService.getStringValue(com.mmo.constant.SystemConfigKeys.SYSTEM_BANK_ACCOUNT_NUMBER, "0813302283");
            String sampleDes = "MMOPREVIEW123456";
            String url = "https://qr.sepay.vn/img?acc=" + URLEncoder.encode(accountNumber, StandardCharsets.UTF_8) +
                    "&bank=" + URLEncoder.encode(bankName, StandardCharsets.UTF_8) +
                    "&des=" + URLEncoder.encode(sampleDes, StandardCharsets.UTF_8);
            model.addAttribute("sepayQrPreview", url);
        } catch (Exception ignored) { }
        model.addAttribute("pageTitle", "System Configuration");
        model.addAttribute("body", "admin/system-config");
        return "admin/layout";
    }

    @PostMapping(value = "/system-config", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String updateSystemConfig(@RequestParam java.util.Map<String, String> params,
                                     Model model,
                                     Authentication authentication) {
        // Find admin user for updatedBy
        User admin = null;
        try {
            Authentication auth = authentication;
            if (auth == null || !auth.isAuthenticated()) auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                String email = auth.getName();
                if (auth instanceof OAuth2AuthenticationToken oauth2Token) {
                    OAuth2User oauthUser = oauth2Token.getPrincipal();
                    String mail = oauthUser.getAttribute("email");
                    if (mail != null) email = mail;
                }
                admin = entityManager.createQuery("select u from User u where lower(u.email)=lower(:e)", User.class)
                        .setParameter("e", email)
                        .getResultStream().findFirst().orElse(null);
            }
        } catch (Exception ignored) { }

        java.util.Map<String, String> errors = systemConfigurationService.updateConfigs(params, admin);
        if (errors.isEmpty()) {
            return "redirect:/admin/system-config?success=1";
        }
        // On errors, re-render with messages and bank options
        model.addAttribute("errors", errors);
        model.addAttribute("configMap", systemConfigurationService.getAllAsMap());
        model.addAttribute("defaults", com.mmo.service.SystemConfigurationService.DEFAULTS);
        model.addAttribute("bankOptions", Bank.listAll());
        model.addAttribute("pageTitle", "System Configuration");
        model.addAttribute("body", "admin/system-config");
        return "admin/layout";
    }

    @PostMapping(path = "/system-config", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String updateSystemConfigMultipart(@RequestParam java.util.Map<String, String> params,
                                              @RequestParam(name = "policy_complaint", required = false) MultipartFile policyComplaint,
                                              @RequestParam(name = "policy_seller_agreement", required = false) MultipartFile policySellerAgreement,
                                              Model model,
                                              Authentication authentication) {
        // Find admin user for updatedBy
        User admin = null;
        try {
            Authentication auth = authentication;
            if (auth == null || !auth.isAuthenticated()) auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                String email = auth.getName();
                if (auth instanceof OAuth2AuthenticationToken oauth2Token) {
                    OAuth2User oauthUser = oauth2Token.getPrincipal();
                    String mail = oauthUser.getAttribute("email");
                    if (mail != null) email = mail;
                }
                admin = entityManager.createQuery("select u from User u where lower(u.email)=lower(:e)", User.class)
                        .setParameter("e", email)
                        .getResultStream().findFirst().orElse(null);
            }
        } catch (Exception ignored) { }

        java.util.Map<String, String> errors = new java.util.LinkedHashMap<>();

        // Handle file uploads for policies
        try {
            // Use absolute path under application root to match WebMvcConfig's /uploads/** mapping
            String rootDir = System.getProperty("user.dir");
            java.nio.file.Path dir = java.nio.file.Paths.get(rootDir, "uploads", "policies");
            java.nio.file.Files.createDirectories(dir);

            if (policyComplaint != null && !policyComplaint.isEmpty()) {
                String original = policyComplaint.getOriginalFilename();
                String lower = original != null ? original.toLowerCase() : "";
                if (!(lower.endsWith(".pdf") || lower.endsWith(".doc") || lower.endsWith(".docx"))) {
                    errors.put("system_complaint", "Only PDF, DOC, or DOCX files are allowed.");
                } else {
                    String filename = "complaint-" + System.currentTimeMillis() + "-" + (original != null ? original.replaceAll("[^a-zA-Z0-9._-]", "_") : "file");
                    java.nio.file.Path target = dir.resolve(filename);
                    policyComplaint.transferTo(target.toFile());
                    String publicUrl = "/uploads/policies/" + filename;
                    params.put("system_complaint", publicUrl);
                }
            }

            if (policySellerAgreement != null && !policySellerAgreement.isEmpty()) {
                String original = policySellerAgreement.getOriginalFilename();
                String lower = original != null ? original.toLowerCase() : "";
                if (!(lower.endsWith(".pdf") || lower.endsWith(".doc") || lower.endsWith(".docx"))) {
                    errors.put("system_contract", "Only PDF, DOC, or DOCX files are allowed.");
                } else {
                    String filename = "seller-agreement-" + System.currentTimeMillis() + "-" + (original != null ? original.replaceAll("[^a-zA-Z0-9._-]", "_") : "file");
                    java.nio.file.Path target = dir.resolve(filename);
                    policySellerAgreement.transferTo(target.toFile());
                    String publicUrl = "/uploads/policies/" + filename;
                    params.put("system_contract", publicUrl);
                }
            }
        } catch (Exception ex) {
            errors.put("_global", "Failed to upload policy files: " + ex.getMessage());
        }

        // Merge validation from service
        java.util.Map<String, String> svcErrors = systemConfigurationService.updateConfigs(params, admin);
        if (!svcErrors.isEmpty()) errors.putAll(svcErrors);

        if (errors.isEmpty()) {
            return "redirect:/admin/system-config?success=1";
        }
        // On errors, re-render with messages and bank options
        model.addAttribute("errors", errors);
        model.addAttribute("configMap", systemConfigurationService.getAllAsMap());
        model.addAttribute("defaults", com.mmo.service.SystemConfigurationService.DEFAULTS);
        model.addAttribute("bankOptions", Bank.listAll());
        model.addAttribute("pageTitle", "System Configuration");
        model.addAttribute("body", "admin/system-config");
        return "admin/layout";
    }

    @GetMapping(value = "/coin-deposits/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> getCoinDepositDetail(@PathVariable Long id, Authentication auth) {
        try {
            // Auth check: try param auth then security context
            Authentication authentication = auth;
            if (authentication == null || !authentication.isAuthenticated()) {
                authentication = SecurityContextHolder.getContext().getAuthentication();
            }
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }

            // Basic admin validation (authorities or DB role)
            boolean hasAdminAuthority = false;
            try {
                if (authentication.getAuthorities() != null) {
                    for (GrantedAuthority ga : authentication.getAuthorities()) {
                        String a = ga == null || ga.getAuthority() == null ? "" : ga.getAuthority().trim();
                        if ("ADMIN".equalsIgnoreCase(a) || "ROLE_ADMIN".equalsIgnoreCase(a)) {
                            hasAdminAuthority = true;
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {}

            User admin = null;
            try {
                admin = entityManager.createQuery("select u from User u where lower(u.email)=lower(:e)", User.class)
                        .setParameter("e", authentication.getName())
                        .getResultStream().findFirst().orElse(null);
            } catch (Exception ignored) {}

            boolean okAdmin = hasAdminAuthority;
            if (!okAdmin && admin != null && admin.getRole() != null) {
                String role = admin.getRole().trim();
                if (role.toUpperCase().startsWith("ROLE_")) role = role.substring(5);
                okAdmin = "ADMIN".equalsIgnoreCase(role);
            }
            if (!okAdmin) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");

            CoinDeposit cd = entityManager.createQuery("SELECT c FROM CoinDeposit c LEFT JOIN FETCH c.user WHERE c.id = :id", CoinDeposit.class)
                    .setParameter("id", id)
                    .getResultStream().findFirst().orElse(null);
            if (cd == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Top-up not found");

            CoinDepositDetailResponse resp = new CoinDepositDetailResponse(
                    cd.getId(),
                    cd.getUser() != null ? cd.getUser().getId() : null,
                    cd.getUser() != null ? cd.getUser().getFullName() : "",
                    cd.getUser() != null ? cd.getUser().getEmail() : "",
                    cd.getAmount(),
                    cd.getCoinsAdded(),
                    cd.getStatus(),
                    cd.getSepayTransactionId(),
                    cd.getSepayReferenceCode(),
                    cd.getGateway(),
                    cd.getTransactionDate() != null ? cd.getTransactionDate().toString() : "",
                    cd.getContent(),
                    cd.getCreatedAt() != null ? cd.getCreatedAt().toString() : "",
                    cd.getUpdatedAt() != null ? cd.getUpdatedAt().toString() : ""
            );
            return ResponseEntity.ok(resp);
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }

    // ==================== CATEGORY MANAGEMENT ====================

    @GetMapping("/categories")
    @Transactional(readOnly = true)
    public String categoriesManagement(@RequestParam(name = "page", defaultValue = "0") int page,
                                       @RequestParam(name = "search", defaultValue = "") String search,
                                       @RequestParam(name = "type", defaultValue = "") String type,
                                       @RequestParam(name = "sort", defaultValue = "") String sort,
                                       Model model) {
        Pageable pageable = PageRequest.of(page, 10);
        
        // Build dynamic query WITHOUT JOIN FETCH for better performance
        StringBuilder jpql = new StringBuilder("SELECT c FROM Category c WHERE c.isDelete = false");
        
        if (search != null && !search.trim().isEmpty()) {
            jpql.append(" AND LOWER(c.name) LIKE LOWER(:search)");
        }
        
        if (type != null && !type.trim().isEmpty()) {
            jpql.append(" AND LOWER(c.type) = LOWER(:type)");
        }
        
        // Add sorting
        if (sort != null && !sort.isEmpty()) {
            if (sort.equals("products_asc")) {
                jpql.append(" ORDER BY SIZE(c.products) ASC");
            } else if (sort.equals("products_desc")) {
                jpql.append(" ORDER BY SIZE(c.products) DESC");
            }
        }
        
        // Create query
        TypedQuery<Category> query = entityManager.createQuery(jpql.toString(), Category.class);
        
        if (search != null && !search.trim().isEmpty()) {
            query.setParameter("search", "%" + search.trim() + "%");
        }
        
        if (type != null && !type.trim().isEmpty()) {
            query.setParameter("type", type.trim());
        }
        
        // Get all results first (needed for total count and sorting)
        List<Category> allResults = query.getResultList();
        int total = allResults.size();
        
        // Apply pagination manually
        int start = page * 10;
        int end = Math.min(start + 10, total);
        List<Category> categories = allResults.subList(start, end);
        
        // Efficiently load product counts for the current page only
        if (!categories.isEmpty()) {
            List<Long> categoryIds = categories.stream().map(Category::getId).toList();
            List<Object[]> counts = entityManager.createQuery(
                "SELECT p.category.id, COUNT(p) FROM Product p WHERE p.category.id IN :ids GROUP BY p.category.id",
                Object[].class
            ).setParameter("ids", categoryIds).getResultList();
            
            // Map counts to categories
            java.util.Map<Long, Long> countMap = counts.stream()
                .collect(java.util.stream.Collectors.toMap(
                    row -> (Long) row[0],
                    row -> (Long) row[1]
                ));
            
            // Set cached counts
            for (Category category : categories) {
                Long count = countMap.getOrDefault(category.getId(), 0L);
                category.setProductCountCache(count.intValue());
            }
        }
        
        Page<Category> categoryPage = new PageImpl<>(categories, pageable, total);

        model.addAttribute("categories", categoryPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("currentSearch", search);
        model.addAttribute("currentType", type);
        model.addAttribute("currentSort", sort);
        model.addAttribute("totalPages", categoryPage.getTotalPages());
        model.addAttribute("pageTitle", "Categories Management");
        model.addAttribute("body", "admin/categories");
        return "admin/layout";
    }

    @PostMapping("/categories")
    @ResponseBody
    public ResponseEntity<?> createCategory(@RequestBody CreateCategoryRequest request, Authentication auth) {
        try {
            if (auth == null || !auth.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }

            User admin = entityManager.createQuery("select u from User u where lower(u.email)=lower(:e)", User.class)
                    .setParameter("e", auth.getName())
                    .getResultStream().findFirst().orElse(null);

            if (admin == null || admin.getRole() == null || !admin.getRole().equalsIgnoreCase("ADMIN")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");
            }

            Category category = categoryService.createCategory(request, admin.getId());
            return ResponseEntity.ok(category);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }

    @GetMapping("/categories/{id}")
    @ResponseBody
    public ResponseEntity<?> getCategoryById(@PathVariable Long id) {
        try {
            Category category = categoryService.getCategoryById(id);
            return ResponseEntity.ok(category);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }

    @PutMapping("/categories/{id}")
    @ResponseBody
    public ResponseEntity<?> updateCategory(@PathVariable Long id,
                                           @RequestBody UpdateCategoryRequest request,
                                           Authentication auth) {
        try {
            if (auth == null || !auth.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }

            User admin = entityManager.createQuery("select u from User u where lower(u.email)=lower(:e)", User.class)
                    .setParameter("e", auth.getName())
                    .getResultStream().findFirst().orElse(null);

            if (admin == null || admin.getRole() == null || !admin.getRole().equalsIgnoreCase("ADMIN")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");
            }

            Category category = categoryService.updateCategory(id, request);
            return ResponseEntity.ok(category);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }

    @DeleteMapping("/categories/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteCategory(@PathVariable Long id, Authentication auth) {
        try {
            if (auth == null || !auth.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }

            User admin = entityManager.createQuery("select u from User u where lower(u.email)=lower(:e)", User.class)
                    .setParameter("e", auth.getName())
                    .getResultStream().findFirst().orElse(null);

            if (admin == null || admin.getRole() == null || !admin.getRole().equalsIgnoreCase("ADMIN")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");
            }

            categoryService.deleteCategory(id, admin.getId());
            return ResponseEntity.ok().body("Category deleted successfully");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }

    @PutMapping("/categories/{id}/toggle-status")
    @ResponseBody
    public ResponseEntity<?> toggleCategoryStatus(@PathVariable Long id, Authentication auth) {
        try {
            if (auth == null || !auth.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }

            User admin = entityManager.createQuery("select u from User u where lower(u.email)=lower(:e)", User.class)
                    .setParameter("e", auth.getName())
                    .getResultStream().findFirst().orElse(null);

            if (admin == null || admin.getRole() == null || !admin.getRole().equalsIgnoreCase("ADMIN")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");
            }

            Category category = categoryService.toggleCategoryStatus(id);
            return ResponseEntity.ok(category);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }

    @GetMapping("/categories/deleted")
    @Transactional(readOnly = true)
    public String deletedCategories(@RequestParam(name = "page", defaultValue = "0") int page,
                                    @RequestParam(name = "search", defaultValue = "") String search,
                                    @RequestParam(name = "type", defaultValue = "") String type,
                                    @RequestParam(name = "sort", defaultValue = "") String sort,
                                    Model model) {
        logger.info("=== ENTERED deletedCategories METHOD ===");
        logger.info("Parameters - page: {}, search: {}, type: {}, sort: {}", page, search, type, sort);
        
        try {
            logger.info("Step 1: Creating pageable");
            Pageable pageable = PageRequest.of(page, 10);
            
            logger.info("Step 2: Building query");
            // Simple query - load only category data
            StringBuilder jpql = new StringBuilder("SELECT c FROM Category c WHERE c.isDelete = true");
            
            if (search != null && !search.trim().isEmpty()) {
                jpql.append(" AND LOWER(c.name) LIKE LOWER(:search)");
            }
            
            if (type != null && !type.trim().isEmpty()) {
                jpql.append(" AND LOWER(c.type) = LOWER(:type)");
            }
            
            jpql.append(" ORDER BY c.updatedAt DESC");
            
            logger.info("Step 3: Query built: {}", jpql);

            // Create query
            TypedQuery<Category> query = entityManager.createQuery(jpql.toString(), Category.class);
            
            if (search != null && !search.trim().isEmpty()) {
                query.setParameter("search", "%" + search.trim() + "%");
            }
            
            if (type != null && !type.trim().isEmpty()) {
                query.setParameter("type", type.trim());
            }
            
            logger.info("Step 4: Executing query");
            // Get all results first
            List<Category> allResults = query.getResultList();
            logger.info("Step 5: Found {} deleted categories", allResults.size());
            
            int total = allResults.size();
            
            // Apply pagination manually
            int start = page * 10;
            int end = Math.min(start + 10, total);
            List<Category> categories = start < total ? allResults.subList(start, end) : new java.util.ArrayList<>();
            
            logger.info("Step 6: Paginated {} categories (from {} to {})", categories.size(), start, end);
            
            logger.info("Step 7: Loading lazy relationships");
            // Eager load relationships for the paginated results within transaction
            for (int i = 0; i < categories.size(); i++) {
                Category cat = categories.get(i);
                logger.debug("Loading details for category {}/{}: id={}, name={}", i+1, categories.size(), cat.getId(), cat.getName());
                try {
                    // Force load products collection to calculate count
                    if (cat.getProducts() != null) {
                        int productCount = cat.getProducts().size(); // This will trigger lazy load within transaction
                        logger.debug("Category {} has {} products", cat.getId(), productCount);
                    } else {
                        logger.debug("Category {} has null products collection", cat.getId());
                    }
                    
                    // Load deletedByUser if exists
                    if (cat.getDeletedBy() != null) {
                        logger.debug("Loading deletedByUser for category {} (deletedBy={})", cat.getId(), cat.getDeletedBy());
                        User deletedByUser = entityManager.find(User.class, cat.getDeletedBy());
                        if (deletedByUser != null) {
                            cat.setDeletedByUser(deletedByUser);
                            logger.debug("Loaded deletedByUser: {}", deletedByUser.getEmail());
                        } else {
                            logger.warn("DeletedByUser not found for ID: {}", cat.getDeletedBy());
                        }
                    }
                } catch (Exception e) {
                    logger.error("Failed to load details for category {}: {}", cat.getId(), e.getMessage(), e);
                }
            }
            
            logger.info("Step 8: Creating page object");
            Page<Category> categoryPage = new PageImpl<>(categories, pageable, total);

            logger.info("Step 9: Adding attributes to model");
            model.addAttribute("categories", categoryPage.getContent());
            model.addAttribute("currentPage", page);
            model.addAttribute("currentSearch", search);
            model.addAttribute("currentType", type);
            model.addAttribute("currentSort", sort);
            model.addAttribute("totalPages", categoryPage.getTotalPages());
            model.addAttribute("pageTitle", "Deleted Categories");
            model.addAttribute("body", "admin/deleted-categories");
            
            logger.info("Step 10: Returning view - admin/layout");
            return "admin/layout";
        } catch (Exception ex) {
            logger.error("=== ERROR in deletedCategories ===", ex);
            logger.error("Exception class: {}", ex.getClass().getName());
            logger.error("Exception message: {}", ex.getMessage());
            if (ex.getCause() != null) {
                logger.error("Cause: {}", ex.getCause().getMessage());
            }
            
            model.addAttribute("categories", new java.util.ArrayList<>());
            model.addAttribute("currentPage", 0);
            model.addAttribute("totalPages", 0);
            model.addAttribute("currentSearch", "");
            model.addAttribute("currentType", "");
            model.addAttribute("currentSort", "");
            model.addAttribute("pageTitle", "Deleted Categories");
            model.addAttribute("body", "admin/deleted-categories");
            model.addAttribute("errorMessage", "Failed to load deleted categories: " + ex.getMessage());
            return "admin/layout";
        }
    }

    @GetMapping("/categories/deleted/list")
    @ResponseBody
    public ResponseEntity<?> getDeletedCategoriesList() {
        try {
            Pageable pageable = PageRequest.of(0, 100);
            Page<Category> categoryPage = categoryService.getDeletedCategories(pageable);
            return ResponseEntity.ok(categoryPage.getContent());
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }

    @PostMapping("/categories/{id}/restore")
    @ResponseBody
    public ResponseEntity<?> restoreCategory(@PathVariable Long id, Authentication auth) {
        try {
            if (auth == null || !auth.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }

            User admin = entityManager.createQuery("select u from User u where lower(u.email)=lower(:e)", User.class)
                    .setParameter("e", auth.getName())
                    .getResultStream().findFirst().orElse(null);

            if (admin == null || admin.getRole() == null || !admin.getRole().equalsIgnoreCase("ADMIN")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");
            }

            Category category = categoryService.restoreCategory(id);
            return ResponseEntity.ok(category);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }

    // ==================== BLOG MANAGEMENT ====================

    @GetMapping("/blogs")
    @Transactional(readOnly = true)
    public String blogsManagement(@RequestParam(name = "page", defaultValue = "0") int page,
                                  @RequestParam(name = "search", defaultValue = "") String search,
                                  @RequestParam(name = "status", defaultValue = "") String status,
                                  @RequestParam(name = "sort", defaultValue = "") String sort,
                                  Model model) {
        Pageable pageable = PageRequest.of(page, 10);
        
        // Build dynamic query
        StringBuilder jpql = new StringBuilder("SELECT b FROM Blog b WHERE b.isDelete = false");
        
        if (search != null && !search.trim().isEmpty()) {
            jpql.append(" AND LOWER(b.title) LIKE LOWER(:search)");
        }
        
        if (status != null && !status.trim().isEmpty()) {
            if (status.equals("popular")) {
                jpql.append(" AND b.likes >= 100");
            } else if (status.equals("new")) {
                jpql.append(" AND b.createdAt >= :sevenDaysAgo");
            }
        }
        
        // Add sorting - avoid SIZE() for comments as it's slow
        if (sort != null && !sort.isEmpty()) {
            if (sort.equals("likes_asc")) {
                jpql.append(" ORDER BY b.likes ASC");
            } else if (sort.equals("likes_desc")) {
                jpql.append(" ORDER BY b.likes DESC");
            } else if (sort.equals("views_asc")) {
                jpql.append(" ORDER BY b.views ASC");
            } else if (sort.equals("views_desc")) {
                jpql.append(" ORDER BY b.views DESC");
            } else if (sort.equals("comments_asc") || sort.equals("comments_desc")) {
                // For comment sort, use subquery (slower but necessary)
                String direction = sort.equals("comments_asc") ? "ASC" : "DESC";
                jpql.append(" ORDER BY (SELECT COUNT(c) FROM Comment c WHERE c.blog.id = b.id) ").append(direction);
            } else {
                jpql.append(" ORDER BY b.createdAt DESC");
            }
        } else {
            jpql.append(" ORDER BY b.createdAt DESC");
        }
        
        // Separate COUNT query for better performance
        StringBuilder countJpql = new StringBuilder("SELECT COUNT(b) FROM Blog b WHERE b.isDelete = false");
        if (search != null && !search.trim().isEmpty()) {
            countJpql.append(" AND LOWER(b.title) LIKE LOWER(:search)");
        }
        if (status != null && !status.trim().isEmpty()) {
            if (status.equals("popular")) {
                countJpql.append(" AND b.likes >= 100");
            } else if (status.equals("new")) {
                countJpql.append(" AND b.createdAt >= :sevenDaysAgo");
            }
        }
        
        TypedQuery<Long> countQuery = entityManager.createQuery(countJpql.toString(), Long.class);
        if (search != null && !search.trim().isEmpty()) {
            countQuery.setParameter("search", "%" + search.trim() + "%");
        }
        if (status != null && status.equals("new")) {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.add(java.util.Calendar.DAY_OF_MONTH, -7);
            countQuery.setParameter("sevenDaysAgo", cal.getTime());
        }
        long total = countQuery.getSingleResult();
        
        // Get paginated results
        TypedQuery<com.mmo.entity.Blog> query = entityManager.createQuery(jpql.toString(), com.mmo.entity.Blog.class);
        
        if (search != null && !search.trim().isEmpty()) {
            query.setParameter("search", "%" + search.trim() + "%");
        }
        
        if (status != null && status.equals("new")) {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.add(java.util.Calendar.DAY_OF_MONTH, -7);
            query.setParameter("sevenDaysAgo", cal.getTime());
        }
        
        query.setFirstResult(page * 10);
        query.setMaxResults(10);
        List<com.mmo.entity.Blog> blogs = query.getResultList();
        
        Page<com.mmo.entity.Blog> blogPage = new PageImpl<>(blogs, pageable, total);

        model.addAttribute("blogs", blogPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("currentSearch", search);
        model.addAttribute("currentStatus", status);
        model.addAttribute("currentSort", sort);
        model.addAttribute("totalPages", blogPage.getTotalPages());
        model.addAttribute("pageTitle", "Blogs Management");
        model.addAttribute("body", "admin/blogs");
        return "admin/layout";
    }

    @PostMapping("/blogs")
    @ResponseBody
    public ResponseEntity<?> createBlog(@RequestBody com.mmo.dto.CreateBlogRequest request, Authentication auth) {
        try {
            if (auth == null || !auth.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }

            User admin = entityManager.createQuery("select u from User u where lower(u.email)=lower(:e)", User.class)
                    .setParameter("e", auth.getName())
                    .getResultStream().findFirst().orElse(null);

            if (admin == null || admin.getRole() == null || !admin.getRole().equalsIgnoreCase("ADMIN")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");
            }

            com.mmo.entity.Blog blog = blogService.createBlog(request, admin.getId());
            return ResponseEntity.ok(blog);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }

    @GetMapping("/blogs/{id}")
    @ResponseBody
    public ResponseEntity<?> getBlog(@PathVariable Long id, Authentication auth) {
        try {
            if (auth == null || !auth.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }

            User admin = entityManager.createQuery("select u from User u where lower(u.email)=lower(:e)", User.class)
                    .setParameter("e", auth.getName())
                    .getResultStream().findFirst().orElse(null);

            if (admin == null || admin.getRole() == null || !admin.getRole().equalsIgnoreCase("ADMIN")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");
            }

            com.mmo.entity.Blog blog = blogService.getBlogById(id);
            long commentsCount = blogService.getCommentsCount(id);
            com.mmo.dto.BlogResponse response = com.mmo.dto.BlogResponse.fromEntity(blog, commentsCount);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }

    @PutMapping("/blogs/{id}")
    @ResponseBody
    public ResponseEntity<?> updateBlog(@PathVariable Long id,
                                       @RequestBody com.mmo.dto.UpdateBlogRequest request,
                                       Authentication auth) {
        try {
            if (auth == null || !auth.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }

            User admin = entityManager.createQuery("select u from User u where lower(u.email)=lower(:e)", User.class)
                    .setParameter("e", auth.getName())
                    .getResultStream().findFirst().orElse(null);

            if (admin == null || admin.getRole() == null || !admin.getRole().equalsIgnoreCase("ADMIN")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");
            }

            com.mmo.entity.Blog blog = blogService.updateBlog(id, request);
            return ResponseEntity.ok(blog);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }

    @PutMapping("/blogs/{id}/toggle-status")
    @ResponseBody
    public ResponseEntity<?> toggleBlogStatus(@PathVariable Long id, Authentication auth) {
        try {
            if (auth == null || !auth.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }

            User admin = entityManager.createQuery("select u from User u where lower(u.email)=lower(:e)", User.class)
                    .setParameter("e", auth.getName())
                    .getResultStream().findFirst().orElse(null);

            if (admin == null || admin.getRole() == null || !admin.getRole().equalsIgnoreCase("ADMIN")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");
            }

            com.mmo.entity.Blog blog = blogService.toggleBlogStatus(id);
            String message = blog.isStatus() ? "Blog activated successfully" : "Blog deactivated successfully";
            return ResponseEntity.ok().body(Map.of("message", message, "status", blog.isStatus()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }

    // ==================== SHOP MANAGEMENT ====================

    @GetMapping("/shops")
    @Transactional(readOnly = true)
    public String shops(@RequestParam(name = "page", defaultValue = "0") int page,
                       @RequestParam(name = "search", defaultValue = "") String search,
                       @RequestParam(name = "sort", defaultValue = "") String sort,
                       Model model) {
        Pageable pageable = PageRequest.of(page, 10);
        
        // For rating sort, we need a different approach with subquery
        boolean isRatingSort = sort != null && (sort.equals("rating_asc") || sort.equals("rating_desc"));
        
        if (isRatingSort) {
            // Use optimized rating sort with single query
            String direction = sort.equals("rating_asc") ? "ASC" : "DESC";
            String jpql = "SELECT DISTINCT s FROM ShopInfo s LEFT JOIN FETCH s.user " +
                         "ORDER BY (SELECT COALESCE(AVG(CAST(r.rating AS double)), 0.0) " +
                         "FROM Review r WHERE r.product.seller.id = s.user.id) " + direction;
            
            if (search != null && !search.trim().isEmpty()) {
                jpql = "SELECT DISTINCT s FROM ShopInfo s LEFT JOIN FETCH s.user " +
                      "WHERE (LOWER(s.shopName) LIKE LOWER(:search) OR LOWER(s.user.fullName) LIKE LOWER(:search)) " +
                      "ORDER BY (SELECT COALESCE(AVG(CAST(r.rating AS double)), 0.0) " +
                      "FROM Review r WHERE r.product.seller.id = s.user.id) " + direction;
            }
            
            // Get total count
            StringBuilder countJpql = new StringBuilder("SELECT COUNT(s) FROM ShopInfo s");
            if (search != null && !search.trim().isEmpty()) {
                countJpql.append(" WHERE (LOWER(s.shopName) LIKE LOWER(:search) OR LOWER(s.user.fullName) LIKE LOWER(:search))");
            }
            
            TypedQuery<Long> countQuery = entityManager.createQuery(countJpql.toString(), Long.class);
            if (search != null && !search.trim().isEmpty()) {
                countQuery.setParameter("search", "%" + search.trim() + "%");
            }
            long total = countQuery.getSingleResult();
            
            // Get paginated results
            TypedQuery<ShopInfo> query = entityManager.createQuery(jpql, ShopInfo.class);
            if (search != null && !search.trim().isEmpty()) {
                query.setParameter("search", "%" + search.trim() + "%");
            }
            query.setFirstResult(page * 10);
            query.setMaxResults(10);
            List<ShopInfo> shopInfos = query.getResultList();
            
            // Build response with batch-loaded data
            List<com.mmo.dto.ShopResponse> shops = buildShopResponses(shopInfos);
            
            Page<com.mmo.dto.ShopResponse> shopPage = new PageImpl<>(shops, pageable, total);
            
            model.addAttribute("shops", shopPage.getContent());
            model.addAttribute("currentPage", page);
            model.addAttribute("currentSearch", search);
            model.addAttribute("currentSort", sort);
            model.addAttribute("totalPages", shopPage.getTotalPages());
            model.addAttribute("pageTitle", "Shop Management");
            model.addAttribute("body", "admin/shops");
            return "admin/layout";
        }
        
        // Normal sort (commission, or default by ID)
        StringBuilder jpql = new StringBuilder(
            "SELECT DISTINCT s FROM ShopInfo s LEFT JOIN FETCH s.user"
        );
        
        if (search != null && !search.trim().isEmpty()) {
            jpql.append(" WHERE (LOWER(s.shopName) LIKE LOWER(:search) OR LOWER(s.user.fullName) LIKE LOWER(:search))");
        }
        
        // Add simple sorting
        if (sort != null && !sort.isEmpty()) {
            if (sort.equals("commission_asc")) {
                jpql.append(" ORDER BY s.commission ASC");
            } else if (sort.equals("commission_desc")) {
                jpql.append(" ORDER BY s.commission DESC");
            } else {
                jpql.append(" ORDER BY s.id DESC");
            }
        } else {
            jpql.append(" ORDER BY s.id DESC");
        }
        
        // Get total count efficiently with separate COUNT query
        StringBuilder countJpql = new StringBuilder("SELECT COUNT(s) FROM ShopInfo s");
        if (search != null && !search.trim().isEmpty()) {
        }
        
        TypedQuery<Long> countQuery = entityManager.createQuery(countJpql.toString(), Long.class);
        if (search != null && !search.trim().isEmpty()) {
            countQuery.setParameter("search", "%" + search.trim() + "%");
        }
        long total = countQuery.getSingleResult();
        
        // Get paginated results
        TypedQuery<ShopInfo> query = entityManager.createQuery(jpql.toString(), ShopInfo.class);
        if (search != null && !search.trim().isEmpty()) {
            query.setParameter("search", "%" + search.trim() + "%");
        }
        query.setFirstResult(page * 10);
        query.setMaxResults(10);
        List<ShopInfo> shopInfos = query.getResultList();
        
        // Build response with batch-loaded data
        List<com.mmo.dto.ShopResponse> shops = buildShopResponses(shopInfos);
        
        Page<com.mmo.dto.ShopResponse> shopPage = new PageImpl<>(shops, pageable, total);

        model.addAttribute("shops", shopPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("currentSearch", search);
        model.addAttribute("currentSort", sort);
        model.addAttribute("totalPages", shopPage.getTotalPages());
        model.addAttribute("pageTitle", "Shop Management");
        model.addAttribute("body", "admin/shops");
        return "admin/layout";
    }
    
    // Helper method to build shop responses with batch-loaded data
    private List<com.mmo.dto.ShopResponse> buildShopResponses(List<ShopInfo> shopInfos) {
        List<com.mmo.dto.ShopResponse> shops = new java.util.ArrayList<>();
        if (!shopInfos.isEmpty()) {
            List<Long> sellerIds = shopInfos.stream()
                .map(s -> s.getUser().getId())
                .collect(java.util.stream.Collectors.toList());
            
            // Batch query for product counts
            List<Object[]> productCounts = entityManager.createQuery(
                "SELECT p.seller.id, COUNT(p) FROM Product p WHERE p.seller.id IN :ids GROUP BY p.seller.id",
                Object[].class
            ).setParameter("ids", sellerIds).getResultList();
            
            java.util.Map<Long, Long> countMap = productCounts.stream()
                .collect(java.util.stream.Collectors.toMap(
                    row -> (Long) row[0],
                    row -> (Long) row[1]
                ));
            
            // Batch query for ratings
            List<Object[]> ratings = entityManager.createQuery(
                "SELECT r.product.seller.id, AVG(CAST(r.rating AS double)) FROM Review r WHERE r.product.seller.id IN :ids GROUP BY r.product.seller.id",
                Object[].class
            ).setParameter("ids", sellerIds).getResultList();
            
            java.util.Map<Long, Double> ratingMap = ratings.stream()
                .collect(java.util.stream.Collectors.toMap(
                    row -> (Long) row[0],
                    row -> (Double) row[1]
                ));
            
            // Build response with cached data
            for (ShopInfo shop : shopInfos) {
                Long sellerId = shop.getUser().getId();
                Long productCount = countMap.getOrDefault(sellerId, 0L);
                Double rating = ratingMap.getOrDefault(sellerId, 0.0);
                shops.add(com.mmo.dto.ShopResponse.fromEntity(shop, productCount, rating));
            }
        }
        return shops;
    }

    @PutMapping("/shops/{id}/commission")
    @ResponseBody
    public ResponseEntity<?> updateCommission(@PathVariable Long id,
                                             @RequestBody com.mmo.dto.UpdateCommissionRequest request,
                                             Authentication auth) {
        try {
            if (auth == null || !auth.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }

            User admin = entityManager.createQuery("select u from User u where lower(u.email)=lower(:e)", User.class)
                    .setParameter("e", auth.getName())
                    .getResultStream().findFirst().orElse(null);

            if (admin == null || admin.getRole() == null || !admin.getRole().equalsIgnoreCase("ADMIN")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");
            }

            shopService.updateCommission(id, request, admin.getId());
            return ResponseEntity.ok().body("Commission updated successfully");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }

    @GetMapping("/shops/{id}/detail")
    @ResponseBody
    @Transactional(readOnly = true)
    public ResponseEntity<?> getShopDetail(@PathVariable Long id) {
        try {
            // Find shop by ID
            ShopInfo shop = entityManager.find(ShopInfo.class, id);
            if (shop == null || shop.isDelete()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Shop not found");
            }

            // Get product count using seller_id
            Long productCount = entityManager.createQuery(
                "SELECT COUNT(p) FROM Product p WHERE p.seller.id = :sellerId AND p.isDelete = false", 
                Long.class)
                .setParameter("sellerId", shop.getUser().getId())
                .getSingleResult();

            // Get average rating - Review -> Product -> Seller
            Double avgRating = entityManager.createQuery(
                "SELECT AVG(CAST(r.rating AS double)) FROM Review r WHERE r.product.seller.id = :sellerId AND r.isDelete = false", 
                Double.class)
                .setParameter("sellerId", shop.getUser().getId())
                .getSingleResult();

            // Build response
            java.util.Map<String, Object> response = new java.util.HashMap<>();
            response.put("id", shop.getId());
            response.put("shopName", shop.getShopName());
            response.put("status", shop.getUser() != null ? shop.getUser().getShopStatus() : "Inactive");
            response.put("productCount", productCount);
            response.put("rating", avgRating != null ? avgRating : 0.0);
            response.put("commission", shop.getCommission());
            response.put("sellerId", shop.getUser() != null ? shop.getUser().getId() : null);
            response.put("sellerName", shop.getUser() != null ? shop.getUser().getFullName() : "-");
            response.put("sellerEmail", shop.getUser() != null ? shop.getUser().getEmail() : "-");

            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            logger.error("Error getting shop detail for ID: {}", id, ex);
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }

    @DeleteMapping("/shops/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteShop(@PathVariable Long id, Authentication auth) {
        try {
            if (auth == null || !auth.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }

            User admin = entityManager.createQuery("select u from User u where lower(u.email)=lower(:e)", User.class)
                    .setParameter("e", auth.getName())
                    .getResultStream().findFirst().orElse(null);

            if (admin == null || admin.getRole() == null || !admin.getRole().equalsIgnoreCase("ADMIN")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");
            }

            shopService.deleteShop(id, admin.getId());
            return ResponseEntity.ok().body("Shop deleted successfully");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }

    @PutMapping("/shops/{id}/toggle-status")
    @ResponseBody
    @Transactional
    public ResponseEntity<?> toggleShopStatus(@PathVariable Long id, Authentication auth) {
        try {
            if (auth == null || !auth.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }

            User admin = entityManager.createQuery("select u from User u where lower(u.email)=lower(:e)", User.class)
                    .setParameter("e", auth.getName())
                    .getResultStream().findFirst().orElse(null);

            if (admin == null || admin.getRole() == null || !admin.getRole().equalsIgnoreCase("ADMIN")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");
            }

            // Find the shop by id
            ShopInfo shop = entityManager.createQuery(
                "SELECT s FROM ShopInfo s WHERE s.id = :id", ShopInfo.class)
                .setParameter("id", id)
                .getSingleResult();

            // Toggle the isDelete status
            boolean currentIsDelete = shop.isDelete();
            shop.setDelete(!currentIsDelete);

            if (!currentIsDelete) {
                // If we're setting isDelete to true (deactivating), set deletedBy
                shop.setDeletedBy(admin);
            } else {
                // If we're setting isDelete to false (activating), clear deletedBy
                shop.setDeletedBy(null);
            }

            entityManager.merge(shop);

            String action = currentIsDelete ? "activated" : "deactivated";
            return ResponseEntity.ok().body("Shop " + action + " successfully");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }

    @GetMapping("/shops/deleted")
    public String deletedShops(@RequestParam(name = "page", defaultValue = "0") int page, Model model) {
        Pageable pageable = PageRequest.of(page, 10);
        Page<com.mmo.dto.ShopResponse> shopPage = shopService.getDeletedShops(pageable);

        model.addAttribute("shops", shopPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", shopPage.getTotalPages());
        model.addAttribute("pageTitle", "Deleted Shops");
        model.addAttribute("body", "admin/deleted-shops");
        return "admin/layout";
    }

    @GetMapping("/shops/deleted/list")
    @ResponseBody
    public ResponseEntity<?> getDeletedShopsList() {
        try {
            Pageable pageable = PageRequest.of(0, 100); // Get up to 100 deleted shops
            Page<com.mmo.dto.ShopResponse> shopPage = shopService.getDeletedShops(pageable);
            return ResponseEntity.ok(shopPage.getContent());
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }

    @PostMapping("/shops/{id}/restore")
    @ResponseBody
    public ResponseEntity<?> restoreShop(@PathVariable Long id, Authentication auth) {
        try {
            if (auth == null || !auth.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }

            User admin = entityManager.createQuery("select u from User u where lower(u.email)=lower(:e)", User.class)
                    .setParameter("e", auth.getName())
                    .getResultStream().findFirst().orElse(null);

            if (admin == null || admin.getRole() == null || !admin.getRole().equalsIgnoreCase("ADMIN")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");
            }

            shopService.restoreShop(id);
            return ResponseEntity.ok().body("Shop restored successfully");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }

    // ==================== USER MANAGEMENT ====================

    @GetMapping("/users")
    @Transactional(readOnly = true)
    public String usersManagement(@RequestParam(name = "page", defaultValue = "0") int page,
                                  @RequestParam(name = "search", defaultValue = "") String search,
                                  @RequestParam(name = "role", defaultValue = "") String role,
                                  @RequestParam(name = "shopStatus", defaultValue = "") String shopStatus,
                                  @RequestParam(name = "sort", defaultValue = "") String sort,
                                  Model model) {
        Pageable pageable = PageRequest.of(page, 10);

        // Build dynamic query
        StringBuilder jpql = new StringBuilder("SELECT u FROM User u WHERE u.isDelete = false");

        if (search != null && !search.trim().isEmpty()) {
            jpql.append(" AND (LOWER(u.fullName) LIKE LOWER(:search) OR LOWER(u.email) LIKE LOWER(:search))");
        }

        if (role != null && !role.trim().isEmpty()) {
            jpql.append(" AND LOWER(u.role) = LOWER(:role)");
        }

        if (shopStatus != null && !shopStatus.trim().isEmpty()) {
            jpql.append(" AND LOWER(u.shopStatus) = LOWER(:shopStatus)");
        }

        // Add sorting
        if (sort != null && !sort.isEmpty()) {
            if (sort.equals("role_asc")) {
                jpql.append(" ORDER BY u.role ASC");
            } else if (sort.equals("role_desc")) {
                jpql.append(" ORDER BY u.role DESC");
            } else if (sort.equals("shopStatus_asc")) {
                jpql.append(" ORDER BY u.shopStatus ASC");
            } else if (sort.equals("shopStatus_desc")) {
                jpql.append(" ORDER BY u.shopStatus DESC");
            } else if (sort.equals("coins_asc")) {
                jpql.append(" ORDER BY u.coins ASC");
            } else if (sort.equals("coins_desc")) {
                jpql.append(" ORDER BY u.coins DESC");
            } else {
                jpql.append(" ORDER BY u.createdAt DESC");
            }
        } else {
            jpql.append(" ORDER BY u.createdAt DESC");
        }

        // Get total count
        StringBuilder countJpql = new StringBuilder("SELECT COUNT(u) FROM User u WHERE u.isDelete = false");
        if (search != null && !search.trim().isEmpty()) {
            countJpql.append(" AND (LOWER(u.fullName) LIKE LOWER(:search) OR LOWER(u.email) LIKE LOWER(:search))");
        }
        if (role != null && !role.trim().isEmpty()) {
            countJpql.append(" AND LOWER(u.role) = LOWER(:role)");
        }
        if (shopStatus != null && !shopStatus.trim().isEmpty()) {
            countJpql.append(" AND LOWER(u.shopStatus) = LOWER(:shopStatus)");
        }

        TypedQuery<Long> countQuery = entityManager.createQuery(countJpql.toString(), Long.class);
        if (search != null && !search.trim().isEmpty()) {
            countQuery.setParameter("search", "%" + search.trim() + "%");
        }
        if (role != null && !role.trim().isEmpty()) {
            countQuery.setParameter("role", role.trim());
        }
        if (shopStatus != null && !shopStatus.trim().isEmpty()) {
            countQuery.setParameter("shopStatus", shopStatus.trim());
        }
        long total = countQuery.getSingleResult();

        // Get paginated results
        TypedQuery<User> query = entityManager.createQuery(jpql.toString(), User.class);
        if (search != null && !search.trim().isEmpty()) {
            query.setParameter("search", "%" + search.trim() + "%");
        }
        if (role != null && !role.trim().isEmpty()) {
            query.setParameter("role", role.trim());
        }
        if (shopStatus != null && !shopStatus.trim().isEmpty()) {
            query.setParameter("shopStatus", shopStatus.trim());
        }

        query.setFirstResult(page * 10);
        query.setMaxResults(10);
        List<User> users = query.getResultList();

        Page<User> userPage = new PageImpl<>(users, pageable, total);

        model.addAttribute("users", userPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("currentSearch", search);
        model.addAttribute("currentRole", role);
        model.addAttribute("currentShopStatus", shopStatus);
        model.addAttribute("currentSort", sort);
        model.addAttribute("totalPages", userPage.getTotalPages());
        model.addAttribute("pageTitle", "User Management");
        model.addAttribute("body", "admin/users");
        return "admin/layout";
    }

    @GetMapping("/users/banned")
    @Transactional(readOnly = true)
    public String bannedUsers(@RequestParam(name = "page", defaultValue = "0") int page,
                              @RequestParam(name = "search", defaultValue = "") String search,
                              Model model) {
        Pageable pageable = PageRequest.of(page, 10);

        // Build dynamic query
        StringBuilder jpql = new StringBuilder("SELECT u FROM User u WHERE u.isDelete = true");

        if (search != null && !search.trim().isEmpty()) {
            jpql.append(" AND (LOWER(u.fullName) LIKE LOWER(:search) OR LOWER(u.email) LIKE LOWER(:search))");
        }

        jpql.append(" ORDER BY u.updatedAt DESC");

        // Get total count
        StringBuilder countJpql = new StringBuilder("SELECT COUNT(u) FROM User u WHERE u.isDelete = true");
        if (search != null && !search.trim().isEmpty()) {
            countJpql.append(" AND (LOWER(u.fullName) LIKE LOWER(:search) OR LOWER(u.email) LIKE LOWER(:search))");
        }

        TypedQuery<Long> countQuery = entityManager.createQuery(countJpql.toString(), Long.class);
        if (search != null && !search.trim().isEmpty()) {
            countQuery.setParameter("search", "%" + search.trim() + "%");
        }
        long total = countQuery.getSingleResult();

        // Get paginated results
        TypedQuery<User> query = entityManager.createQuery(jpql.toString(), User.class);
        if (search != null && !search.trim().isEmpty()) {
            query.setParameter("search", "%" + search.trim() + "%");
        }

        query.setFirstResult(page * 10);
        query.setMaxResults(10);
        List<User> users = query.getResultList();

        // Load deletedBy user information
        for (User user : users) {
            if (user.getDeletedBy() != null) {
                User deletedByUser = entityManager.find(User.class, user.getDeletedBy());
                if (deletedByUser != null) {
                    user.setDeletedByUser(deletedByUser);
                }
            }
        }

        Page<User> userPage = new PageImpl<>(users, pageable, total);

        model.addAttribute("users", userPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("currentSearch", search);
        model.addAttribute("totalPages", userPage.getTotalPages());
        model.addAttribute("pageTitle", "Banned Users");
        model.addAttribute("body", "admin/banned-users");
        return "admin/layout";
    }

    @GetMapping(value = "/users/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> getUserDetail(@PathVariable Long id, Authentication auth) {
        try {
            if (auth == null || !auth.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }

            User admin = entityManager.createQuery("select u from User u where lower(u.email)=lower(:e)", User.class)
                    .setParameter("e", auth.getName())
                    .getResultStream().findFirst().orElse(null);

            if (admin == null || admin.getRole() == null || !admin.getRole().equalsIgnoreCase("ADMIN")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");
            }

            User user = entityManager.find(User.class, id);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
            }

            // Build response
            java.util.Map<String, Object> response = new java.util.HashMap<>();
            response.put("id", user.getId());
            response.put("email", user.getEmail());
            response.put("fullName", user.getFullName());
            response.put("role", user.getRole());
            response.put("phone", user.getPhone());
            response.put("shopStatus", user.getShopStatus());
            response.put("coins", user.getCoins());
            response.put("verified", user.isVerified());
            response.put("isDelete", user.isDelete());
            response.put("createdAt", user.getCreatedAt() != null ? user.getCreatedAt().toString() : "");
            response.put("updatedAt", user.getUpdatedAt() != null ? user.getUpdatedAt().toString() : "");

            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }

    @PutMapping("/users/{id}/ban")
    @ResponseBody
    @Transactional
    public ResponseEntity<?> banUser(@PathVariable Long id, Authentication auth) {
        try {
            if (auth == null || !auth.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }

            User admin = entityManager.createQuery("select u from User u where lower(u.email)=lower(:e)", User.class)
                    .setParameter("e", auth.getName())
                    .getResultStream().findFirst().orElse(null);

            if (admin == null || admin.getRole() == null || !admin.getRole().equalsIgnoreCase("ADMIN")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");
            }

            User user = entityManager.find(User.class, id);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
            }

            if (user.getRole() != null && user.getRole().equalsIgnoreCase("ADMIN")) {
                return ResponseEntity.badRequest().body("Cannot ban admin users");
            }

            if (user.isDelete()) {
                return ResponseEntity.badRequest().body("User is already banned");
            }

            user.setDelete(true);
            user.setDeletedBy(admin.getId());
            entityManager.merge(user);

            // Create notification for banned user
            notificationService.createNotificationForUser(user.getId(), "Account Banned",
                "Your account has been banned by administrator. Please contact support for more information.");

            return ResponseEntity.ok().body("User banned successfully");
        } catch (Exception ex) {
            logger.error("Error banning user {}: {}", id, ex.getMessage(), ex);
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }

    @PutMapping("/users/{id}/unban")
    @ResponseBody
    @Transactional
    public ResponseEntity<?> unbanUser(@PathVariable Long id, Authentication auth) {
        try {
            if (auth == null || !auth.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }

            User admin = entityManager.createQuery("select u from User u where lower(u.email)=lower(:e)", User.class)
                    .setParameter("e", auth.getName())
                    .getResultStream().findFirst().orElse(null);

            if (admin == null || admin.getRole() == null || !admin.getRole().equalsIgnoreCase("ADMIN")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");
            }

            User user = entityManager.find(User.class, id);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
            }

            if (!user.isDelete()) {
                return ResponseEntity.badRequest().body("User is not banned");
            }

            user.setDelete(false);
            user.setDeletedBy(null);
            entityManager.merge(user);

            // Create notification for unbanned user
            notificationService.createNotificationForUser(user.getId(), "Account Unbanned",
                "Your account has been unbanned. You can now access the platform again.");

            return ResponseEntity.ok().body("User unbanned successfully");
        } catch (Exception ex) {
            logger.error("Error unbanning user {}: {}", id, ex.getMessage(), ex);
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }

    // ==================== CHANGE PASSWORD ====================

    @GetMapping("/change-password")
    public String changePasswordPage(Model model) {
        model.addAttribute("pageTitle", "Change Password");
        model.addAttribute("body", "admin/change-password");
        return "admin/layout";
    }

    @PostMapping("/change-password")
    @Transactional
    public String changePassword(@ModelAttribute com.mmo.dto.ChangePasswordRequest request,
                                Authentication auth,
                                RedirectAttributes redirectAttributes) {
        try {
            if (auth == null || !auth.isAuthenticated()) {
                redirectAttributes.addFlashAttribute("errorMessage", "You must be logged in");
                return "redirect:/authen/login";
            }

            User admin = entityManager.createQuery("select u from User u where lower(u.email)=lower(:e)", User.class)
                    .setParameter("e", auth.getName())
                    .getResultStream().findFirst().orElse(null);

            if (admin == null || admin.getRole() == null || !admin.getRole().equalsIgnoreCase("ADMIN")) {
                redirectAttributes.addFlashAttribute("errorMessage", "Access denied");
                return "redirect:/";
            }

            // Validate current password
            if (!org.springframework.security.crypto.bcrypt.BCrypt.checkpw(
                    request.getCurrentPassword(), admin.getPassword())) {
                redirectAttributes.addFlashAttribute("errorMessage", "Current password is incorrect");
                return "redirect:/admin/change-password";
            }

            // Validate new password
            if (request.getNewPassword() == null || request.getNewPassword().length() < 6) {
                redirectAttributes.addFlashAttribute("errorMessage", "New password must be at least 6 characters");
                return "redirect:/admin/change-password";
            }

            if (!request.getNewPassword().equals(request.getConfirmPassword())) {
                redirectAttributes.addFlashAttribute("errorMessage", "New password and confirm password do not match");
                return "redirect:/admin/change-password";
            }

            // Update password
            admin.setPassword(org.springframework.security.crypto.bcrypt.BCrypt.hashpw(
                    request.getNewPassword(), org.springframework.security.crypto.bcrypt.BCrypt.gensalt()));
            entityManager.merge(admin);

            redirectAttributes.addFlashAttribute("successMessage", "Password changed successfully!");
            return "redirect:/admin/change-password";
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to change password: " + ex.getMessage());
            return "redirect:/admin/change-password";
        }
    }
}

