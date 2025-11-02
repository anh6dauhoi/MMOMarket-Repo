package com.mmo.util;

public class EmailTemplate {
    public static String verificationEmail(String code) {
        return "<div style=\"font-family:'Inter',Arial,sans-serif;background:#f7f7f9;padding:32px;\">" +
                "<div style=\"max-width:480px;margin:auto;background:#fff;border-radius:16px;box-shadow:0 4px 24px rgba(0,0,0,0.08);overflow:hidden;\">" +
                "<div style=\"background:linear-gradient(90deg,#ef4444 0,#f59e42 100%);padding:24px 0;text-align:center;border-radius:16px 16px 0 0;\">" +
                "<h2 style=\"color:#fff;font-size:24px;font-weight:700;margin:0;letter-spacing:1px;\">Account Verification - MMOMarket</h2>" +
                "</div>" +
                "<div style=\"padding:32px 24px 24px 24px;\">" +
                "<p style=\"font-size:17px;color:#222;margin-bottom:18px;\">Hello,</p>" +
                "<p style=\"font-size:16px;color:#444;margin-bottom:28px;\">You have requested account verification or password reset at <b>MMOMarket</b>. Please use the OTP code below to complete the verification process:</p>" +
                "<div style=\"text-align:center;margin-bottom:28px;\">" +
                "<span style=\"display:inline-block;background:#ef4444;color:#fff;font-size:36px;font-weight:700;letter-spacing:8px;padding:18px 40px;border-radius:10px;box-shadow:0 2px 8px rgba(239,68,68,0.12);\">" + code + "</span>" +
                "</div>" +
                "<p style=\"font-size:15px;color:#666;margin-bottom:16px;text-align:center;\">This OTP code is valid for <b>5 minutes</b>. Please do not share this code with anyone to ensure account security.</p>" +
                "<p style=\"font-size:15px;color:#666;margin-bottom:24px;text-align:center;\">If you did not make this request, please ignore this email or contact support.</p>" +
                "<div style=\"text-align:center;margin-bottom:8px;\">" +
                "<a href='http://localhost:8080/authen/login' style='display:inline-block;background:#ef4444;color:#fff;font-weight:600;padding:12px 36px;border-radius:8px;text-decoration:none;font-size:16px;box-shadow:0 2px 8px rgba(239,68,68,0.10);transition:background 0.2s;'>Login to MMOMarket</a>" +
                "</div>" +
                "</div>" +
                "<div style=\"background:#f7f7f9;color:#aaa;font-size:13px;text-align:center;padding:16px 8px;border-radius:0 0 16px 16px;\">&copy; 2024 MMOMarket. All rights reserved.</div>" +
                "</div>" +
                "</div>";
    }

    public static String withdrawalRequestEmail(String userName, String amount, String bankInfo, String requestDate) {
        return "<div style=\"font-family:'Inter',Arial,sans-serif;background:#f7f7f9;padding:32px;\">" +
                "<div style=\"max-width:480px;margin:auto;background:#fff;border-radius:16px;box-shadow:0 4px 24px rgba(0,0,0,0.08);overflow:hidden;\">" +
                "<div style=\"background:linear-gradient(90deg,#ef4444 0,#f59e42 100%);padding:24px 0;text-align:center;border-radius:16px 16px 0 0;\">" +
                "<h2 style=\"color:#fff;font-size:24px;font-weight:700;margin:0;letter-spacing:1px;\">Withdrawal Request Confirmation</h2>" +
                "</div>" +
                "<div style=\"padding:32px 24px 24px 24px;\">" +
                "<p style=\"font-size:17px;color:#222;margin-bottom:18px;\">Hello <b>" + userName + "</b>,</p>" +
                "<p style=\"font-size:16px;color:#444;margin-bottom:18px;\">We have received your withdrawal request with the following information:</p>" +
                "<ul style=\"font-size:15px;color:#444;margin-bottom:18px;list-style:none;padding:0;\">" +
                "<li><b>Amount:</b> " + amount + "</li>" +
                "<li><b>Bank:</b> " + bankInfo + "</li>" +
                "<li><b>Request time:</b> " + requestDate + "</li>" +
                "</ul>" +
                "<p style=\"font-size:15px;color:#666;margin-bottom:24px;\">The request will be processed within <b>3-5 business days</b>. If you need support, please contact customer service.</p>" +
                "</div>" +
                "<div style=\"background:#f7f7f9;color:#aaa;font-size:13px;text-align:center;padding:16px 8px;border-radius:0 0 16px 16px;\">&copy; 2024 MMOMarket. All rights reserved.</div>" +
                "</div>" +
                "</div>";
    }

    public static String sellerRegistrationSuccessEmail(String userName) {
        return "<div style=\"font-family:'Inter',Arial,sans-serif;background:#f7f7f9;padding:32px;\">" +
                "<div style=\"max-width:480px;margin:auto;background:#fff;border-radius:16px;box-shadow:0 4px 24px rgba(0,0,0,0.08);overflow:hidden;\">" +
                "<div style=\"background:linear-gradient(90deg,#ef4444 0,#f59e42 100%);padding:24px 0;text-align:center;border-radius:16px 16px 0 0;\">" +
                "<h2 style=\"color:#fff;font-size:24px;font-weight:700;margin:0;letter-spacing:1px;\">Seller Registration Successful</h2>" +
                "</div>" +
                "<div style=\"padding:32px 24px 24px 24px;\">" +
                "<p style=\"font-size:17px;color:#222;margin-bottom:18px;\">Hello <b>" + userName + "</b>,</p>" +
                "<p style=\"font-size:16px;color:#444;margin-bottom:18px;\">Congratulations! You have successfully registered as a Seller at <b>MMOMarket</b>! Please wait for the administrator to verify your information.</p>" +
                "<p style=\"font-size:15px;color:#666;margin-bottom:24px;\">You will receive an email notification when your account is approved or if additional information is required.</p>" +
                "</div>" +
                "<div style=\"background:#f7f7f9;color:#aaa;font-size:13px;text-align:center;padding:16px 8px;border-radius:0 0 16px 16px;\">&copy; 2024 MMOMarket. All rights reserved.</div>" +
                "</div>" +
                "</div>";
    }

    public static String sellerVerificationEmail(String userName, String verifyLink) {
        return "<div style=\"font-family:'Inter',Arial,sans-serif;background:#f7f7f9;padding:32px;\">" +
                "<div style=\"max-width:480px;margin:auto;background:#fff;border-radius:16px;box-shadow:0 4px 24px rgba(0,0,0,0.08);overflow:hidden;\">" +
                "<div style=\"background:linear-gradient(90deg,#ef4444 0,#f59e42 100%);padding:24px 0;text-align:center;border-radius:16px 16px 0 0;\">" +
                "<h2 style=\"color:#fff;font-size:24px;font-weight:700;margin:0;letter-spacing:1px;\">Seller Account Verification</h2>" +
                "</div>" +
                "<div style=\"padding:32px 24px 24px 24px;\">" +
                "<p style=\"font-size:17px;color:#222;margin-bottom:18px;\">Hello <b>" + userName + "</b>,</p>" +
                "<p style=\"font-size:16px;color:#444;margin-bottom:18px;\">Please click the button below to verify your Seller account:</p>" +
                "<div style=\"text-align:center;margin-bottom:28px;\">" +
                "<a href='" + verifyLink + "' style='display:inline-block;background:#ef4444;color:#fff;font-weight:600;padding:12px 36px;border-radius:8px;text-decoration:none;font-size:16px;box-shadow:0 2px 8px rgba(239,68,68,0.10);transition:background 0.2s;'>Verify Now</a>" +
                "</div>" +
                "<p style=\"font-size:15px;color:#666;margin-bottom:24px;text-align:center;\">If you did not make this request, please ignore this email or contact support.</p>" +
                "</div>" +
                "<div style=\"background:#f7f7f9;color:#aaa;font-size:13px;text-align:center;padding:16px 8px;border-radius:0 0 16px 16px;\">&copy; 2024 MMOMarket. All rights reserved.</div>" +
                "</div>" +
                "</div>";
    }

    public static String sellerStatusEmail(String userName, String status, String note) {
        String statusColor = status.equalsIgnoreCase("approved") ? "#22c55e" : status.equalsIgnoreCase("rejected") ? "#ef4444" : "#f59e42";
        String statusText = status.equalsIgnoreCase("approved") ? "Approved" : status.equalsIgnoreCase("rejected") ? "Rejected" : "Pending";
        return "<div style=\"font-family:'Inter',Arial,sans-serif;background:#f7f7f9;padding:32px;\">" +
                "<div style=\"max-width:480px;margin:auto;background:#fff;border-radius:16px;box-shadow:0 4px 24px rgba(0,0,0,0.08);overflow:hidden;\">" +
                "<div style=\"background:linear-gradient(90deg,#ef4444 0,#f59e42 100%);padding:24px 0;text-align:center;border-radius:16px 16px 0 0;\">" +
                "<h2 style=\"color:#fff;font-size:24px;font-weight:700;margin:0;letter-spacing:1px;\">Seller Account Status</h2>" +
                "</div>" +
                "<div style=\"padding:32px 24px 24px 24px;\">" +
                "<p style=\"font-size:17px;color:#222;margin-bottom:18px;\">Hello <b>" + userName + "</b>,</p>" +
                "<p style=\"font-size:16px;color:#444;margin-bottom:18px;\">Your Seller account status: <span style='color:" + statusColor + ";font-weight:600;'>" + statusText + "</span></p>" +
                (note != null && !note.isEmpty() ? "<p style=\"font-size:15px;color:#666;margin-bottom:18px;\"><b>Note:</b> " + note + "</p>" : "") +
                "<p style=\"font-size:15px;color:#666;margin-bottom:24px;\">If you have any questions, please contact support.</p>" +
                "</div>" +
                "<div style=\"background:#f7f7f9;color:#aaa;font-size:13px;text-align:center;padding:16px 8px;border-radius:0 0 16px 16px;\">&copy; 2024 MMOMarket. All rights reserved.</div>" +
                "</div>" +
                "</div>";
    }

    public static String withdrawalApprovedEmail(String userName, String amount, String bankInfo, String approveDate, String proofFile) {
        return "<div style=\"font-family:'Inter',Arial,sans-serif;background:#f7f7f9;padding:32px;\">" +
                "<div style=\"max-width:480px;margin:auto;background:#fff;border-radius:16px;box-shadow:0 4px 24px rgba(0,0,0,0.08);overflow:hidden;\">" +
                "<div style=\"background:linear-gradient(90deg,#22c55e 0,#38bdf8 100%);padding:24px 0;text-align:center;border-radius:16px 16px 0 0;\">" +
                "<h2 style=\"color:#fff;font-size:24px;font-weight:700;margin:0;letter-spacing:1px;\">Withdrawal Request Approved</h2>" +
                "</div>" +
                "<div style=\"padding:32px 24px 24px 24px;\">" +
                "<p style=\"font-size:17px;color:#222;margin-bottom:18px;\">Hello <b>" + userName + "</b>,</p>" +
                "<p style=\"font-size:16px;color:#444;margin-bottom:18px;\">Your withdrawal request has been approved with the following information:</p>" +
                "<ul style=\"font-size:15px;color:#444;margin-bottom:18px;list-style:none;padding:0;\">" +
                "<li><b>Amount:</b> " + amount + "</li>" +
                "<li><b>Bank:</b> " + bankInfo + "</li>" +
                "<li><b>Approval time:</b> " + approveDate + "</li>" +
                (proofFile != null && !proofFile.isEmpty() ? "<li><b>Transfer proof:</b> <a href='" + proofFile + "'>View proof</a></li>" : "") +
                "</ul>" +
                "<p style=\"font-size:15px;color:#666;margin-bottom:24px;\">If you have any questions, please contact customer service.</p>" +
                "</div>" +
                "<div style=\"background:#f7f7f9;color:#aaa;font-size:13px;text-align:center;padding:16px 8px;border-radius:0 0 16px 16px;\">&copy; 2024 MMOMarket. All rights reserved.</div>" +
                "</div>" +
                "</div>";
    }

    public static String withdrawalRejectedEmail(String userName, String amount, String bankInfo, String rejectDate, String reason) {
        return "<div style=\"font-family:'Inter',Arial,sans-serif;background:#f7f7f9;padding:32px;\">" +
                "<div style=\"max-width:480px;margin:auto;background:#fff;border-radius:16px;box-shadow:0 4px 24px rgba(0,0,0,0.08);overflow:hidden;\">" +
                "<div style=\"background:linear-gradient(90deg,#ef4444 0,#f59e42 100%);padding:24px 0;text-align:center;border-radius:16px 16px 0 0;\">" +
                "<h2 style=\"color:#fff;font-size:24px;font-weight:700;margin:0;letter-spacing:1px;\">Withdrawal Request Rejected</h2>" +
                "</div>" +
                "<div style=\"padding:32px 24px 24px 24px;\">" +
                "<p style=\"font-size:17px;color:#222;margin-bottom:18px;\">Hello <b>" + userName + "</b>,</p>" +
                "<p style=\"font-size:16px;color:#444;margin-bottom:18px;\">Your withdrawal request has been rejected with the following information:</p>" +
                "<ul style=\"font-size:15px;color:#444;margin-bottom:18px;list-style:none;padding:0;\">" +
                "<li><b>Amount:</b> " + amount + "</li>" +
                "<li><b>Bank:</b> " + bankInfo + "</li>" +
                "<li><b>Rejection time:</b> " + rejectDate + "</li>" +
                "<li><b>Rejection reason:</b> " + reason + "</li>" +
                "</ul>" +
                "<p style=\"font-size:15px;color:#666;margin-bottom:24px;\">If you have any questions, please contact customer service.</p>" +
                "</div>" +
                "<div style=\"background:#f7f7f9;color:#aaa;font-size:13px;text-align:center;padding:16px 8px;border-radius:0 0 16px 16px;\">&copy; 2024 MMOMarket. All rights reserved.</div>" +
                "</div>" +
                "</div>";
    }

    public static String withdrawalOtpEmail(String code) {
        return "<div style=\"font-family:'Inter',Arial,sans-serif;background:#f7f7f9;padding:32px;\">" +
                "<div style=\"max-width:520px;margin:auto;background:#fff;border-radius:16px;box-shadow:0 6px 28px rgba(0,0,0,0.08);overflow:hidden;\">" +
                "<div style=\"background:linear-gradient(90deg,#ef4444 0,#f59e42 100%);padding:24px 0;text-align:center;border-radius:16px 16px 0 0;\">" +
                "<h2 style=\"color:#fff;font-size:22px;font-weight:800;margin:0;letter-spacing:0.5px;\">Withdrawal Verification - MMOMarket</h2>" +
                "</div>" +
                "<div style=\"padding:28px 24px 24px 24px;\">" +
                "<p style=\"font-size:16px;color:#222;margin:0 0 12px;\">You have requested <b>withdrawal confirmation</b> on MMOMarket.</p>" +
                "<p style=\"font-size:15px;color:#444;margin:0 0 18px;\">Please enter the OTP code below within <b>5 minutes</b> to complete the verification:</p>" +
                "<div style=\"text-align:center;margin:20px 0 16px;\">" +
                "<span style=\"display:inline-block;background:#ef4444;color:#fff;font-size:34px;font-weight:800;letter-spacing:8px;padding:16px 36px;border-radius:10px;box-shadow:0 3px 10px rgba(239,68,68,0.15);\">" + code + "</span>" +
                "</div>" +
                "<div style=\"background:#fffbeb;border:1px solid #fde68a;border-radius:10px;padding:12px 14px;margin:10px 0 16px;\">" +
                "<p style=\"font-size:13px;color:#92400e;margin:0;\"><b>Security note:</b> MMOMarket will <u>never</u> ask you to enter OTP outside the official website/app. If you did <u>not</u> request a withdrawal, <b>do not share this code</b> and change your password immediately.</p>" +
                "</div>" +
                "<div style=\"text-align:center;margin:10px 0 0;\">" +
                "<a href='http://localhost:8080/seller/withdraw-money' style='display:inline-block;background:#ef4444;color:#fff;font-weight:700;padding:10px 28px;border-radius:8px;text-decoration:none;font-size:15px;box-shadow:0 2px 8px rgba(239,68,68,0.15);'>Open withdrawal page</a>" +
                "</div>" +
                "</div>" +
                "<div style=\"background:#f7f7f9;color:#aaa;font-size:12px;text-align:center;padding:14px 8px;border-radius:0 0 16px 16px;\">&copy; 2025 MMOMarket. All rights reserved.</div>" +
                "</div>" +
                "</div>";
    }

    public static String withdrawalBankInfoUpdatedEmail(String userName, String oldBankInfo, String newBankInfo, String updatedDate) {
        return "<div style=\"font-family:'Inter',Arial,sans-serif;background:#f7f7f9;padding:32px;\">" +
                "<div style=\"max-width:520px;margin:auto;background:#fff;border-radius:16px;box-shadow:0 6px 28px rgba(0,0,0,0.08);overflow:hidden;\">" +
                "<div style=\"background:linear-gradient(90deg,#3b82f6 0,#22c55e 100%);padding:22px 0;text-align:center;border-radius:16px 16px 0 0;\">" +
                "<h2 style=\"color:#fff;font-size:22px;font-weight:800;margin:0;letter-spacing:0.5px;\">Withdrawal Bank Information Updated</h2>" +
                "</div>" +
                "<div style=\"padding:24px;\">" +
                "<p style=\"font-size:16px;color:#222;margin:0 0 12px;\">Hello <b>" + escape(userName) + "</b>,</p>" +
                "<p style=\"font-size:15px;color:#444;margin:0 0 16px;\">You have just updated your <b>bank information</b> for your pending withdrawal request.</p>" +
                "<div style=\"background:#f8fafc;border:1px solid #e2e8f0;border-radius:12px;padding:12px 14px;margin:10px 0 16px;\">" +
                "<p style=\"font-size:14px;color:#334155;margin:0 0 8px;\"><b>Before:</b> " + escape(oldBankInfo) + "</p>" +
                "<p style=\"font-size:14px;color:#334155;margin:0;\"><b>After:</b> " + escape(newBankInfo) + "</p>" +
                "</div>" +
                "<p style=\"font-size:13px;color:#475569;margin:0 0 14px;\">Update time: <b>" + escape(updatedDate) + "</b></p>" +
                "<div style=\"background:#fffbeb;border:1px solid #fde68a;border-radius:10px;padding:10px 12px;margin:10px 0 0;\">" +
                "<p style=\"font-size:13px;color:#92400e;margin:0;\"><b>Security note:</b> If you did <u>not</u> make this change, please change your password and contact support immediately.</p>" +
                "</div>" +
                "<div style=\"text-align:center;margin:16px 0 0;\">" +
                "<a href='http://localhost:8080/seller/withdraw-money' style='display:inline-block;background:#3b82f6;color:#fff;font-weight:700;padding:10px 28px;border-radius:8px;text-decoration:none;font-size:15px;box-shadow:0 2px 8px rgba(59,130,246,0.15);'>View withdrawal history</a>" +
                "</div>" +
                "</div>" +
                "<div style=\"background:#f7f7f9;color:#aaa;font-size:12px;text-align:center;padding:14px 8px;border-radius:0 0 16px 16px;\">&copy; 2025 MMOMarket. All rights reserved.</div>" +
                "</div>" +
                "</div>";
    }

    // Minimal HTML escape for template parameters
    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    public static String contactFormEmail(String name, String email, String message) {
        String safeName = escape(name);
        String safeEmail = escape(email);
        String safeMsg = escape(message).replace("\n", "<br/>");

        return "<!doctype html>" +
                "<html><head>" +
                "<meta name=\"viewport\" content=\"width=device-width\" />" +
                "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />" +
                "<title>New Contact Inquiry • MMOMarket</title>" +
                "</head>" +
                "<body style=\"margin:0;padding:0;background:#f4f5f7;\">" +
                "  <table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f4f5f7;padding:24px 0;\">" +
                "    <tr><td align=\"center\">" +
                "      <table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#ffffff;border:1px solid #ececec;border-radius:12px;overflow:hidden;box-shadow:0 10px 28px rgba(245,62,50,0.08);\">" +
                "        <tr>" +
                "          <td style=\"padding:18px 24px;background:#F53E32;color:#ffffff;font-family:Inter,Arial,sans-serif;\">" +
                "            <div style=\"font-size:12px;opacity:.95;letter-spacing:.4px;text-transform:uppercase;\">MMOMarket</div>" +
                "            <h1 style=\"margin:4px 0 0 0;font-size:20px;font-weight:800;letter-spacing:-.2px;\">New Contact Inquiry</h1>" +
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
    }

    // New: OTP email for shop deletion (English)
    public static String deleteShopOtpEmail(String code) {
        return "<div style=\"font-family:'Inter',Arial,sans-serif;background:#f7f7f9;padding:32px;\">" +
                "<div style=\"max-width:520px;margin:auto;background:#fff;border-radius:16px;box-shadow:0 6px 28px rgba(0,0,0,0.08);overflow:hidden;\">" +
                "<div style=\"background:linear-gradient(90deg,#ef4444 0,#f59e42 100%);padding:24px 0;text-align:center;border-radius:16px 16px 0 0;\">" +
                "<h2 style=\"color:#fff;font-size:22px;font-weight:800;margin:0;letter-spacing:0.5px;\">Confirm Shop Cancellation - MMOMarket</h2>" +
                "</div>" +
                "<div style=\"padding:28px 24px 24px 24px;\">" +
                "<p style=\"font-size:16px;color:#222;margin:0 0 12px;\">You have requested to <b>cancel your shop</b> on MMOMarket.</p>" +
                "<p style=\"font-size:15px;color:#444;margin:0 0 18px;\">Please enter the OTP code below within <b>5 minutes</b> to confirm this irreversible action:</p>" +
                "<div style=\"text-align:center;margin:20px 0 16px;\">" +
                "<span style=\"display:inline-block;background:#ef4444;color:#fff;font-size:34px;font-weight:800;letter-spacing:8px;padding:16px 36px;border-radius:10px;box-shadow:0 3px 10px rgba(239,68,68,0.15);\">" + code + "</span>" +
                "</div>" +
                "<div style=\"background:#fffbeb;border:1px solid #fde68a;border-radius:10px;padding:12px 14px;margin:10px 0 16px;\">" +
                "<p style=\"font-size:13px;color:#92400e;margin:0;\"><b>Security note:</b> MMOMarket will <u>never</u> ask you to enter OTP outside the official website/app. If you did <u>not</u> request this action, <b>do not share this code</b> and change your password immediately.</p>" +
                "</div>" +
                "<div style=\"text-align:center;margin:10px 0 0;\">" +
                "<a href='http://localhost:8080/seller/shop-info' style='display:inline-block;background:#ef4444;color:#fff;font-weight:700;padding:10px 28px;border-radius:8px;text-decoration:none;font-size:15px;box-shadow:0 2px 8px rgba(239,68,68,0.15);'>Open shop info</a>" +
                "</div>" +
                "</div>" +
                "<div style=\"background:#f7f7f9;color:#aaa;font-size:12px;text-align:center;padding:14px 8px;border-radius:0 0 16px 16px;\">&copy; 2025 MMOMarket. All rights reserved.</div>" +
                "</div>" +
                "</div>";
    }

    // New: Success email for shop cancellation (English)
    public static String deleteShopSuccessEmail(String userName, String shopName, String cancelDate) {
        String safeUser = escape(userName);
        String safeShop = escape(shopName);
        String safeDate = escape(cancelDate);
        return "<div style=\"font-family:'Inter',Arial,sans-serif;background:#f7f7f9;padding:32px;\">" +
                "<div style=\"max-width:520px;margin:auto;background:#fff;border-radius:16px;box-shadow:0 6px 28px rgba(0,0,0,0.08);overflow:hidden;\">" +
                "<div style=\"background:linear-gradient(90deg,#22c55e 0,#38bdf8 100%);padding:24px 0;text-align:center;border-radius:16px 16px 0 0;\">" +
                "<h2 style=\"color:#fff;font-size:22px;font-weight:800;margin:0;letter-spacing:0.5px;\">Shop Cancellation Successful</h2>" +
                "</div>" +
                "<div style=\"padding:28px 24px 24px 24px;\">" +
                "<p style=\"font-size:16px;color:#222;margin:0 0 12px;\">Hello <b>" + safeUser + "</b>,</p>" +
                "<p style=\"font-size:15px;color:#444;margin:0 0 16px;\">Your shop <b>" + safeShop + "</b> has been cancelled successfully on <b>" + safeDate + "</b>.</p>" +
                "<p style=\"font-size:14px;color:#475569;margin:0 0 16px;\">All related data has been soft-deleted and your shop status is now <b>Inactive</b>. You can register a new shop at any time.</p>" +
                "<div style=\"text-align:center;margin:10px 0 0;\">" +
                "<a href='http://localhost:8080/' style='display:inline-block;background:#22c55e;color:#fff;font-weight:700;padding:10px 28px;border-radius:8px;text-decoration:none;font-size:15px;box-shadow:0 2px 8px rgba(34,197,94,0.15);'>Return to homepage</a>" +
                "</div>" +
                "</div>" +
                "<div style=\"background:#f7f7f9;color:#aaa;font-size:12px;text-align:center;padding:14px 8px;border-radius:0 0 16px 16px;\">&copy; 2025 MMOMarket. All rights reserved.</div>" +
                "</div>" +
                "</div>";
    }

    public static String buyPointsOtpEmail(String code) {
        return "<div style=\"font-family:'Inter',Arial,sans-serif;background:#f7f7f9;padding:32px;\">" +
                "<div style=\"max-width:520px;margin:auto;background:#fff;border-radius:16px;box-shadow:0 6px 28px rgba(0,0,0,0.08);overflow:hidden;\">" +
                "<div style=\"background:linear-gradient(90deg,#f59e0b 0,#ef4444 100%);padding:24px 0;text-align:center;border-radius:16px 16px 0 0;\">" +
                "<h2 style=\"color:#fff;font-size:22px;font-weight:800;margin:0;letter-spacing:0.5px;\">Confirm Points Purchase - MMOMarket</h2>" +
                "</div>" +
                "<div style=\"padding:28px 24px 24px 24px;\">" +
                "<p style=\"font-size:16px;color:#222;margin:0 0 12px;\">You are requesting to <b>buy points</b> to upgrade your seller level.</p>" +
                "<p style=\"font-size:15px;color:#444;margin:0 0 18px;\">Enter the OTP below within <b>5 minutes</b> to confirm:</p>" +
                "<div style=\"text-align:center;margin:20px 0 16px;\">" +
                "<span style=\"display:inline-block;background:#f59e0b;color:#fff;font-size:34px;font-weight:800;letter-spacing:8px;padding:16px 36px;border-radius:10px;box-shadow:0 3px 10px rgba(245,158,11,0.15);\">" + code + "</span>" +
                "</div>" +
                "<div style=\"background:#fffbeb;border:1px solid #fde68a;border-radius:10px;padding:12px 14px;margin:10px 0 16px;\">" +
                "<p style=\"font-size:13px;color:#92400e;margin:0;\"><b>Security note:</b> MMOMarket will <u>never</u> ask for your OTP outside the official website/app.</p>" +
                "</div>" +
                "<div style=\"text-align:center;margin:10px 0 0;\">" +
                "<a href='http://localhost:8080/seller/shop-info' style='display:inline-block;background:#f59e0b;color:#fff;font-weight:700;padding:10px 28px;border-radius:8px;text-decoration:none;font-size:15px;box-shadow:0 2px 8px rgba(245,158,11,0.15);'>Open shop info</a>" +
                "</div>" +
                "</div>" +
                "<div style=\"background:#f7f7f9;color:#aaa;font-size:12px;text-align:center;padding:14px 8px;border-radius:0 0 16px 16px;\">&copy; 2025 MMOMarket. All rights reserved.</div>" +
                "</div>" +
                "</div>";
    }

    public static String buyPointsSuccessEmail(String userName, String points, String newLevel, String totalPoints, String newCommission) {
        return "<div style=\"font-family:'Inter',Arial,sans-serif;background:#f7f7f9;padding:32px;\">" +
                "<div style=\"max-width:520px;margin:auto;background:#fff;border-radius:16px;box-shadow:0 6px 28px rgba(0,0,0,0.08);overflow:hidden;\">" +
                "<div style=\"background:linear-gradient(90deg,#ef4444 0,#f59e42 100%);padding:24px 0;text-align:center;border-radius:16px 16px 0 0;\">" +
                "<h2 style=\"color:#fff;font-size:24px;font-weight:700;margin:0;letter-spacing:1px;\">Points Purchase Successful</h2>" +
                "</div>" +
                "<div style=\"padding:28px 24px 24px 24px;\">" +
                "<p style=\"font-size:17px;color:#222;margin:0 0 16px;\">Hello <b>" + escape(userName) + "</b>,</p>" +
                "<p style=\"font-size:16px;color:#444;margin:0 0 20px;\">You have successfully purchased <b style=\"color:#ef4444;\">" + escape(points) + " points</b>.</p>" +
                "<div style=\"background:#fef2f2;border-left:4px solid #ef4444;padding:16px;border-radius:8px;margin:0 0 20px;\">" +
                "<ul style=\"font-size:15px;color:#334155;margin:0;list-style:none;padding:0;\">" +
                "<li style=\"margin-bottom:10px;\"><b>New Level:</b> <span style=\"color:#ef4444;font-weight:600;\">" + escape(newLevel) + "</span></li>" +
                "<li style=\"margin-bottom:10px;\"><b>Total Points:</b> <span style=\"color:#ef4444;font-weight:600;\">" + escape(totalPoints) + "</span></li>" +
                "<li><b>Commission Rate:</b> <span style=\"color:#ef4444;font-weight:600;\">" + escape(newCommission) + "%</span></li>" +
                "</ul>" +
                "</div>" +
                "<p style=\"font-size:14px;color:#64748b;margin:0 0 20px;line-height:1.6;\">Your shop level has been upgraded! Enjoy lower commission rates and higher selling limits.</p>" +
                "<p style=\"font-size:13px;color:#94a3b8;margin:0 0 20px;\">If this was not you, please change your password and contact support immediately.</p>" +
                "<div style=\"text-align:center;margin:10px 0 0;\">" +
                "<a href='http://localhost:8080/seller/shop-info' style='display:inline-block;background:#ef4444;color:#fff;font-weight:600;padding:12px 32px;border-radius:8px;text-decoration:none;font-size:16px;box-shadow:0 2px 8px rgba(239,68,68,0.15);transition:background 0.2s;'>View Shop Info</a>" +
                "</div>" +
                "</div>" +
                "<div style=\"background:#f7f7f9;color:#aaa;font-size:13px;text-align:center;padding:16px 8px;border-radius:0 0 16px 16px;\">&copy; 2025 MMOMarket. All rights reserved.</div>" +
                "</div>" +
                "</div>";
    }
}
