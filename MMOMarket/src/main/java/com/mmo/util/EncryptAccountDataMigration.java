package com.mmo.util;

import com.mmo.entity.ProductVariantAccount;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * One-time migration script to encrypt existing account data in database
 * Run this ONCE after deploying the encryption feature
 *
 * To enable: set application property: migration.encrypt-accounts=true
 * After migration completes, disable this by setting: migration.encrypt-accounts=false
 */
@Component
@ConditionalOnProperty(name = "migration.encrypt-accounts", havingValue = "true")
public class EncryptAccountDataMigration implements CommandLineRunner {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        System.out.println("========================================");
        System.out.println("STARTING ACCOUNT DATA ENCRYPTION MIGRATION");
        System.out.println("========================================");

        try {
            // Load all accounts in batches
            int batchSize = 100;
            int offset = 0;
            int totalProcessed = 0;
            int totalEncrypted = 0;
            int totalSkipped = 0;
            int totalErrors = 0;

            while (true) {
                List<ProductVariantAccount> accounts = entityManager.createQuery(
                    "SELECT a FROM ProductVariantAccount a WHERE a.isDelete = false",
                    ProductVariantAccount.class)
                    .setFirstResult(offset)
                    .setMaxResults(batchSize)
                    .getResultList();

                if (accounts.isEmpty()) {
                    break;
                }

                System.out.println("Processing batch: " + (offset / batchSize + 1) + " (" + accounts.size() + " accounts)");

                for (ProductVariantAccount account : accounts) {
                    totalProcessed++;

                    try {
                        String currentData = account.getAccountData();

                        // Check if already encrypted
                        if (EncryptionUtil.isEncrypted(currentData)) {
                            totalSkipped++;
                            if (totalProcessed % 100 == 0) {
                                System.out.println("  Account #" + account.getId() + " already encrypted, skipping...");
                            }
                            continue;
                        }

                        // Encrypt the plain text data
                        String encrypted = EncryptionUtil.encrypt(currentData);
                        account.setAccountData(encrypted);
                        entityManager.merge(account);
                        totalEncrypted++;

                        if (totalProcessed % 100 == 0) {
                            System.out.println("  Account #" + account.getId() + " encrypted successfully");
                        }

                    } catch (Exception e) {
                        totalErrors++;
                        System.err.println("  ERROR encrypting account #" + account.getId() + ": " + e.getMessage());
                        // Continue with next account
                    }
                }

                // Flush to database
                entityManager.flush();
                entityManager.clear();

                offset += batchSize;

                // Progress report
                System.out.println("Progress: " + totalProcessed + " processed, " +
                                 totalEncrypted + " encrypted, " +
                                 totalSkipped + " skipped, " +
                                 totalErrors + " errors");
            }

            System.out.println("\n========================================");
            System.out.println("MIGRATION COMPLETED SUCCESSFULLY");
            System.out.println("========================================");
            System.out.println("Total accounts processed: " + totalProcessed);
            System.out.println("Total encrypted: " + totalEncrypted);
            System.out.println("Total already encrypted (skipped): " + totalSkipped);
            System.out.println("Total errors: " + totalErrors);
            System.out.println("\nIMPORTANT: Please disable this migration by setting:");
            System.out.println("  migration.encrypt-accounts=false");
            System.out.println("========================================\n");

        } catch (Exception e) {
            System.err.println("\n========================================");
            System.err.println("MIGRATION FAILED");
            System.err.println("========================================");
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.err.println("========================================\n");
            throw e;
        }
    }
}

