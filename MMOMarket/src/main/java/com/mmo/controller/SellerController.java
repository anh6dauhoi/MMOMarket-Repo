package com.mmo.controller;

import com.mmo.entity.SellerRegistration;
import com.mmo.entity.User;
import com.mmo.entity.SellerBankInfo; // NEW: import SellerBankInfo
import com.mmo.repository.SellerRegistrationRepository;
import com.mmo.repository.UserRepository;
import com.mmo.service.SellerService;
import com.mmo.service.SellerBankInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.validation.Valid;
import java.util.Optional;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

// NEW: imports for withdrawal creation
import com.mmo.entity.Withdrawal;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;

@Controller
@RequestMapping("/seller")
public class SellerController {
    @Autowired
    private SellerService sellerService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SellerRegistrationRepository sellerRegistrationRepository;

    @Autowired
    private SellerBankInfoService sellerBankInfoService;

    @PersistenceContext
    private EntityManager entityManager;

    @GetMapping("/register")
    public String showRegistrationForm(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = null;
        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof UserDetails) {
                email = ((UserDetails) principal).getUsername();
            } else if (principal instanceof OidcUser) {
                email = ((OidcUser) principal).getEmail();
            } else if (principal instanceof OAuth2User) {
                Object mailAttr = ((OAuth2User) principal).getAttributes().get("email");
                if (mailAttr != null) email = mailAttr.toString();
            } else {
                email = authentication.getName();
            }
        }

        if (email == null) {
            return "redirect:/login";
        }

        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            // Handle the case where the user is not found, perhaps redirect to an error page or login
            return "redirect:/login";
        }

        Optional<SellerRegistration> sellerRegistration = sellerRegistrationRepository.findByUserId(user.getId());

        if (sellerRegistration.isPresent()) {
            SellerRegistration reg = sellerRegistration.get();
            String status = reg.getStatus();
            String upper = status != null ? status.trim().toUpperCase(java.util.Locale.ROOT) : "";
            boolean rejectedRegistration = upper.equals("REJECTED") || upper.equals("REJECTED_STAGE") || upper.equals("REJECTED_REGISTRATION");
            if (rejectedRegistration) {
                // Show the registration form pre-filled inside account setting layout
                if (!model.containsAttribute("sellerRegistration")) {
                    model.addAttribute("sellerRegistration", reg);
                }
                // DO NOT set 'registration' so account-setting shows the form fragment
                return "customer/account-setting";
            }
            // For other statuses (including REJECTED_CONTRACT), show status inside account setting layout
            model.addAttribute("registration", reg);
            return "customer/account-setting";
        }

        if (!model.containsAttribute("sellerRegistration")) {
            model.addAttribute("sellerRegistration", new SellerRegistration());
        }
        return "customer/account-setting";
    }

    @PostMapping("/register")
    public String registerSeller(@Valid @ModelAttribute("sellerRegistration") SellerRegistration sellerRegistration,
                                 BindingResult bindingResult,
                                 @RequestParam(value = "agree", required = false) Boolean agree,
                                 RedirectAttributes redirectAttributes) {
        if (agree == null || !agree) {
            redirectAttributes.addFlashAttribute("agreeError", "You must agree with term and policy to continue.");
        }
        if (bindingResult.hasErrors() || (agree == null || !agree)) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.sellerRegistration", bindingResult);
            redirectAttributes.addFlashAttribute("sellerRegistration", sellerRegistration);
            return "redirect:/seller/register";
        }
        sellerService.registerSeller(sellerRegistration);
        redirectAttributes.addFlashAttribute("successMessage", "Your registration has been submitted successfully and is pending review.");
        return "redirect:/seller/register";
    }

    @GetMapping("/contract")
    public ResponseEntity<Resource> downloadContract(@RequestParam(name = "signed", defaultValue = "false") boolean signed) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = (authentication != null) ? authentication.getName() : null;
        if (authentication != null && authentication.getPrincipal() instanceof OidcUser oidc) {
            email = oidc.getEmail();
        } else if (authentication != null && authentication.getPrincipal() instanceof OAuth2User ou) {
            Object mailAttr = ou.getAttributes().get("email");
            if (mailAttr != null) email = mailAttr.toString();
        }
        if (email == null) return ResponseEntity.status(401).build();
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) return ResponseEntity.status(401).build();
        Optional<SellerRegistration> regOpt = sellerRegistrationRepository.findByUserId(user.getId());
        if (regOpt.isEmpty()) return ResponseEntity.notFound().build();
        try {
            Resource res = sellerService.loadContract(regOpt.get().getId(), signed);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + res.getFilename() + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(res);
        } catch (Exception ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping(value = "/contract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String uploadSignedContract(@RequestParam("signed") MultipartFile file,
                                       RedirectAttributes redirectAttributes) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String email = (authentication != null) ? authentication.getName() : null;
            if (authentication != null && authentication.getPrincipal() instanceof OidcUser oidc) {
                email = oidc.getEmail();
            } else if (authentication != null && authentication.getPrincipal() instanceof OAuth2User ou) {
                Object mailAttr = ou.getAttributes().get("email");
                if (mailAttr != null) email = mailAttr.toString();
            }
            if (email == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "You must be logged in to upload a contract.");
                return "redirect:/seller/register";
            }
            User user = userRepository.findByEmail(email).orElse(null);
            if (user == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "User not found.");
                return "redirect:/seller/register";
            }
            Optional<SellerRegistration> regOpt = sellerRegistrationRepository.findByUserId(user.getId());
            if (regOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "No seller registration found.");
                return "redirect:/seller/register";
            }

            SellerRegistration reg = regOpt.get();
            String rawStatus = reg.getStatus();
            String statusNormalized = rawStatus == null ? "" :
                    rawStatus.trim()
                             .toUpperCase(Locale.ROOT)
                             .replace('-', '_')
                             .replace(' ', '_')
                             .replaceAll("[^A-Z_]", ""); // strip quotes/odd chars to match APPROVED_STAGE, etc.

            boolean isApprovedLike = statusNormalized.contains("APPROVED"); // matches APPROVED, APPROVED_STAGE, APPROVED_REGISTRATION
            boolean isRejectedLike = statusNormalized.contains("REJECTED"); // matches REJECTED_*
            boolean hasContract = reg.getContract() != null && !reg.getContract().isBlank();

            // Allow upload for Approved*, Rejected* (e.g., REJECTED_CONTRACT), or when contract is already provided
            boolean allowed = isApprovedLike || isRejectedLike || hasContract;
            if (!allowed) {
                redirectAttributes.addFlashAttribute("errorMessage", "You can upload a signed contract only when your registration is Approved or Rejected.");
                return "redirect:/seller/register";
            }

            if (file == null || file.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Please choose a file to upload.");
                return "redirect:/seller/register";
            }

            sellerService.submitSignedContract(file);
            redirectAttributes.addFlashAttribute("successMessage", "Signed contract uploaded successfully and sent for review.");
        } catch (IllegalStateException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Upload failed: " + e.getMessage());
        }
        // Redirect back to /seller/register which renders account-setting with status fragment
        return "redirect:/seller/register";
    }

    @GetMapping("/my-shop")
    public String showMyShop(Model model) {
        return "seller/my-shop";
    }

    @GetMapping("/withdraw-money")
    public String showWithdrawMoneyPage(Model model, RedirectAttributes redirectAttributes) {
        // Require logged-in and Active shop
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = null;
        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof UserDetails) {
                email = ((UserDetails) principal).getUsername();
            } else if (principal instanceof OidcUser) {
                email = ((OidcUser) principal).getEmail();
            } else if (principal instanceof OAuth2User) {
                Object mailAttr = ((OAuth2User) principal).getAttributes().get("email");
                if (mailAttr != null) email = mailAttr.toString();
            } else {
                email = authentication.getName();
            }
        }
        if (email == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please login to continue.");
            return "redirect:/login";
        }
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "User not found.");
            return "redirect:/login";
        }
        boolean activeShop = user.getShopStatus() != null && user.getShopStatus().equalsIgnoreCase("Active");
        if (!activeShop) {
            redirectAttributes.addFlashAttribute("errorMessage", "Your shop is not Active. Please complete registration.");
            return "redirect:/seller/register";
        }

        // Load coins
        Long coins = user.getCoins() == null ? 0L : user.getCoins();
        model.addAttribute("coins", coins);

        // Load existing bank info (robust to different mappings)
        SellerBankInfo bankInfo = findBankInfoForOwner(user);
        Map<String, Object> bank = new HashMap<>();
        if (bankInfo != null) {
            bank.put("id", tryGetId(bankInfo));
            bank.put("bankName", tryGetString(bankInfo, "getBankName"));
            bank.put("accountNumber", tryGetString(bankInfo, "getAccountNumber"));
            bank.put("accountHolder", firstNonBlank(
                    tryGetString(bankInfo, "getAccountHolder"),
                    tryGetString(bankInfo, "getAccountName"),
                    tryGetString(bankInfo, "getHolderName"),
                    tryGetString(bankInfo, "getBeneficiaryName"),
                    tryGetString(bankInfo, "getOwnerName")
            ));
            bank.put("branch", tryGetString(bankInfo, "getBranch"));
        }
        model.addAttribute("bank", bank);

        return "seller/withdraw-money";
    }

    // NEW: Save or update bank info for current Active seller
    @PostMapping("/bank-info")
    public String saveBankInfo(@RequestParam("bankName") String bankName,
                               @RequestParam("accountNumber") String accountNumber,
                               @RequestParam(value = "accountHolder", required = false) String accountHolder,
                               @RequestParam(value = "branch", required = false) String branch,
                               RedirectAttributes redirectAttributes) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String email = null;
            if (authentication != null && authentication.isAuthenticated()) {
                Object principal = authentication.getPrincipal();
                if (principal instanceof UserDetails) {
                    email = ((UserDetails) principal).getUsername();
                } else if (principal instanceof OidcUser) {
                    email = ((OidcUser) principal).getEmail();
                } else if (principal instanceof OAuth2User) {
                    Object mailAttr = ((OAuth2User) principal).getAttributes().get("email");
                    if (mailAttr != null) email = mailAttr.toString();
                } else {
                    email = authentication.getName();
                }
            }
            if (email == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Please login to continue.");
                return "redirect:/login";
            }
            User user = userRepository.findByEmail(email).orElse(null);
            if (user == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "User not found.");
                return "redirect:/login";
            }
            boolean activeShop = user.getShopStatus() != null && user.getShopStatus().equalsIgnoreCase("Active");
            if (!activeShop) {
                redirectAttributes.addFlashAttribute("errorMessage", "Your shop is not Active. Please complete registration.");
                return "redirect:/seller/register";
            }
            if (bankName == null || bankName.isBlank() || accountNumber == null || accountNumber.isBlank()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Bank name and account number are required.");
                return "redirect:/seller/withdraw-money";
            }
            sellerBankInfoService.saveOrUpdateBankInfo(user, bankName, accountNumber, accountHolder, branch);
            redirectAttributes.addFlashAttribute("successMessage", "Bank details saved successfully.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to save bank details: " + ex.getMessage());
        }
        return "redirect:/seller/withdraw-money";
    }

    // NEW: Seller creates a withdrawal request
    @PostMapping(path = "/withdrawals", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @Transactional
    public ResponseEntity<?> createWithdrawal(@RequestBody CreateWithdrawalRequest req,
                                              Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }
            String email = authentication.getName();
            if (authentication.getPrincipal() instanceof OidcUser oidc) email = oidc.getEmail();
            else if (authentication.getPrincipal() instanceof OAuth2User ou) {
                Object mailAttr = ou.getAttributes().get("email");
                if (mailAttr != null) email = mailAttr.toString();
            }
            User seller = userRepository.findByEmail(email).orElse(null);
            if (seller == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }
            boolean sellerRole = seller.getRole() != null && seller.getRole().equalsIgnoreCase("SELLER");
            boolean activeShop = seller.getShopStatus() != null && seller.getShopStatus().equalsIgnoreCase("Active");
            if (!sellerRole && !activeShop) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");
            }

            // Validate amount (BR-14)
            if (req == null || req.getAmount() == null || req.getAmount() <= 0) {
                return ResponseEntity.badRequest().body("MSG16: Minimum withdrawal amount is 50000.");
            }
            if (req.getAmount() < 50_000L) {
                return ResponseEntity.badRequest().body("MSG16: Minimum withdrawal amount is 50000.");
            }

            // Check balance (MSG07)
            Long currentCoins = seller.getCoins() == null ? 0L : seller.getCoins();
            if (currentCoins < req.getAmount()) {
                return ResponseEntity.badRequest().body("MSG07: Insufficient balance.");
            }

            // Validate bank info ownership (MSG17)
            if (req.getBankInfoId() == null) {
                return ResponseEntity.badRequest().body("MSG17: Bank information not found.");
            }
            SellerBankInfo bankInfo = entityManager.find(SellerBankInfo.class, req.getBankInfoId());
            if (bankInfo == null) {
                return ResponseEntity.badRequest().body("MSG17: Bank information not found.");
            }
            Long ownerId = tryResolveOwnerId(bankInfo);
            if (ownerId == null || !ownerId.equals(seller.getId())) {
                return ResponseEntity.badRequest().body("MSG17: Bank information does not belong to the seller.");
            }

            // Create withdrawal
            Withdrawal wd = new Withdrawal();
            wd.setSeller(seller);
            wd.setBankInfo(bankInfo);
            wd.setAmount(req.getAmount());
            wd.setStatus("Pending");
            tryCopyBankDisplayFields(bankInfo, wd);
            wd.setCreatedAt(new java.util.Date());
            wd.setUpdatedAt(new java.util.Date());
            wd.setCreatedBy(seller.getId());
            entityManager.persist(wd);

            // Deduct coins immediately (hold)
            seller.setCoins(currentCoins - req.getAmount());
            entityManager.merge(seller);

            return ResponseEntity.status(HttpStatus.CREATED).body(WithdrawalResponse.from(wd));
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Internal error: " + ex.getMessage());
        }
    }

    // NEW: request/response DTOs (local to avoid new files)
    public static class CreateWithdrawalRequest {
        private Long amount;
        private Long bankInfoId;
        public Long getAmount() { return amount; }
        public void setAmount(Long amount) { this.amount = amount; }
        public Long getBankInfoId() { return bankInfoId; }
        public void setBankInfoId(Long bankInfoId) { this.bankInfoId = bankInfoId; }
    }
    public record WithdrawalResponse(Long id, Long amount, String status, Long bankInfoId) {
        public static WithdrawalResponse from(Withdrawal w) {
            return new WithdrawalResponse(
                    w.getId(),
                    w.getAmount(),
                    w.getStatus(),
                    w.getBankInfo() != null ? w.getBankInfo().getId() : null
            );
        }
    }

    // Helpers to tolerate different SellerBankInfo mappings without adding new dependencies
    private Long tryResolveOwnerId(SellerBankInfo bankInfo) {
        try {
            Object owner = bankInfo.getClass().getMethod("getSeller").invoke(bankInfo);
            if (owner != null) {
                Object id = owner.getClass().getMethod("getId").invoke(owner);
                return id instanceof Long ? (Long) id : null;
            }
        } catch (Exception ignored) { }
        try {
            Object owner = bankInfo.getClass().getMethod("getUser").invoke(bankInfo);
            if (owner != null) {
                Object id = owner.getClass().getMethod("getId").invoke(owner);
                return id instanceof Long ? (Long) id : null;
            }
        } catch (Exception ignored) { }
        try {
            Object id = bankInfo.getClass().getMethod("getUserId").invoke(bankInfo);
            return id instanceof Long ? (Long) id : null;
        } catch (Exception ignored) { }
        return null;
    }

    private void tryCopyBankDisplayFields(SellerBankInfo bankInfo, Withdrawal wd) {
        try {
            Object bankName = bankInfo.getClass().getMethod("getBankName").invoke(bankInfo);
            if (bankName instanceof String s) wd.setBankName(s);
        } catch (Exception ignored) { }
        try {
            Object acc = bankInfo.getClass().getMethod("getAccountNumber").invoke(bankInfo);
            if (acc instanceof String s) wd.setAccountNumber(s);
        } catch (Exception ignored) { }
        try {
            Object br = bankInfo.getClass().getMethod("getBranch").invoke(bankInfo);
            if (br instanceof String s) wd.setBranch(s);
        } catch (Exception ignored) { }
    }

    // Helper: find SellerBankInfo by owner using tolerant queries
    private SellerBankInfo findBankInfoForOwner(User owner) {
        Long uid = owner.getId();
        String[] possibleFields = {"seller.id", "user.id", "userId"};

        for (String field : possibleFields) {
            try {
                String hql = "select b from SellerBankInfo b where b." + field + " = :uid";
                return entityManager.createQuery(hql, SellerBankInfo.class)
                        .setParameter("uid", uid)
                        .setMaxResults(1)
                        .getSingleResult();
            } catch (Exception ignored) {
                // This query failed, try the next one
            }
        }
        return null;
    }

    // Helper: set owner relation via reflection
    private void setOwner(SellerBankInfo bankInfo, User owner) {
        try {
            bankInfo.getClass().getMethod("setSeller", User.class).invoke(bankInfo, owner);
            return;
        } catch (Exception ignored) {}
        try {
            bankInfo.getClass().getMethod("setUser", User.class).invoke(bankInfo, owner);
            return;
        } catch (Exception ignored) {}
        try {
            bankInfo.getClass().getMethod("setUserId", Long.class).invoke(bankInfo, owner.getId());
        } catch (Exception ignored) {}
    }

    private Long tryGetId(SellerBankInfo bankInfo) {
        try {
            Object id = bankInfo.getClass().getMethod("getId").invoke(bankInfo);
            if (id instanceof Long l) return l;
        } catch (Exception ignored) {}
        return null;
    }

    private boolean trySetString(Object target, String setterName, String value) {
        try {
            target.getClass().getMethod(setterName, String.class).invoke(target, value);
            return true;
        } catch (Exception ignored) {}
        return false;
    }

    private String tryGetString(Object target, String getterName) {
        try {
            Object v = target.getClass().getMethod(getterName).invoke(target);
            return v != null ? v.toString() : null;
        } catch (Exception ignored) {}
        return null;
    }

    private String firstNonBlank(String... vals) {
        if (vals == null) return null;
        for (String s : vals) {
            if (s != null && !s.isBlank()) return s;
        }
        return null;
    }
}
