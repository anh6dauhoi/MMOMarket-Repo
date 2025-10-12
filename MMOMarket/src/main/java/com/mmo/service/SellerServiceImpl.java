package com.mmo.service;

import com.mmo.constant.ShopStatus;
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

    private final Path storageRoot = Paths.get("uploads", "contracts");

    private static final List<String> BANNED_WORDS = Arrays.asList("spam", "fake", "illegal");

    @Override
    public void registerSeller(SellerRegistration sellerRegistration) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return;
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
            return;
        }

        userRepository.findByEmail(email).ifPresent(user -> {
            Optional<SellerRegistration> existingOpt = sellerRegistrationRepository.findByUserId(user.getId());
            if (existingOpt.isPresent()) {
                SellerRegistration existing = existingOpt.get();
                if ("Rejected".equalsIgnoreCase(existing.getStatus())) {
                    existing.setShopName(sellerRegistration.getShopName());
                    existing.setDescription(sellerRegistration.getDescription());
                    existing.setStatus("Pending");
                    existing.setReason(null);
                    existing.setUpdatedAt(new Date());
                    sellerRegistrationRepository.save(existing);
                }
                // If existing is not Rejected, do nothing to avoid duplicates
            } else {
                sellerRegistration.setUser(user);
                sellerRegistration.setStatus("Pending");
                sellerRegistration.setCreatedAt(new Date());
                sellerRegistration.setUpdatedAt(new Date());
                sellerRegistrationRepository.save(sellerRegistration);
            }
        });
    }

    @Override
    public Page<SellerRegistration> findAllRegistrations(String status, Pageable pageable) {
        if ("All".equalsIgnoreCase(status)) {
            return sellerRegistrationRepository.findAll(pageable);
        }
        return sellerRegistrationRepository.findByStatus(status, pageable);
    }

    @Override
    public Optional<SellerRegistration> findById(Long id) {
        return sellerRegistrationRepository.findById(id);
    }

    @Override
    public SellerRegistration approve(Long id, MultipartFile contractFile) throws IOException {
        SellerRegistration reg = sellerRegistrationRepository.findById(id).orElseThrow();
        // Validate shop name against duplicates and banned words
        if (reg.getShopName() != null && sellerRegistrationRepository.existsByShopNameIgnoreCaseAndStatusIn(
                reg.getShopName(), Arrays.asList("Approved", "Completed"))) {
            throw new IllegalArgumentException("Shop name already exists in approved/completed registrations.");
        }
        if (containsBannedWord(reg.getShopName()) || containsBannedWord(reg.getDescription())) {
            throw new IllegalArgumentException("Shop name/description contains prohibited words.");
        }

        reg.setStatus("Approved");
        if (contractFile != null && !contractFile.isEmpty()) {
            ensureStorage();
            String filename = "contract-" + id + "-" + System.currentTimeMillis() + "-" + sanitize(contractFile.getOriginalFilename());
            Path target = storageRoot.resolve(filename);
            Files.copy(contractFile.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            reg.setContract(filename);
        }
        reg.setUpdatedAt(new Date());
        SellerRegistration saved = sellerRegistrationRepository.save(reg);

        notificationService.createNotificationForUser(
                saved.getUser(),
                "Seller registration approved",
                "Your seller registration has been approved. Please proceed to the contract step."
        );
        return saved;
    }

    @Override
    public SellerRegistration reject(Long id, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Reject reason is required.");
        }
        SellerRegistration reg = sellerRegistrationRepository.findById(id).orElseThrow();
        reg.setStatus("Rejected");
        reg.setReason(reason);
        reg.setUpdatedAt(new Date());
        SellerRegistration saved = sellerRegistrationRepository.save(reg);
        notificationService.createNotificationForUser(
                saved.getUser(),
                "Seller registration rejected",
                "Your seller registration was rejected. Reason: " + reason
        );
        return saved;
    }

    @Override
    public void activate(Long id) {
        SellerRegistration reg = sellerRegistrationRepository.findById(id).orElseThrow();
        if (!"Approved".equalsIgnoreCase(reg.getStatus())) {
            throw new IllegalStateException("Registration must be Approved before activation.");
        }
        if (reg.getSignedContract() == null || reg.getSignedContract().isEmpty()) {
            throw new IllegalStateException("Signed contract is required before activation.");
        }
        User user = reg.getUser();
        user.setShopStatus(ShopStatus.Active);
        userRepository.save(user);
        reg.setStatus("Completed");
        reg.setUpdatedAt(new Date());
        sellerRegistrationRepository.save(reg);
        notificationService.createNotificationForUser(
                user,
                "Shop activated",
                "Congratulations! Your shop is now active."
        );
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
        if (!"Approved".equalsIgnoreCase(reg.getStatus()) && !"Rejected".equalsIgnoreCase(reg.getStatus())) {
            throw new IllegalStateException("You can upload a signed contract only when your registration is Approved or Rejected.");
        }
        ensureStorage();
        String filename = "signed-" + reg.getId() + "-" + System.currentTimeMillis() + "-" + sanitize(file.getOriginalFilename());
        Path target = storageRoot.resolve(filename);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        reg.setSignedContract(filename);
        reg.setUpdatedAt(new Date());
        sellerRegistrationRepository.save(reg);
        notificationService.createNotificationForUser(
                user,
                "Signed contract submitted",
                "We received your signed contract. Our admins will review it shortly."
        );
    }

    private boolean containsBannedWord(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        for (String w : BANNED_WORDS) {
            if (lower.contains(w)) return true;
        }
        return false;
    }

    private void ensureStorage() throws IOException {
        if (!Files.exists(storageRoot)) {
            Files.createDirectories(storageRoot);
        }
    }

    private String sanitize(String name) {
        return name == null ? "file" : name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
