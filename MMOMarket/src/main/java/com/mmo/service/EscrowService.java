package com.mmo.service;

import com.mmo.entity.Transaction;
import com.mmo.entity.User;
import com.mmo.entity.Complaint;
import com.mmo.repository.TransactionRepository;
import com.mmo.repository.UserRepository;
import com.mmo.repository.ComplaintRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
public class EscrowService {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ComplaintRepository complaintRepository;

    /**
     * Chạy mỗi giờ để tự động release escrow
     * Cron: 0 0 * * * * = Chạy vào đầu mỗi giờ
     */
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void autoReleaseEscrow() {
        Date now = new Date();

        try {
            // Tìm các transaction đã đến hạn release
            List<Transaction> transactions = transactionRepository
                    .findByStatusAndEscrowReleaseDateBeforeAndIsDeleteFalse("Held", now);

            System.out.println("=== AUTO RELEASE ESCROW ===");
            System.out.println("Found " + transactions.size() + " transactions ready for release");

            int successCount = 0;
            int skipCount = 0;

            for (Transaction transaction : transactions) {
                // Kiểm tra có complaint đang mở không
                List<Complaint> openComplaints = complaintRepository
                        .findByTransactionIdAndStatusAndIsDeleteFalse(
                                transaction.getId(), "Open");

                if (openComplaints.isEmpty()) {
                    // Không có complaint → Release escrow

                    try {
                        // 1. Update transaction status
                        transaction.setStatus("Completed");
                        transactionRepository.save(transaction);

                        // 2. Cộng tiền cho seller
                        User seller = userRepository.findById(transaction.getSellerId()).orElse(null);
                        if (seller != null) {
                            long amountToRelease = transaction.getAmount() - transaction.getCommission();
                            seller.setCoins(seller.getCoins() + amountToRelease);
                            userRepository.save(seller);

                            System.out.println("✅ Released " + amountToRelease +
                                    " coins to seller ID: " + seller.getId() +
                                    " (Transaction #" + transaction.getId() + ")");

                            successCount++;
                        }
                    } catch (Exception e) {
                        System.err.println("❌ Error releasing transaction #" + transaction.getId() + ": " + e.getMessage());
                    }
                } else {
                    System.out.println("⚠️ Skipped transaction #" + transaction.getId() +
                            " - Has " + openComplaints.size() + " open complaint(s)");
                    skipCount++;
                }
            }

            System.out.println("=== ESCROW RELEASE SUMMARY ===");
            System.out.println("Success: " + successCount);
            System.out.println("Skipped: " + skipCount);
            System.out.println("==============================");

        } catch (Exception e) {
            System.err.println("❌ Critical error in autoReleaseEscrow: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Manual release escrow (for admin)
     */
    @Transactional
    public boolean manualReleaseEscrow(Long transactionId, Long adminId) {
        try {
            Transaction transaction = transactionRepository.findById(transactionId).orElse(null);

            if (transaction == null || !"Held".equals(transaction.getStatus())) {
                return false;
            }

            // Update transaction
            transaction.setStatus("Completed");
            transactionRepository.save(transaction);

            // Cộng tiền cho seller
            User seller = userRepository.findById(transaction.getSellerId()).orElse(null);
            if (seller != null) {
                long amountToRelease = transaction.getAmount() - transaction.getCommission();
                seller.setCoins(seller.getCoins() + amountToRelease);
                userRepository.save(seller);
            }

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}