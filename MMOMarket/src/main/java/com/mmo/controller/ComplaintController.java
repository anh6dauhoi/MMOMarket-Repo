package com.mmo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmo.entity.Complaint;
import com.mmo.entity.User;
import com.mmo.repository.ComplaintRepository;
import com.mmo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.*;
import java.util.stream.Collectors;

@Controller
public class ComplaintController {

    @Autowired
    private ComplaintRepository complaintRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.mmo.repository.OrdersRepository ordersRepository;

    @Autowired
    private com.mmo.repository.TransactionRepository transactionRepository;

    @Autowired
    private com.mmo.service.ComplaintService complaintService;

    private String resolveEmail(Authentication authentication) {
        if (authentication == null) return null;
        Object principal = authentication.getPrincipal();
        if (principal instanceof OAuth2User) {
            return ((OAuth2User) principal).getAttribute("email");
        }
        return authentication.getName();
    }

    @GetMapping("/account/complaints")
    public String myComplaints(Model model,
                               Authentication authentication,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "10") int size,
                               @RequestParam(required = false) String search,
                               @RequestParam(required = false) String status,
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
        User customer = userOpt.get();

        // Get all complaints for this customer - convert to ArrayList to make it mutable
        List<Complaint> allComplaints = new ArrayList<>(complaintRepository.findByCustomer(customer));

        // Filter by status if provided
        if (status != null && !status.trim().isEmpty()) {
            try {
                Complaint.ComplaintStatus statusEnum = Complaint.ComplaintStatus.valueOf(status.trim());
                allComplaints = allComplaints.stream()
                    .filter(c -> c.getStatus() == statusEnum)
                    .collect(Collectors.toCollection(ArrayList::new));
            } catch (IllegalArgumentException e) {
                // Invalid status, ignore filter
            }
        }

        // Apply sorting if specified
        if (sortBy != null && !sortBy.isEmpty()) {
            Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
            String fieldName = sortBy.equals("status") ? "status" : "createdAt";
            boolean ascending = direction == Sort.Direction.ASC;

            allComplaints.sort((c1, c2) -> {
                int result = 0;
                if (fieldName.equals("status")) {
                    result = c1.getStatus().compareTo(c2.getStatus());
                } else {
                    result = c1.getCreatedAt().compareTo(c2.getCreatedAt());
                }
                return ascending ? result : -result;
            });
        } else {
            // Default: sort by createdAt DESC
            allComplaints.sort((c1, c2) -> c2.getCreatedAt().compareTo(c1.getCreatedAt()));
        }

        // Filter by search if provided
        List<Complaint> filteredComplaints = allComplaints;
        if (search != null && !search.trim().isEmpty()) {
            String searchLower = search.trim().toLowerCase();
            filteredComplaints = allComplaints.stream()
                .filter(c -> {
                    // Search in complaint type
                    if (c.getComplaintType() != null &&
                        c.getComplaintType().name().toLowerCase().contains(searchLower)) {
                        return true;
                    }
                    // Search in description
                    if (c.getDescription() != null &&
                        c.getDescription().toLowerCase().contains(searchLower)) {
                        return true;
                    }
                    // Search in status
                    return c.getStatus() != null &&
                        c.getStatus().name().toLowerCase().contains(searchLower);
                })
                .collect(Collectors.toCollection(ArrayList::new));
        }

        // Manual pagination
        int start = page * size;
        int end = Math.min(start + size, filteredComplaints.size());
        List<Complaint> pageContent = filteredComplaints.subList(start, end);
        int totalPages = (int) Math.ceil((double) filteredComplaints.size() / size);

        // Map to lightweight view models
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < pageContent.size(); i++) {
            Complaint c = pageContent.get(i);
            Map<String, Object> row = new HashMap<>();
            row.put("index", start + i + 1);
            row.put("id", c.getId());

            // Get product name from transaction
            String productName = "Unknown Product";
            if (c.getTransactionId() != null) {
                transactionRepository.findById(c.getTransactionId()).ifPresent(tx -> {
                    if (tx.getProduct() != null) {
                        row.put("productName", tx.getProduct().getName());
                    }
                });
            }
            if (!row.containsKey("productName")) {
                row.put("productName", productName);
            }

            row.put("status", c.getStatus() != null ? formatStatus(c.getStatus()) : "Unknown");
            row.put("statusRaw", c.getStatus() != null ? c.getStatus().name() : "");
            row.put("createdAt", c.getCreatedAt());
            row.put("updatedAt", c.getUpdatedAt());
            items.add(row);
        }

        model.addAttribute("complaints", items);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageSize", size);
        model.addAttribute("totalElements", filteredComplaints.size());

        return "customer/my-complaints";
    }

    @GetMapping("/account/complaints/{id}")
    public String complaintDetail(@PathVariable("id") Long id, Model model, Authentication authentication) {
        String email = resolveEmail(authentication);
        if (email == null) {
            return "redirect:/authen/login";
        }
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return "redirect:/authen/login";
        }
        User customer = userOpt.get();

        Optional<Complaint> complaintOpt = complaintRepository.findById(id);
        if (complaintOpt.isEmpty() || !Objects.equals(complaintOpt.get().getCustomer().getId(), customer.getId())) {
            return "redirect:/account/complaints";
        }

        Complaint complaint = complaintOpt.get();

        // Parse evidence JSON
        List<String> evidenceList = new ArrayList<>();
        if (complaint.getEvidence() != null && !complaint.getEvidence().trim().isEmpty()) {
            try {
                evidenceList = objectMapper.readValue(complaint.getEvidence(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            } catch (Exception e) {
                // If parsing fails, try to treat as a single URL
                evidenceList.add(complaint.getEvidence());
            }
        }

        // Get transaction and order information with safe null handling
        com.mmo.entity.Transaction transaction = null;
        com.mmo.entity.Orders order = null;
        String productName = "N/A";

        if (complaint.getTransactionId() != null) {
            Optional<com.mmo.entity.Transaction> txOpt = transactionRepository.findById(complaint.getTransactionId());
            if (txOpt.isPresent()) {
                transaction = txOpt.get();
                // Safely get product name
                try {
                    if (transaction.getProduct() != null) {
                        productName = transaction.getProduct().getName();
                    }
                } catch (Exception e) {
                    // Handle lazy loading exception
                    productName = "Product #" + transaction.getProduct().getId();
                }

                // Find order by transaction id
                List<com.mmo.entity.Orders> orders = ordersRepository.findByTransactionId(complaint.getTransactionId());
                if (!orders.isEmpty()) {
                    order = orders.get(0);
                }
            }
        }

        model.addAttribute("complaint", complaint);
        model.addAttribute("evidenceList", evidenceList);
        model.addAttribute("complaintTypeFormatted", formatComplaintType(complaint.getComplaintType()));
        model.addAttribute("statusFormatted", formatStatus(complaint.getStatus()));
        model.addAttribute("statusRaw", complaint.getStatus() != null ? complaint.getStatus().name() : "NEW");
        model.addAttribute("transaction", transaction);
        model.addAttribute("order", order);
        model.addAttribute("productName", productName);

        // Add all complaint fields to avoid lazy loading
        model.addAttribute("complaintId", complaint.getId());
        model.addAttribute("complaintCreatedAt", complaint.getCreatedAt());
        model.addAttribute("complaintUpdatedAt", complaint.getUpdatedAt());
        model.addAttribute("complaintRespondedAt", complaint.getRespondedAt());
        model.addAttribute("complaintTransactionId", complaint.getTransactionId());
        model.addAttribute("complaintDescription", complaint.getDescription());
        model.addAttribute("sellerResponse", complaint.getSellerFinalResponse());
        model.addAttribute("escalationReason", complaint.getEscalationReason());
        model.addAttribute("adminDecision", complaint.getAdminDecisionNotes());

        // Safely get seller and admin handler names
        String sellerName = "N/A";
        String adminHandlerName = null;
        Long sellerId = null;

        try {
            if (complaint.getSeller() != null) {
                User seller = complaint.getSeller();
                sellerId = seller.getId();
                // Use fullName if available, otherwise email
                sellerName = (seller.getFullName() != null && !seller.getFullName().isEmpty())
                    ? seller.getFullName()
                    : seller.getEmail();
            }
        } catch (Exception e) {
            if (complaint.getSeller() != null) {
                sellerId = complaint.getSeller().getId();
                sellerName = "Seller #" + sellerId;
            }
        }

        try {
            if (complaint.getAdminHandler() != null) {
                User admin = complaint.getAdminHandler();
                // Use fullName if available, otherwise email
                adminHandlerName = (admin.getFullName() != null && !admin.getFullName().isEmpty())
                    ? admin.getFullName()
                    : admin.getEmail();
            }
        } catch (Exception e) {
            adminHandlerName = "Admin";
        }

        model.addAttribute("sellerName", sellerName);
        model.addAttribute("sellerId", sellerId);
        model.addAttribute("adminHandlerName", adminHandlerName);

        // Determine available actions based on status
        boolean canChat = true; // Always allow chat
        boolean canRequestAdmin = complaint.getStatus() == Complaint.ComplaintStatus.IN_PROGRESS
                                || complaint.getStatus() == Complaint.ComplaintStatus.PENDING_CONFIRMATION;
        boolean canCancel = complaint.getStatus() == Complaint.ComplaintStatus.NEW;

        model.addAttribute("canChat", canChat);
        model.addAttribute("canRequestAdmin", canRequestAdmin);
        model.addAttribute("canCancel", canCancel);

        return "customer/complaint-detail";
    }

    /**
     * Customer cancels complaint (only NEW status)
     */
    @org.springframework.web.bind.annotation.PostMapping("/account/complaints/{id}/cancel")
    @org.springframework.web.bind.annotation.ResponseBody
    public org.springframework.http.ResponseEntity<?> cancelComplaint(
            @org.springframework.web.bind.annotation.PathVariable("id") Long id,
            Authentication authentication) {
        try {
            String email = resolveEmail(authentication);
            if (email == null) {
                return org.springframework.http.ResponseEntity.status(401).body(java.util.Map.of("error", "Unauthorized"));
            }

            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) {
                return org.springframework.http.ResponseEntity.status(401).body(java.util.Map.of("error", "Unauthorized"));
            }

            User customer = userOpt.get();
            complaintService.cancelComplaint(id, customer.getId());

            return org.springframework.http.ResponseEntity.ok(java.util.Map.of(
                "success", true,
                "message", "Complaint cancelled successfully"
            ));
        } catch (Exception e) {
            return org.springframework.http.ResponseEntity.badRequest().body(java.util.Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Customer requests admin support (escalate)
     */
    @org.springframework.web.bind.annotation.PostMapping("/account/complaints/{id}/escalate")
    @org.springframework.web.bind.annotation.ResponseBody
    public org.springframework.http.ResponseEntity<?> escalateComplaint(
            @org.springframework.web.bind.annotation.PathVariable("id") Long id,
            @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, String> request,
            Authentication authentication) {
        try {
            String email = resolveEmail(authentication);
            if (email == null) {
                return org.springframework.http.ResponseEntity.status(401).body(java.util.Map.of("error", "Unauthorized"));
            }

            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) {
                return org.springframework.http.ResponseEntity.status(401).body(java.util.Map.of("error", "Unauthorized"));
            }

            User customer = userOpt.get();
            String reason = request.get("reason");

            if (reason == null || reason.trim().isEmpty()) {
                return org.springframework.http.ResponseEntity.badRequest().body(java.util.Map.of(
                    "success", false,
                    "error", "Reason is required"
                ));
            }

            complaintService.escalateToAdmin(id, customer.getId(), reason, false);

            return org.springframework.http.ResponseEntity.ok(java.util.Map.of(
                "success", true,
                "message", "Complaint escalated to admin successfully. Admin team will review and respond within 3-5 business days."
            ));
        } catch (Exception e) {
            return org.springframework.http.ResponseEntity.badRequest().body(java.util.Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Customer confirms resolution (accepts seller's solution)
     */
    @org.springframework.web.bind.annotation.PostMapping("/account/complaints/{id}/confirm")
    @org.springframework.web.bind.annotation.ResponseBody
    public org.springframework.http.ResponseEntity<?> confirmResolution(
            @org.springframework.web.bind.annotation.PathVariable("id") Long id,
            @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Boolean> request,
            Authentication authentication) {
        try {
            String email = resolveEmail(authentication);
            if (email == null) {
                return org.springframework.http.ResponseEntity.status(401).body(java.util.Map.of("error", "Unauthorized"));
            }

            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) {
                return org.springframework.http.ResponseEntity.status(401).body(java.util.Map.of("error", "Unauthorized"));
            }

            User customer = userOpt.get();
            Boolean accept = request.get("accept");

            if (accept == null) {
                return org.springframework.http.ResponseEntity.badRequest().body(java.util.Map.of(
                    "success", false,
                    "error", "Accept parameter is required"
                ));
            }

            complaintService.confirmResolution(id, customer.getId(), accept);

            return org.springframework.http.ResponseEntity.ok(java.util.Map.of(
                "success", true,
                "message", accept ? "Solution accepted. Complaint marked as resolved." : "Please use 'Request Admin Support' to reject the solution"
            ));
        } catch (Exception e) {
            return org.springframework.http.ResponseEntity.badRequest().body(java.util.Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    private String formatComplaintType(Complaint.ComplaintType type) {
        if (type == null) return "Unknown";
        return switch (type) {
            case ITEM_NOT_WORKING -> "Item Not Working";
            case ITEM_NOT_AS_DESCRIBED -> "Item Not As Described";
            case FRAUD_SUSPICION -> "Fraud Suspicion";
            case OTHER -> "Other";
        };
    }

    private String formatStatus(Complaint.ComplaintStatus status) {
        if (status == null) return "Unknown";
        return switch (status) {
            case NEW -> "New";
            case IN_PROGRESS -> "In Progress";
            case PENDING_CONFIRMATION -> "Pending Confirmation";
            case ESCALATED -> "Escalated";
            case RESOLVED -> "Resolved";
            case CLOSED_BY_ADMIN -> "Closed by Admin";
            case CANCELLED -> "Cancelled";
        };
    }
}

