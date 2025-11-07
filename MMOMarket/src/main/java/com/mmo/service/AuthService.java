package com.mmo.service;

import com.mmo.entity.User;
import com.mmo.repository.UserRepository;
import jakarta.mail.MessagingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Random;
import java.util.zip.CRC32;

@Service
public class AuthService {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public User findByEmail(String email) {
        return userRepository.findByEmailAndIsDelete(email, false);
    }

    public User findByDepositCode(String depositCode) {
        return userRepository.findByDepositCodeAndIsDelete(depositCode, false);
    }

    public Optional<User> findOptionalById(Long id) {
        return userRepository.findById(id).filter(u -> !u.isDelete());
    }

    public User findById(Long id) {
        return findOptionalById(id).orElse(null);
    }

    public User register(String email, String password, String fullName) throws MessagingException {
        User user = new User();
        user.setEmail(email);
        // Hash password using configured encoder
        user.setPassword(passwordEncoder.encode(password));
        user.setFullName(fullName); // Có thể null
        user.setRole("CUSTOMER");
        user.setVerified(false); // Đúng với cột isVerified (camelCase)
        user.setCoins(0L);
        return userRepository.save(user);
    }

    public User saveUser(User user) {
        return userRepository.save(user);
    }

    // Encode and update password safely
    public User updatePassword(User user, String rawPassword) {
        user.setPassword(passwordEncoder.encode(rawPassword));
        return userRepository.save(user);
    }

    public String generateVerificationCode() {
        Random random = new Random();
        return String.format("%06d", random.nextInt(1000000));
    }

    public void sendVerificationCodeEmail(String email, String code) throws MessagingException {
        // Delegate to async email service with retry; keep signature for controller compatibility
        emailService.sendVerificationCodeEmailAsync(email, code);
    }

    /**
     * Generate unique deposit code for user
     * Format: MMO + 2-5 alphanumeric (from base36 userId) + 6 digits (deterministic checksum)
     */
    public String generateDepositCode(User user) {
        long id = user.getId() != null ? user.getId() : new Random().nextLong();
        String mid = Long.toString(Math.abs(id), 36).toUpperCase();
        if (mid.length() < 2) {
            mid = (mid + "XX").substring(0, 2);
        }
        if (mid.length() > 5) {
            mid = mid.substring(mid.length() - 5);
        }
        // Deterministic 6-digit numeric suffix based on email+id
        String seed = (user.getEmail() != null ? user.getEmail() : "") + id;
        CRC32 crc = new CRC32();
        crc.update(seed.getBytes(StandardCharsets.UTF_8));
        long val = Math.abs(crc.getValue() % 1_000_000L);
        String suffix = String.format("%06d", val);
        return "MMO" + mid + suffix;
    }

    /**
     * Ensure user has a deposit code, generate and save if missing
     */
    public void ensureDepositCode(User user) {
        if (user.getDepositCode() == null || user.getDepositCode().isBlank()) {
            // Ensure we have an id before generating code for base36
            if (user.getId() == null) {
                user = userRepository.save(user);
            }
            String code = generateDepositCode(user);
            user.setDepositCode(code);
            userRepository.save(user);
        }
    }
}