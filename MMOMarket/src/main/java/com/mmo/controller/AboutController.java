package com.mmo.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

@Controller
public class AboutController {

    @Autowired
    private EmailService emailService;

    @Value("${application.admin.email:qle9131@gmail.com}")
    private String adminEmail;

    private static final Logger log = LoggerFactory.getLogger(AboutController.class);

    @GetMapping("/about")
    public String about(Model model) {
        if (!model.containsAttribute("aboutForm")) {
            model.addAttribute("aboutForm", new AboutForm());
        }
        return "customer/aboutus";
    }

    @PostMapping("/about")
    public String submitAbout(@Valid @ModelAttribute("aboutForm") AboutForm form,
                              BindingResult bindingResult,
                              RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "customer/aboutus";
        }

        String safeName = HtmlUtils.htmlEscape(form.getName());
        String safeEmail = HtmlUtils.htmlEscape(form.getEmail());
        String safeMsg = HtmlUtils.htmlEscape(form.getMessage()).replace("\n", "<br/>");

        String subject = "MMOMarket — New About Inquiry from " + safeName;
        String html =
                "<!doctype html>" +
                "<html><head>" +
                "<meta name=\"viewport\" content=\"width=device-width\" />" +
                "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />" +
                "<title>New About Inquiry • MMOMarket</title>" +
                "</head>" +
                "<body style=\"margin:0;padding:0;background:#f4f5f7;\">" +
                "  <table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f4f5f7;padding:24px 0;\">" +
                "    <tr><td align=\"center\">" +
                "      <table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#ffffff;border:1px solid #ececec;border-radius:12px;overflow:hidden;box-shadow:0 10px 28px rgba(245,62,50,0.08);\">" +
                "        <tr>" +
                "          <td style=\"padding:18px 24px;background:#F53E32;color:#ffffff;font-family:Inter,Arial,sans-serif;\">" +
                "            <div style=\"font-size:12px;opacity:.95;letter-spacing:.4px;text-transform:uppercase;\">MMOMarket</div>" +
                "            <h1 style=\"margin:4px 0 0 0;font-size:20px;font-weight:800;letter-spacing:-.2px;\">New About Inquiry</h1>" +
                "          </td>" +
                "        </tr>" +
                "        <tr>" +
                "          <td style=\"padding:22px 24px;font-family:Inter,Arial,sans-serif;color:#111;\">" +
                "            <p style=\"margin:0 0 16px 0;color:#374151;\">You have received a new message from the About page.</p>" +
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
                "            <div style=\"margin-top:18px;color:#6b7280;font-size:12px;\">Sent automatically from the About page.</div>" +
                "          </td>" +
                "        </tr>" +
                "      </table>" +
                "      <div style=\"font-family:Inter,Arial,sans-serif;color:#9ca3af;font-size:12px;margin-top:12px;\">© MMOMarket</div>" +
                "    </td></tr>" +
                "  </table>" +
                "</body></html>";

        try {
            if (adminEmail == null || adminEmail.isBlank()) {
                log.warn("Admin email is not configured. Skipping about email send.");
            } else {
                emailService.sendEmailAsync(adminEmail, subject, html);
                log.info("Queued about email to admin {} for {} <{}>", adminEmail, form.getName(), form.getEmail());
            }
        } catch (Exception ex) {
            log.error("Error queueing about email for {} <{}>: {}", form.getName(), form.getEmail(), ex.toString());
        }

        redirectAttributes.addFlashAttribute("successMessage",
                "Thank you, " + form.getName() + ". Your message has been sent to our admin.");
        return "redirect:/about";
    }

    public static class AboutForm {
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
