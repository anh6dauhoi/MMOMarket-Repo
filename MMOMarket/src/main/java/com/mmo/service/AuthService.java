package com.mmo.service;

import com.mmo.entity.User;
import com.mmo.repository.UserRepository;
import jakarta.mail.MessagingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
public class AuthService {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    public User findByEmail(String email) {
        // Đảm bảo ánh xạ đúng với cột isDelete (camelCase) trong DB
        return userRepository.findByEmailAndIsDelete(email, false);
    }

    public User findByDepositCode(String depositCode) {
        return userRepository.findByDepositCodeAndIsDelete(depositCode, false);
    }

    public User register(String email, String password, String fullName) throws MessagingException {
        User user = new User();
        user.setEmail(email);
        user.setPassword(password); // Lưu plaintext
        user.setFullName(fullName); // Có thể null
        user.setRole("CUSTOMER");
        user.setVerified(false); // Đúng với cột isVerified (camelCase)
        user.setCoins(0L);
        return userRepository.save(user);
    }

    public User saveUser(User user) {
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
}