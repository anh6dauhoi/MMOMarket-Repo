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
import org.springframework.web.util.HtmlUtils;
import com.mmo.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.CompletableFuture;

@Controller
public class ContactController {

    @Autowired
    private EmailService emailService;
    @Value("${application.admin.email:qle9131@gmail.com}")
    private String adminEmail;

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

        // Build safe HTML email to admin
        String safeName = HtmlUtils.htmlEscape(form.getName());
        String safeEmail = HtmlUtils.htmlEscape(form.getEmail());
        String safeMsg = HtmlUtils.htmlEscape(form.getMessage()).replace("\n", "<br/>");
        // Updated, branded subject
        String subject = "MMOMarket — New Contact from " + safeName;
        String html =
                "<!doctype html>" +
                "<html><head>" +
                "<meta name=\"viewport\" content=\"width=device-width\" />" +
                "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />" +
                "<title>New Contact Inquiry • MMOMarket</title>" + // updated title
                "</head>" +
                "<body style=\"margin:0;padding:0;background:#f4f5f7;\">" +
                "  <table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f4f5f7;padding:24px 0;\">" +
                "    <tr><td align=\"center\">" +
                "      <table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#ffffff;border:1px solid #ececec;border-radius:12px;overflow:hidden;box-shadow:0 10px 28px rgba(245,62,50,0.08);\">" +
                "        <tr>" +
                "          <td style=\"padding:18px 24px;background:#F53E32;color:#ffffff;font-family:Inter,Arial,sans-serif;\">" +
                "            <div style=\"font-size:12px;opacity:.95;letter-spacing:.4px;text-transform:uppercase;\">MMOMarket</div>" +
                "            <h1 style=\"margin:4px 0 0 0;font-size:20px;font-weight:800;letter-spacing:-.2px;\">New Contact Inquiry</h1>" + // updated header
                "          </td>" +
                "        </tr>" +
                "        <tr>" +
                "          <td style=\"padding:22px 24px;font-family:Inter,Arial,sans-serif;color:#111;\">" +
                "            <p style=\"margin:0 0 16px 0;color:#374151;\">You have received a new message from the contact form.</p>" +
                "            <table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"border-collapse:separate;border-spacing:0 10px;margin:6px 0 8px 0;\">" +
                "              <tr>" +
                "                <td style=\"width:140px;color:#6b7280;font-weight:600;\">Name</td>" +
                "                <td style=\"color:#111;\">" + safeName + "</td>" +
                "              </tr>" +
                "              <tr>" +
                "                <td style=\"width:140px;color:#6b7280;font-weight:600;\">Email</td>" +
                "                <td><a href=\"mailto:" + safeEmail + "\" style=\"color:#F53E32;text-decoration:none;\">" + safeEmail + "</a></td>" +
                "              </tr>" +
                "            </table>" +
                "            <div style=\"margin-top:12px;padding:16px;border:1px solid #f1f1f1;border-radius:10px;background:#fff7f6;\">" +
                "              <div style=\"color:#6b7280;font-weight:600;margin-bottom:8px;\">Message</div>" +
                "              <div style=\"color:#111;line-height:1.6;\">" + safeMsg + "</div>" +
                "            </div>" +
                "            <div style=\"margin-top:18px;color:#6b7280;font-size:12px;\">Sent automatically from the Contact page.</div>" +
                "          </td>" +
                "        </tr>" +
                "      </table>" +
                "      <div style=\"font-family:Inter,Arial,sans-serif;color:#9ca3af;font-size:12px;margin-top:12px;\">© MMOMarket</div>" +
                "    </td></tr>" +
                "  </table>" +
                "</body></html>";

        try {
            if (adminEmail == null || adminEmail.isBlank()) {
                log.warn("Admin email is not configured. Skipping contact email send.");
            } else {
                // Use existing async service (non-blocking)
                emailService.sendEmailAsync(adminEmail, subject, html);
                log.info("Queued contact email to admin {} for {} <{}>", adminEmail, form.getName(), form.getEmail());
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
