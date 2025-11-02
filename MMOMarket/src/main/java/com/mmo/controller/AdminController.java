package com.mmo.controller;

import com.mmo.dto.ProcessWithdrawalRequest;
import com.mmo.dto.AdminWithdrawalResponse;
import com.mmo.dto.WithdrawalDetailResponse;
import com.mmo.dto.CoinDepositDetailResponse;
import com.mmo.entity.User;
import com.mmo.entity.Withdrawal;
import com.mmo.entity.CoinDeposit;
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
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

// Added imports
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.GrantedAuthority;
import com.mmo.util.Bank;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Controller
@RequestMapping("/admin")
@SuppressWarnings("unchecked")
public class AdminController {

    @Autowired
    private com.mmo.service.NotificationService notificationService;

    @Autowired
    private com.mmo.service.WithdrawalService withdrawalService;

    @Autowired
    private com.mmo.service.EmailService emailService;

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
            java.nio.file.Path dir = java.nio.file.Paths.get("uploads", "policies");
            java.nio.file.Files.createDirectories(dir);

            if (policyComplaint != null && !policyComplaint.isEmpty()) {
                String original = policyComplaint.getOriginalFilename();
                String lower = original != null ? original.toLowerCase() : "";
                if (!(lower.endsWith(".pdf") || lower.endsWith(".doc") || lower.endsWith(".docx"))) {
                    errors.put("policy.complaint.url", "Only PDF, DOC, or DOCX files are allowed.");
                } else {
                    String filename = "complaint-" + System.currentTimeMillis() + "-" + original.replaceAll("[^a-zA-Z0-9._-]", "_");
                    java.nio.file.Path target = dir.resolve(filename);
                    policyComplaint.transferTo(target.toFile());
                    String publicUrl = "/uploads/policies/" + filename;
                    params.put("policy.complaint.url", publicUrl);
                }
            }

            if (policySellerAgreement != null && !policySellerAgreement.isEmpty()) {
                String original = policySellerAgreement.getOriginalFilename();
                String lower = original != null ? original.toLowerCase() : "";
                if (!(lower.endsWith(".pdf") || lower.endsWith(".doc") || lower.endsWith(".docx"))) {
                    errors.put("policy.seller_agreement.url", "Only PDF, DOC, or DOCX files are allowed.");
                } else {
                    String filename = "seller-agreement-" + System.currentTimeMillis() + "-" + original.replaceAll("[^a-zA-Z0-9._-]", "_");
                    java.nio.file.Path target = dir.resolve(filename);
                    policySellerAgreement.transferTo(target.toFile());
                    String publicUrl = "/uploads/policies/" + filename;
                    params.put("policy.seller_agreement.url", publicUrl);
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
}

