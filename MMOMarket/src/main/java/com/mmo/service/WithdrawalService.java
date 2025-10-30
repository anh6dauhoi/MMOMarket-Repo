package com.mmo.service;

import com.mmo.entity.Withdrawal;
import com.mmo.repository.WithdrawalRepository;
import com.mmo.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WithdrawalService {
    private static final Logger log = LoggerFactory.getLogger(WithdrawalService.class);

    @Autowired
    private WithdrawalRepository withdrawalRepository;

    @Autowired
    private com.mmo.service.EmailService emailService;

    @Autowired
    private UserRepository userRepository;

    @Transactional
    public Withdrawal approveWithdrawal(Long id) {
        Withdrawal wd = withdrawalRepository.findById(id).orElseThrow();
        wd.setStatus("APPROVED");
        Withdrawal result = withdrawalRepository.save(wd);
        // Gửi email bất đồng bộ cho seller
        String userName = wd.getSeller() != null ? wd.getSeller().getFullName() : "";
        String email = wd.getSeller() != null ? wd.getSeller().getEmail() : null;
        String amount = wd.getAmount() != null ? wd.getAmount().toString() : "";
        String bankInfo = wd.getBankName() + " - " + wd.getAccountNumber();
        String approveDate = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(new java.util.Date());
        String proofFile = wd.getProofFile();
        if (email != null && !email.isBlank()) {
            String subject = "[MMOMarket] Yêu cầu rút tiền đã được duyệt";
            String html = com.mmo.util.EmailTemplate.withdrawalApprovedEmail(userName, amount, bankInfo, approveDate, proofFile);
            log.info("Queue email to {} for withdrawal id={} subject={}", email, id, subject);
            emailService.sendEmailAsync(email, subject, html);
        } else {
            log.warn("Seller for withdrawal id={} has no email configured, skipping notification", id);
        }
        return result;
    }

    @Transactional
    public Withdrawal rejectWithdrawal(Long id) {
        Withdrawal wd = withdrawalRepository.findById(id).orElseThrow();
        wd.setStatus("REJECTED");
        Withdrawal result = withdrawalRepository.save(wd);
        // Gửi email bất đồng bộ cho seller
        String userName = wd.getSeller() != null ? wd.getSeller().getFullName() : "";
        String email = wd.getSeller() != null ? wd.getSeller().getEmail() : null;
        String amount = wd.getAmount() != null ? wd.getAmount().toString() : "";
        String bankInfo = wd.getBankName() + " - " + wd.getAccountNumber();
        String rejectDate = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(new java.util.Date());
        String reason = wd.getProofFile(); // hoặc lấy lý do từ nơi khác nếu có
        if (email != null && !email.isBlank()) {
            String subject = "[MMOMarket] Yêu cầu rút tiền bị từ chối";
            String html = com.mmo.util.EmailTemplate.withdrawalRejectedEmail(userName, amount, bankInfo, rejectDate, reason);
            log.info("Queue email to {} for withdrawal id={} subject={}", email, id, subject);
            emailService.sendEmailAsync(email, subject, html);
        } else {
            log.warn("Seller for withdrawal id={} has no email configured, skipping notification", id);
        }
        return result;
    }

    @Transactional
    public Withdrawal processWithdrawal(Long id, String status, String proofFile, String reason, boolean refund) {
        Withdrawal wd = withdrawalRepository.findById(id).orElseThrow();
        if ("Approved".equalsIgnoreCase(status)) {
            wd.setStatus("Approved");
            wd.setProofFile(proofFile);
            wd.setUpdatedAt(new java.util.Date());
            withdrawalRepository.save(wd);
            // Gửi email
            String userName = wd.getSeller() != null ? wd.getSeller().getFullName() : "";
            String email = wd.getSeller() != null ? wd.getSeller().getEmail() : null;
            String amount = wd.getAmount() != null ? wd.getAmount().toString() : "";
            String bankInfo = wd.getBankName() + " - " + wd.getAccountNumber();
            String approveDate = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(new java.util.Date());
            String subject = "[MMOMarket] Yêu cầu rút tiền đã được duyệt";
            String html = com.mmo.util.EmailTemplate.withdrawalApprovedEmail(userName, amount, bankInfo, approveDate, proofFile);
            if (email != null && !email.isBlank()) {
                log.info("Queue email to {} for withdrawal id={} subject={}", email, id, subject);
                emailService.sendEmailAsync(email, subject, html);
            } else {
                log.warn("Seller for withdrawal id={} has no email configured, skipping notification", id);
            }
        } else if ("Rejected".equalsIgnoreCase(status)) {
            wd.setStatus("Rejected");
            wd.setProofFile(reason);
            wd.setUpdatedAt(new java.util.Date());
            withdrawalRepository.save(wd);
            // Refund 95% coins nếu cần - use repository update to avoid loading and saving user entity
            if (refund && wd.getSeller() != null && wd.getSeller().getId() != null) {
                Long amount = wd.getAmount() == null ? 0L : wd.getAmount();
                long refundAmount = Math.round(amount * 0.95);
                if (refundAmount != 0L) {
                    userRepository.addCoins(wd.getSeller().getId(), refundAmount);
                }
            }
            // Gửi email
            String userName = wd.getSeller() != null ? wd.getSeller().getFullName() : "";
            String email = wd.getSeller() != null ? wd.getSeller().getEmail() : null;
            String amount = wd.getAmount() != null ? wd.getAmount().toString() : "";
            String bankInfo = wd.getBankName() + " - " + wd.getAccountNumber();
            String rejectDate = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(new java.util.Date());
            String subject = "[MMOMarket] Yêu cầu rút tiền bị từ chối";
            String html = com.mmo.util.EmailTemplate.withdrawalRejectedEmail(userName, amount, bankInfo, rejectDate, reason);
            if (email != null && !email.isBlank()) {
                log.info("Queue email to {} for withdrawal id={} subject={}", email, id, subject);
                emailService.sendEmailAsync(email, subject, html);
            } else {
                log.warn("Seller for withdrawal id={} has no email configured, skipping notification", id);
            }
        }
        return wd;
    }
}
