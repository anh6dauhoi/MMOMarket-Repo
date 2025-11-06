package com.mmo.service;

import com.mmo.entity.Complaint;
import com.mmo.entity.User;
import com.mmo.repository.ComplaintRepository;
import com.mmo.repository.UserRepository;
import com.mmo.util.EmailTemplate;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@Service
public class ComplaintService {
    private static final Logger log = LoggerFactory.getLogger(ComplaintService.class);

    @Autowired
    private ComplaintRepository complaintRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private NotificationService notificationService;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Customer cancels complaint (only in NEW status)
     */
    @Transactional
    public void cancelComplaint(Long complaintId, Long customerId) throws Exception {
        Complaint complaint = complaintRepository.findById(complaintId)
            .orElseThrow(() -> new Exception("Complaint not found"));

        // Validate ownership
        if (!complaint.getCustomer().getId().equals(customerId)) {
            throw new Exception("Unauthorized: You are not the owner of this complaint");
        }

        // Validate status
        if (complaint.getStatus() != Complaint.ComplaintStatus.NEW) {
            throw new Exception("Can only cancel complaints with NEW status");
        }

        // Update status
        complaint.setStatus(Complaint.ComplaintStatus.CANCELLED);
        complaint.setUpdatedAt(new Date());
        complaintRepository.save(complaint);

        // Notify seller
        try {
            String notifTitle = "Complaint Cancelled by Customer";
            String notifMessage = "The customer has cancelled complaint #" + complaint.getId() +
                " for transaction #" + complaint.getTransactionId();
            notificationService.createNotificationForUser(complaint.getSeller().getId(), notifTitle, notifMessage);
        } catch (Exception e) {
            log.error("Failed to create notification for seller", e);
        }

        log.info("Complaint #{} cancelled by customer #{}", complaintId, customerId);
    }

    /**
     * Customer or Seller requests admin support (escalate complaint)
     */
    @Transactional
    public void escalateToAdmin(Long complaintId, Long userId, String reason, boolean isSeller) throws Exception {
        Complaint complaint = complaintRepository.findById(complaintId)
            .orElseThrow(() -> new Exception("Complaint not found"));

        // Validate ownership
        if (isSeller) {
            if (!complaint.getSeller().getId().equals(userId)) {
                throw new Exception("Unauthorized: You are not the seller of this complaint");
            }
        } else {
            if (!complaint.getCustomer().getId().equals(userId)) {
                throw new Exception("Unauthorized: You are not the customer of this complaint");
            }
        }

        // Validate status - can escalate from IN_PROGRESS or PENDING_CONFIRMATION
        if (complaint.getStatus() != Complaint.ComplaintStatus.IN_PROGRESS &&
            complaint.getStatus() != Complaint.ComplaintStatus.PENDING_CONFIRMATION) {
            throw new Exception("Can only escalate complaints with IN_PROGRESS or PENDING_CONFIRMATION status");
        }

        // Validate reason
        if (reason == null || reason.trim().isEmpty()) {
            throw new Exception("Reason is required");
        }

        if (reason.trim().length() < 20) {
            throw new Exception("Reason must be at least 20 characters");
        }

        // Get first admin to assign
        List<User> admins = userRepository.findByRoleIgnoreCaseAndIsDelete("ADMIN", false);
        if (admins.isEmpty()) {
            throw new Exception("No admin available to handle this complaint");
        }
        User assignedAdmin = admins.get(0); // Simple assignment to first admin

        // Update complaint
        complaint.setStatus(Complaint.ComplaintStatus.ESCALATED);
        complaint.setAdminHandler(assignedAdmin);
        complaint.setUpdatedAt(new Date());

        // Save escalation reason with metadata
        String escalationNote = String.format(
            "Escalated by %s on %s:\n%s",
            isSeller ? "Seller" : "Customer",
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()),
            reason.trim()
        );
        complaint.setEscalationReason(escalationNote);

        complaintRepository.save(complaint);

        // Send email to assigned admin
        try {
            String requesterType = isSeller ? "seller" : "customer";
            String requesterName = isSeller
                ? (complaint.getSeller().getFullName() != null ? complaint.getSeller().getFullName() : complaint.getSeller().getEmail())
                : (complaint.getCustomer().getFullName() != null ? complaint.getCustomer().getFullName() : complaint.getCustomer().getEmail());

            String productName = "N/A";
            if (complaint.getTransactionId() != null) {
                try {
                    var tx = entityManager.find(com.mmo.entity.Transaction.class, complaint.getTransactionId());
                    if (tx != null && tx.getProduct() != null) {
                        productName = tx.getProduct().getName();
                    }
                } catch (Exception e) {
                    log.warn("Failed to get product name", e);
                }
            }

            String subject = String.format("[MMOMarket] Complaint #%d Escalated - Admin Action Required", complaint.getId());
            String emailContent = EmailTemplate.complaintEscalatedToAdminEmail(
                assignedAdmin.getFullName() != null ? assignedAdmin.getFullName() : "Admin",
                requesterName,
                requesterType,
                productName,
                complaint.getTransactionId() != null ? complaint.getTransactionId().toString() : "N/A",
                formatComplaintType(complaint.getComplaintType()),
                complaint.getId().toString(),
                reason.trim(),
                new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date())
            );

            emailService.sendEmailAsync(assignedAdmin.getEmail(), subject, emailContent);
        } catch (Exception e) {
            log.error("Failed to send escalation email to admin", e);
        }

        // Notify admin (assigned handler)
        try {
            String requesterType = isSeller ? "seller" : "customer";
            String requesterName = isSeller
                ? (complaint.getSeller().getFullName() != null ? complaint.getSeller().getFullName() : complaint.getSeller().getEmail())
                : (complaint.getCustomer().getFullName() != null ? complaint.getCustomer().getFullName() : complaint.getCustomer().getEmail());

            String adminNotifTitle = "New Complaint Escalation - Action Required";
            String adminNotifMessage = String.format(
                "Complaint #%d has been escalated to you by %s (%s). Transaction #%s - %s. Please review and take action.",
                complaint.getId(),
                requesterType,
                requesterName,
                complaint.getTransactionId() != null ? complaint.getTransactionId() : "N/A",
                formatComplaintType(complaint.getComplaintType())
            );

            notificationService.createNotificationForUser(assignedAdmin.getId(), adminNotifTitle, adminNotifMessage);
        } catch (Exception e) {
            log.error("Failed to create notification for admin", e);
        }

        // Notify both customer and seller
        try {
            String notifTitle = "Complaint Escalated to Admin";
            String notifMessage = String.format(
                "Complaint #%d has been escalated to admin team for review. Admin will respond within 3-5 business days.",
                complaint.getId()
            );

            notificationService.createNotificationForUser(complaint.getCustomer().getId(), notifTitle, notifMessage);
            notificationService.createNotificationForUser(complaint.getSeller().getId(), notifTitle, notifMessage);
        } catch (Exception e) {
            log.error("Failed to create escalation notifications", e);
        }

        log.info("Complaint #{} escalated to admin by {} (user #{})", complaintId, isSeller ? "seller" : "customer", userId);
    }

    /**
     * Customer confirms resolution (accepts seller's solution)
     */
    @Transactional
    public void confirmResolution(Long complaintId, Long customerId, boolean accept) throws Exception {
        Complaint complaint = complaintRepository.findById(complaintId)
            .orElseThrow(() -> new Exception("Complaint not found"));

        // Validate ownership
        if (!complaint.getCustomer().getId().equals(customerId)) {
            throw new Exception("Unauthorized: You are not the owner of this complaint");
        }

        // Validate status
        if (complaint.getStatus() != Complaint.ComplaintStatus.PENDING_CONFIRMATION) {
            throw new Exception("Can only confirm complaints with PENDING_CONFIRMATION status");
        }

        if (accept) {
            // Customer accepts solution - mark as RESOLVED
            complaint.setStatus(Complaint.ComplaintStatus.RESOLVED);
            complaint.setUpdatedAt(new Date());
            complaintRepository.save(complaint);

            // Notify seller
            try {
                String notifTitle = "Complaint Resolved";
                String notifMessage = "The customer has accepted your solution for complaint #" + complaint.getId();
                notificationService.createNotificationForUser(complaint.getSeller().getId(), notifTitle, notifMessage);
            } catch (Exception e) {
                log.error("Failed to create notification for seller", e);
            }

            log.info("Complaint #{} resolved - customer accepted solution", complaintId);
        } else {
            // Customer rejects solution - must provide reason and escalate
            throw new Exception("To reject the solution, please use 'Request Admin Support' with your reason");
        }
    }

    private String formatComplaintType(Complaint.ComplaintType type) {
        if (type == null) return "Unknown";
        return switch (type) {
            case ITEM_NOT_WORKING -> "Item Not Working";
            case ITEM_NOT_AS_DESCRIBED -> "Item Not As Described";
            case FRAUD_SUSPICION -> "Fraud Suspicion";
            case OTHER -> "Other";
        };
    }
}

