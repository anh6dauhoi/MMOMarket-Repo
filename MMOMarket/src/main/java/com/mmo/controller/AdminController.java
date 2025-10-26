package com.mmo.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

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
import com.mmo.dto.SellerRegistrationDTO;
import com.mmo.dto.UpdateCategoryRequest;
import com.mmo.dto.WithdrawalDetailResponse;
import com.mmo.entity.Category;
import com.mmo.entity.CoinDeposit;
import com.mmo.entity.SellerRegistration;
import com.mmo.entity.ShopInfo;
import com.mmo.entity.User;
import com.mmo.entity.Withdrawal;
import com.mmo.service.CategoryService;
import com.mmo.service.SellerService;
import com.mmo.util.Bank;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

@Controller
@RequestMapping("/admin")
@SuppressWarnings("unchecked")
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    @Autowired
    private SellerService sellerService;

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

    @PersistenceContext
    private EntityManager entityManager;

    // ==================== USER MANAGEMENT ====================
    @GetMapping("/users")
    public String usersManagement(@RequestParam(name = "page", defaultValue = "0") int page,
                                  @RequestParam(name = "search", defaultValue = "") String search,
                                  @RequestParam(name = "role", defaultValue = "") String role,
                                  @RequestParam(name = "shopStatus", defaultValue = "") String shopStatus,
                                  @RequestParam(name = "sort", defaultValue = "") String sort,
                                  Model model) {
        // Determine sort field and direction
        String sortField = "createdAt";
        Sort.Direction sortDirection = Sort.Direction.DESC;
        
        if (sort != null && !sort.isBlank()) {
            if (sort.equals("role_asc")) {
                sortField = "role";
                sortDirection = Sort.Direction.ASC;
            } else if (sort.equals("role_desc")) {
                sortField = "role";
                sortDirection = Sort.Direction.DESC;
            } else if (sort.equals("shopStatus_asc")) {
                sortField = "shopStatus";
                sortDirection = Sort.Direction.ASC;
            } else if (sort.equals("shopStatus_desc")) {
                sortField = "shopStatus";
                sortDirection = Sort.Direction.DESC;
            } else if (sort.equals("coins_asc")) {
                sortField = "coins";
                sortDirection = Sort.Direction.ASC;
            } else if (sort.equals("coins_desc")) {
                sortField = "coins";
                sortDirection = Sort.Direction.DESC;
            }
        }
        
        Pageable pageable = PageRequest.of(page, 10, Sort.by(sortDirection, sortField));
        
        StringBuilder query = new StringBuilder("SELECT u FROM User u WHERE 1=1");
        
        if (search != null && !search.isBlank()) {
            query.append(" AND (LOWER(u.fullName) LIKE LOWER(:search) OR LOWER(u.email) LIKE LOWER(:search))");
        }
        
        if (role != null && !role.isBlank()) {
            query.append(" AND LOWER(u.role) = LOWER(:role)");
        }
        
        if (shopStatus != null && !shopStatus.isBlank()) {
            query.append(" AND LOWER(u.shopStatus) = LOWER(:shopStatus)");
        }
        
        query.append(" ORDER BY u.").append(sortField).append(" ").append(sortDirection.name());
        
        var q = entityManager.createQuery(query.toString(), User.class);
        
        if (search != null && !search.isBlank()) {
            q.setParameter("search", "%" + search + "%");
        }
        
        if (role != null && !role.isBlank()) {
            q.setParameter("role", role);
        }
        
        if (shopStatus != null && !shopStatus.isBlank()) {
            q.setParameter("shopStatus", shopStatus);
        }
        
        List<User> allUsers = q.getResultList();
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), allUsers.size());
        List<User> pageContent = allUsers.subList(start, end);
        Page<User> userPage = new PageImpl<>(pageContent, pageable, allUsers.size());
        
        model.addAttribute("users", userPage.getContent());
        model.addAttribute("totalPages", userPage.getTotalPages());
        model.addAttribute("currentPage", page);
        model.addAttribute("currentSearch", search);
        model.addAttribute("currentRole", role);
        model.addAttribute("currentShopStatus", shopStatus);
        model.addAttribute("currentSort", sort);
        model.addAttribute("pageTitle", "User Management");
        model.addAttribute("body", "admin/users");
        return "admin/layout";
    }

    @GetMapping("/users/{id}")
    @ResponseBody
    public ResponseEntity<?> getUser(@PathVariable Long id, Authentication auth) {
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
            
            return ResponseEntity.ok(user);
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }

    @PutMapping("/users/{id}")
    @ResponseBody
    @Transactional
    public ResponseEntity<?> updateUser(@PathVariable Long id,
                                       @RequestBody java.util.Map<String, Object> request,
                                       Authentication auth) {
        logger.info("=== START updateUser - ID: {}, Request: {}", id, request);
        try {
            if (auth == null || !auth.isAuthenticated()) {
                logger.warn("Unauthorized access attempt");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }

            User admin = entityManager.createQuery("select u from User u where lower(u.email)=lower(:e)", User.class)
                    .setParameter("e", auth.getName())
                    .getResultStream().findFirst().orElse(null);

            if (admin == null || admin.getRole() == null || !admin.getRole().equalsIgnoreCase("ADMIN")) {
                logger.warn("Forbidden access attempt by: {}", auth.getName());
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");
            }

            // Use repository instead of EntityManager
            logger.debug("Finding user with ID: {}", id);
            User user = userRepository.findById(id).orElse(null);
            if (user == null) {
                logger.warn("User not found with ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
            }

            logger.debug("User found - Before update: fullName={}, phone={}, role={}, coins={}", 
                user.getFullName(), user.getPhone(), user.getRole(), user.getCoins());

            if (request.containsKey("fullName")) {
                user.setFullName((String) request.get("fullName"));
                logger.debug("Updated fullName to: {}", user.getFullName());
            }
            if (request.containsKey("phone")) {
                String phone = (String) request.get("phone");
                if (phone != null && !phone.isEmpty() && !phone.matches("^0\\d{9}$")) {
                    logger.warn("Invalid phone format: {}", phone);
                    return ResponseEntity.badRequest().body("Phone must be 10 digits starting with 0");
                }
                user.setPhone(phone);
                logger.debug("Updated phone to: {}", user.getPhone());
            }
            if (request.containsKey("role")) {
                user.setRole((String) request.get("role"));
                logger.debug("Updated role to: {}", user.getRole());
            }
            if (request.containsKey("coins")) {
                Object coinsObj = request.get("coins");
                logger.debug("Coins object type: {}, value: {}", coinsObj != null ? coinsObj.getClass().getName() : "null", coinsObj);
                if (coinsObj instanceof Number) {
                    Long oldCoins = user.getCoins();
                    user.setCoins(((Number) coinsObj).longValue());
                    logger.debug("Updated coins from {} to {}", oldCoins, user.getCoins());
                } else if (coinsObj instanceof String) {
                    try {
                        Long oldCoins = user.getCoins();
                        user.setCoins(Long.parseLong((String) coinsObj));
                        logger.debug("Updated coins (from String) from {} to {}", oldCoins, user.getCoins());
                    } catch (NumberFormatException e) {
                        logger.warn("Invalid coins format: {}", coinsObj);
                    }
                } else {
                    logger.warn("Coins is neither Number nor String, it's: {}", coinsObj != null ? coinsObj.getClass().getName() : "null");
                }
            }

            logger.debug("User after update - Before save: fullName={}, phone={}, role={}, coins={}", 
                user.getFullName(), user.getPhone(), user.getRole(), user.getCoins());

            // Use repository.save() instead of entityManager.merge()
            User savedUser = userRepository.save(user);
            logger.info("User saved successfully - After save: fullName={}, phone={}, role={}, coins={}", 
                savedUser.getFullName(), savedUser.getPhone(), savedUser.getRole(), savedUser.getCoins());
            
            return ResponseEntity.ok("User updated successfully");
        } catch (Exception ex) {
            logger.error("Error updating user with ID: {}", id, ex);
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }

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

    // ==================== CATEGORY MANAGEMENT ====================

    @GetMapping("/categories")
    public String categoriesManagement(@RequestParam(name = "page", defaultValue = "0") int page,
                                       @RequestParam(name = "search", defaultValue = "") String search,
                                       @RequestParam(name = "type", defaultValue = "") String type,
                                       @RequestParam(name = "sort", defaultValue = "") String sort,
                                       Model model) {
        Pageable pageable = PageRequest.of(page, 10);
        
        // Build dynamic query with LEFT JOIN FETCH to eagerly load products
        StringBuilder jpql = new StringBuilder("SELECT DISTINCT c FROM Category c LEFT JOIN FETCH c.products WHERE c.isDelete = false");
        
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
            
            logger.info("Step 3: Query built: {}", jpql.toString());
            
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
            } else if (sort.equals("comments_asc")) {
                jpql.append(" ORDER BY SIZE(b.comments) ASC");
            } else if (sort.equals("comments_desc")) {
                jpql.append(" ORDER BY SIZE(b.comments) DESC");
            }
        }
        
        // Create query
        TypedQuery<com.mmo.entity.Blog> query = entityManager.createQuery(jpql.toString(), com.mmo.entity.Blog.class);
        
        if (search != null && !search.trim().isEmpty()) {
            query.setParameter("search", "%" + search.trim() + "%");
        }
        
        if (status != null && status.equals("new")) {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.add(java.util.Calendar.DAY_OF_MONTH, -7);
            query.setParameter("sevenDaysAgo", cal.getTime());
        }
        
        // Get total count
        List<com.mmo.entity.Blog> allResults = query.getResultList();
        int total = allResults.size();
        
        // Apply pagination
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

    @DeleteMapping("/blogs/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteBlog(@PathVariable Long id, Authentication auth) {
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

            blogService.deleteBlog(id, admin.getId());
            return ResponseEntity.ok().body("Blog deleted successfully");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }

    @GetMapping("/blogs/deleted")
    public String deletedBlogs(@RequestParam(name = "page", defaultValue = "0") int page,
                               @RequestParam(name = "search", defaultValue = "") String search,
                               Model model) {
        Pageable pageable = PageRequest.of(page, 10);
        Page<com.mmo.entity.Blog> blogPage;

        if (search == null || search.trim().isEmpty()) {
            blogPage = blogService.getDeletedBlogs(pageable);
        } else {
            blogPage = blogService.searchDeletedBlogs(search.trim(), pageable);
        }

        model.addAttribute("blogs", blogPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("currentSearch", search);
        model.addAttribute("totalPages", blogPage.getTotalPages());
        model.addAttribute("pageTitle", "Deleted Blogs");
        model.addAttribute("body", "admin/deleted-blogs");
        return "admin/layout";
    }

    @GetMapping("/blogs/deleted/list")
    @ResponseBody
    public ResponseEntity<?> getDeletedBlogsList() {
        try {
            Pageable pageable = PageRequest.of(0, 100);
            Page<com.mmo.entity.Blog> blogPage = blogService.getDeletedBlogs(pageable);
            return ResponseEntity.ok(blogPage.getContent());
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }

    @PostMapping("/blogs/{id}/restore")
    @ResponseBody
    public ResponseEntity<?> restoreBlog(@PathVariable Long id, Authentication auth) {
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

            com.mmo.entity.Blog blog = blogService.restoreBlog(id);
            return ResponseEntity.ok(blog);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }

    // ==================== SHOP MANAGEMENT ====================

    @GetMapping("/shops")
    public String shops(@RequestParam(name = "page", defaultValue = "0") int page,
                       @RequestParam(name = "search", defaultValue = "") String search,
                       @RequestParam(name = "status", defaultValue = "") String status,
                       @RequestParam(name = "sort", defaultValue = "") String sort,
                       Model model) {
        Pageable pageable = PageRequest.of(page, 10);
        
        // Build dynamic query using ShopInfo entity
        StringBuilder jpql = new StringBuilder(
            "SELECT s FROM ShopInfo s WHERE s.isDelete = false"
        );
        
        if (search != null && !search.trim().isEmpty()) {
            jpql.append(" AND (LOWER(s.shopName) LIKE LOWER(:search) OR LOWER(s.user.fullName) LIKE LOWER(:search))");
        }
        
        if (status != null && !status.trim().isEmpty()) {
            jpql.append(" AND s.user.shopStatus = :status");
        }
        
        // Add sorting
        if (sort != null && !sort.isEmpty()) {
            if (sort.equals("rating_asc")) {
                jpql.append(" ORDER BY (SELECT AVG(CAST(r.rating AS double)) FROM Review r WHERE r.product.seller.id = s.user.id) ASC NULLS FIRST");
            } else if (sort.equals("rating_desc")) {
                jpql.append(" ORDER BY (SELECT AVG(CAST(r.rating AS double)) FROM Review r WHERE r.product.seller.id = s.user.id) DESC NULLS LAST");
            } else if (sort.equals("commission_asc")) {
                jpql.append(" ORDER BY s.commission ASC");
            } else if (sort.equals("commission_desc")) {
                jpql.append(" ORDER BY s.commission DESC");
            }
        }
        
        // Create query
        TypedQuery<ShopInfo> query = entityManager.createQuery(jpql.toString(), ShopInfo.class);
        
        if (search != null && !search.trim().isEmpty()) {
            query.setParameter("search", "%" + search.trim() + "%");
        }
        
        if (status != null && !status.trim().isEmpty()) {
            query.setParameter("status", status);
        }
        
        // Get total count
        List<ShopInfo> allResults = query.getResultList();
        int total = allResults.size();
        
        // Apply pagination
        query.setFirstResult(page * 10);
        query.setMaxResults(10);
        List<ShopInfo> shopInfos = query.getResultList();
        
        // Convert to ShopResponse
        List<com.mmo.dto.ShopResponse> shops = shopInfos.stream()
            .map(shop -> com.mmo.dto.ShopResponse.fromEntity(shop, 0L, 0.0))
            .collect(java.util.stream.Collectors.toList());
        
        Page<com.mmo.dto.ShopResponse> shopPage = new PageImpl<>(shops, pageable, total);

        model.addAttribute("shops", shopPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("currentSearch", search);
        model.addAttribute("currentStatus", status);
        model.addAttribute("currentSort", sort);
        model.addAttribute("totalPages", shopPage.getTotalPages());
        model.addAttribute("pageTitle", "Shop Management");
        model.addAttribute("body", "admin/shops");
        return "admin/layout";
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
