package com.mmo.controller;

import com.mmo.entity.Orders;
import com.mmo.entity.User;
import com.mmo.repository.OrdersRepository;
import com.mmo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.*;

import com.mmo.entity.ProductVariant;
import com.mmo.entity.ProductVariantAccount;
import com.mmo.repository.ProductVariantRepository;
import com.mmo.repository.ProductVariantAccountRepository;

@Controller
public class OrderController {

    @Autowired
    private OrdersRepository ordersRepository;

    @Autowired
    private UserRepository userRepository;

    // New: inject variant and account repositories
    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private ProductVariantAccountRepository productVariantAccountRepository;

    private String resolveEmail(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return null;
        String email = authentication.getName();
        if (authentication instanceof OAuth2AuthenticationToken oauth2Token) {
            OAuth2User oauthUser = oauth2Token.getPrincipal();
            String mail = oauthUser.getAttribute("email");
            if (mail != null) email = mail;
        }
        return email;
    }

    @GetMapping("/account/orders")
    public String myOrders(Model model,
                           Authentication authentication,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "10") int size,
                           @RequestParam(required = false) String search,
                           @RequestParam(required = false) String sortBy,
                           @RequestParam(required = false) String sortDir) {
        String email = resolveEmail(authentication);
        if (email == null) {
            return "redirect:/authen/login";
        }
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return "redirect:/authen/login";
        }
        Long customerId = userOpt.get().getId();

        // Build pageable with sorting
        Pageable pageable;
        if (sortBy != null && !sortBy.isEmpty()) {
            org.springframework.data.domain.Sort.Direction direction =
                "asc".equalsIgnoreCase(sortDir) ? org.springframework.data.domain.Sort.Direction.ASC : org.springframework.data.domain.Sort.Direction.DESC;
            String fieldName;
            if ("price".equals(sortBy)) {
                fieldName = "totalPrice";
            } else if ("date".equals(sortBy)) {
                fieldName = "createdAt";
            } else {
                fieldName = "createdAt"; // default
            }
            pageable = PageRequest.of(page, size, org.springframework.data.domain.Sort.by(direction, fieldName));
        } else {
            pageable = PageRequest.of(page, size, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        }

        Page<Orders> orderPage;
        if (search != null && !search.trim().isEmpty()) {
            orderPage = ordersRepository.findByCustomerIdAndProductNameContaining(customerId, search.trim(), pageable);
        } else {
            orderPage = ordersRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable);
        }

        // Map to lightweight view models
        List<Map<String, Object>> items = new ArrayList<>();
        int startIndex = page * size;
        for (int i = 0; i < orderPage.getContent().size(); i++) {
            Orders o = orderPage.getContent().get(i);
            Map<String, Object> row = new HashMap<>();
            row.put("index", startIndex + i + 1);
            row.put("id", o.getId());
            row.put("productName", o.getProduct() != null ? o.getProduct().getName() : ("#" + o.getProductId()));
            row.put("status", o.getStatus() != null ? o.getStatus().name() : "");
            row.put("totalPrice", o.getTotalPrice());
            row.put("createdAt", o.getCreatedAt());
            items.add(row);
        }

        model.addAttribute("orders", items);
        model.addAttribute("currentPage", orderPage.getNumber());
        model.addAttribute("totalPages", orderPage.getTotalPages());
        model.addAttribute("pageSize", orderPage.getSize());
        model.addAttribute("totalElements", orderPage.getTotalElements());

        return "customer/my-orders";
    }

    @GetMapping("/account/orders/{id}")
    public String orderDetail(@PathVariable("id") Long id, Model model, Authentication authentication) {
        String email = resolveEmail(authentication);
        if (email == null) {
            return "redirect:/authen/login";
        }
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return "redirect:/authen/login";
        }
        Orders order = ordersRepository.findById(id).orElse(null);
        if (order == null || !Objects.equals(order.getCustomerId(), userOpt.get().getId())) {
            // Not found or not your order
            return "redirect:/account/orders";
        }
        // Ensure product is available for name display (fallback if null)
        String productName = (order.getProduct() != null) ? order.getProduct().getName() : ("Product #" + order.getProductId());
        model.addAttribute("order", order);
        model.addAttribute("productName", productName);

        // Load ProductVariant (tolerant to lazy loading)
        ProductVariant variant = null;
        try {
            if (order.getVariantId() != null) {
                variant = productVariantRepository.findById(order.getVariantId()).orElse(null);
            }
            if (variant == null) {
                variant = order.getVariant(); // may trigger lazy load inside Tx
            }
        } catch (Exception ignored) { }
        model.addAttribute("variant", variant);

        // Load delivered accounts linked to this order's transaction (exclude soft-deleted)
        java.util.List<ProductVariantAccount> accounts = java.util.Collections.emptyList();
        try {
            if (order.getTransactionId() != null) {
                accounts = productVariantAccountRepository.findByTransaction_IdAndIsDeleteFalse(order.getTransactionId());
            }
        } catch (Exception ignored) { }
        model.addAttribute("accounts", accounts);

        // Derive category type for warning content: 'warning' or 'common'
        String categoryType = "common";
        String categoryName = "Unknown";
        try {
            if (order.getProduct() != null && order.getProduct().getCategory() != null) {
                categoryName = order.getProduct().getCategory().getName();
                String t = order.getProduct().getCategory().getType();
                if (t != null && t.equalsIgnoreCase("Warning")) categoryType = "warning";
            }
        } catch (Exception ignored) { }
        model.addAttribute("categoryType", categoryType);
        model.addAttribute("categoryName", categoryName);

        // Load complaint policy URL from system configuration
        String complaintPolicyUrl = null;
        try {
            com.mmo.service.SystemConfigurationService systemConfigService =
                applicationContext.getBean(com.mmo.service.SystemConfigurationService.class);
            String configValue = systemConfigService.getStringValue(
                com.mmo.constant.SystemConfigKeys.POLICY_COMPLAINT_URL,
                null
            );
            // Only set if URL is valid (not null, not empty, and starts with http/https or /)
            if (configValue != null && !configValue.trim().isEmpty()) {
                configValue = configValue.trim();
                // Check if it's a valid URL pattern
                if (configValue.startsWith("http://") ||
                    configValue.startsWith("https://") ||
                    configValue.startsWith("/")) {
                    complaintPolicyUrl = configValue;
                }
            }
        } catch (Exception ignored) { }

        model.addAttribute("complaintPolicyUrl", complaintPolicyUrl);

        // Compute activation status and counts
        int activatedCount = 0;
        boolean activated = false;
        try {
            if (accounts != null && !accounts.isEmpty()) {
                for (ProductVariantAccount a : accounts) { if (a.isActivated()) activatedCount++; }
                activated = activatedCount == accounts.size();
            }
        } catch (Exception ignored) { }
        model.addAttribute("activated", activated);
        model.addAttribute("activatedCount", activatedCount);

        return "customer/order-detail";
    }

    @PostMapping("/account/orders/{id}/activate")
    @ResponseBody
    @Transactional
    public ResponseEntity<?> activateDeliveredAccounts(@PathVariable("id") Long id, Authentication authentication) {
        try {
            String email = resolveEmail(authentication);
            if (email == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            var userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            Orders order = ordersRepository.findById(id).orElse(null);
            if (order == null || !Objects.equals(order.getCustomerId(), userOpt.get().getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");
            }
            if (order.getTransactionId() == null) {
                return ResponseEntity.badRequest().body("No delivered accounts to activate.");
            }
            java.util.List<ProductVariantAccount> accounts = productVariantAccountRepository.findByTransaction_IdAndIsDeleteFalse(order.getTransactionId());
            if (accounts == null || accounts.isEmpty()) {
                return ResponseEntity.badRequest().body("No delivered accounts to activate.");
            }
            java.util.Date now = new java.util.Date();
            boolean anyUpdated = false;
            for (ProductVariantAccount acc : accounts) {
                if (!acc.isActivated()) {
                    acc.setActivated(true);
                    acc.setActivatedAt(now);
                    anyUpdated = true;
                }
            }
            if (anyUpdated) {
                productVariantAccountRepository.saveAll(accounts);
            }
            // Build payload
            java.util.Map<String,Object> payload = new java.util.HashMap<>();
            payload.put("ok", true);
            payload.put("count", accounts.size());
            java.util.List<java.util.Map<String,Object>> list = new java.util.ArrayList<>();
            for (ProductVariantAccount acc : accounts) {
                java.util.Map<String,Object> m = new java.util.HashMap<>();
                m.put("id", acc.getId());
                m.put("accountData", acc.getAccountData());
                m.put("activated", acc.isActivated());
                m.put("activatedAt", acc.getActivatedAt());
                list.add(m);
            }
            payload.put("accounts", list);
            return ResponseEntity.ok(payload);
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }

    @PostMapping("/account/orders/{orderId}/accounts/{accId}/activate")
    @ResponseBody
    @Transactional
    public ResponseEntity<?> activateSingleAccount(@PathVariable("orderId") Long orderId,
                                                   @PathVariable("accId") Long accId,
                                                   Authentication authentication) {
        try {
            String email = resolveEmail(authentication);
            if (email == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            var userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");

            Orders order = ordersRepository.findById(orderId).orElse(null);
            if (order == null || !Objects.equals(order.getCustomerId(), userOpt.get().getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");
            }
            if (order.getTransactionId() == null) {
                return ResponseEntity.badRequest().body("No delivered accounts to activate.");
            }
            var accOpt = productVariantAccountRepository.findByIdAndTransaction_IdAndIsDeleteFalse(accId, order.getTransactionId());
            if (accOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Account not found for this order.");
            }
            ProductVariantAccount acc = accOpt.get();
            if (!acc.isActivated()) {
                acc.setActivated(true);
                acc.setActivatedAt(new java.util.Date());
                productVariantAccountRepository.save(acc);
            }
            java.util.Map<String,Object> payload = new java.util.HashMap<>();
            payload.put("ok", true);
            payload.put("id", acc.getId());
            payload.put("activated", acc.isActivated());
            payload.put("activatedAt", acc.getActivatedAt());
            payload.put("accountData", acc.getAccountData());
            return ResponseEntity.ok(payload);
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }

    @GetMapping("/account/orders/{orderId}/accounts/{accId}/data")
    @ResponseBody
    @Transactional
    public ResponseEntity<?> getAccountData(@PathVariable("orderId") Long orderId,
                                           @PathVariable("accId") Long accId,
                                           Authentication authentication) {
        try {
            String email = resolveEmail(authentication);
            if (email == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            var userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");

            Orders order = ordersRepository.findById(orderId).orElse(null);
            if (order == null || !Objects.equals(order.getCustomerId(), userOpt.get().getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");
            }
            if (order.getTransactionId() == null) {
                return ResponseEntity.badRequest().body("No delivered accounts.");
            }
            var accOpt = productVariantAccountRepository.findByIdAndTransaction_IdAndIsDeleteFalse(accId, order.getTransactionId());
            if (accOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Account not found for this order.");
            }
            ProductVariantAccount acc = accOpt.get();

            // Only return data if account is activated
            if (!acc.isActivated()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Account must be activated first.");
            }

            java.util.Map<String,Object> payload = new java.util.HashMap<>();
            payload.put("ok", true);
            payload.put("id", acc.getId());
            payload.put("accountData", acc.getAccountData());
            payload.put("activatedAt", acc.getActivatedAt());
            return ResponseEntity.ok(payload);
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }

    // New: API endpoint to poll order status after purchase
    @GetMapping("/api/orders/{id}/status")
    @ResponseBody
    public ResponseEntity<?> checkOrderStatus(@PathVariable("id") Long id, Authentication authentication) {
        try {
            String email = resolveEmail(authentication);
            if (email == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));

            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));

            Orders order = ordersRepository.findById(id).orElse(null);
            if (order == null || !Objects.equals(order.getCustomerId(), userOpt.get().getId())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Order not found"));
            }

            Map<String, Object> response = new HashMap<>();
            response.put("orderId", order.getId());
            response.put("status", order.getStatus() != null ? order.getStatus().name() : "UNKNOWN");
            response.put("errorMessage", order.getErrorMessage());
            response.put("transactionId", order.getTransactionId());

            if (order.getStatus() == Orders.QueueStatus.COMPLETED) {
                // Include product name for success message
                String productName = (order.getProduct() != null) ? order.getProduct().getName() : ("Product #" + order.getProductId());
                response.put("productName", productName);
                response.put("quantity", order.getQuantity());
                response.put("totalPrice", order.getTotalPrice());
            }

            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal error"));
        }
    }

    // Complaint validation endpoint
    @PostMapping("/account/orders/{id}/complaint/validate")
    @ResponseBody
    @Transactional
    public ResponseEntity<?> validateComplaint(@PathVariable("id") Long id, Authentication authentication) {
        try {
            String email = resolveEmail(authentication);
            if (email == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("valid", false, "message", "Unauthorized"));

            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("valid", false, "message", "Unauthorized"));

            Orders order = ordersRepository.findById(id).orElse(null);
            if (order == null || !Objects.equals(order.getCustomerId(), userOpt.get().getId())) {
                return ResponseEntity.ok(Map.of("valid", false, "message", "Order not found"));
            }

            // Check if order is completed
            if (order.getStatus() != Orders.QueueStatus.COMPLETED) {
                return ResponseEntity.ok(Map.of("valid", false, "message", "Only completed orders can be complained about"));
            }

            // Check if transaction exists
            if (order.getTransactionId() == null) {
                return ResponseEntity.ok(Map.of("valid", false, "message", "No transaction found for this order"));
            }

            // Check if all accounts have been activated
            java.util.List<ProductVariantAccount> accounts =
                productVariantAccountRepository.findByTransaction_IdAndIsDeleteFalse(order.getTransactionId());

            if (accounts != null && !accounts.isEmpty()) {
                boolean allActivated = true;
                for (ProductVariantAccount acc : accounts) {
                    if (!acc.isActivated()) {
                        allActivated = false;
                        break;
                    }
                }

                if (!allActivated) {
                    return ResponseEntity.ok(Map.of("valid", false,
                        "message", "You must activate all delivered accounts before filing a complaint. Please activate them first to review the product."));
                }
            }

            // Check if there's already an active complaint
            com.mmo.repository.ComplaintRepository complaintRepository =
                applicationContext.getBean(com.mmo.repository.ComplaintRepository.class);

            boolean hasActiveComplaint = complaintRepository.existsByTransactionIdAndStatus(
                order.getTransactionId(),
                com.mmo.entity.Complaint.ComplaintStatus.NEW
            ) || complaintRepository.existsByTransactionIdAndStatus(
                order.getTransactionId(),
                com.mmo.entity.Complaint.ComplaintStatus.IN_PROGRESS
            ) || complaintRepository.existsByTransactionIdAndStatus(
                order.getTransactionId(),
                com.mmo.entity.Complaint.ComplaintStatus.ESCALATED
            );

            if (hasActiveComplaint) {
                return ResponseEntity.ok(Map.of("valid", false, "message", "There is already an active complaint for this order"));
            }

            // Check complaint deadline based on category type
            String categoryType = "common";
            try {
                if (order.getProduct() != null && order.getProduct().getCategory() != null) {
                    String t = order.getProduct().getCategory().getType();
                    if (t != null && t.equalsIgnoreCase("Warning")) {
                        categoryType = "warning";
                    }
                }
            } catch (Exception ignored) { }

            java.util.Date now = new java.util.Date();
            java.util.Date orderDate = order.getCreatedAt();

            if ("warning".equals(categoryType)) {
                // Warning category: 3 days from order date
                long diffInMillis = now.getTime() - orderDate.getTime();
                long daysDiff = diffInMillis / (1000 * 60 * 60 * 24);

                if (daysDiff > 3) {
                    return ResponseEntity.ok(Map.of("valid", false,
                        "message", "Complaint deadline has passed. Warning category items can only be complained about within 3 days of purchase."));
                }
            }
            // For common category, we would check against product expiration date
            // For now, we'll allow complaints for common products (can be refined later)

            return ResponseEntity.ok(Map.of("valid", true, "message", "You can file a complaint"));

        } catch (Exception ex) {
            return ResponseEntity.status(500).body(Map.of("valid", false, "message", "Internal error: " + ex.getMessage()));
        }
    }

    // Show complaint form
    @GetMapping("/account/orders/{id}/complaint")
    public String showComplaintForm(@PathVariable("id") Long id, Model model, Authentication authentication) {
        String email = resolveEmail(authentication);
        if (email == null) {
            return "redirect:/authen/login";
        }
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return "redirect:/authen/login";
        }

        Orders order = ordersRepository.findById(id).orElse(null);
        if (order == null || !Objects.equals(order.getCustomerId(), userOpt.get().getId())) {
            return "redirect:/account/orders";
        }

        // Get product and category info
        String productName = (order.getProduct() != null) ? order.getProduct().getName() : ("Product #" + order.getProductId());
        String categoryType = "common";
        String categoryName = "Unknown";

        try {
            if (order.getProduct() != null && order.getProduct().getCategory() != null) {
                categoryName = order.getProduct().getCategory().getName();
                String t = order.getProduct().getCategory().getType();
                if (t != null && t.equalsIgnoreCase("Warning")) {
                    categoryType = "warning";
                }
            }
        } catch (Exception ignored) { }

        // Calculate deadline
        java.util.Date orderDate = order.getCreatedAt();
        java.util.Date deadlineDate;
        String deadlineText;
        boolean isExpired;

        if ("warning".equals(categoryType)) {
            // 3 days from order date
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTime(orderDate);
            cal.add(java.util.Calendar.DAY_OF_MONTH, 3);
            deadlineDate = cal.getTime();
            deadlineText = "3 days from order date";

            java.util.Date now = new java.util.Date();
            isExpired = now.after(deadlineDate);
        } else {
            // Common products - until product expiration (for now, just show a reasonable deadline)
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTime(orderDate);
            cal.add(java.util.Calendar.DAY_OF_MONTH, 30); // Default 30 days for common
            deadlineDate = cal.getTime();
            deadlineText = "Until product expiration (typically 30 days)";
            isExpired = new java.util.Date().after(deadlineDate);
        }

        model.addAttribute("order", order);
        model.addAttribute("productName", productName);
        model.addAttribute("categoryType", categoryType);
        model.addAttribute("categoryName", categoryName);
        model.addAttribute("deadlineDate", deadlineDate);
        model.addAttribute("deadlineText", deadlineText);
        model.addAttribute("isExpired", isExpired);

        return "customer/complaint-form";
    }

    // Submit complaint
    @PostMapping("/account/orders/{id}/complaint")
    public String submitComplaint(@PathVariable("id") Long id,
                                  @RequestParam("complaintType") String complaintType,
                                  @RequestParam("description") String description,
                                  @RequestParam("evidence") org.springframework.web.multipart.MultipartFile[] evidenceFiles,
                                  Authentication authentication,
                                  org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            String email = resolveEmail(authentication);
            if (email == null) {
                return "redirect:/authen/login";
            }
            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) {
                return "redirect:/authen/login";
            }
            User customer = userOpt.get();

            Orders order = ordersRepository.findById(id).orElse(null);
            if (order == null || !Objects.equals(order.getCustomerId(), customer.getId())) {
                redirectAttributes.addFlashAttribute("error", "Order not found");
                return "redirect:/account/orders";
            }

            // Validate order status
            if (order.getStatus() != Orders.QueueStatus.COMPLETED) {
                redirectAttributes.addFlashAttribute("error", "Only completed orders can be complained about");
                return "redirect:/account/orders/" + id;
            }

            if (order.getTransactionId() == null) {
                redirectAttributes.addFlashAttribute("error", "No transaction found for this order");
                return "redirect:/account/orders/" + id;
            }

            // Validate description
            if (description == null || description.trim().length() < 20) {
                redirectAttributes.addFlashAttribute("error", "Description must be at least 20 characters");
                return "redirect:/account/orders/" + id + "/complaint";
            }

            // Get category type
            String categoryType = "common";
            try {
                if (order.getProduct() != null && order.getProduct().getCategory() != null) {
                    String t = order.getProduct().getCategory().getType();
                    if (t != null && t.equalsIgnoreCase("Warning")) {
                        categoryType = "warning";
                    }
                }
            } catch (Exception ignored) { }

            // Validate evidence files
            if (evidenceFiles == null || evidenceFiles.length == 0 ||
                (evidenceFiles.length == 1 && evidenceFiles[0].isEmpty())) {
                redirectAttributes.addFlashAttribute("error", "Please upload at least one piece of evidence");
                return "redirect:/account/orders/" + id + "/complaint";
            }

            // For warning category, require at least one video
            if ("warning".equals(categoryType)) {
                boolean hasVideo = false;
                for (org.springframework.web.multipart.MultipartFile file : evidenceFiles) {
                    if (!file.isEmpty() && file.getContentType() != null &&
                        file.getContentType().startsWith("video/")) {
                        hasVideo = true;
                        break;
                    }
                }
                if (!hasVideo) {
                    redirectAttributes.addFlashAttribute("error",
                        "Warning category products require video evidence showing the complete activation process");
                    return "redirect:/account/orders/" + id + "/complaint";
                }
            }

            // Upload evidence files
            java.util.List<String> evidenceUrls = new java.util.ArrayList<>();
            String uploadDir = "uploads/complaints";
            java.io.File uploadDirFile = new java.io.File(uploadDir);
            if (!uploadDirFile.exists()) {
                boolean created = uploadDirFile.mkdirs();
                if (!created) {
                    redirectAttributes.addFlashAttribute("error", "Failed to create upload directory");
                    return "redirect:/account/orders/" + id + "/complaint";
                }
            }

            for (org.springframework.web.multipart.MultipartFile file : evidenceFiles) {
                if (file.isEmpty()) continue;

                // Validate file size (max 50MB)
                if (file.getSize() > 50 * 1024 * 1024) {
                    redirectAttributes.addFlashAttribute("error", "File size must be less than 50MB per file");
                    return "redirect:/account/orders/" + id + "/complaint";
                }

                String originalFilename = file.getOriginalFilename();
                String extension = "";
                if (originalFilename != null && originalFilename.contains(".")) {
                    extension = originalFilename.substring(originalFilename.lastIndexOf("."));
                }
                String uniqueFilename = java.util.UUID.randomUUID() + extension;
                java.nio.file.Path filePath = java.nio.file.Paths.get(uploadDir, uniqueFilename);

                java.nio.file.Files.write(filePath, file.getBytes());
                evidenceUrls.add("/" + uploadDir + "/" + uniqueFilename);
            }

            // Get seller from product
            User seller = null;
            try {
                if (order.getProduct() != null) {
                    seller = order.getProduct().getSeller();
                }
            } catch (Exception ignored) { }

            if (seller == null) {
                redirectAttributes.addFlashAttribute("error", "Cannot determine seller for this order");
                return "redirect:/account/orders/" + id + "/complaint";
            }

            // Create complaint
            com.mmo.repository.ComplaintRepository complaintRepository =
                applicationContext.getBean(com.mmo.repository.ComplaintRepository.class);

            com.mmo.entity.Complaint complaint = new com.mmo.entity.Complaint();
            complaint.setTransactionId(order.getTransactionId());
            complaint.setCustomer(customer);
            complaint.setSeller(seller);

            // Parse complaint type
            try {
                complaint.setComplaintType(com.mmo.entity.Complaint.ComplaintType.valueOf(complaintType));
            } catch (Exception e) {
                complaint.setComplaintType(com.mmo.entity.Complaint.ComplaintType.OTHER);
            }

            complaint.setDescription(description.trim());

            // Store evidence as JSON array
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            complaint.setEvidence(mapper.writeValueAsString(evidenceUrls));

            complaint.setStatus(com.mmo.entity.Complaint.ComplaintStatus.NEW);
            complaint.setCreatedBy(customer.getId());

            com.mmo.entity.Complaint savedComplaint = complaintRepository.save(complaint);

            // Send complaint info to chat
            com.mmo.service.ChatService chatService = applicationContext.getBean(com.mmo.service.ChatService.class);

            // Build complaint message for chat with clear line breaks
            StringBuilder chatMessage = new StringBuilder();
            chatMessage.append("🚨 NEW COMPLAINT FILED\n");
            chatMessage.append("━━━━━━━━━━━━━━━━━━━━\n\n");
            chatMessage.append("📦 Product: ").append(order.getProduct() != null ? order.getProduct().getName() : "Unknown").append("\n");
            chatMessage.append("🆔 Order ID: #").append(order.getId()).append("\n");
            chatMessage.append("📋 Complaint Type: ").append(formatComplaintType(complaintType)).append("\n");
            chatMessage.append("⚠️ Complaint ID: #").append(savedComplaint.getId()).append("\n");
            chatMessage.append("📅 Filed on: ").append(new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(new java.util.Date())).append("\n\n");
            chatMessage.append("━━━━━━━━━━━━━━━━━━━━\n");
            chatMessage.append("📝 DESCRIPTION:\n\n");
            chatMessage.append(description.trim()).append("\n\n");
            chatMessage.append("━━━━━━━━━━━━━━━━━━━━\n");
            chatMessage.append("📎 Evidence: ").append(evidenceUrls.size()).append(" file(s) attached below");

            // Send main complaint message to chat (from customer to seller)
            try {
                chatService.send(customer.getId(), seller.getId(), chatMessage.toString());

                // Send each evidence file separately so they display properly in chat
                for (int i = 0; i < evidenceUrls.size(); i++) {
                    String evidenceUrl = evidenceUrls.get(i);
                    java.io.File evidenceFile = new java.io.File(evidenceUrl.substring(1)); // Remove leading "/"

                    if (evidenceFile.exists()) {
                        String fileName = evidenceFile.getName();
                        long fileSize = evidenceFile.length();

                        // Determine file type
                        String fileType = "document";
                        String contentType = java.nio.file.Files.probeContentType(evidenceFile.toPath());
                        if (contentType != null) {
                            if (contentType.startsWith("image/")) {
                                fileType = "image";
                            } else if (contentType.startsWith("video/")) {
                                fileType = "video";
                            }
                        }

                        // Send file with caption indicating it's evidence
                        String evidenceMessage = "Evidence " + (i + 1) + "/" + evidenceUrls.size();
                        chatService.sendWithFile(
                            customer.getId(),
                            seller.getId(),
                            evidenceMessage,
                            evidenceUrl,
                            fileType,
                            fileName,
                            fileSize
                        );
                    }
                }
            } catch (Exception ex) {
                // Log but don't fail the complaint submission
                System.err.println("Failed to send complaint to chat: " + ex.getMessage());
                ex.printStackTrace();
            }

            // Send notifications to both customer and seller
            try {
                com.mmo.service.NotificationService notificationService =
                    applicationContext.getBean(com.mmo.service.NotificationService.class);

                // Notification for customer (confirmation)
                String customerNotifTitle = "Complaint Submitted Successfully";
                String customerNotifContent = "Your complaint #" + savedComplaint.getId() +
                    " for order #" + order.getId() + " has been submitted. The seller will be notified and should respond within 48 hours.";
                notificationService.createNotificationForUser(customer.getId(), customerNotifTitle, customerNotifContent);

                // Notification for seller (alert)
                String sellerNotifTitle = "⚠️ New Complaint Filed Against Your Order";
                String sellerNotifContent = "Customer " + (customer.getFullName() != null ? customer.getFullName() : customer.getEmail()) +
                    " has filed a complaint (#" + savedComplaint.getId() + ") regarding order #" + order.getId() +
                    ". Please review and respond within 48 hours.";
                notificationService.createNotificationForUser(seller.getId(), sellerNotifTitle, sellerNotifContent);

            } catch (Exception ex) {
                System.err.println("Failed to send notifications: " + ex.getMessage());
                ex.printStackTrace();
            }

            // Send email to seller (async)
            try {
                com.mmo.service.EmailService emailService =
                    applicationContext.getBean(com.mmo.service.EmailService.class);

                String sellerEmail = seller.getEmail();
                String sellerName = seller.getFullName() != null ? seller.getFullName() : "Seller";
                String customerName = customer.getFullName() != null ? customer.getFullName() : customer.getEmail();
                String productName = order.getProduct() != null ? order.getProduct().getName() : "Unknown Product";
                String filedDate = new java.text.SimpleDateFormat("MMM dd, yyyy 'at' HH:mm").format(new java.util.Date());

                String emailSubject = "⚠️ New Complaint Filed - Order #" + order.getId() + " - MMOMarket";
                String emailContent = com.mmo.util.EmailTemplate.complaintFiledEmail(
                    sellerName,
                    customerName,
                    productName,
                    order.getId().toString(),
                    formatComplaintType(complaintType),
                    savedComplaint.getId().toString(),
                    description.trim(),
                    filedDate
                );

                emailService.sendEmailAsync(sellerEmail, emailSubject, emailContent);

            } catch (Exception ex) {
                System.err.println("Failed to send email to seller: " + ex.getMessage());
                ex.printStackTrace();
            }

            // Redirect to chat with seller
            return "redirect:/chat?sellerId=" + seller.getId() + "&complaintSuccess=true";

        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Failed to submit complaint: " + ex.getMessage());
            return "redirect:/account/orders/" + id + "/complaint";
        }
    }

    // Helper method to format complaint type for display
    private String formatComplaintType(String type) {
        if (type == null) return "Other";
        switch (type) {
            case "ITEM_NOT_WORKING": return "Item Not Working";
            case "ITEM_NOT_AS_DESCRIBED": return "Item Not As Described";
            case "FRAUD_SUSPICION": return "Fraud Suspicion";
            case "OTHER": return "Other";
            default: return type;
        }
    }

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;
}


