package com.mmo.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.mmo.service.EmailService;
import com.mmo.util.EmailTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.CompletableFuture;
import com.mmo.service.SystemConfigurationService;
import static com.mmo.constant.SystemConfigKeys.SYSTEM_EMAIL_CONTACT;

@Controller
public class ContactController {

    @Autowired
    private EmailService emailService;
    @Value("${application.admin.email:qle9131@gmail.com}")
    private String adminEmail;

    @Autowired(required = false)
    private SystemConfigurationService systemConfigurationService;

    private static final Logger log = LoggerFactory.getLogger(ContactController.class);

    @GetMapping("/contact")
    public String contactPage(Model model) {
        if (!model.containsAttribute("contactForm")) {
            model.addAttribute("contactForm", new ContactForm());
        }
        return "customer/contact";
    }

    @PostMapping("/contact")
    public String submitContact(@Valid @ModelAttribute("contactForm") ContactForm form,
                                BindingResult bindingResult,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            // return same view with validation errors
            return "customer/contact";
        }

        // Build email using template
        String subject = "MMOMarket — New Contact from " + form.getName();
        String html = EmailTemplate.contactFormEmail(form.getName(), form.getEmail(), form.getMessage());

        try {
            String recipient = null;
            try {
                if (systemConfigurationService != null) {
                    String cfg = systemConfigurationService.getStringValue(SYSTEM_EMAIL_CONTACT, null);
                    if (cfg != null && !cfg.isBlank()) recipient = cfg.trim();
                }
            } catch (Exception ignored) {}
            if (recipient == null || recipient.isBlank()) {
                recipient = adminEmail; // fallback to legacy property
            }
            if (recipient == null || recipient.isBlank()) {
                log.warn("Admin/Contact email is not configured. Skipping contact email send.");
            } else {
                emailService.sendEmailAsync(recipient, subject, html);
                log.info("Queued contact email to {} for {} <{}>", recipient, form.getName(), form.getEmail());
            }
        } catch (Exception ex) {
            // Never block or fail the request due to email issues
            log.error("Error queueing contact email for {} <{}>: {}", form.getName(), form.getEmail(), ex.toString());
        }

        // Flash success and redirect
        redirectAttributes.addFlashAttribute("successMessage",
                "Thank you, " + form.getName() + ". Your message has been sent to our admin.");
        return "redirect:/contact";
    }

    public static class ContactForm {
        @NotBlank(message = "Please enter your name")
        private String name;

        @NotBlank(message = "Please enter your email")
        @Email(message = "Please enter a valid email")
        private String email;

        @NotBlank(message = "Please enter a message")
        @Size(min = 1, max = 2000, message = "Message should be between 1 and 2000 characters")
        private String message;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}
