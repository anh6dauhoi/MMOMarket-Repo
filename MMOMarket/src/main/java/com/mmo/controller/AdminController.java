package com.mmo.controller;


import com.mmo.dto.ProcessWithdrawalRequest;
import com.mmo.dto.AdminWithdrawalResponse;
import com.mmo.dto.WithdrawalDetailResponse;
import com.mmo.dto.CoinDepositDetailResponse;
import com.mmo.dto.TransactionDetailResponse;
import com.mmo.entity.*;
import com.mmo.entity.ShopPointPurchase;

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

import com.mmo.dto.*;
import com.mmo.entity.User;
import com.mmo.entity.Withdrawal;
import com.mmo.entity.CoinDeposit;
import jakarta.persistence.TypedQuery;

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
import java.util.*;
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
import com.mmo.service.ChatService;
import com.mmo.service.AuthService;

import com.mmo.repository.ShopPointPurchaseRepository;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
@SuppressWarnings("unchecked")
public class AdminController {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AdminController.class);

    @Autowired
    private com.mmo.service.NotificationService notificationService;

    @Autowired
    private com.mmo.service.WithdrawalService withdrawalService;

    @Autowired
    private com.mmo.service.EmailService emailService;

    @Autowired
    private com.mmo.service.SystemConfigurationService systemConfigurationService;

    @Autowired
    private com.mmo.service.CategoryService categoryService;

    @Autowired
    private com.mmo.service.BlogService blogService;

    @Autowired
    private com.mmo.service.ShopService shopService;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private ChatService chatService;

    @Autowired
    private AuthService authService;

    @Autowired
    private ShopPointPurchaseRepository shopPointPurchaseRepository;

    @Autowired
    private com.mmo.service.DashboardService dashboardService;

    @Autowired
    private com.mmo.repository.ComplaintRepository complaintRepository;

    @Autowired
    private com.mmo.repository.TransactionRepository transactionRepository;

    @Autowired
    private com.mmo.repository.ChatRepository chatRepository;

    @Autowired
    private com.mmo.service.SepayApiService sepayApiService;

    // NEW: Admin Dashboard route
    @GetMapping({"", "/"})
    public String dashboard(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            Model model) {

        java.util.Date fromDate = null;
        java.util.Date toDate = null;

        // Parse date parameters
        if (from != null && !from.isEmpty() && to != null && !to.isEmpty()) {
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
                fromDate = sdf.parse(from);

                // Add one day to toDate to include the entire day
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.setTime(sdf.parse(to));
                cal.add(java.util.Calendar.DAY_OF_MONTH, 1);
                toDate = cal.getTime();

                model.addAttribute("from", from);
                model.addAttribute("to", to);
            } catch (Exception e) {
                logger.error("Error parsing dates", e);
            }
        }

        // Get dashboard data
        DashboardTotalsDTO totals = dashboardService.getDashboardTotals(fromDate, toDate);
        List<TopSellerDTO> topSellers = dashboardService.getTopSellers(fromDate, toDate);
        List<TopProductDTO> topProducts = dashboardService.getTopProducts(fromDate, toDate);
        List<TopPointPurchaseDTO> topPointPurchases = dashboardService.getTopPointPurchases(fromDate, toDate);
        List<TopBuyerDTO> topBuyers = dashboardService.getTopBuyers(fromDate, toDate);
        Map<String, Object> revenueData = dashboardService.getRevenueTimeSeries(fromDate, toDate);

        // Add to model - ensure lists are never null
        model.addAttribute("totals", totals != null ? totals : new DashboardTotalsDTO(0L, 0L, 0L, 0L, "N/A"));
        model.addAttribute("topSellers", topSellers != null ? topSellers : new java.util.ArrayList<>());
        model.addAttribute("topProducts", topProducts != null ? topProducts : new java.util.ArrayList<>());
        model.addAttribute("topPointPurchases", topPointPurchases != null ? topPointPurchases : new java.util.ArrayList<>());
        model.addAttribute("topBuyers", topBuyers != null ? topBuyers : new java.util.ArrayList<>());
        model.addAttribute("labels", revenueData != null && revenueData.get("labels") != null ? revenueData.get("labels") : new java.util.ArrayList<>());
        model.addAttribute("revenueSeries", revenueData != null && revenueData.get("series") != null ? revenueData.get("series") : new java.util.ArrayList<>());

        model.addAttribute("pageTitle", "Admin Dashboard");
        model.addAttribute("body", "admin/dashboard");
        return "admin/layout";
    }

    // NEW: Admin Chat route
    @GetMapping("/chat")
    public String adminChat(@RequestParam(value = "partnerId", required = false) Long partnerId,
                            Model model, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        User currentUser = authService.findByEmail(authentication.getName());
        if (currentUser == null) {
            return "redirect:/login";
        }

        // Get all conversations for admin
        List<ConversationSummaryDto> conversations = chatService.listConversations(currentUser.getId());

        model.addAttribute("conversations", conversations);
        model.addAttribute("currentUser", currentUser);
        if (partnerId != null) model.addAttribute("partnerId", partnerId);
        model.addAttribute("pageTitle", "Chat Management");
        model.addAttribute("body", "admin/chat");
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

    @PostMapping(value = "/coin-deposits/{id}/retry", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> retryCoinDeposit(@PathVariable Long id, Authentication auth) {
        try {
            // Auth check
            Authentication authentication = auth;
            if (authentication == null || !authentication.isAuthenticated()) {
                authentication = SecurityContextHolder.getContext().getAuthentication();
            }
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false, "message", "Unauthorized"));
            }

            // Admin validation
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
            if (!okAdmin) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false, "message", "Forbidden"));
            }

            // Call retry service
            String result = sepayApiService.retryFailedDeposit(id);

            boolean success = result.toLowerCase().startsWith("success");
            return ResponseEntity.ok(Map.of(
                "success", success,
                "message", result
            ));

        } catch (Exception ex) {
            logger.error("Error retrying deposit: ", ex);
            return ResponseEntity.status(500).body(Map.of("success", false, "message", "Internal error: " + ex.getMessage()));
        }
    }


    @GetMapping("/transactions")
    public String transactionManagement(@RequestParam(name = "page", defaultValue = "1") int page,
                                        @RequestParam(name = "search", defaultValue = "") String search,
                                        @RequestParam(name = "sort", defaultValue = "date_desc") String sort,
                                        Model model) {
        try {
            final int pageSize = 10;

            // Convert from 1-based to 0-based page index
            int pageIndex = Math.max(0, page - 1);

            // Build WHERE clause and parameters once
            String where = " WHERE t.isDelete = false" +
                    (search != null && !search.isBlank()
                            ? " AND (LOWER(c.fullName) LIKE LOWER(:search) OR LOWER(s.fullName) LIKE LOWER(:search) OR LOWER(p.title) LIKE LOWER(:search) OR CAST(t.id AS string) LIKE :search)"
                            : "");

            // Determine ORDER BY clause based on sort parameter
            String orderBy;
            if (sort != null && sort.startsWith("amount_")) {
                orderBy = sort.endsWith("asc") ? " ORDER BY t.amount ASC" : " ORDER BY t.amount DESC";
            } else {
                orderBy = " ORDER BY t.createdAt DESC";
            }

            // 1) Count query (no FETCH joins)
            StringBuilder countSb = new StringBuilder(
                    "SELECT COUNT(t) FROM Transaction t " +
                            "LEFT JOIN t.customer c " +
                            "LEFT JOIN t.seller s " +
                            "LEFT JOIN t.product p " +
                            "LEFT JOIN t.variant v"
            );
            countSb.append(where);
            jakarta.persistence.Query countQuery = entityManager.createQuery(countSb.toString());
            if (search != null && !search.isBlank()) {
                countQuery.setParameter("search", "%" + search + "%");
            }
            long total = (long) countQuery.getSingleResult();

            // Compute total pages and clamp current page
            int totalPages = (int) Math.ceil(total / (double) pageSize);
            if (pageIndex < 0) pageIndex = 0;
            if (totalPages > 0 && pageIndex >= totalPages) pageIndex = totalPages - 1;

            // 2) Paged select query with FETCH joins for view rendering
            StringBuilder listSb = new StringBuilder(
                    "SELECT t FROM Transaction t " +
                            "LEFT JOIN FETCH t.customer c " +
                            "LEFT JOIN FETCH t.seller s " +
                            "LEFT JOIN FETCH t.product p " +
                            "LEFT JOIN FETCH t.variant v"
            );
            listSb.append(where);
            listSb.append(orderBy);

            jakarta.persistence.TypedQuery<Transaction> listQuery = entityManager.createQuery(listSb.toString(), Transaction.class);
            if (search != null && !search.isBlank()) {
                listQuery.setParameter("search", "%" + search + "%");
            }
            listQuery.setFirstResult(pageIndex * pageSize);
            listQuery.setMaxResults(pageSize);
            List<Transaction> pageList = listQuery.getResultList();

            model.addAttribute("transactions", pageList);
            model.addAttribute("currentPage", page);  // Pass 1-based page to view
            model.addAttribute("currentSearch", search);
            model.addAttribute("currentSort", sort);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("pageSize", pageSize);
            model.addAttribute("pageTitle", "Transaction Management");
            model.addAttribute("body", "admin/transaction-management");
            return "admin/layout";
        } catch (Exception ex) {
            ex.printStackTrace();
            model.addAttribute("transactions", new java.util.ArrayList<>());
            model.addAttribute("currentPage", 1);  // Use 1-based default
            model.addAttribute("currentSearch", "");
            model.addAttribute("totalPages", 1);
            model.addAttribute("pageTitle", "Transaction Management");
            model.addAttribute("body", "admin/transaction-management");
        }
        return "admin/layout";
    }

    public String transactionsManagement(@RequestParam(name = "status", defaultValue = "All") String txStatus,
                                         @RequestParam(name = "orderStatus", defaultValue = "All") String orderStatus,
                                         @RequestParam(name = "page", defaultValue = "0") int page,
                                         @RequestParam(name = "search", defaultValue = "") String search,
                                         @RequestParam(name = "sort", defaultValue = "date_desc") String sort,
                                         Model model) {
        // Build base JPQL for transactions + eager parts
        StringBuilder sb = new StringBuilder("SELECT t FROM Transaction t " +
                " LEFT JOIN FETCH t.product p " +
                " LEFT JOIN FETCH t.variant v " +
                " LEFT JOIN FETCH t.customer c " +
                " LEFT JOIN FETCH t.seller s " +
                " WHERE 1=1");
        if (!"All".equalsIgnoreCase(txStatus)) {
            sb.append(" AND LOWER(t.status) = LOWER(:txStatus)");
        }
        if (search != null && !search.isBlank()) {
            sb.append(" AND (" +
                    " CAST(t.id AS string) LIKE :kw " +
                    " OR LOWER(c.fullName) LIKE LOWER(:kw) OR LOWER(c.email) LIKE LOWER(:kw) " +
                    " OR LOWER(s.fullName) LIKE LOWER(:kw) OR LOWER(s.email) LIKE LOWER(:kw) " +
                    " OR LOWER(p.name) LIKE LOWER(:kw) " +
                    ")");
        }

        // Apply sorting based on sort parameter
        String orderClause;
        switch (sort.toLowerCase()) {
            case "date_asc":
                orderClause = " ORDER BY t.createdAt ASC";
                break;
            case "amount_desc":
                orderClause = " ORDER BY t.amount DESC";
                break;
            case "amount_asc":
                orderClause = " ORDER BY t.amount ASC";
                break;
            case "quantity_desc":
                orderClause = " ORDER BY t.quantity DESC";
                break;
            case "quantity_asc":
                orderClause = " ORDER BY t.quantity ASC";
                break;
            default: // date_desc
                orderClause = " ORDER BY t.createdAt DESC";
        }
        sb.append(orderClause);

        jakarta.persistence.Query tq = entityManager.createQuery(sb.toString(), com.mmo.entity.Transaction.class);
        if (!"All".equalsIgnoreCase(txStatus)) {
            tq.setParameter("txStatus", txStatus);
        }
        if (search != null && !search.isBlank()) {
            tq.setParameter("kw", "%" + search + "%");
        }
        java.util.List<com.mmo.entity.Transaction> all = tq.getResultList();

        int total = all.size();
        int size = 10;
        int totalPages = (int) Math.ceil((double) total / size);
        java.util.List<com.mmo.entity.Transaction> pageList = all.stream()
                .skip((long) page * size)
                .limit(size)
                .toList();

        // Load Orders for the page transactions to simulate JOIN view
        java.util.Set<Long> txIds = pageList.stream().map(com.mmo.entity.Transaction::getId).collect(java.util.stream.Collectors.toSet());
        java.util.Map<Long, com.mmo.entity.Orders> orderByTxId = new java.util.HashMap<>();
        if (!txIds.isEmpty()) {
            entityManager.createQuery("SELECT o FROM Orders o LEFT JOIN FETCH o.transaction WHERE o.transaction.id IN :ids", com.mmo.entity.Orders.class)
                    .setParameter("ids", txIds)
                    .getResultList()
                    .forEach(o -> orderByTxId.put(o.getTransaction() != null ? o.getTransaction().getId() : o.getTransactionId(), o));
        }

        // If orderStatus filter is applied, re-filter page list using the loaded orders
        if (!"All".equalsIgnoreCase(orderStatus)) {
            pageList = pageList.stream()
                    .filter(t -> {
                        com.mmo.entity.Orders o = orderByTxId.get(t.getId());
                        String os = o != null && o.getStatus() != null ? o.getStatus().name() : null;
                        return os != null && os.equalsIgnoreCase(orderStatus);
                    })
                    .toList();
        }

        // Map to list items
        java.util.List<com.mmo.dto.AdminTransactionListItem> items = new java.util.ArrayList<>();
        for (com.mmo.entity.Transaction t : pageList) {
            com.mmo.entity.Orders o = orderByTxId.get(t.getId());
            String productName = t.getProduct() != null ? t.getProduct().getName() : "";
            String variantName = t.getVariant() != null ? t.getVariant().getVariantName() : "";
            String customerName = t.getCustomer() != null ? t.getCustomer().getFullName() : "";
            String customerEmail = t.getCustomer() != null ? t.getCustomer().getEmail() : "";
            String sellerName = t.getSeller() != null ? t.getSeller().getFullName() : "";
            String sellerEmail = t.getSeller() != null ? t.getSeller().getEmail() : "";
            Long qty = (o != null && o.getQuantity() != null) ? o.getQuantity() : (t.getQuantity() != null ? t.getQuantity() : 0L);
            items.add(new com.mmo.dto.AdminTransactionListItem(
                    t.getId(),
                    o != null ? o.getId() : null,
                    o != null ? o.getRequestId() : null,
                    o != null && o.getStatus() != null ? o.getStatus().name() : null,
                    t.getStatus(),
                    t.getAmount(),
                    t.getCoinSeller(),
                    t.getCoinAdmin(),
                    qty,
                    productName,
                    variantName,
                    customerName,
                    customerEmail,
                    sellerName,
                    sellerEmail,
                    t.getCreatedAt(),
                    o != null ? o.getProcessedAt() : null
            ));
        }

        model.addAttribute("transactions", items);
        model.addAttribute("currentStatus", txStatus);
        model.addAttribute("currentOrderStatus", orderStatus);
        model.addAttribute("currentPage", page);
        model.addAttribute("currentSearch", search);
        model.addAttribute("currentSort", sort);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageTitle", "Transactions Management");
        model.addAttribute("body", "admin/transactions");
        return "admin/layout";
    }

    @GetMapping(value = "/transactions/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> getTransactionDetail(@PathVariable Long id, Authentication auth) {
        try {
            Authentication authentication = auth;
            if (authentication == null || !authentication.isAuthenticated()) {
                authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
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

            com.mmo.entity.Transaction t = entityManager.createQuery(
                    "SELECT t FROM Transaction t " +
                            " LEFT JOIN FETCH t.product p " +
                            " LEFT JOIN FETCH t.variant v " +
                            " LEFT JOIN FETCH t.customer c " +
                            " LEFT JOIN FETCH t.seller s " +
                            " WHERE t.id = :id", com.mmo.entity.Transaction.class)
                    .setParameter("id", id)
                    .getResultStream().findFirst().orElse(null);
            if (t == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Transaction not found");

            com.mmo.entity.Orders o = entityManager.createQuery("SELECT o FROM Orders o WHERE o.transaction.id = :id", com.mmo.entity.Orders.class)
                    .setParameter("id", id)
                    .getResultStream().findFirst().orElse(null);

            String productName = t.getProduct() != null ? t.getProduct().getName() : "";
            String variantName = t.getVariant() != null ? t.getVariant().getVariantName() : "";
            String customerName = t.getCustomer() != null ? t.getCustomer().getFullName() : "";
            String customerEmail = t.getCustomer() != null ? t.getCustomer().getEmail() : "";
            String sellerName = t.getSeller() != null ? t.getSeller().getFullName() : "";
            String sellerEmail = t.getSeller() != null ? t.getSeller().getEmail() : "";

            com.mmo.dto.AdminTransactionDetailResponse resp = new com.mmo.dto.AdminTransactionDetailResponse(
                    t.getId(),
                    o != null ? o.getId() : null,
                    o != null ? o.getRequestId() : null,
                    o != null && o.getStatus() != null ? o.getStatus().name() : null,
                    o != null ? o.getErrorMessage() : null,
                    o != null && o.getProcessedAt() != null ? o.getProcessedAt().toString() : null,
                    t.getStatus(),
                    t.getAmount(),
                    t.getCommission(),
                    t.getCoinAdmin(),
                    t.getCoinSeller(),
                    o != null && o.getQuantity() != null ? o.getQuantity() : t.getQuantity(),
                    productName,
                    variantName,
                    t.getCustomer() != null ? t.getCustomer().getId() : null,
                    customerName,
                    customerEmail,
                    t.getSeller() != null ? t.getSeller().getId() : null,
                    sellerName,
                    sellerEmail,
                    t.getEscrowReleaseDate() != null ? t.getEscrowReleaseDate().toString() : null,
                    t.getCreatedAt() != null ? t.getCreatedAt().toString() : null,
                    t.getUpdatedAt() != null ? t.getUpdatedAt().toString() : null
            );
            return ResponseEntity.ok(resp);
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }

    @GetMapping("/point-purchases")
    public String pointPurchases(@RequestParam(name = "page", defaultValue = "0") int page,
                                 @RequestParam(name = "search", defaultValue = "") String search,
                                 @RequestParam(name = "sort", defaultValue = "date_desc") String sort,
                                 Model model) {
        String queryStr = "SELECT p FROM ShopPointPurchase p LEFT JOIN FETCH p.user WHERE 1=1";
        if (!search.isBlank()) {
            queryStr += " AND (LOWER(p.user.fullName) LIKE LOWER(:search) OR LOWER(p.user.email) LIKE LOWER(:search) OR CAST(p.id AS string) LIKE :search OR CAST(p.user.id AS string) LIKE :search)";
        }

        // Apply sorting
        switch (sort.toLowerCase()) {
            case "date_asc":
                queryStr += " ORDER BY p.createdAt ASC";
                break;
            case "points_desc":
                queryStr += " ORDER BY p.pointsBought DESC";
                break;
            case "points_asc":
                queryStr += " ORDER BY p.pointsBought ASC";
                break;
            case "coins_desc":
                queryStr += " ORDER BY p.coinsSpent DESC";
                break;
            case "coins_asc":
                queryStr += " ORDER BY p.coinsSpent ASC";
                break;
            default: // date_desc
                queryStr += " ORDER BY p.createdAt DESC";
        }

        jakarta.persistence.Query query = entityManager.createQuery(queryStr, ShopPointPurchase.class);
        if (!search.isBlank()) {
            query.setParameter("search", "%" + search + "%");
        }
        List<ShopPointPurchase> all = query.getResultList();
        int total = all.size();
        int totalPages = (int) Math.ceil((double) total / 10);
        List<ShopPointPurchase> pageList = all.stream()
                .skip((long) page * 10)
                .limit(10)
                .toList();

        model.addAttribute("purchases", pageList);
        model.addAttribute("currentPage", page);
        model.addAttribute("currentSearch", search);
        model.addAttribute("currentSort", sort);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageTitle", "Point Purchases");
        model.addAttribute("body", "admin/point-purchases");
        return "admin/layout";
    }

    // NEW: Admin-only API endpoint to search/list users for starting new chats
    @GetMapping(value = "/api/users", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> adminSearchUsers(@RequestParam(name = "kw", required = false) String kw,
                                              @RequestParam(name = "page", defaultValue = "0") int page,
                                              @RequestParam(name = "size", defaultValue = "10") int size,
                                              Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }
            User currentUser = authService.findByEmail(authentication.getName());
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }
            org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
            java.util.List<User> users;
            if (kw == null || kw.trim().isEmpty()) {
                users = entityManager.createQuery("SELECT u FROM User u WHERE u.isDelete = false AND u.id <> :exclude ORDER BY LOWER(COALESCE(u.fullName, '')) ASC, u.id ASC", User.class)
                        .setParameter("exclude", currentUser.getId())
                        .setFirstResult(pageable.getPageNumber() * pageable.getPageSize())
                        .setMaxResults(pageable.getPageSize())
                        .getResultList();
            } else {
                String keyword = "%" + kw.trim().toLowerCase() + "%";
                users = entityManager.createQuery("SELECT u FROM User u WHERE u.isDelete = false AND u.id <> :exclude AND (LOWER(COALESCE(u.fullName, '')) LIKE :kw OR LOWER(COALESCE(u.email, '')) LIKE :kw) ORDER BY LOWER(COALESCE(u.fullName, '')) ASC, u.id ASC", User.class)
                        .setParameter("exclude", currentUser.getId())
                        .setParameter("kw", keyword)
                        .setFirstResult(pageable.getPageNumber() * pageable.getPageSize())
                        .setMaxResults(pageable.getPageSize())
                        .getResultList();
            }
            java.util.List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
            for (User u : users) {
                String name = (u.getFullName() == null || u.getFullName().isBlank()) ? ("User #" + u.getId()) : u.getFullName();
                out.add(java.util.Map.of(
                        "id", u.getId(),
                        "fullName", name,
                        "email", u.getEmail(),
                        "role", u.getRole()
                ));
            }
            return ResponseEntity.ok(out);
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(java.util.Map.of("error", ex.getMessage()));
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
                                  @RequestParam(name = "status", defaultValue = "All") String status,
                                  @RequestParam(name = "sort", defaultValue = "") String sort,
                                  Model model) {
        Pageable pageable = PageRequest.of(page, 10);

        // Build dynamic query
        StringBuilder jpql = new StringBuilder("SELECT b FROM Blog b WHERE b.isDelete = false");


        if (search != null && !search.trim().isEmpty()) {
            jpql.append(" AND (LOWER(b.title) LIKE LOWER(:search) OR LOWER(b.content) LIKE LOWER(:search))");
        }


        if (status != null && !status.trim().isEmpty()) {
            if (status.equals("popular")) {
                jpql.append(" AND b.likes >= 100");
            } else if (status.equals("new")) {
                jpql.append(" AND b.createdAt >= :sevenDaysAgo");
            }
        }

        // Add sorting - avoid SIZE() for comments as it's slow

        // Filter by status (Active/Inactive)
        if (status != null && !status.trim().isEmpty() && !status.equalsIgnoreCase("All")) {
            if (status.equalsIgnoreCase("Active")) {
                jpql.append(" AND b.status = true");
            } else if (status.equalsIgnoreCase("Inactive")) {
                jpql.append(" AND b.status = false");
            }
        }

        // Add sorting
        if (sort != null && !sort.isEmpty()) {
            if (sort.equals("likes_asc")) {
                jpql.append(" ORDER BY b.likes ASC");
            } else if (sort.equals("likes_desc")) {
                jpql.append(" ORDER BY b.likes DESC");
            } else if (sort.equals("views_asc")) {
                jpql.append(" ORDER BY b.views ASC");
            } else if (sort.equals("views_desc")) {
                jpql.append(" ORDER BY b.views DESC");
            } else if (sort.equals("date_asc")) {
                jpql.append(" ORDER BY b.createdAt ASC");
            } else if (sort.equals("date_desc")) {
                jpql.append(" ORDER BY b.createdAt DESC");
            } else {
                jpql.append(" ORDER BY b.createdAt DESC");
            }
        } else {
            jpql.append(" ORDER BY b.createdAt DESC");
        }

        // Separate COUNT query for better performance
        StringBuilder countJpql = new StringBuilder("SELECT COUNT(b) FROM Blog b WHERE b.isDelete = false");
        if (search != null && !search.trim().isEmpty()) {
            countJpql.append(" AND (LOWER(b.title) LIKE LOWER(:search) OR LOWER(b.content) LIKE LOWER(:search))");
        }
        if (status != null && !status.trim().isEmpty() && !status.equalsIgnoreCase("All")) {
            if (status.equalsIgnoreCase("Active")) {
                countJpql.append(" AND b.status = true");
            } else if (status.equalsIgnoreCase("Inactive")) {
                countJpql.append(" AND b.status = false");
            }
        }

        TypedQuery<Long> countQuery = entityManager.createQuery(countJpql.toString(), Long.class);
        if (search != null && !search.trim().isEmpty()) {
            countQuery.setParameter("search", "%" + search.trim() + "%");
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


    @PostMapping("/blogs/upload-image")
    @ResponseBody
    public ResponseEntity<?> uploadBlogImage(@RequestParam("image") org.springframework.web.multipart.MultipartFile file,
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

            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("No file uploaded");
            }

            // Validate file type
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return ResponseEntity.badRequest().body("Only image files are allowed");
            }

            // Validate file size (max 5MB)
            if (file.getSize() > 5 * 1024 * 1024) {
                return ResponseEntity.badRequest().body("File size must not exceed 5MB");
            }

            // Create uploads directory if it doesn't exist
            String uploadDir = "uploads/blogs/";
            java.nio.file.Path uploadPath = java.nio.file.Paths.get(uploadDir);
            if (!java.nio.file.Files.exists(uploadPath)) {
                java.nio.file.Files.createDirectories(uploadPath);
            }

            // Generate unique filename
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : ".jpg";
            String filename = java.util.UUID.randomUUID().toString() + extension;

            // Save file
            java.nio.file.Path filePath = uploadPath.resolve(filename);
            file.transferTo(filePath.toFile());

            // Return URL
            String imageUrl = "/" + uploadDir + filename;
            return ResponseEntity.ok(Map.of("imageUrl", imageUrl));

        } catch (Exception ex) {
            ex.printStackTrace();
            return ResponseEntity.status(500).body("Failed to upload image: " + ex.getMessage());
        }
    }


    // ==================== SHOP MANAGEMENT ====================

    @GetMapping("/shops")
    @Transactional(readOnly = true)
    public String shops(@RequestParam(name = "page", defaultValue = "0") int page,
                        @RequestParam(name = "search", defaultValue = "") String search,
                        @RequestParam(name = "sort", defaultValue = "") String sort,
                        @RequestParam(name = "status", defaultValue = "All") String status,
                        Model model) {
        Pageable pageable = PageRequest.of(page, 10);

        // For rating sort, we need a different approach with subquery
        boolean isRatingSort = sort != null && (sort.equals("rating_asc") || sort.equals("rating_desc"));

        if (isRatingSort) {
            // Use optimized rating sort with single query
            String direction = sort.equals("rating_asc") ? "ASC" : "DESC";
            StringBuilder whereClause = new StringBuilder();
            boolean hasWhere = false;

            // Add status filter
            if (!"All".equalsIgnoreCase(status)) {
                if (status.equalsIgnoreCase("Active")) {
                    whereClause.append("s.isDelete = false");
                } else if (status.equalsIgnoreCase("Inactive")) {
                    whereClause.append("s.isDelete = true");
                }
                hasWhere = true;
            }

            // Add search filter
            if (search != null && !search.trim().isEmpty()) {
                if (hasWhere) {
                    whereClause.append(" AND ");
                }
                whereClause.append("(LOWER(s.shopName) LIKE LOWER(:search) OR LOWER(s.user.fullName) LIKE LOWER(:search))");
                hasWhere = true;
            }

            String jpql = "SELECT DISTINCT s FROM ShopInfo s LEFT JOIN FETCH s.user ";
            if (hasWhere) {
                jpql += "WHERE " + whereClause.toString() + " ";
            }
            jpql += "ORDER BY (SELECT COALESCE(AVG(CAST(r.rating AS double)), 0.0) " +
                    "FROM Review r WHERE r.product.seller.id = s.user.id) " + direction;

            // Get total count
            StringBuilder countJpql = new StringBuilder("SELECT COUNT(s) FROM ShopInfo s");
            if (hasWhere) {
                countJpql.append(" WHERE ").append(whereClause.toString());
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
            model.addAttribute("currentStatus", status);
            model.addAttribute("totalPages", shopPage.getTotalPages());
            model.addAttribute("pageTitle", "Shop Management");
            model.addAttribute("body", "admin/shops");
            return "admin/layout";
        }

        // Normal sort (commission, or default by ID)
        StringBuilder jpql = new StringBuilder(
                "SELECT DISTINCT s FROM ShopInfo s LEFT JOIN FETCH s.user"
        );

        boolean hasWhere = false;

        // Add status filter
        if (!"All".equalsIgnoreCase(status)) {
            jpql.append(" WHERE ");
            if (status.equalsIgnoreCase("Active")) {
                jpql.append("s.isDelete = false");
            } else if (status.equalsIgnoreCase("Inactive")) {
                jpql.append("s.isDelete = true");
            }
            hasWhere = true;
        }

        // Add search filter
        if (search != null && !search.trim().isEmpty()) {
            if (hasWhere) {
                jpql.append(" AND ");
            } else {
                jpql.append(" WHERE ");
            }
            jpql.append("(LOWER(s.shopName) LIKE LOWER(:search) OR LOWER(s.user.fullName) LIKE LOWER(:search))");
            hasWhere = true;
        }

        // Add simple sorting
        if (sort != null && !sort.isEmpty()) {
            if (sort.equals("commission_asc")) {
                jpql.append(" ORDER BY s.commission ASC");
            } else if (sort.equals("commission_desc")) {
                jpql.append(" ORDER BY s.commission DESC");
            } else if (sort.equals("level_asc")) {
                jpql.append(" ORDER BY s.shopLevel ASC");
            } else if (sort.equals("level_desc")) {
                jpql.append(" ORDER BY s.shopLevel DESC");
            } else {
                jpql.append(" ORDER BY s.id DESC");
            }
        } else {
            jpql.append(" ORDER BY s.id DESC");
        }

        // Get total count efficiently with separate COUNT query
        StringBuilder countJpql = new StringBuilder("SELECT COUNT(s) FROM ShopInfo s");
        boolean hasCountWhere = false;

        // Add status filter to count
        if (!"All".equalsIgnoreCase(status)) {
            countJpql.append(" WHERE ");
            if (status.equalsIgnoreCase("Active")) {
                countJpql.append("s.isDelete = false");
            } else if (status.equalsIgnoreCase("Inactive")) {
                countJpql.append("s.isDelete = true");
            }
            hasCountWhere = true;
        }

        // Add search filter to count
        if (search != null && !search.trim().isEmpty()) {
            if (hasCountWhere) {
                countJpql.append(" AND ");
            } else {
                countJpql.append(" WHERE ");
            }
            countJpql.append("(LOWER(s.shopName) LIKE LOWER(:search) OR LOWER(s.user.fullName) LIKE LOWER(:search))");
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
        model.addAttribute("currentStatus", status);
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
            if (shop == null) {
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
            response.put("isDelete", shop.isDelete());
            response.put("shopLevel", shop.getShopLevel() != null ? shop.getShopLevel() : 0);
            response.put("points", shop.getPoints() != null ? shop.getPoints() : 0L);
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

            // Find the shop by id with user joined
            ShopInfo shop = entityManager.createQuery(
                            "SELECT s FROM ShopInfo s LEFT JOIN FETCH s.user WHERE s.id = :id", ShopInfo.class)
                    .setParameter("id", id)
                    .getSingleResult();

            if (shop.getUser() == null) {
                return ResponseEntity.badRequest().body("Shop owner not found");
            }

            User seller = shop.getUser();

            // Toggle the isDelete status
            boolean currentIsDelete = shop.isDelete();
            boolean newIsDelete = !currentIsDelete;
            shop.setDelete(newIsDelete);

            // Update user shopStatus correspondingly
            if (newIsDelete) {
                // Deactivating: set isDelete=true AND shopStatus=Inactive
                shop.setDeletedBy(admin);
                seller.setShopStatus("Inactive");
                logger.info("Admin {} deactivated shop {} - set isDelete=true and shopStatus=Inactive", admin.getId(), id);

                // Also hide all products from this shop
                int productsUpdated = entityManager.createQuery(
                    "UPDATE Product p SET p.isDelete = true WHERE p.seller.id = :sellerId AND p.isDelete = false")
                    .setParameter("sellerId", seller.getId())
                    .executeUpdate();
                logger.info("Deactivated {} products from shop {}", productsUpdated, id);
            } else {
                // Activating: set isDelete=false AND shopStatus=Active
                shop.setDeletedBy(null);
                seller.setShopStatus("Active");
                logger.info("Admin {} activated shop {} - set isDelete=false and shopStatus=Active", admin.getId(), id);

                // Also restore all products from this shop
                int productsUpdated = entityManager.createQuery(
                    "UPDATE Product p SET p.isDelete = false WHERE p.seller.id = :sellerId AND p.isDelete = true")
                    .setParameter("sellerId", seller.getId())
                    .executeUpdate();
                logger.info("Reactivated {} products from shop {}", productsUpdated, id);
            }

            entityManager.merge(shop);
            entityManager.merge(seller);

            String action = currentIsDelete ? "activated" : "deactivated";
            return ResponseEntity.ok().body("Shop " + action + " successfully");
        } catch (RuntimeException e) {
            logger.error("Error toggling shop status for shop {}", id, e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception ex) {
            logger.error("Internal error toggling shop status for shop {}", id, ex);
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }

    @PutMapping("/shops/{id}/level-points")
    @ResponseBody
    @Transactional
    public ResponseEntity<?> updateShopLevelAndPoints(@PathVariable Long id,
                                                       @RequestBody java.util.Map<String, Object> request,
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

            // Find the shop by id
            ShopInfo shop = entityManager.find(ShopInfo.class, id);
            if (shop == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Shop not found");
            }

            // Update shop level
            if (request.containsKey("shopLevel")) {
                Integer shopLevel = Integer.parseInt(request.get("shopLevel").toString());
                if (shopLevel < 0 || shopLevel > 255) {
                    return ResponseEntity.badRequest().body("Shop level must be between 0 and 255");
                }
                shop.setShopLevel(shopLevel.shortValue());
            }

            // Update points
            if (request.containsKey("points")) {
                Long points = Long.parseLong(request.get("points").toString());
                if (points < 0) {
                    return ResponseEntity.badRequest().body("Points must be greater than or equal to 0");
                }
                shop.setPoints(points);
            }

            entityManager.merge(shop);

            return ResponseEntity.ok().body(java.util.Map.of(
                "message", "Shop level and points updated successfully",
                "shopLevel", shop.getShopLevel(),
                "points", shop.getPoints()
            ));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body("Invalid number format");
        } catch (Exception ex) {
            logger.error("Error updating shop level and points for shop ID: {}", id, ex);
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

            // Set user as deleted (banned)
            user.setDelete(true);
            user.setDeletedBy(admin.getId());

            // If user is a seller, also set shopStatus to Inactive for consistency
            if (user.getShopStatus() != null && !"Inactive".equalsIgnoreCase(user.getShopStatus())) {
                user.setShopStatus("Inactive");
                logger.info("Admin {} banned user {} - set isDelete=true and shopStatus=Inactive", admin.getId(), id);
            } else {
                logger.info("Admin {} banned user {} - set isDelete=true", admin.getId(), id);
            }

            entityManager.merge(user);

            // Also update associated shop if exists
            try {
                ShopInfo shop = entityManager.createQuery(
                    "SELECT s FROM ShopInfo s WHERE s.user.id = :userId", ShopInfo.class)
                    .setParameter("userId", id)
                    .getResultStream()
                    .findFirst()
                    .orElse(null);

                if (shop != null && !shop.isDelete()) {
                    shop.setDelete(true);
                    entityManager.merge(shop);
                    logger.info("Also set shop {} isDelete=true when banning user {}", shop.getId(), id);
                }
            } catch (Exception e) {
                logger.warn("Could not update shop for banned user {}: {}", id, e.getMessage());
            }

            // Hide all products from this banned user/seller
            try {
                int productsUpdated = entityManager.createQuery(
                    "UPDATE Product p SET p.isDelete = true, p.deletedBy = :adminId WHERE p.seller.id = :userId AND p.isDelete = false")
                    .setParameter("adminId", admin.getId())
                    .setParameter("userId", id)
                    .executeUpdate();

                if (productsUpdated > 0) {
                    logger.info("Banned user {} - also hid {} products", id, productsUpdated);
                }
            } catch (Exception e) {
                logger.warn("Could not update products for banned user {}: {}", id, e.getMessage());
            }

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

            // Unban user
            user.setDelete(false);
            user.setDeletedBy(null);

            // If user is/was a seller, also set shopStatus to Active for consistency
            // Only set to Active if they had shopStatus before (not null)
            if (user.getShopStatus() != null) {
                user.setShopStatus("Active");
                logger.info("Admin {} unbanned user {} - set isDelete=false and shopStatus=Active", admin.getId(), id);
            } else {
                logger.info("Admin {} unbanned user {} - set isDelete=false", admin.getId(), id);
            }

            entityManager.merge(user);

            // Also update associated shop if exists
            try {
                ShopInfo shop = entityManager.createQuery(
                    "SELECT s FROM ShopInfo s WHERE s.user.id = :userId", ShopInfo.class)
                    .setParameter("userId", id)
                    .getResultStream()
                    .findFirst()
                    .orElse(null);

                if (shop != null && shop.isDelete()) {
                    shop.setDelete(false);
                    entityManager.merge(shop);
                    logger.info("Also set shop {} isDelete=false when unbanning user {}", shop.getId(), id);
                }
            } catch (Exception e) {
                logger.warn("Could not update shop for unbanned user {}: {}", id, e.getMessage());
            }

            // Restore all products from this unbanned user/seller
            try {
                int productsUpdated = entityManager.createQuery(
                    "UPDATE Product p SET p.isDelete = false, p.deletedBy = null WHERE p.seller.id = :userId AND p.isDelete = true")
                    .setParameter("userId", id)
                    .executeUpdate();

                if (productsUpdated > 0) {
                    logger.info("Unbanned user {} - also restored {} products", id, productsUpdated);
                }
            } catch (Exception e) {
                logger.warn("Could not restore products for unbanned user {}: {}", id, e.getMessage());
            }

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

            // Validate new password strength: min 6 chars, uppercase, number, special character
            String newPassword = request.getNewPassword();
            if (newPassword == null || newPassword.length() < 6) {
                redirectAttributes.addFlashAttribute("errorMessage", "New password must be at least 6 characters");
                return "redirect:/admin/change-password";
            }
            // Check for uppercase letter
            if (!newPassword.matches(".*[A-Z].*")) {
                redirectAttributes.addFlashAttribute("errorMessage", "New password must include at least one uppercase letter");
                return "redirect:/admin/change-password";
            }

            // Check for number
            if (!newPassword.matches(".*\\d.*")) {
                redirectAttributes.addFlashAttribute("errorMessage", "New password must include at least one number");
                return "redirect:/admin/change-password";
            }

            // Check for special character
            if (!newPassword.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*")) {
                redirectAttributes.addFlashAttribute("errorMessage", "New password must include at least one special character");
                return "redirect:/admin/change-password";
            }

            if (!newPassword.equals(request.getConfirmPassword())) {

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

    // ==================== COMPLAINT MANAGEMENT ====================

    @GetMapping("/complaints")
    public String listComplaints(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false, defaultValue = "date_desc") String sort,
            @RequestParam(defaultValue = "0") int page,
            Model model) {
        try {
            int pageSize = 10;
            Pageable pageable = PageRequest.of(page, pageSize);

            // Build query
            StringBuilder jpql = new StringBuilder("SELECT c FROM Complaint c WHERE c.isDelete = false");
            List<Object> params = new ArrayList<>();
            int paramIndex = 1;

            // Search filter
            if (search != null && !search.trim().isEmpty()) {
                jpql.append(" AND (LOWER(c.customer.fullName) LIKE ?").append(paramIndex)
                    .append(" OR LOWER(c.seller.fullName) LIKE ?").append(paramIndex + 1)
                    .append(" OR CAST(c.id AS string) LIKE ?").append(paramIndex + 2).append(")");
                String searchPattern = "%" + search.toLowerCase() + "%";
                params.add(searchPattern);
                params.add(searchPattern);
                params.add("%" + search + "%");
                paramIndex += 3;
            }

            // Status filter
            if (status != null && !status.trim().isEmpty() && !"".equals(status)) {
                try {
                    Complaint.ComplaintStatus statusEnum = Complaint.ComplaintStatus.valueOf(status);
                    jpql.append(" AND c.status = ?").append(paramIndex);
                    params.add(statusEnum);
                    paramIndex++;
                } catch (IllegalArgumentException e) {
                    // Invalid status, ignore
                }
            }

            // Type filter
            if (type != null && !type.trim().isEmpty() && !"".equals(type)) {
                try {
                    Complaint.ComplaintType typeEnum = Complaint.ComplaintType.valueOf(type);
                    jpql.append(" AND c.complaintType = ?").append(paramIndex);
                    params.add(typeEnum);
                    paramIndex++;
                } catch (IllegalArgumentException e) {
                    // Invalid type, ignore
                }
            }

            // Sorting
            String orderBy = " ORDER BY ";
            switch (sort) {
                case "date_asc":
                    orderBy += "c.createdAt ASC";
                    break;
                case "priority_desc":
                    orderBy += "CASE c.status " +
                              "WHEN 'ESCALATED' THEN 1 " +
                              "WHEN 'NEW' THEN 2 " +
                              "WHEN 'IN_PROGRESS' THEN 3 " +
                              "WHEN 'PENDING_CONFIRMATION' THEN 4 " +
                              "ELSE 5 END, c.createdAt DESC";
                    break;
                case "status_asc":
                    orderBy += "c.status ASC, c.createdAt DESC";
                    break;
                default: // date_desc
                    orderBy += "c.createdAt DESC";
            }
            jpql.append(orderBy);

            // Count query
            String countJpql = jpql.toString().replace("SELECT c FROM", "SELECT COUNT(c) FROM");
            countJpql = countJpql.substring(0, countJpql.indexOf(" ORDER BY"));

            TypedQuery<Long> countQuery = entityManager.createQuery(countJpql, Long.class);
            for (int i = 0; i < params.size(); i++) {
                countQuery.setParameter(i + 1, params.get(i));
            }
            long totalElements = countQuery.getSingleResult();

            // Data query
            TypedQuery<Complaint> query = entityManager.createQuery(jpql.toString(), Complaint.class);
            for (int i = 0; i < params.size(); i++) {
                query.setParameter(i + 1, params.get(i));
            }
            query.setFirstResult(page * pageSize);
            query.setMaxResults(pageSize);
            List<Complaint> complaints = query.getResultList();

            // Get escalated count
            long escalatedCount = complaintRepository.findByStatus(Complaint.ComplaintStatus.ESCALATED).size();

            // Add to model
            model.addAttribute("complaints", complaints);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", (int) Math.ceil((double) totalElements / pageSize));
            model.addAttribute("currentSearch", search);
            model.addAttribute("currentStatus", status);
            model.addAttribute("currentType", type);
            model.addAttribute("currentSort", sort);
            model.addAttribute("escalatedCount", escalatedCount);
            model.addAttribute("pageTitle", "Complaint Management");
            model.addAttribute("body", "admin/complaint-management");

            return "admin/layout";
        } catch (Exception e) {
            logger.error("Error listing complaints", e);
            model.addAttribute("errorMessage", "Failed to load complaints: " + e.getMessage());
            return "error/500";
        }
    }

    @GetMapping("/complaints/{id}")
    public String viewComplaintDetail(@PathVariable Long id, Model model) {
        try {
            // Use JOIN FETCH to eagerly load all required relationships
            Complaint complaint = entityManager.createQuery(
                    "SELECT c FROM Complaint c " +
                    "LEFT JOIN FETCH c.customer " +
                    "LEFT JOIN FETCH c.seller " +
                    "LEFT JOIN FETCH c.adminHandler " +
                    "WHERE c.id = :id", Complaint.class)
                    .setParameter("id", id)
                    .getSingleResult();

            // Get transaction if exists
            Transaction transaction = null;
            if (complaint.getTransactionId() != null) {
                transaction = transactionRepository.findById(complaint.getTransactionId()).orElse(null);
            }

            // Get chat messages/communication history between customer and seller
            List<Chat> messages = new ArrayList<>();
            if (complaint.getCustomer() != null && complaint.getSeller() != null) {
                messages = chatRepository.findConversation(
                    complaint.getCustomer().getId(),
                    complaint.getSeller().getId()
                );
            }

            model.addAttribute("complaint", complaint);
            model.addAttribute("transaction", transaction);
            model.addAttribute("messages", messages);
            model.addAttribute("pageTitle", "Complaint Detail #" + id);
            model.addAttribute("body", "admin/complaint-detail");

            return "admin/layout";
        } catch (jakarta.persistence.NoResultException e) {
            logger.error("Complaint not found with id: " + id, e);
            model.addAttribute("errorMessage", "Complaint not found");
            return "error/404";
        } catch (Exception e) {
            logger.error("Error viewing complaint detail", e);
            model.addAttribute("errorMessage", "Failed to load complaint details: " + e.getMessage());
            return "error/500";
        }
    }

    @PostMapping("/complaints/{id}/resolve")
    @ResponseBody
    public ResponseEntity<?> resolveComplaint(
            @PathVariable Long id,
            @RequestBody Map<String, Object> requestData,
            Authentication authentication) {
        try {
            // Get current admin
            String username = authentication.getName();
            User admin = entityManager.createQuery("SELECT u FROM User u WHERE u.email = :email", User.class)
                    .setParameter("email", username)
                    .getSingleResult();

            // Get complaint
            Optional<Complaint> complaintOpt = complaintRepository.findById(id);
            if (!complaintOpt.isPresent()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Complaint not found"));
            }

            Complaint complaint = complaintOpt.get();

            // Validate status
            if (complaint.getStatus() != Complaint.ComplaintStatus.ESCALATED) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Only escalated complaints can be resolved by admin"));
            }

            // Get request data
            String decision = (String) requestData.get("decision");
            String notes = (String) requestData.get("notes");
            Boolean refund = (Boolean) requestData.getOrDefault("refund", false);
            Boolean warningSeller = (Boolean) requestData.getOrDefault("warningSeller", false);
            Boolean compensate = (Boolean) requestData.getOrDefault("compensate", false);
            Boolean banSeller = (Boolean) requestData.getOrDefault("banSeller", false);

            // Validate
            if (decision == null || decision.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Decision is required"));
            }
            if (notes == null || notes.length() < 30) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Decision notes must be at least 30 characters"));
            }

            // Update complaint
            complaint.setStatus(Complaint.ComplaintStatus.CLOSED_BY_ADMIN);
            complaint.setAdminDecisionNotes(decision + ": " + notes);
            complaint.setAdminHandler(admin);
            complaint = complaintRepository.save(complaint);

            // Process actions
            if (refund) {
                // TODO: Implement refund logic
                logger.info("Refund requested for complaint #{}", id);
            }
            if (warningSeller) {
                // TODO: Implement warning logic
                logger.info("Warning seller for complaint #{}", id);
            }
            if (compensate) {
                // TODO: Implement compensation logic
                logger.info("Compensation requested for complaint #{}", id);
            }
            if (banSeller) {
                // TODO: Implement ban logic
                logger.info("Ban seller requested for complaint #{}", id);
            }

            // Send notifications
            try {
                // Notify customer
                notificationService.createNotificationForUser(
                        complaint.getCustomer().getId(),
                        "Admin Decision on Complaint #" + complaint.getId(),
                        "Admin has made a decision on your complaint. Decision: " + decision
                );

                // Notify seller
                notificationService.createNotificationForUser(
                        complaint.getSeller().getId(),
                        "Admin Decision on Complaint #" + complaint.getId(),
                        "Admin has made a decision on the complaint. Decision: " + decision
                );
            } catch (Exception e) {
                logger.error("Failed to send notifications for complaint #{}", id, e);
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Complaint resolved successfully"
            ));

        } catch (Exception e) {
            logger.error("Error resolving complaint", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to resolve complaint: " + e.getMessage()));
        }
    }

    // ==================== SHOP FLAG MANAGEMENT ====================

    @Autowired
    private com.mmo.service.ShopFlagService shopFlagService;

    @Autowired
    private com.mmo.repository.ShopFlagRepository shopFlagRepository;

    /**
     * Flag Management page - List all flags
     */
    @GetMapping("/flag-management")
    public String flagManagement(@RequestParam(name = "page", defaultValue = "0") int page,
                                 @RequestParam(name = "search", defaultValue = "") String search,
                                 @RequestParam(name = "status", defaultValue = "All") String status,
                                 @RequestParam(name = "level", defaultValue = "All") String level,
                                 Model model) {
        try {
            StringBuilder jpql = new StringBuilder(
                    "SELECT f FROM ShopFlag f " +
                    "LEFT JOIN f.shop s " +
                    "LEFT JOIN s.user su " +
                    "LEFT JOIN f.admin a " +
                    "WHERE 1=1"
            );

            // Status filter
            if (!"All".equalsIgnoreCase(status)) {
                if ("ACTIVE".equalsIgnoreCase(status)) {
                    jpql.append(" AND f.status = com.mmo.entity.ShopFlag$FlagStatus.ACTIVE");
                } else if ("RESOLVED".equalsIgnoreCase(status)) {
                    jpql.append(" AND f.status = com.mmo.entity.ShopFlag$FlagStatus.RESOLVED");
                }
            }

            // Level filter
            if (!"All".equalsIgnoreCase(level)) {
                if ("WARNING".equalsIgnoreCase(level)) {
                    jpql.append(" AND f.flagLevel = com.mmo.entity.ShopFlag$FlagLevel.WARNING");
                } else if ("SEVERE".equalsIgnoreCase(level)) {
                    jpql.append(" AND f.flagLevel = com.mmo.entity.ShopFlag$FlagLevel.SEVERE");
                } else if ("BANNED".equalsIgnoreCase(level)) {
                    jpql.append(" AND f.flagLevel = com.mmo.entity.ShopFlag$FlagLevel.BANNED");
                }
            }

            // Search filter - only search if shop exists
            if (search != null && !search.trim().isEmpty()) {
                jpql.append(" AND (LOWER(s.shopName) LIKE LOWER(:search) OR LOWER(su.fullName) LIKE LOWER(:search) OR CAST(f.id AS string) LIKE :search)");
            }

            jpql.append(" ORDER BY f.createdAt DESC");

            // Count query
            StringBuilder countJpql = new StringBuilder("SELECT COUNT(f) FROM ShopFlag f LEFT JOIN f.shop s LEFT JOIN s.user su WHERE 1=1");
            if (!"All".equalsIgnoreCase(status)) {
                if ("ACTIVE".equalsIgnoreCase(status)) {
                    countJpql.append(" AND f.status = com.mmo.entity.ShopFlag$FlagStatus.ACTIVE");
                } else if ("RESOLVED".equalsIgnoreCase(status)) {
                    countJpql.append(" AND f.status = com.mmo.entity.ShopFlag$FlagStatus.RESOLVED");
                }
            }
            if (!"All".equalsIgnoreCase(level)) {
                if ("WARNING".equalsIgnoreCase(level)) {
                    countJpql.append(" AND f.flagLevel = com.mmo.entity.ShopFlag$FlagLevel.WARNING");
                } else if ("SEVERE".equalsIgnoreCase(level)) {
                    countJpql.append(" AND f.flagLevel = com.mmo.entity.ShopFlag$FlagLevel.SEVERE");
                } else if ("BANNED".equalsIgnoreCase(level)) {
                    countJpql.append(" AND f.flagLevel = com.mmo.entity.ShopFlag$FlagLevel.BANNED");
                }
            }
            if (search != null && !search.trim().isEmpty()) {
                countJpql.append(" AND (LOWER(s.shopName) LIKE LOWER(:search) OR LOWER(su.fullName) LIKE LOWER(:search) OR CAST(f.id AS string) LIKE :search)");
            }

            TypedQuery<Long> countQuery = entityManager.createQuery(countJpql.toString(), Long.class);
            if (search != null && !search.trim().isEmpty()) {
                countQuery.setParameter("search", "%" + search.trim() + "%");
            }
            long total = countQuery.getSingleResult();

            // Get paginated results
            TypedQuery<com.mmo.entity.ShopFlag> query = entityManager.createQuery(jpql.toString(), com.mmo.entity.ShopFlag.class);
            if (search != null && !search.trim().isEmpty()) {
                query.setParameter("search", "%" + search.trim() + "%");
            }
            query.setFirstResult(page * 10);
            query.setMaxResults(10);
            List<com.mmo.entity.ShopFlag> flags = query.getResultList();

            int totalPages = (int) Math.ceil((double) total / 10);

            model.addAttribute("flags", flags);
            model.addAttribute("currentPage", page);
            model.addAttribute("currentSearch", search);
            model.addAttribute("currentStatus", status);
            model.addAttribute("currentLevel", level);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("pageTitle", "Flag Management");
            model.addAttribute("body", "admin/flag-management");
            return "admin/layout";
        } catch (Exception e) {
            logger.error("Error loading flag management page", e);
            model.addAttribute("flags", new java.util.ArrayList<>());
            model.addAttribute("currentPage", page);
            model.addAttribute("currentSearch", search);
            model.addAttribute("currentStatus", status);
            model.addAttribute("currentLevel", level);
            model.addAttribute("totalPages", 0);
            model.addAttribute("errorMessage", "Error loading flags: " + e.getMessage());
            model.addAttribute("pageTitle", "Flag Management");
            model.addAttribute("body", "admin/flag-management");
            return "admin/layout";
        }
    }

    /**
     * Create a new flag for a shop
     */
    @PostMapping("/shops/{shopId}/flag")
    @ResponseBody
    public ResponseEntity<?> flagShop(@PathVariable Long shopId,
                                      @RequestBody CreateShopFlagRequest request,
                                      Authentication auth) {
        try {
            logger.info("=== FLAG SHOP REQUEST START ===");
            logger.info("Received flag request for shop {}", shopId);
            logger.info("Request body: shopId={}, reason={}, flagLevel={}, relatedComplaintId={}",
                    request.shopId(), request.reason(), request.flagLevel(), request.relatedComplaintId());

            if (auth == null || !auth.isAuthenticated()) {
                logger.warn("Unauthorized flag attempt for shop {}", shopId);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Unauthorized"));
            }

            String authEmail = auth.getName();
            logger.info("Auth user email: {}", authEmail);

            User admin = entityManager.createQuery("select u from User u where lower(u.email)=lower(:e)", User.class)
                    .setParameter("e", authEmail)
                    .getResultStream().findFirst().orElse(null);

            if (admin == null) {
                logger.error("Admin user not found for email: {}", authEmail);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Admin user not found"));
            }

            logger.info("Admin found: id={}, role={}", admin.getId(), admin.getRole());

            if (!"ADMIN".equalsIgnoreCase(admin.getRole())) {
                logger.warn("Non-admin user {} (role={}) attempted to flag shop {}",
                        authEmail, admin.getRole(), shopId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Forbidden - Admin role required"));
            }

            // Validate request
            if (request.reason() == null || request.reason().trim().isEmpty()) {
                logger.warn("Flag request missing reason for shop {}", shopId);
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Reason is required"));
            }

            if (request.flagLevel() == null || request.flagLevel().trim().isEmpty()) {
                logger.warn("Flag request missing flag level for shop {}", shopId);
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Flag level is required"));
            }

            com.mmo.entity.ShopFlag.FlagLevel flagLevel;
            try {
                flagLevel = com.mmo.entity.ShopFlag.FlagLevel.valueOf(request.flagLevel().toUpperCase());
                logger.info("Parsed flag level: {}", flagLevel);
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid flag level {} for shop {}", request.flagLevel(), shopId);
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Invalid flag level. Must be WARNING, SEVERE, or BANNED"));
            }

            logger.info("Creating {} flag for shop {} by admin {}", flagLevel, shopId, admin.getId());

            com.mmo.entity.ShopFlag flag = shopFlagService.createFlag(
                    shopId,
                    admin.getId(),
                    request.relatedComplaintId(),
                    request.reason(),
                    flagLevel
            );

            logger.info("Successfully created flag {} for shop {}", flag.getId(), shopId);
            logger.info("=== FLAG SHOP REQUEST SUCCESS ===");

            return ResponseEntity.ok(com.mmo.dto.ShopFlagResponse.from(flag));

        } catch (Exception e) {
            logger.error("=== FLAG SHOP REQUEST FAILED ===");
            logger.error("Error flagging shop {}: {}", shopId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to flag shop: " + e.getMessage()));
        }
    }

    /**
     * Resolve a flag
     */
    @PutMapping("/flags/{flagId}/resolve")
    @ResponseBody
    public ResponseEntity<?> resolveFlag(@PathVariable Long flagId,
                                         @RequestBody ResolveShopFlagRequest request,
                                         Authentication auth) {
        try {
            if (auth == null || !auth.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
            }

            User admin = entityManager.createQuery("select u from User u where lower(u.email)=lower(:e)", User.class)
                    .setParameter("e", auth.getName())
                    .getResultStream().findFirst().orElse(null);

            if (admin == null || !"ADMIN".equalsIgnoreCase(admin.getRole())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Forbidden"));
            }

            com.mmo.entity.ShopFlag flag = shopFlagService.resolveFlag(flagId, request.resolutionNotes());

            return ResponseEntity.ok(com.mmo.dto.ShopFlagResponse.from(flag));

        } catch (Exception e) {
            logger.error("Error resolving flag", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to resolve flag: " + e.getMessage()));
        }
    }

    /**
     * Get flag details
     */
    @GetMapping("/flags/{flagId}")
    @ResponseBody
    public ResponseEntity<?> getFlagDetail(@PathVariable Long flagId, Authentication auth) {
        try {
            if (auth == null || !auth.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
            }

            com.mmo.entity.ShopFlag flag = entityManager.createQuery(
                    "SELECT f FROM ShopFlag f LEFT JOIN FETCH f.shop s LEFT JOIN FETCH s.user LEFT JOIN FETCH f.admin LEFT JOIN FETCH f.relatedComplaint WHERE f.id = :id",
                    com.mmo.entity.ShopFlag.class
            ).setParameter("id", flagId).getResultStream().findFirst().orElse(null);

            if (flag == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Flag not found"));
            }

            return ResponseEntity.ok(com.mmo.dto.ShopFlagResponse.from(flag));

        } catch (Exception e) {
            logger.error("Error getting flag detail", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get flag detail: " + e.getMessage()));
        }
    }

    /**
     * Get shop flag statistics
     */
    @GetMapping("/shops/{shopId}/flag-stats")
    @ResponseBody
    public ResponseEntity<?> getShopFlagStats(@PathVariable Long shopId, Authentication auth) {
        try {
            if (auth == null || !auth.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
            }

            Map<String, Object> stats = shopFlagService.getShopFlagStats(shopId);
            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            logger.error("Error getting shop flag stats", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get shop flag stats: " + e.getMessage()));
        }
    }

    /**
     * Process amnesty request for a shop
     */
    @PostMapping("/shops/{shopId}/amnesty")
    @ResponseBody
    public ResponseEntity<?> processAmnesty(@PathVariable Long shopId, Authentication auth) {
        try {
            if (auth == null || !auth.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
            }

            User admin = entityManager.createQuery("select u from User u where lower(u.email)=lower(:e)", User.class)
                    .setParameter("e", auth.getName())
                    .getResultStream().findFirst().orElse(null);

            if (admin == null || !"ADMIN".equalsIgnoreCase(admin.getRole())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Forbidden"));
            }

            boolean success = shopFlagService.processAmnestyRequest(shopId);

            if (success) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Amnesty granted successfully"
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Shop is not eligible for amnesty. Requirements: No flags in last 90 days and at least one warning flag."
                ));
            }

        } catch (Exception e) {
            logger.error("Error processing amnesty", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process amnesty: " + e.getMessage()));
        }
    }

    // ==================== SCHEDULER MANAGEMENT ====================

    @Autowired
    private com.mmo.scheduler.ShopFlagScheduler shopFlagScheduler;

    /**
     * Manual trigger: Auto-remove old flags
     * For testing scheduler without waiting
     */
    @PostMapping("/scheduler/flags/auto-remove")
    @ResponseBody
    public ResponseEntity<?> triggerAutoRemoveOldFlags(Authentication auth) {
        try {
            if (auth == null || !auth.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
            }

            User admin = entityManager.createQuery("select u from User u where lower(u.email)=lower(:e)", User.class)
                    .setParameter("e", auth.getName())
                    .getResultStream().findFirst().orElse(null);

            if (admin == null || !"ADMIN".equalsIgnoreCase(admin.getRole())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Forbidden"));
            }

            logger.info("Admin {} manually triggered auto-remove old flags job", admin.getId());
            shopFlagScheduler.runAutoRemoveOldFlagsManually();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Auto-remove old flags job triggered successfully. Check logs for details."
            ));

        } catch (Exception e) {
            logger.error("Error triggering auto-remove old flags", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to trigger job: " + e.getMessage()));
        }
    }

    /**
     * Manual trigger: Check flags and notify admins
     * For testing scheduler without waiting
     */
    @PostMapping("/scheduler/flags/check-notify")
    @ResponseBody
    public ResponseEntity<?> triggerCheckFlagsNotify(Authentication auth) {
        try {
            if (auth == null || !auth.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
            }

            User admin = entityManager.createQuery("select u from User u where lower(u.email)=lower(:e)", User.class)
                    .setParameter("e", auth.getName())
                    .getResultStream().findFirst().orElse(null);

            if (admin == null || !"ADMIN".equalsIgnoreCase(admin.getRole())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Forbidden"));
            }

            logger.info("Admin {} manually triggered check flags and notify job", admin.getId());
            shopFlagScheduler.runCheckFlagsManually();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Check flags and notify admins job triggered successfully. Check logs for details."
            ));

        } catch (Exception e) {
            logger.error("Error triggering check flags notify", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to trigger job: " + e.getMessage()));
        }
    }

    /**
     * Get scheduler status and statistics
     */
    @GetMapping("/scheduler/flags/status")
    @ResponseBody
    public ResponseEntity<?> getSchedulerStatus(Authentication auth) {
        try {
            if (auth == null || !auth.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
            }

            // Get overall statistics
            long totalFlags = entityManager.createQuery("SELECT COUNT(f) FROM ShopFlag f", Long.class)
                    .getSingleResult();
            long activeFlags = entityManager.createQuery(
                    "SELECT COUNT(f) FROM ShopFlag f WHERE f.status = com.mmo.entity.ShopFlag$FlagStatus.ACTIVE", Long.class)
                    .getSingleResult();
            long resolvedFlags = entityManager.createQuery(
                    "SELECT COUNT(f) FROM ShopFlag f WHERE f.status = com.mmo.entity.ShopFlag$FlagStatus.RESOLVED", Long.class)
                    .getSingleResult();

            // Flags eligible for auto-removal (older than 180 days)
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.add(java.util.Calendar.DAY_OF_MONTH, -180);
            Date oneEightyDaysAgo = cal.getTime();

            long eligibleForRemoval = entityManager.createQuery(
                    "SELECT COUNT(f) FROM ShopFlag f WHERE f.status = com.mmo.entity.ShopFlag$FlagStatus.ACTIVE " +
                    "AND f.createdAt < :date", Long.class)
                    .setParameter("date", oneEightyDaysAgo)
                    .getSingleResult();

            // Shops needing penalty check
            long shopsWithActiveFlags = entityManager.createQuery(
                    "SELECT COUNT(DISTINCT f.shop.id) FROM ShopFlag f WHERE f.status = com.mmo.entity.ShopFlag$FlagStatus.ACTIVE", Long.class)
                    .getSingleResult();

            Map<String, Object> status = new java.util.HashMap<>();
            status.put("totalFlags", totalFlags);
            status.put("activeFlags", activeFlags);
            status.put("resolvedFlags", resolvedFlags);
            status.put("eligibleForAutoRemoval", eligibleForRemoval);
            status.put("shopsWithActiveFlags", shopsWithActiveFlags);
            status.put("schedulerEnabled", true);
            status.put("schedules", Map.of(
                    "autoRemoveOldFlags", "Daily at 2:00 AM - Auto-remove flags older than 180 days (cron: 0 0 2 * * ?)",
                    "checkFlagsNotify", "Every 6 hours - Check flags and notify admins (3 flags/5 flags alerts, 7 flags auto-ban) (cron: 0 0 */6 * * ?)",
                    "dailyReport", "Daily at 8:00 AM - Generate statistics report (cron: 0 0 8 * * ?)"
            ));
            status.put("notes", Map.of(
                    "autoDowngrade", "NOT ENABLED - Admins must manually downgrade shop levels",
                    "autoBan", "ENABLED - 7 flags or 1 BANNED flag will auto-ban shop"
            ));

            return ResponseEntity.ok(status);

        } catch (Exception e) {
            logger.error("Error getting scheduler status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get scheduler status: " + e.getMessage()));
        }
    }
}


