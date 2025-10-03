package com.mmo.util;

public class EmailTemplate {
    public static String verificationEmail(String code) {
        return "<div style=\"font-family:'Inter',Arial,sans-serif;background:#f7f7f9;padding:32px;\">" +
                "<div style=\"max-width:480px;margin:auto;background:#fff;border-radius:16px;box-shadow:0 4px 24px rgba(0,0,0,0.08);overflow:hidden;\">" +
                "<div style=\"background:linear-gradient(90deg,#ef4444 0,#f59e42 100%);padding:24px 0;text-align:center;border-radius:16px 16px 0 0;\">" +
                "<h2 style=\"color:#fff;font-size:24px;font-weight:700;margin:0;letter-spacing:1px;\">MMOMarket OTP Verification</h2>" +
                "</div>" +
                "<div style=\"padding:32px 24px 24px 24px;\">" +
                "<p style=\"font-size:17px;color:#222;margin-bottom:18px;\">Xin chào,</p>" +
                "<p style=\"font-size:16px;color:#444;margin-bottom:28px;\">Bạn vừa yêu cầu xác thực tài khoản hoặc đổi mật khẩu tại <b>MMOMarket</b>. Vui lòng sử dụng mã OTP bên dưới để hoàn tất quá trình:</p>" +
                "<div style=\"text-align:center;margin-bottom:28px;\">" +
                "<span style=\"display:inline-block;background:#ef4444;color:#fff;font-size:36px;font-weight:700;letter-spacing:8px;padding:18px 40px;border-radius:10px;box-shadow:0 2px 8px rgba(239,68,68,0.12);\">" + code + "</span>" +
                "</div>" +
                "<p style=\"font-size:15px;color:#666;margin-bottom:16px;text-align:center;\">Mã OTP này có hiệu lực trong <b>5 phút</b>. Vui lòng không chia sẻ mã này cho bất kỳ ai.</p>" +
                "<p style=\"font-size:15px;color:#666;margin-bottom:24px;text-align:center;\">Nếu bạn không thực hiện yêu cầu này, hãy bỏ qua email này hoặc liên hệ hỗ trợ.</p>" +
                "<div style=\"text-align:center;margin-bottom:8px;\">" +
                "<a href='http://localhost:8080/authen/login' style='display:inline-block;background:#ef4444;color:#fff;font-weight:600;padding:12px 36px;border-radius:8px;text-decoration:none;font-size:16px;box-shadow:0 2px 8px rgba(239,68,68,0.10);transition:background 0.2s;'>Đăng nhập MMO Market</a>" +
                "</div>" +
                "</div>" +
                "<div style=\"background:#f7f7f9;color:#aaa;font-size:13px;text-align:center;padding:16px 8px;border-radius:0 0 16px 16px;\">&copy; 2024 MMO Market. All rights reserved.</div>" +
                "</div>" +
                "</div>";
    }
}
