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
import java.util.Optional;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private SellerService sellerService;

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

    @GetMapping("/seller-registrations/{id}/contract")
    public ResponseEntity<Resource> downloadContract(@PathVariable Long id,
                                                     @RequestParam(name = "signed", defaultValue = "false") boolean signed) throws Exception {
        Resource resource = sellerService.loadContract(id, signed);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}
