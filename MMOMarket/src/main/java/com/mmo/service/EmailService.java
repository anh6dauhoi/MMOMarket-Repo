package com.mmo.service;

import com.mmo.util.EmailTemplate;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Value("${application.email:}")
    private String fromEmailConfigured;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${application.email.personalName:MMOMarket}")
    private String personalName;

    private static final int MAX_RETRIES = 3;
    private static final long BASE_DELAY_MS = 2000L;
    private static final long BACKOFF_MULTIPLIER = 2L;

    // Public API: generic async email sender with retry
    @Async
    public void sendEmailAsync(String to, String subject, String htmlContent) {
        if (to == null || to.isBlank()) {
            log.warn("Skip sending email: missing recipient");
            return;
        }
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                sendEmail(to, subject, htmlContent);
                log.info("Email sent successfully to {}", to);
                break;
            } catch (Exception e) {
                log.warn("Attempt {} to send email to {} failed: {}", attempt, to, e.getMessage());
                if (attempt == MAX_RETRIES) {
                    break;
                }
                try {
                    Thread.sleep(BASE_DELAY_MS * (long) Math.pow(BACKOFF_MULTIPLIER, attempt - 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.error("Email retry interrupted", ie);
                    break;
                }
            }
        }
    }

    // Convenience: send verification code email using existing EmailTemplate
    public void sendVerificationCodeEmailAsync(String to, String code) {
        String subject = "MMOMarket - Xác thực tài khoản & Đổi mật khẩu";
        String content = EmailTemplate.verificationEmail(code);
        sendEmailAsync(to, subject, content);
    }

    // Core send method
    private void sendEmail(String to, String subject, String htmlContent) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        String fromAddr = resolveFromAddress();
        try {
            helper.setFrom(fromAddr, personalName);
        } catch (Exception e) {
            // Fallback: set without personal name
            helper.setFrom(fromAddr);
        }
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlContent, true);
        mailSender.send(message);
    }

    private String resolveFromAddress() {
        if (fromEmailConfigured != null && !fromEmailConfigured.isBlank()) {
            return fromEmailConfigured.trim();
        }
        if (mailUsername != null && !mailUsername.isBlank()) {
            return mailUsername.trim();
        }
        return "no-reply@mmomarket.com";
    }
}

