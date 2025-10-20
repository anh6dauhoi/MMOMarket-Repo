package com.mmo.controller;

import com.mmo.dto.SellerRegistrationDTO;
import com.mmo.dto.ProcessWithdrawalRequest;
import com.mmo.dto.AdminWithdrawalResponse;
import com.mmo.dto.WithdrawalDetailResponse;
import com.mmo.dto.CoinDepositDetailResponse;
import com.mmo.entity.SellerRegistration;
import com.mmo.entity.User;
import com.mmo.entity.Withdrawal;
import com.mmo.entity.CoinDeposit;
import com.mmo.service.SellerService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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

@Controller
@RequestMapping("/admin")
@SuppressWarnings("unchecked")
public class AdminController {

    @Autowired
    private SellerService sellerService;

    @Autowired
    private com.mmo.service.NotificationService notificationService;

    @Autowired
    private com.mmo.service.WithdrawalService withdrawalService;

    @Autowired
    private com.mmo.service.EmailService emailService;

    @PersistenceContext
    private EntityManager entityManager;

    @GetMapping("/seller-registrations")
    public String sellerRegistrations(@RequestParam(name = "status", defaultValue = "All") String status,
                                      @RequestParam(name = "page", defaultValue = "0") int page,
                                      @RequestParam(name = "search", defaultValue = "") String search,
                                      @RequestParam(name = "sort", defaultValue = "date_desc") String sort,
                                      Model model) {
        // Determine sort direction for createdAt
        Sort.Direction dir = Sort.Direction.DESC;
        if (sort != null) {
            String s = sort.trim().toLowerCase();
            if ("date_asc".equals(s) || "created_at_asc".equals(s)) dir = Sort.Direction.ASC;
        }
        Pageable pageable = PageRequest.of(page, 10, Sort.by(dir, "createdAt"));

        Page<SellerRegistration> registrationPage;
        if (search == null || search.isBlank()) {
            // use existing service method (2 args)
            registrationPage = sellerService.findAllRegistrations(status, pageable);
        } else {
            // fallback: perform JPQL search and manual paging into PageImpl
            StringBuilder q = new StringBuilder("SELECT s FROM SellerRegistration s LEFT JOIN FETCH s.user u WHERE 1=1");
            if (!"All".equalsIgnoreCase(status)) {
                q.append(" AND LOWER(s.status) = LOWER(:status)");
            }
            q.append(" AND (LOWER(u.fullName) LIKE LOWER(:search) OR LOWER(u.email) LIKE LOWER(:search) OR LOWER(s.shopName) LIKE LOWER(:search) OR CAST(s.id AS string) LIKE :search)");
            q.append(" ORDER BY s.createdAt ").append(dir.isAscending() ? "ASC" : "DESC");
            jakarta.persistence.Query query = entityManager.createQuery(q.toString(), SellerRegistration.class);
            if (!"All".equalsIgnoreCase(status)) query.setParameter("status", status);
            query.setParameter("search", "%" + search + "%");
            List<SellerRegistration> all = query.getResultList();
            int total = all.size();
            int start = (int) pageable.getOffset();
            int end = Math.min((start + pageable.getPageSize()), total);
            List<SellerRegistration> pageContent = start <= end ? all.subList(start, end) : List.of();
            registrationPage = new PageImpl<>(pageContent, pageable, total);
        }

        model.addAttribute("registrations", registrationPage.getContent());
        model.addAttribute("currentStatus", status);
        model.addAttribute("currentPage", page);
        model.addAttribute("currentSearch", search);
        model.addAttribute("currentSort", sort);
        model.addAttribute("totalPages", registrationPage.getTotalPages());
        model.addAttribute("pageTitle", "Seller Registrations");
        model.addAttribute("body", "admin/seller-registrations");
        return "admin/layout";
    }

    @GetMapping("/seller-registrations/{id}")
    @ResponseBody
    public ResponseEntity<SellerRegistrationDTO> getRegistrationDetail(@PathVariable Long id) {
        return sellerService.findById(id)
                .map(reg -> ResponseEntity.ok(SellerRegistrationDTO.builder()
                        .id(reg.getId())
                        .customerId(reg.getUser().getId())
                        .shopName(reg.getShopName())
                        .email(reg.getUser().getEmail())
                        .phone(reg.getUser().getPhone())
                        .registrationDate(reg.getCreatedAt() != null ?
                                reg.getCreatedAt().toInstant().atZone(ZoneId.systemDefault()).toLocalDate().toString() : "")
                        .status(reg.getStatus())
                        .contractName(reg.getContract())
                        .contractUrl(reg.getContract() != null ? "/admin/seller-registrations/" + reg.getId() + "/contract" : null)
                        .signedContractName(reg.getSignedContract())
                        .signedContractUrl(reg.getSignedContract() != null ? "/admin/seller-registrations/" + reg.getId() + "/contract?signed=true" : null)
                        .reason(reg.getReason())
                        .description(reg.getDescription())
                        .build()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Approve: use service (handles storage + validations)
    @PostMapping(value = "/seller-registrations/{id}/approve", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public ResponseEntity<?> approve(@PathVariable Long id,
                                     @RequestPart(value = "contract", required = false) MultipartFile contract) {
        try {
            // Check if the registration already has a contract
            Optional<SellerRegistration> registration = sellerService.findById(id);
            boolean hasExistingContract = registration.isPresent() &&
                    registration.get().getContract() != null &&
                    !registration.get().getContract().isEmpty();

            // Only require a contract file if there isn't one already
            if (!hasExistingContract && (contract == null || contract.isEmpty())) {
                return ResponseEntity.badRequest().body("A contract file is required to approve a seller registration.");
            }

            sellerService.approve(id, contract);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }

    // Reject: use service
    @PostMapping("/seller-registrations/{id}/reject")
    @ResponseBody
    public ResponseEntity<?> reject(@PathVariable Long id, @RequestParam("reason") String reason) {
        try {
            sellerService.reject(id, reason);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }

    // Activate: use service (updates reg to Active and user.shopStatus = Active)
    @PostMapping("/seller-registrations/{id}/activate")
    @ResponseBody
    public ResponseEntity<?> activate(@PathVariable Long id) {
        try {
            sellerService.activate(id);
            return ResponseEntity.ok().build();
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
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

    @GetMapping("/seller-registrations/{id}/contract")
    public ResponseEntity<Resource> downloadContract(@PathVariable Long id,
                                                     @RequestParam(name = "signed", defaultValue = "false") boolean signed) throws Exception {
        Resource resource = sellerService.loadContract(id, signed);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
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

            String action = req.getStatus() == null ? "" : req.getStatus().trim();
            boolean approve = "Approved".equalsIgnoreCase(action);
            boolean reject = "Rejected".equalsIgnoreCase(action);
            if (!approve && !reject) {
                return ResponseEntity.badRequest().body("Status must be 'Approved' or 'Rejected'.");
            }
            boolean refund = reject; // chỉ refund khi bị từ chối
            Withdrawal wd = withdrawalService.processWithdrawal(
                id,
                req.getStatus(),
                req.getProofFile(),
                req.getReason(),
                refund
            );
            // Gửi notification cho Seller
            User seller = wd.getSeller();
            Long amount = wd.getAmount() == null ? 0L : wd.getAmount();
            if (approve) {
                notificationService.createNotificationForUser(seller.getId(), "Withdrawal Approved", "Your withdrawal request of " + amount + " VND has been approved. Proof: " + req.getProofFile());
            } else {
                notificationService.createNotificationForUser(seller.getId(), "Withdrawal Rejected", "Your withdrawal request of " + amount + " VND has been rejected. Reason: " + req.getReason() + ". 95% of the amount has been refunded to your account.");
            }
            return ResponseEntity.ok(AdminWithdrawalResponse.from(wd));
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

            String action = status == null ? "" : status.trim();
            boolean approve = "Approved".equalsIgnoreCase(action);
            if (!approve) {
                return ResponseEntity.badRequest().body("This endpoint only supports Approved via multipart (file upload).");
            }

            if (proof == null || proof.isEmpty()) {
                return ResponseEntity.badRequest().body("Proof file is required for Approved.");
            }

            // Save uploaded file under uploads/withdrawals
            try {
                // Use absolute path for uploads directory (project root)
                String rootDir = System.getProperty("user.dir"); // Project root
                Path storage = Paths.get(rootDir, "uploads", "withdrawals");
                Files.createDirectories(storage);
                String original = proof.getOriginalFilename() == null ? "file" : proof.getOriginalFilename().replaceAll("[^a-zA-Z0-9._-]", "_");
                String filename = "withdrawal-" + id + "-" + System.currentTimeMillis() + "-" + original;
                Path dest = storage.resolve(filename);
                proof.transferTo(dest.toFile());
                // File URL for static serving (assumes /uploads/** is mapped in WebMvcConfigurer)
                String fileUrl = "/uploads/withdrawals/" + filename;

                wd.setStatus("Approved");
                wd.setProofFile(fileUrl);
                wd.setUpdatedAt(new java.util.Date());
                entityManager.merge(wd);

                User seller = wd.getSeller();
                Long amount = wd.getAmount() == null ? 0L : wd.getAmount();
                notificationService.createNotificationForUser(seller.getId(), "Withdrawal Approved", "Your withdrawal request of " + amount + " VND has been approved. Proof: " + fileUrl);

                return ResponseEntity.ok(AdminWithdrawalResponse.from(wd));
            } catch (Exception ex) {
                return ResponseEntity.status(500).body("Failed to save proof file: " + ex.getMessage());
            }
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
