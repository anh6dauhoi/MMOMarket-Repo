package com.mmo.mq;

import com.mmo.entity.Complaint;
import com.mmo.entity.SellerBankInfo;
import com.mmo.entity.User;
import com.mmo.entity.Withdrawal;
import com.mmo.mq.dto.WithdrawalCreateMessage;
import com.mmo.repository.ComplaintRepository;
import com.mmo.repository.EmailVerificationRepository;
import com.mmo.repository.UserRepository;
import com.mmo.repository.WithdrawalRepository;
import com.mmo.service.EmailService;
import com.mmo.service.NotificationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@Component
public class WithdrawalCreateListener {
    private static final Logger log = LoggerFactory.getLogger(WithdrawalCreateListener.class);

    private final UserRepository userRepository;
    private final WithdrawalRepository withdrawalRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final ComplaintRepository complaintRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;

    @PersistenceContext
    private EntityManager entityManager;

    public WithdrawalCreateListener(UserRepository userRepository,
                                    WithdrawalRepository withdrawalRepository,
                                    EmailVerificationRepository emailVerificationRepository,
                                    ComplaintRepository complaintRepository,
                                    NotificationService notificationService,
                                    EmailService emailService) {
        this.userRepository = userRepository;
        this.withdrawalRepository = withdrawalRepository;
        this.emailVerificationRepository = emailVerificationRepository;
        this.complaintRepository = complaintRepository;
        this.notificationService = notificationService;
        this.emailService = emailService;
    }

    @Transactional
    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void handle(WithdrawalCreateMessage msg) {
        if (msg == null || msg.sellerId() == null || msg.amount() == null) return;
        log.info("Consuming withdrawal-create message sellerId={} bankInfoId={} amount={} dedupeKey={}",
                msg.sellerId(), msg.bankInfoId(), msg.amount(), msg.dedupeKey());

        User seller = userRepository.findById(msg.sellerId()).orElse(null);
        if (seller == null) {
            log.warn("Seller id={} not found, skipping", msg.sellerId());
            return;
        }

        // OTP validation: single-use and not expired
        try {
            var ov = emailVerificationRepository.findTopByUserAndVerificationCodeAndIsUsedFalseOrderByCreatedAtDesc(seller, msg.otp());
            var ev = ov.orElse(null);
            if (ev == null || (ev.getExpiryDate() != null && ev.getExpiryDate().before(new Date()))) {
                log.warn("OTP invalid/expired for seller id={}, skipping", seller.getId());
                return; // drop silently; client will see no new withdrawal appear
            }
            // Mark OTP used now (idempotency)
            ev.setUsed(true);
            emailVerificationRepository.save(ev);
        } catch (Exception ex) {
            log.error("Error validating OTP for seller id={}: {}", seller.getId(), ex.getMessage(), ex);
            throw ex; // requeue to retry
        }

        // Validate bank info belongs to seller
        SellerBankInfo bankInfo = entityManager.find(SellerBankInfo.class, msg.bankInfoId());
        if (bankInfo == null || bankInfo.isDelete() || bankInfo.getUser() == null || !bankInfo.getUser().getId().equals(seller.getId())) {
            log.warn("BankInfo id={} invalid for seller id={}, skipping", msg.bankInfoId(), seller.getId());
            return;
        }

        // BUSINESS RULE: Check for open complaints - seller cannot withdraw if there are any open complaints
        // Open complaints: NEW, IN_PROGRESS, PENDING_CONFIRMATION, ESCALATED
        try {
            List<Complaint> openComplaints = complaintRepository.findBySeller(seller);
            if (openComplaints != null && !openComplaints.isEmpty()) {
                long openCount = openComplaints.stream()
                        .filter(c -> c.getStatus() == Complaint.ComplaintStatus.NEW ||
                                     c.getStatus() == Complaint.ComplaintStatus.IN_PROGRESS ||
                                     c.getStatus() == Complaint.ComplaintStatus.PENDING_CONFIRMATION ||
                                     c.getStatus() == Complaint.ComplaintStatus.ESCALATED)
                        .count();

                if (openCount > 0) {
                    log.warn("Seller id={} has {} open complaint(s), withdrawal blocked", seller.getId(), openCount);
                    notificationService.createNotificationForUser(
                            seller.getId(),
                            "Withdrawal Blocked - Open Complaint(s)",
                            "Your withdrawal request cannot be processed because you have " + openCount +
                            " open complaint(s). Please resolve all complaints before requesting withdrawal."
                    );
                    return;
                }
            }
        } catch (Exception ex) {
            log.error("Error checking complaints for seller id={}: {}", seller.getId(), ex.getMessage(), ex);
            // In case of error checking complaints, block withdrawal for safety
            notificationService.createNotificationForUser(
                    seller.getId(),
                    "Withdrawal Failed",
                    "Unable to process withdrawal at this time. Please contact support."
            );
            return;
        }

        // Atomic coin deduction to avoid race/negative balance
        int updated = userRepository.deductCoinsIfEnough(seller.getId(), msg.amount());
        if (updated == 0) {
            log.warn("Insufficient balance for seller id={} amount={}, skipping", seller.getId(), msg.amount());
            notificationService.createNotificationForUser(seller.getId(), "Withdrawal failed", "Insufficient balance for withdrawal of " + msg.amount() + " VND.");
            return;
        }

        // Create withdrawal pending
        Withdrawal wd = new Withdrawal();
        wd.setSeller(seller);
        wd.setBankInfo(bankInfo);
        wd.setAmount(msg.amount());
        wd.setStatus("Pending");
        // Display fields
        String bankName = msg.bankName() != null ? msg.bankName() : bankInfo.getBankName();
        String accountNumber = msg.accountNumber() != null ? msg.accountNumber() : bankInfo.getAccountNumber();
        String accountName = msg.accountName() != null ? msg.accountName() : bankInfo.getAccountHolder();
        String branch = msg.branch() != null ? msg.branch() : bankInfo.getBranch();
        wd.setBankName(bankName);
        wd.setAccountNumber(accountNumber);
        wd.setAccountName(accountName);
        wd.setBranch(branch);
        wd.setCreatedAt(new Date());
        wd.setUpdatedAt(new Date());
        wd.setCreatedBy(seller.getId());

        withdrawalRepository.save(wd);

        // Notify seller and admins
        try {
            notificationService.createNotificationForUser(seller.getId(), "Withdrawal Request", "Your withdrawal request of " + msg.amount() + " VND has been submitted and is pending approval.");
            notificationService.createNotificationForRole("ADMIN", "Withdrawal request pending approval", "New withdrawal request of " + String.format("%,d VND", msg.amount()) + " by " + (seller.getFullName() != null ? seller.getFullName() : seller.getEmail()) + " (user id: " + seller.getId() + ") is pending approval.");
        } catch (Exception ignored) {}

        // Send email confirmation
        try {
            String subject = "[MMOMarket] Withdrawal Request Submitted";
            String content = com.mmo.util.EmailTemplate.withdrawalRequestEmail(
                    seller.getFullName(),
                    String.format("%,d VND", msg.amount()),
                    bankName + " - " + accountNumber,
                    new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date())
            );
            if (seller.getEmail() != null && !seller.getEmail().isBlank()) {
                emailService.sendEmailAsync(seller.getEmail(), subject, content);
            }
        } catch (Exception ex) {
            log.warn("Failed sending email for withdrawal create seller id={}: {}", seller.getId(), ex.getMessage());
        }

        log.info("Withdrawal created id={} for seller id={} amount={}", wd.getId(), seller.getId(), wd.getAmount());
    }
}

