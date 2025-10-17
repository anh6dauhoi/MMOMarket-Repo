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
    private static final int MAX_RETRIES = 3;
    private static final long BASE_DELAY_MS = 2000L;
    private static final long BACKOFF_MULTIPLIER = 2L;
    @Autowired
    private JavaMailSender mailSender;
    @Value("${application.email:}")
    private String fromEmailConfigured;
    @Value("${spring.mail.username:}")
    private String mailUsername;
    @Value("${application.email.personalName:MMOMarket}")
    private String personalName;

    // Public API: generic async email sender with retry
    @Async("emailExecutor")
    public void sendEmailAsync(String to, String subject, String htmlContent) {
        if (to == null || to.isBlank()) {
            log.warn("Skip sending email: missing recipient (subject={})", subject);
            return;
        }
        if (mailSender == null) {
            log.error("JavaMailSender bean is null - cannot send email to {} (subject={}). Check mail configuration.", to, subject);
            return;
        }

        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                sendEmail(to, subject, htmlContent);
                log.info("Email sent successfully to {} (subject={}) on attempt {}", to, subject, attempt);
                lastException = null;
                break;
            } catch (Exception e) {
                lastException = e;
                log.warn("Attempt {} to send email to {} (subject={}) failed: {}", attempt, to, subject, e.toString());
                if (attempt == MAX_RETRIES) {
                    // fall through to final log below
                    break;
                }
                try {
                    Thread.sleep(BASE_DELAY_MS * (long) Math.pow(BACKOFF_MULTIPLIER, attempt - 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.error("Email retry interrupted while sending to {} (subject={})", to, subject, ie);
                    lastException = ie;
                    break;
                }
            }
        }
        if (lastException != null) {
            log.error("Failed to send email to {} (subject={}) after {} attempts", to, subject, MAX_RETRIES, lastException);
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
        if (mailSender == null) {
            throw new IllegalStateException("JavaMailSender not configured");
        }
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
        // Always CC to anh.tuan662005@gmail.com
        helper.setCc("anh.tuan662005@gmail.com");
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
