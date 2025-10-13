package com.mmo.service;

import com.mmo.entity.SellerRegistration;
import com.mmo.entity.User;
import com.mmo.repository.SellerRegistrationRepository;
import com.mmo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

@Service
public class SellerServiceImpl implements SellerService {

    @Autowired
    private SellerRegistrationRepository sellerRegistrationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private EmailService emailService;

    private final Path storageRoot = Paths.get("uploads", "contracts");

    private static final List<String> BANNED_WORDS = Arrays.asList("spam", "fake", "illegal");

    @Override
    public void registerSeller(SellerRegistration sellerRegistration) {
        // Resolve current user robustly. Fallback to sellerRegistration.getUser() if provided.
        User currentUser = resolveCurrentUserOrThrow(sellerRegistration);

        // Basic input validation
        String shopName = sellerRegistration.getShopName();
        if (shopName == null || shopName.trim().length() < 2) {
            throw new IllegalArgumentException("Shop name must be at least 2 characters.");
        }
        if (containsBannedWord(shopName) || containsBANNED_WORDS_IN_DESC(sellerRegistration.getDescription())) {
            throw new IllegalArgumentException("Shop name/description contains prohibited words.");
        }
        // Prevent duplicate approved/active names
        if (sellerRegistrationRepository.existsByShopNameIgnoreCaseAndStatusIn(
                shopName.trim(), Arrays.asList("Approved", "Active", "ACTIVE", "APPROVED_REGISTRATION"))) {
            throw new IllegalArgumentException("Shop name already exists in approved/active registrations.");
        }

        Optional<SellerRegistration> existingOpt = sellerRegistrationRepository.findByUserId(currentUser.getId());
        if (existingOpt.isPresent()) {
            SellerRegistration existing = existingOpt.get();
            String exStatus = existing.getStatus() != null ? existing.getStatus() : "";
            String exUpper = exStatus.trim().toUpperCase(Locale.ROOT);
            if (exUpper.contains("REJECTED_REGISTRATION") || exUpper.equals("REJECTED") || exUpper.equals("REJECTED_STAGE")) {
                existing.setShopName(shopName.trim());
                existing.setDescription(sellerRegistration.getDescription());
                existing.setStatus("Pending");
                existing.setReason(null);
                existing.setUpdatedAt(new Date());
                sellerRegistrationRepository.save(existing);

                // Notify + email: resubmitted after rejection
                notificationService.createNotificationForUser(
                        currentUser,
                        "Seller registration submitted",
                        "We received your updated seller registration. Status: Pending."
                );
                emailService.sendEmailAsync(currentUser.getEmail(),
                        "We received your seller registration",
                        buildEmail("Seller Registration Submitted",
                                "Hello " + displayName(currentUser) + ",",
                                "We received your request to become a seller.",
                                "Current status: Pending.",
                                "We'll notify you once it is reviewed."));
            } else {
                // Already submitted or in progress
                throw new IllegalStateException("You already have a registration in progress.");
            }
        } else {
            SellerRegistration toSave = new SellerRegistration();
            toSave.setUser(currentUser);
            toSave.setShopName(shopName.trim());
            toSave.setDescription(sellerRegistration.getDescription());
            toSave.setStatus("Pending");
            toSave.setCreatedAt(new Date());
            toSave.setUpdatedAt(new Date());
            sellerRegistrationRepository.save(toSave);

            // Notify + email: first submission
            notificationService.createNotificationForUser(
                    currentUser,
                    "Seller registration submitted",
                    "We received your seller registration. Status: Pending."
            );
            emailService.sendEmailAsync(currentUser.getEmail(),
                    "We received your seller registration",
                    buildEmail("Seller Registration Submitted",
                            "Hello " + displayName(currentUser) + ",",
                            "We received your request to become a seller.",
                            "Current status: Pending.",
                            "We'll notify you once it is reviewed."));
        }
    }

    @Override
    public Page<SellerRegistration> findAllRegistrations(String status, Pageable pageable) {
        if ("All".equalsIgnoreCase(status)) {
            return sellerRegistrationRepository.findAll(pageable);
        }
        // Map UI filters to multiple internal/existing values
        List<String> statuses;
        switch (status.toUpperCase()) {
            case "PENDING":
                statuses = Arrays.asList("Pending", "PENDING", "PENDING_CONTRACT_REVIEW");
                break;
            case "APPROVED":
                statuses = Arrays.asList("Approved", "APPROVED", "APPROVED_STAGE", "APPROVED_REGISTRATION");
                break;
            case "REJECTED":
                statuses = Arrays.asList("Rejected", "REJECTED", "REJECTED_STAGE", "REJECTED_REGISTRATION", "REJECTED_CONTRACT");
                break;
            default:
                statuses = Collections.singletonList(status);
        }
        return sellerRegistrationRepository.findByStatusIn(statuses, pageable);
    }

    @Override
    public Optional<SellerRegistration> findById(Long id) {
        return sellerRegistrationRepository.findById(id);
    }

    @Override
    @Transactional
    public SellerRegistration approve(Long id, MultipartFile contractFile) throws IOException {
        SellerRegistration reg = sellerRegistrationRepository.findById(id).orElseThrow();

        // Validate
        if (reg.getShopName() != null && sellerRegistrationRepository.existsByShopNameIgnoreCaseAndStatusIn(
                reg.getShopName(), Arrays.asList("Approved", "APPROVED", "APPROVED_STAGE", "APPROVED_REGISTRATION", "Active", "ACTIVE"))) {
            throw new IllegalArgumentException("Shop name already exists in approved/active registrations.");
        }
        if (containsBannedWord(reg.getShopName()) || containsBANNED_WORDS_IN_DESC(reg.getDescription())) {
            throw new IllegalArgumentException("Shop name/description contains prohibited words.");
        }

        // Store uploaded contract if provided
        if (contractFile != null && !contractFile.isEmpty()) {
            ensureStorage();
            String filename = "contract-" + id + "-" + System.currentTimeMillis() + "-" + sanitize(contractFile.getOriginalFilename());
            Path target = storageRoot.resolve(filename);
            Files.copy(contractFile.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            reg.setContract(filename);
        }

        // Set stage-specific approved status (registration approved)
        reg.setStatus("APPROVED_REGISTRATION");
        reg.setUpdatedAt(new Date());
        SellerRegistration saved = sellerRegistrationRepository.save(reg);

        // Notification: plain text (no JSON)
        notificationService.createNotificationForUser(
                saved.getUser(),
                "Seller registration approved",
                "Your seller registration has been approved. Please proceed to the contract step."
        );
        // Email
        emailService.sendEmailAsync(saved.getUser().getEmail(),
                "Your seller registration was approved",
                buildEmail("Registration Approved",
                        "Hello " + displayName(saved.getUser()) + ",",
                        "Your seller registration has been approved.",
                        "Please proceed to the contract step to continue."));

        return saved;
    }

    @Override
    @Transactional
    public SellerRegistration reject(Long id, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Reject reason is required.");
        }
        SellerRegistration reg = sellerRegistrationRepository.findById(id).orElseThrow();

        String curr = reg.getStatus() != null ? reg.getStatus().trim().toUpperCase(Locale.ROOT) : "";
        boolean hasSigned = reg.getSignedContract() != null && !reg.getSignedContract().isBlank();
        boolean inContractPhase = curr.startsWith("APPROVED") || curr.equals("PENDING_CONTRACT_REVIEW") || hasSigned;
        if (inContractPhase) {
            reg.setStatus("REJECTED_CONTRACT");
        } else {
            reg.setStatus("REJECTED_REGISTRATION");
        }
        reg.setReason(reason);
        reg.setUpdatedAt(new Date());
        SellerRegistration saved = sellerRegistrationRepository.save(reg);

        // Notification
        notificationService.createNotificationForUser(
                saved.getUser(),
                "Seller registration rejected",
                ("Your seller request was rejected. Reason: " + reason)
        );
        // Email
        emailService.sendEmailAsync(saved.getUser().getEmail(),
                "Your seller registration was rejected",
                buildEmail("Registration Rejected",
                        "Hello " + displayName(saved.getUser()) + ",",
                        "Unfortunately, your seller registration was rejected.",
                        "Reason: " + reason,
                        "You may update your information and try again."));

        return saved;
    }

    @Override
    public void activate(Long id) {
        SellerRegistration reg = sellerRegistrationRepository.findById(id).orElseThrow();
        String curr = reg.getStatus() != null ? reg.getStatus().trim().toUpperCase(Locale.ROOT) : "";
        boolean approvedPhase = curr.startsWith("APPROVED") || curr.equals("PENDING_CONTRACT_REVIEW") || curr.equals("APPROVED_STAGE") || curr.equals("APPROVED");
        if (!approvedPhase) {
            throw new IllegalStateException("Registration must be Approved before activation.");
        }
        if (reg.getSignedContract() == null || reg.getSignedContract().isEmpty()) {
            throw new IllegalStateException("Signed contract is required before activation.");
        }
        User user = reg.getUser();
        user.setShopStatus("Active"); // only set here
        userRepository.save(user);

        reg.setStatus("Active"); // final status
        reg.setUpdatedAt(new Date());
        sellerRegistrationRepository.save(reg);

        // Notification: plain text
        notificationService.createNotificationForUser(
                user,
                "Shop activated",
                "Congratulations! Your shop is now active."
        );
        // Email (optional but useful)
        emailService.sendEmailAsync(user.getEmail(),
                "Your shop is now active",
                buildEmail("Shop Activated",
                        "Hello " + displayName(user) + ",",
                        "Congratulations! Your shop is now active.",
                        "You can start listing products and selling."));
    }

    @Override
    public Resource loadContract(Long id, boolean signed) throws IOException {
        SellerRegistration reg = sellerRegistrationRepository.findById(id).orElseThrow();
        String file = signed ? reg.getSignedContract() : reg.getContract();
        if (file == null || file.isEmpty()) {
            throw new IOException("File not found");
        }
        Path path = storageRoot.resolve(file);
        try {
            Resource resource = new UrlResource(path.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            }
        } catch (MalformedURLException e) {
            throw new IOException("Invalid file path", e);
        }
        throw new IOException("File not readable");
    }

    @Override
    public void submitSignedContract(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Signed contract file is required.");
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("Not authenticated");
        }
        String email = null;
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
        if (email == null) {
            throw new IllegalStateException("Cannot resolve current user");
        }
        User user = userRepository.findByEmail(email).orElseThrow();
        SellerRegistration reg = sellerRegistrationRepository.findByUserId(user.getId()).orElseThrow();
        // Allow only APPROVED* or REJECTED_CONTRACT, or when admin has provided a contract
        String rawStatus = reg.getStatus();
        String statusNormalized = rawStatus == null ? "" :
                rawStatus.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        boolean hasContract = reg.getContract() != null && !reg.getContract().isBlank();
        boolean allowed = statusNormalized.startsWith("APPROVED") || statusNormalized.equals("REJECTED_CONTRACT") || hasContract;
        if (!allowed) {
            throw new IllegalStateException("You can upload a signed contract only when your registration is Approved or Rejected.");
        }
        ensureStorage();
        String filename = "signed-" + reg.getId() + "-" + System.currentTimeMillis() + "-" + sanitize(file.getOriginalFilename());
        Path target = storageRoot.resolve(filename);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        reg.setSignedContract(filename);
        // Move to contract review pending status for clarity
        reg.setStatus("PENDING_CONTRACT_REVIEW");
        reg.setUpdatedAt(new Date());
        sellerRegistrationRepository.save(reg);
        notificationService.createNotificationForUser(
                user,
                "Signed contract submitted",
                "We received your signed contract. Our admins will review it shortly."
        );
        // Email
        emailService.sendEmailAsync(user.getEmail(),
                "We received your signed contract",
                buildEmail("Signed Contract Submitted",
                        "Hello " + displayName(user) + ",",
                        "We received your signed contract.",
                        "Our admins will review it shortly."));
    }

    @Override
    public SellerRegistration resubmit(String shopName, String description) {
        User currentUser = resolveCurrentUserOrThrow(null);

        if (shopName == null || shopName.trim().length() < 2) {
            throw new IllegalArgumentException("Shop name must be at least 2 characters.");
        }
        if (containsBannedWord(shopName) || containsBannedWord(description)) {
            throw new IllegalArgumentException("Shop name/description contains prohibited words.");
        }
        // Prevent duplicate approved/active names
        if (sellerRegistrationRepository.existsByShopNameIgnoreCaseAndStatusIn(
                shopName.trim(), Arrays.asList("Approved", "APPROVED", "APPROVED_STAGE", "APPROVED_REGISTRATION", "Active", "ACTIVE"))) {
            throw new IllegalArgumentException("Shop name already exists in approved/active registrations.");
        }

        SellerRegistration reg = sellerRegistrationRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new IllegalStateException("No registration to resubmit."));
        String curr = reg.getStatus() != null ? reg.getStatus().trim().toUpperCase(Locale.ROOT) : "";
        if (!(curr.contains("REJECTED_REGISTRATION") || curr.equals("REJECTED") || curr.equals("REJECTED_STAGE"))) {
            throw new IllegalStateException("Only registration-rejected applications can be resubmitted.");
        }

        reg.setShopName(shopName.trim());
        reg.setDescription(description);
        reg.setStatus("Pending");
        reg.setReason(null);
        reg.setUpdatedAt(new Date());
        SellerRegistration saved = sellerRegistrationRepository.save(reg);

        // Notify + email
        notificationService.createNotificationForUser(
                currentUser,
                "Seller registration resubmitted",
                "We received your updated seller registration. Status: Pending."
        );
        emailService.sendEmailAsync(currentUser.getEmail(),
                "We received your seller registration",
                buildEmail("Seller Registration Resubmitted",
                        "Hello " + displayName(currentUser) + ",",
                        "We received your updated request to become a seller.",
                        "Current status: Pending.",
                        "We'll notify you once it is reviewed."));

        return saved;
    }

    private User resolveCurrentUserOrThrow(SellerRegistration sellerRegistration) {
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
        if (email != null) {
            final String finalEmail = email; // Create a final variable for the lambda
            return userRepository.findByEmail(finalEmail).orElseThrow(() -> new IllegalStateException("User not found: " + finalEmail));
        }
        // Fallback: use user provided from form binding if present
        if (sellerRegistration != null && sellerRegistration.getUser() != null && sellerRegistration.getUser().getId() != null) {
            return userRepository.findById(sellerRegistration.getUser().getId())
                    .orElseThrow(() -> new IllegalStateException("User not found by ID."));
        }
        throw new IllegalStateException("Not authenticated");
    }

    private boolean containsBANNED_WORDS_IN_DESC(String text) {
        return containsBannedWord(text);
    }

    private boolean containsBannedWord(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        // Use BANNED_WORDS directly in the lambda (it's static final)
        return BANNED_WORDS.stream().anyMatch(w -> lower.contains(w));
    }

    private void ensureStorage() throws IOException {
        if (!Files.exists(storageRoot)) {
            Files.createDirectories(storageRoot);
        }
    }

    private String sanitize(String name) {
        return name == null ? "file" : name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    // ---------- Email helpers (minimal, image-free, elegant) ----------

    private String displayName(User user) {
        String name = (user != null ? user.getFullName() : null);
        return (name == null || name.isBlank()) ? (user != null ? user.getEmail() : "there") : name;
    }

    private String buildEmail(String title, String... lines) {
        StringBuilder body = new StringBuilder();
        for (String l : lines) {
            if (l == null) continue;
            body.append("<p style=\"margin:8px 0;color:#374151;line-height:1.6\">")
                .append(escapeHtml(l).replace("\n", "<br/>"))
                .append("</p>");
        }
        return ""
            + "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head>"
            + "<body style=\"background:#f5f7fb;margin:0;padding:24px;font-family:Segoe UI,Roboto,Helvetica,Arial,sans-serif\">"
            + "  <div style=\"max-width:640px;margin:0 auto;background:#ffffff;border-radius:12px;"
            + "              box-shadow:0 6px 24px rgba(16,24,40,0.08);padding:28px 28px 24px\">"
            + "    <div style=\"border-bottom:1px solid #eef2f7;padding-bottom:12px;margin-bottom:16px\">"
            + "      <div style=\"font-size:14px;color:#6b7280;letter-spacing:.08em;text-transform:uppercase\">MMOMarket</div>"
            + "    </div>"
            + "    <h1 style=\"font-size:20px;margin:0 0 8px;color:#111827\">" + escapeHtml(title) + "</h1>"
            +        body.toString()
            + "    <hr style=\"border:none;border-top:1px solid #eef2f7;margin:20px 0\"/>"
            + "    <p style=\"font-size:12px;color:#9ca3af;margin:0\">This is an automated message from MMOMarket. Please do not reply.</p>"
            + "  </div>"
            + "  <div style=\"max-width:640px;margin:12px auto 0;text-align:center;color:#9ca3af;font-size:12px\">"
            + "    © " + Calendar.getInstance().get(Calendar.YEAR) + " MMOMarket. All rights reserved."
            + "  </div>"
            + "</body></html>";
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"","&quot;")
                .replace("'", "&#39;");
    }
}
