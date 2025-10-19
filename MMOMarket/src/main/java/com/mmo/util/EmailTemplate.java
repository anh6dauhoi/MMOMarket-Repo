package com.mmo.util;

public class EmailTemplate {
    public static String verificationEmail(String code) {
        return "<div style=\"font-family:'Inter',Arial,sans-serif;background:#f7f7f9;padding:32px;\">" +
                "<div style=\"max-width:480px;margin:auto;background:#fff;border-radius:16px;box-shadow:0 4px 24px rgba(0,0,0,0.08);overflow:hidden;\">" +
                "<div style=\"background:linear-gradient(90deg,#ef4444 0,#f59e42 100%);padding:24px 0;text-align:center;border-radius:16px 16px 0 0;\">" +
                "<h2 style=\"color:#fff;font-size:24px;font-weight:700;margin:0;letter-spacing:1px;\">Xác thực tài khoản - MMOMarket</h2>" +
                "</div>" +
                "<div style=\"padding:32px 24px 24px 24px;\">" +
                "<p style=\"font-size:17px;color:#222;margin-bottom:18px;\">Xin chào,</p>" +
                "<p style=\"font-size:16px;color:#444;margin-bottom:28px;\">Bạn đã yêu cầu xác thực tài khoản hoặc đổi mật khẩu tại <b>MMOMarket</b>. Vui lòng sử dụng mã OTP bên dưới để hoàn tất quá trình xác thực:</p>" +
                "<div style=\"text-align:center;margin-bottom:28px;\">" +
                "<span style=\"display:inline-block;background:#ef4444;color:#fff;font-size:36px;font-weight:700;letter-spacing:8px;padding:18px 40px;border-radius:10px;box-shadow:0 2px 8px rgba(239,68,68,0.12);\">" + code + "</span>" +
                "</div>" +
                "<p style=\"font-size:15px;color:#666;margin-bottom:16px;text-align:center;\">Mã OTP có hiệu lực trong <b>5 phút</b>. Vui lòng không chia sẻ mã này cho bất kỳ ai để đảm bảo an toàn tài khoản.</p>" +
                "<p style=\"font-size:15px;color:#666;margin-bottom:24px;text-align:center;\">Nếu bạn không thực hiện yêu cầu này, hãy bỏ qua email hoặc liên hệ bộ phận hỗ trợ.</p>" +
                "<div style=\"text-align:center;margin-bottom:8px;\">" +
                "<a href='http://localhost:8080/authen/login' style='display:inline-block;background:#ef4444;color:#fff;font-weight:600;padding:12px 36px;border-radius:8px;text-decoration:none;font-size:16px;box-shadow:0 2px 8px rgba(239,68,68,0.10);transition:background 0.2s;'>Đăng nhập MMOMarket</a>" +
                "</div>" +
                "</div>" +
                "<div style=\"background:#f7f7f9;color:#aaa;font-size:13px;text-align:center;padding:16px 8px;border-radius:0 0 16px 16px;\">&copy; 2024 MMOMarket. Mọi quyền được bảo lưu.</div>" +
                "</div>" +
                "</div>";
    }

    public static String withdrawalRequestEmail(String userName, String amount, String bankInfo, String requestDate) {
        return "<div style=\"font-family:'Inter',Arial,sans-serif;background:#f7f7f9;padding:32px;\">" +
                "<div style=\"max-width:480px;margin:auto;background:#fff;border-radius:16px;box-shadow:0 4px 24px rgba(0,0,0,0.08);overflow:hidden;\">" +
                "<div style=\"background:linear-gradient(90deg,#ef4444 0,#f59e42 100%);padding:24px 0;text-align:center;border-radius:16px 16px 0 0;\">" +
                "<h2 style=\"color:#fff;font-size:24px;font-weight:700;margin:0;letter-spacing:1px;\">Xác nhận yêu cầu rút tiền</h2>" +
                "</div>" +
                "<div style=\"padding:32px 24px 24px 24px;\">" +
                "<p style=\"font-size:17px;color:#222;margin-bottom:18px;\">Xin chào <b>" + userName + "</b>,</p>" +
                "<p style=\"font-size:16px;color:#444;margin-bottom:18px;\">Chúng tôi đã nhận được yêu cầu rút tiền của bạn với thông tin sau:</p>" +
                "<ul style=\"font-size:15px;color:#444;margin-bottom:18px;list-style:none;padding:0;\">" +
                "<li><b>Số tiền:</b> " + amount + "</li>" +
                "<li><b>Ngân hàng:</b> " + bankInfo + "</li>" +
                "<li><b>Thời gian yêu cầu:</b> " + requestDate + "</li>" +
                "</ul>" +
                "<p style=\"font-size:15px;color:#666;margin-bottom:24px;\">Yêu cầu sẽ được xử lý trong vòng <b>3-5 ngày làm việc</b>. Nếu cần hỗ trợ, vui lòng liên hệ bộ phận chăm sóc khách hàng.</p>" +
                "</div>" +
                "<div style=\"background:#f7f7f9;color:#aaa;font-size:13px;text-align:center;padding:16px 8px;border-radius:0 0 16px 16px;\">&copy; 2024 MMOMarket. Mọi quyền được bảo lưu.</div>" +
                "</div>" +
                "</div>";
    }

    public static String sellerRegistrationSuccessEmail(String userName) {
        return "<div style=\"font-family:'Inter',Arial,sans-serif;background:#f7f7f9;padding:32px;\">" +
                "<div style=\"max-width:480px;margin:auto;background:#fff;border-radius:16px;box-shadow:0 4px 24px rgba(0,0,0,0.08);overflow:hidden;\">" +
                "<div style=\"background:linear-gradient(90deg,#ef4444 0,#f59e42 100%);padding:24px 0;text-align:center;border-radius:16px 16px 0 0;\">" +
                "<h2 style=\"color:#fff;font-size:24px;font-weight:700;margin:0;letter-spacing:1px;\">Đăng ký Seller thành công</h2>" +
                "</div>" +
                "<div style=\"padding:32px 24px 24px 24px;\">" +
                "<p style=\"font-size:17px;color:#222;margin-bottom:18px;\">Xin chào <b>" + userName + "</b>,</p>" +
                "<p style=\"font-size:16px;color:#444;margin-bottom:18px;\">Chúc mừng bạn đã đăng ký thành công tài khoản Seller tại <b>MMOMarket</b>! Vui lòng chờ quản trị viên xác minh thông tin của bạn.</p>" +
                "<p style=\"font-size:15px;color:#666;margin-bottom:24px;\">Bạn sẽ nhận được thông báo qua email khi tài khoản được duyệt hoặc có yêu cầu bổ sung thông tin.</p>" +
                "</div>" +
                "<div style=\"background:#f7f7f9;color:#aaa;font-size:13px;text-align:center;padding:16px 8px;border-radius:0 0 16px 16px;\">&copy; 2024 MMOMarket. Mọi quyền được bảo lưu.</div>" +
                "</div>" +
                "</div>";
    }

    public static String sellerVerificationEmail(String userName, String verifyLink) {
        return "<div style=\"font-family:'Inter',Arial,sans-serif;background:#f7f7f9;padding:32px;\">" +
                "<div style=\"max-width:480px;margin:auto;background:#fff;border-radius:16px;box-shadow:0 4px 24px rgba(0,0,0,0.08);overflow:hidden;\">" +
                "<div style=\"background:linear-gradient(90deg,#ef4444 0,#f59e42 100%);padding:24px 0;text-align:center;border-radius:16px 16px 0 0;\">" +
                "<h2 style=\"color:#fff;font-size:24px;font-weight:700;margin:0;letter-spacing:1px;\">Xác minh tài khoản Seller</h2>" +
                "</div>" +
                "<div style=\"padding:32px 24px 24px 24px;\">" +
                "<p style=\"font-size:17px;color:#222;margin-bottom:18px;\">Xin chào <b>" + userName + "</b>,</p>" +
                "<p style=\"font-size:16px;color:#444;margin-bottom:18px;\">Vui lòng nhấn vào nút bên dưới để xác minh tài khoản Seller của bạn:</p>" +
                "<div style=\"text-align:center;margin-bottom:28px;\">" +
                "<a href='" + verifyLink + "' style='display:inline-block;background:#ef4444;color:#fff;font-weight:600;padding:12px 36px;border-radius:8px;text-decoration:none;font-size:16px;box-shadow:0 2px 8px rgba(239,68,68,0.10);transition:background 0.2s;'>Xác minh ngay</a>" +
                "</div>" +
                "<p style=\"font-size:15px;color:#666;margin-bottom:24px;text-align:center;\">Nếu bạn không thực hiện yêu cầu này, hãy bỏ qua email này hoặc liên hệ hỗ trợ.</p>" +
                "</div>" +
                "<div style=\"background:#f7f7f9;color:#aaa;font-size:13px;text-align:center;padding:16px 8px;border-radius:0 0 16px 16px;\">&copy; 2024 MMOMarket. Mọi quyền được bảo lưu.</div>" +
                "</div>" +
                "</div>";
    }

    public static String sellerStatusEmail(String userName, String status, String note) {
        String statusColor = status.equalsIgnoreCase("approved") ? "#22c55e" : status.equalsIgnoreCase("rejected") ? "#ef4444" : "#f59e42";
        String statusText = status.equalsIgnoreCase("approved") ? "Đã duyệt" : status.equalsIgnoreCase("rejected") ? "Từ chối" : "Đang chờ duyệt";
        return "<div style=\"font-family:'Inter',Arial,sans-serif;background:#f7f7f9;padding:32px;\">" +
                "<div style=\"max-width:480px;margin:auto;background:#fff;border-radius:16px;box-shadow:0 4px 24px rgba(0,0,0,0.08);overflow:hidden;\">" +
                "<div style=\"background:linear-gradient(90deg,#ef4444 0,#f59e42 100%);padding:24px 0;text-align:center;border-radius:16px 16px 0 0;\">" +
                "<h2 style=\"color:#fff;font-size:24px;font-weight:700;margin:0;letter-spacing:1px;\">Trạng thái tài khoản Seller</h2>" +
                "</div>" +
                "<div style=\"padding:32px 24px 24px 24px;\">" +
                "<p style=\"font-size:17px;color:#222;margin-bottom:18px;\">Xin chào <b>" + userName + "</b>,</p>" +
                "<p style=\"font-size:16px;color:#444;margin-bottom:18px;\">Trạng thái tài khoản Seller của bạn: <span style='color:" + statusColor + ";font-weight:600;'>" + statusText + "</span></p>" +
                (note != null && !note.isEmpty() ? "<p style=\"font-size:15px;color:#666;margin-bottom:18px;\"><b>Ghi chú:</b> " + note + "</p>" : "") +
                "<p style=\"font-size:15px;color:#666;margin-bottom:24px;\">Nếu có thắc mắc, vui lòng liên hệ bộ phận hỗ trợ.</p>" +
                "</div>" +
                "<div style=\"background:#f7f7f9;color:#aaa;font-size:13px;text-align:center;padding:16px 8px;border-radius:0 0 16px 16px;\">&copy; 2024 MMOMarket. Mọi quyền được bảo lưu.</div>" +
                "</div>" +
                "</div>";
    }

    public static String withdrawalApprovedEmail(String userName, String amount, String bankInfo, String approveDate, String proofFile) {
        return "<div style=\"font-family:'Inter',Arial,sans-serif;background:#f7f7f9;padding:32px;\">" +
                "<div style=\"max-width:480px;margin:auto;background:#fff;border-radius:16px;box-shadow:0 4px 24px rgba(0,0,0,0.08);overflow:hidden;\">" +
                "<div style=\"background:linear-gradient(90deg,#22c55e 0,#38bdf8 100%);padding:24px 0;text-align:center;border-radius:16px 16px 0 0;\">" +
                "<h2 style=\"color:#fff;font-size:24px;font-weight:700;margin:0;letter-spacing:1px;\">Yêu cầu rút tiền đã được duyệt</h2>" +
                "</div>" +
                "<div style=\"padding:32px 24px 24px 24px;\">" +
                "<p style=\"font-size:17px;color:#222;margin-bottom:18px;\">Xin chào <b>" + userName + "</b>,</p>" +
                "<p style=\"font-size:16px;color:#444;margin-bottom:18px;\">Yêu cầu rút tiền của bạn đã được duyệt với thông tin sau:</p>" +
                "<ul style=\"font-size:15px;color:#444;margin-bottom:18px;list-style:none;padding:0;\">" +
                "<li><b>Số tiền:</b> " + amount + "</li>" +
                "<li><b>Ngân hàng:</b> " + bankInfo + "</li>" +
                "<li><b>Thời gian duyệt:</b> " + approveDate + "</li>" +
                (proofFile != null && !proofFile.isEmpty() ? "<li><b>Minh chứng chuyển khoản:</b> <a href='" + proofFile + "'>Xem minh chứng</a></li>" : "") +
                "</ul>" +
                "<p style=\"font-size:15px;color:#666;margin-bottom:24px;\">Nếu có thắc mắc, vui lòng liên hệ bộ phận chăm sóc khách hàng.</p>" +
                "</div>" +
                "<div style=\"background:#f7f7f9;color:#aaa;font-size:13px;text-align:center;padding:16px 8px;border-radius:0 0 16px 16px;\">&copy; 2024 MMOMarket. Mọi quyền được bảo lưu.</div>" +
                "</div>" +
                "</div>";
    }

    public static String withdrawalRejectedEmail(String userName, String amount, String bankInfo, String rejectDate, String reason) {
        return "<div style=\"font-family:'Inter',Arial,sans-serif;background:#f7f7f9;padding:32px;\">" +
                "<div style=\"max-width:480px;margin:auto;background:#fff;border-radius:16px;box-shadow:0 4px 24px rgba(0,0,0,0.08);overflow:hidden;\">" +
                "<div style=\"background:linear-gradient(90deg,#ef4444 0,#f59e42 100%);padding:24px 0;text-align:center;border-radius:16px 16px 0 0;\">" +
                "<h2 style=\"color:#fff;font-size:24px;font-weight:700;margin:0;letter-spacing:1px;\">Yêu cầu rút tiền bị từ chối</h2>" +
                "</div>" +
                "<div style=\"padding:32px 24px 24px 24px;\">" +
                "<p style=\"font-size:17px;color:#222;margin-bottom:18px;\">Xin chào <b>" + userName + "</b>,</p>" +
                "<p style=\"font-size:16px;color:#444;margin-bottom:18px;\">Yêu cầu rút tiền của bạn đã bị từ chối với thông tin sau:</p>" +
                "<ul style=\"font-size:15px;color:#444;margin-bottom:18px;list-style:none;padding:0;\">" +
                "<li><b>Số tiền:</b> " + amount + "</li>" +
                "<li><b>Ngân hàng:</b> " + bankInfo + "</li>" +
                "<li><b>Thời gian từ chối:</b> " + rejectDate + "</li>" +
                "<li><b>Lý do từ chối:</b> " + reason + "</li>" +
                "</ul>" +
                "<p style=\"font-size:15px;color:#666;margin-bottom:24px;\">Nếu có thắc mắc, vui lòng liên hệ bộ phận chăm sóc khách hàng.</p>" +
                "</div>" +
                "<div style=\"background:#f7f7f9;color:#aaa;font-size:13px;text-align:center;padding:16px 8px;border-radius:0 0 16px 16px;\">&copy; 2024 MMOMarket. Mọi quyền được bảo lưu.</div>" +
                "</div>" +
                "</div>";
    }
}
