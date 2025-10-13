package com.mmo.controller;

import com.mmo.entity.SellerRegistration;
import com.mmo.entity.User;
import com.mmo.repository.SellerRegistrationRepository;
import com.mmo.repository.UserRepository;
import com.mmo.service.SellerService;
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

import jakarta.validation.Valid;
import java.util.Optional;
import java.util.Locale;

@Controller
@RequestMapping("/seller")
public class SellerController {
    @Autowired
    private SellerService sellerService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SellerRegistrationRepository sellerRegistrationRepository;

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
    public String myShop(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = null;
        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof UserDetails ud) {
                email = ud.getUsername();
            } else if (principal instanceof OidcUser oidc) {
                email = oidc.getEmail();
            } else if (principal instanceof OAuth2User ou) {
                Object mailAttr = ou.getAttributes().get("email");
                if (mailAttr != null) email = mailAttr.toString();
            } else {
                email = authentication.getName();
            }
        }
        if (email == null) {
            return "redirect:/login"; // keep consistent with existing redirects in this controller
        }
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return "redirect:/login";
        }
        String status = user.getShopStatus();
        if (status != null && status.equalsIgnoreCase("Active")) {
            model.addAttribute("sellerUser", user);
            return "seller/my-shop";
        }
        return "redirect:/seller/register";
    }
}
