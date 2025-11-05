package com.mmo.service;

import com.mmo.entity.Complaint;
import com.mmo.entity.Transaction;
import com.mmo.repository.ComplaintRepository;
import com.mmo.repository.TransactionRepository;
import com.mmo.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Component
public class EscrowReleaseScheduler {
    private static final Logger log = LoggerFactory.getLogger(EscrowReleaseScheduler.class);

    private final TransactionRepository transactionRepository;
    private final ComplaintRepository complaintRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public EscrowReleaseScheduler(TransactionRepository transactionRepository,
                                  ComplaintRepository complaintRepository,
                                  UserRepository userRepository,
                                  NotificationService notificationService) {
        this.transactionRepository = transactionRepository;
        this.complaintRepository = complaintRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    // Run hourly
    @Transactional
    @Scheduled(cron = "0 0 * * * *")
//    @Scheduled(cron = "0 */1 * * * *")
    public void releaseEscrow() {
        Date now = new Date();
        List<Transaction> due = transactionRepository.findByStatusAndEscrowReleaseDateBefore("ESCROW", now);
        if (due.isEmpty()) return;
        log.info("Escrow release scan found {} transactions due", due.size());
        for (Transaction tx : due) {
            try {
                // Skip if there is an open complaint linked to this transaction
                // Check for complaints with status: NEW, IN_PROGRESS, PENDING_CONFIRMATION, or ESCALATED
                boolean hasOpenComplaint = false;
                try {
                    hasOpenComplaint = complaintRepository.existsByTransactionIdAndStatus(tx.getId(), Complaint.ComplaintStatus.NEW)
                            || complaintRepository.existsByTransactionIdAndStatus(tx.getId(), Complaint.ComplaintStatus.IN_PROGRESS)
                            || complaintRepository.existsByTransactionIdAndStatus(tx.getId(), Complaint.ComplaintStatus.PENDING_CONFIRMATION)
                            || complaintRepository.existsByTransactionIdAndStatus(tx.getId(), Complaint.ComplaintStatus.ESCALATED);
                } catch (Exception ignored) {}
                if (hasOpenComplaint) {
                    log.info("Skipping tx {} due to open complaint", tx.getId());
                    continue;
                }
                // Mark completed and credit seller
                tx.setStatus("COMPLETED");
                transactionRepository.save(tx);
                if (tx.getSeller() != null && tx.getCoinSeller() != null && tx.getCoinSeller() > 0) {
                    userRepository.addCoins(tx.getSeller().getId(), tx.getCoinSeller());
                    try {
                        notificationService.createNotificationForUser(tx.getSeller().getId(), "Payout received",
                                "Your sale has been released from escrow. You received " + String.format("%,d", tx.getCoinSeller()) + " coins.");
                    } catch (Exception ignored) {}
                }
            } catch (Exception ex) {
                log.error("Failed to release tx {}: {}", tx.getId(), ex.getMessage(), ex);
            }
        }
    }
}

