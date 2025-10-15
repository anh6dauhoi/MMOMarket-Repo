package com.mmo.controller;

import com.mmo.dto.SellerRegistrationDTO;
import com.mmo.entity.SellerRegistration;
import com.mmo.entity.User;
import com.mmo.service.SellerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

// NEW: imports for withdrawal processing
import com.mmo.entity.Withdrawal;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;

@Controller
@RequestMapping("/admin") // was {"/admin", "/api/v1/admin"}
public class AdminController {

    @Autowired
    private SellerService sellerService;

    // NEW: direct JPA access without creating new repositories
    @PersistenceContext
    private EntityManager entityManager;

    @GetMapping("/seller-registrations")
    public String sellerRegistrations(@RequestParam(name = "status", defaultValue = "All") String status,
                                      @RequestParam(name = "page", defaultValue = "0") int page,
                                      Model model) {
        Pageable pageable = PageRequest.of(page, 10, Sort.by("createdAt").descending());
        Page<SellerRegistration> registrationPage = sellerService.findAllRegistrations(status, pageable);

        model.addAttribute("registrations", registrationPage.getContent());
        model.addAttribute("currentStatus", status);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", registrationPage.getTotalPages());
        model.addAttribute("pageTitle", "Seller Registrations"); // for dynamic header title
        model.addAttribute("body", "admin/seller-registrations"); // was "admin/seller-registrations :: content"
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
    public String withdrawManagement(Model model) {
        // Fetch real withdrawal data from the database
        List<Withdrawal> withdrawals = entityManager.createQuery("SELECT w FROM Withdrawal w ORDER BY w.createdAt DESC", Withdrawal.class)
                .getResultList();

        model.addAttribute("withdrawals", withdrawals);
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

    // NEW: Admin processes a withdrawal (Approved/Rejected)
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

            if (approve) {
                if (req.getProofFile() == null || req.getProofFile().isBlank()) {
                    return ResponseEntity.badRequest().body("Proof file is required for Approved.");
                }
                wd.setStatus("Completed");
                wd.setProofFile(req.getProofFile()); // image/file path evidence (BR-16)
                wd.setUpdatedAt(new java.util.Date());
                entityManager.merge(wd);
                return ResponseEntity.ok(WithdrawalResponse.from(wd));
            } else {
                if (req.getReason() == null || req.getReason().isBlank()) {
                    return ResponseEntity.badRequest().body("Reason is required for Rejected.");
                }
                // Refund coins (return held amount back to seller) (BR-15)
                User seller = wd.getSeller();
                Long coins = seller.getCoins() == null ? 0L : seller.getCoins();
                Long amount = wd.getAmount() == null ? 0L : wd.getAmount();
                seller.setCoins(coins + amount);
                entityManager.merge(seller);

                wd.setStatus("Rejected");
                wd.setProofFile(req.getReason()); // store rejection reason in proofFile as requested
                wd.setUpdatedAt(new java.util.Date());
                entityManager.merge(wd);
                return ResponseEntity.ok(WithdrawalResponse.from(wd));
            }
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }

    // NEW: minimal DTOs for request/response (kept local to avoid new files)
    public static class ProcessWithdrawalRequest {
        private String status;     // "Approved" or "Rejected"
        private String proofFile;  // required if Approved
        private String reason;     // required if Rejected
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getProofFile() { return proofFile; }
        public void setProofFile(String proofFile) { this.proofFile = proofFile; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
    public record WithdrawalResponse(Long id, Long sellerId, Long amount, String status, String proofFile) {
        public static WithdrawalResponse from(Withdrawal w) {
            return new WithdrawalResponse(
                    w.getId(),
                    w.getSeller() != null ? w.getSeller().getId() : null,
                    w.getAmount(),
                    w.getStatus(),
                    w.getProofFile()
            );
        }
    }
}
