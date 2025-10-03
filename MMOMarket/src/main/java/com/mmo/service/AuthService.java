package com.mmo.service;

import com.mmo.entity.User;
import com.mmo.repository.UserRepository;
import com.mmo.util.EmailTemplate;
import jakarta.mail.MessagingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import jakarta.mail.internet.MimeMessage;

import java.io.UnsupportedEncodingException;
import java.util.Random;

@Service
public class AuthService {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JavaMailSender mailSender;

    public User findByEmail(String email) {
        // Đảm bảo ánh xạ đúng với cột isDelete (camelCase) trong DB
        return userRepository.findByEmailAndIsDelete(email, false);
    }

    public User register(String email, String password, String fullName) throws MessagingException {
        User user = new User();
        user.setEmail(email);
        user.setPassword(password); // Lưu plaintext
        user.setFullName(fullName); // Có thể null
        user.setRole("ROLE_CUSTOMER");
        user.setVerified(false); // Đúng với cột isVerified (camelCase)
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
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        try {
            helper.setFrom("anh.tuan662005@gmail.com", "MMOMarket");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        helper.setTo(email);
        helper.setSubject("MMOMarket - Xác thực tài khoản & Đổi mật khẩu");
        helper.setText(EmailTemplate.verificationEmail(code), true);
        mailSender.send(message);
    }
}