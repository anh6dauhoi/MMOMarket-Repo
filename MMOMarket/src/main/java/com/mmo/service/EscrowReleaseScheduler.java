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
                // Check if there is an open complaint linked to this transaction
                // Open complaints: NEW, IN_PROGRESS, PENDING_CONFIRMATION, or ESCALATED
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

                // If no open complaint exists (including RESOLVED, CLOSED_BY_ADMIN, CANCELLED, or NO complaint at all)
                // and escrow period has passed -> release money to seller
                releaseMoneyToSeller(tx);

            } catch (Exception ex) {
                log.error("Failed to release tx {}: {}", tx.getId(), ex.getMessage(), ex);
            }
        }
    }

    /**
     * Helper method to release money to seller
     * Business rule: After 3 days with no open complaint, money is automatically released to seller
     */
    private void releaseMoneyToSeller(Transaction tx) {
        tx.setStatus("COMPLETED");
        transactionRepository.save(tx);

        if (tx.getSeller() != null && tx.getCoinSeller() != null && tx.getCoinSeller() > 0) {
            userRepository.addCoins(tx.getSeller().getId(), tx.getCoinSeller());
            log.info("Released {} coins to seller #{} for transaction #{}",
                    tx.getCoinSeller(), tx.getSeller().getId(), tx.getId());

            try {
                notificationService.createNotificationForUser(
                        tx.getSeller().getId(),
                        "Payout received",
                        "Your sale has been released from escrow. You received " +
                        String.format("%,d", tx.getCoinSeller()) + " coins. Transaction #" + tx.getId()
                );
            } catch (Exception e) {
                log.error("Failed to notify seller for tx {}", tx.getId(), e);
            }
        }
    }

    /**
     * Auto-resolve complaints that are in PENDING_CONFIRMATION status for more than 3 days
     * If customer doesn't respond within 3 days, automatically accept seller's solution
     * Run every hour
     */
    @Transactional
    @Scheduled(cron = "0 0 * * * *")
//    @Scheduled(cron = "0 */1 * * * *") // For testing: run every minute
    public void autoResolveExpiredPendingComplaints() {
        try {
            // Calculate date 3 days ago
            long threeDaysInMillis = 3L * 24 * 60 * 60 * 1000;
            Date threeDaysAgo = new Date(System.currentTimeMillis() - threeDaysInMillis);

            // Find all complaints with PENDING_CONFIRMATION status that were updated more than 3 days ago
            List<Complaint> expiredComplaints = complaintRepository.findByStatusAndUpdatedAtBefore(
                    Complaint.ComplaintStatus.PENDING_CONFIRMATION,
                    threeDaysAgo
            );

            if (expiredComplaints.isEmpty()) {
                return;
            }

            log.info("Auto-resolve scan found {} expired PENDING_CONFIRMATION complaints", expiredComplaints.size());

            for (Complaint complaint : expiredComplaints) {
                try {
                    // Auto-resolve the complaint
                    complaint.setStatus(Complaint.ComplaintStatus.RESOLVED);
                    complaint.setUpdatedAt(new Date());
                    complaintRepository.save(complaint);

                    log.info("Auto-resolved complaint #{} (customer did not respond within 3 days)", complaint.getId());

                    // Notify customer
                    try {
                        if (complaint.getCustomer() != null) {
                            notificationService.createNotificationForUser(
                                    complaint.getCustomer().getId(),
                                    "Complaint Auto-Resolved",
                                    "Complaint #" + complaint.getId() + " has been automatically resolved as you did not respond within 3 days. The seller's solution has been accepted."
                            );
                        }
                    } catch (Exception e) {
                        log.error("Failed to notify customer for complaint #{}", complaint.getId(), e);
                    }

                    // Notify seller
                    try {
                        if (complaint.getSeller() != null) {
                            notificationService.createNotificationForUser(
                                    complaint.getSeller().getId(),
                                    "Complaint Auto-Resolved",
                                    "Complaint #" + complaint.getId() + " has been automatically resolved as the customer did not respond within 3 days."
                            );
                        }
                    } catch (Exception e) {
                        log.error("Failed to notify seller for complaint #{}", complaint.getId(), e);
                    }

                } catch (Exception ex) {
                    log.error("Failed to auto-resolve complaint #{}: {}", complaint.getId(), ex.getMessage(), ex);
                }
            }
        } catch (Exception ex) {
            log.error("Error in autoResolveExpiredPendingComplaints: {}", ex.getMessage(), ex);
        }
    }
}

