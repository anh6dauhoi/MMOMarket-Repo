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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import com.mmo.entity.ProductVariant;
import com.mmo.entity.ProductVariantAccount;
import com.mmo.repository.ProductVariantRepository;
import com.mmo.repository.ProductVariantAccountRepository;
import com.mmo.repository.ReviewRepository; // NEW

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

    @Autowired
    private ReviewRepository reviewRepository; // NEW for review eligibility

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
        boolean userActive = !userOpt.get().isDelete(); // active flag

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

        // Precompute counts by product to avoid per-row queries
        Map<Long, Long> purchasesByProduct = new HashMap<>();
        Map<Long, Long> reviewsByProduct = new HashMap<>();

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

            boolean completed = (o.getStatus() == Orders.QueueStatus.COMPLETED);
            Date baseDate = o.getCreatedAt();
            boolean withinWindow = false;
            if (baseDate != null) {
                long days = java.time.temporal.ChronoUnit.DAYS.between(baseDate.toInstant(), java.time.Instant.now());
                withinWindow = days <= 30;
            }

            // counts per product
            purchasesByProduct.computeIfAbsent(o.getProductId(), pid ->
                    ordersRepository.countByCustomerIdAndProductIdAndStatus(customerId, pid, Orders.QueueStatus.COMPLETED));
            reviewsByProduct.computeIfAbsent(o.getProductId(), pid ->
                    reviewRepository.countByUser_IdAndProduct_Id(customerId, pid));

            long purchases = purchasesByProduct.getOrDefault(o.getProductId(), 0L);
            long reviewsCnt = reviewsByProduct.getOrDefault(o.getProductId(), 0L);

            boolean canReview = userActive && completed && withinWindow && (reviewsCnt < purchases);
            boolean hasAnyReview = reviewsCnt > 0;

            row.put("canReview", canReview);
            row.put("reviewed", hasAnyReview);
            row.put("feedbackWindowExpired", !withinWindow); // NEW: expose window status
            // Provide a view URL for convenience in the template
            row.put("reviewViewUrl", "/account/orders/" + o.getId() + "/review/view");

            items.add(row);
        }

        model.addAttribute("orders", items);
        model.addAttribute("currentPage", orderPage.getNumber());
        model.addAttribute("totalPages", orderPage.getTotalPages());
        model.addAttribute("pageSize", orderPage.getSize());
        model.addAttribute("totalElements", orderPage.getTotalElements());
        model.addAttribute("userActive", userActive); // pass active flag

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
}
