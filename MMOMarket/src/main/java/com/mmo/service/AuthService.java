package com.mmo.service;

import com.mmo.entity.User;
import com.mmo.repository.UserRepository;
import jakarta.mail.MessagingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    public User findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public User findByEmailAndPassword(String email, String password) {
        User user = userRepository.findByEmail(email);
        if (user != null) {
            // Kiểm tra mật khẩu mặc định 'sa123' trước
            if ("sa123".equals(password) || passwordEncoder.matches(password, user.getPassword())) {
                if (user.isVerified()) {
                    return user;
                }
            }
        }
        return null;
    }

    public User register(String email, String password, String fullName) throws MessagingException {
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole("{\"role\": \"Customer\"}");
        user.setVerified(false);
        return userRepository.save(user);
    }

    public String generateVerificationCode() {
        Random random = new Random();
        return String.format("%06d", random.nextInt(1000000));
    }
}