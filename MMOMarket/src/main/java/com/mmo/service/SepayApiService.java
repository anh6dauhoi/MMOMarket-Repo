package com.mmo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmo.entity.CoinDeposit;
import com.mmo.entity.User;
import com.mmo.repository.CoinDepositRepository;
import com.mmo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SepayApiService {

    private final CoinDepositRepository coinDepositRepository;
    private final UserRepository userRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${SEPAY_WEBHOOK_APIKEY}")
    private String sepayApiKey;

    private static final String SEPAY_API_URL = "https://my.sepay.vn/userapi/transactions/list";

    /**
     * Retry a failed deposit by checking Sepay API and updating status
     * @param depositId The CoinDeposit ID to retry
     * @return Success message or error
     */
    @Transactional
    public String retryFailedDeposit(Long depositId) {
        log.info("[Sepay Retry] Starting retry for depositId={}", depositId);

        // Find the deposit
        Optional<CoinDeposit> depositOpt = coinDepositRepository.findById(depositId);
        if (depositOpt.isEmpty()) {
            log.error("[Sepay Retry] Deposit not found: {}", depositId);
            return "Deposit not found";
        }

        CoinDeposit deposit = depositOpt.get();

        // Check if already completed
        if ("Completed".equalsIgnoreCase(deposit.getStatus()) ||
            "Approved".equalsIgnoreCase(deposit.getStatus())) {
            log.info("[Sepay Retry] Deposit already completed: {}", depositId);
            return "Deposit already completed";
        }

        // Get user
        User user = deposit.getUser();
        if (user == null) {
            log.error("[Sepay Retry] User not found for deposit: {}", depositId);
            return "User not found for this deposit";
        }

        // If we have sepayTransactionId, check it directly
        if (deposit.getSepayTransactionId() != null) {
            return checkAndUpdateBySepayId(deposit, user);
        }

        // Otherwise, check by reference code or content
        if (deposit.getSepayReferenceCode() != null && !deposit.getSepayReferenceCode().isBlank()) {
            return checkAndUpdateByReferenceCode(deposit, user);
        }

        // If no identifiers, check by deposit code in recent transactions
        return checkRecentTransactionsByDepositCode(deposit, user);
    }

    /**
     * Check Sepay transaction by Sepay transaction ID
     */
    private String checkAndUpdateBySepayId(CoinDeposit deposit, User user) {
        try {
            log.info("[Sepay Retry] Checking by sepayTransactionId: {}", deposit.getSepayTransactionId());

            // Call Sepay API
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + sepayApiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(headers);

            // Note: Sepay API might not support direct transaction lookup by ID
            // We'll search in recent transactions
            String url = SEPAY_API_URL + "?limit=100";

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode transactions = root.path("transactions");

                if (transactions.isArray()) {
                    for (JsonNode tx : transactions) {
                        Long txId = tx.path("id").asLong();
                        if (txId.equals(deposit.getSepayTransactionId())) {
                            // Found the transaction
                            return processFoundTransaction(deposit, user, tx);
                        }
                    }
                }
            }

            log.warn("[Sepay Retry] Transaction not found in Sepay API: {}", deposit.getSepayTransactionId());
            return "Transaction not found in Sepay records";

        } catch (Exception e) {
            log.error("[Sepay Retry] Error checking Sepay API: ", e);
            return "Error connecting to Sepay API: " + e.getMessage();
        }
    }

    /**
     * Check by reference code or deposit code
     */
    private String checkAndUpdateByReferenceCode(CoinDeposit deposit, User user) {
        try {
            log.info("[Sepay Retry] Checking by referenceCode: {}", deposit.getSepayReferenceCode());

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + sepayApiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            String url = SEPAY_API_URL + "?limit=100";

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode transactions = root.path("transactions");

                if (transactions.isArray()) {
                    for (JsonNode tx : transactions) {
                        String refCode = tx.path("reference_number").asText("");
                        String content = tx.path("transaction_content").asText("");
                        String depositCode = user.getDepositCode();

                        // Match by reference code or deposit code in content
                        if ((refCode.equals(deposit.getSepayReferenceCode())) ||
                            (depositCode != null && content.contains(depositCode))) {

                            String transferType = tx.path("transfer_type").asText("");
                            if ("in".equalsIgnoreCase(transferType)) {
                                return processFoundTransaction(deposit, user, tx);
                            }
                        }
                    }
                }
            }

            log.warn("[Sepay Retry] Transaction not found by reference code: {}", deposit.getSepayReferenceCode());
            return "Transaction not found in Sepay records";

        } catch (Exception e) {
            log.error("[Sepay Retry] Error checking Sepay API: ", e);
            return "Error connecting to Sepay API: " + e.getMessage();
        }
    }

    /**
     * Check recent transactions by user's deposit code
     */
    private String checkRecentTransactionsByDepositCode(CoinDeposit deposit, User user) {
        try {
            String depositCode = user.getDepositCode();
            if (depositCode == null || depositCode.isBlank()) {
                return "User deposit code not found";
            }

            log.info("[Sepay Retry] Checking by depositCode: {}", depositCode);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + sepayApiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            String url = SEPAY_API_URL + "?limit=100";

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode transactions = root.path("transactions");

                if (transactions.isArray()) {
                    for (JsonNode tx : transactions) {
                        String content = tx.path("transaction_content").asText("");
                        String transferType = tx.path("transfer_type").asText("");
                        Long amount = tx.path("amount_in").asLong(0);

                        // Match by deposit code in content and amount
                        if ("in".equalsIgnoreCase(transferType) &&
                            content.contains(depositCode) &&
                            amount.equals(deposit.getAmount())) {

                            // Check if not already processed
                            Long txId = tx.path("id").asLong();
                            if (!coinDepositRepository.existsBySepayTransactionId(txId)) {
                                return processFoundTransaction(deposit, user, tx);
                            }
                        }
                    }
                }
            }

            log.warn("[Sepay Retry] No matching transaction found for depositCode: {}", depositCode);
            return "No matching transaction found in Sepay records";

        } catch (Exception e) {
            log.error("[Sepay Retry] Error checking Sepay API: ", e);
            return "Error connecting to Sepay API: " + e.getMessage();
        }
    }

    /**
     * Process a found transaction and update the deposit
     */
    private String processFoundTransaction(CoinDeposit deposit, User user, JsonNode tx) {
        try {
            Long txId = tx.path("id").asLong();
            Long amount = tx.path("amount_in").asLong(0);
            String content = tx.path("transaction_content").asText("");
            String refCode = tx.path("reference_number").asText("");
            String gateway = tx.path("gateway").asText("Sepay");
            String txDate = tx.path("transaction_date").asText("");

            log.info("[Sepay Retry] Found matching transaction: txId={}, amount={}", txId, amount);

            // Update deposit
            deposit.setSepayTransactionId(txId);
            deposit.setSepayReferenceCode(refCode);
            deposit.setGateway(gateway);
            deposit.setStatus("Completed");

            if (deposit.getContent() == null || deposit.getContent().isBlank()) {
                deposit.setContent(content);
            }

            // Parse transaction date
            if (txDate != null && !txDate.isBlank()) {
                try {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    LocalDateTime ldt = LocalDateTime.parse(txDate, formatter);
                    deposit.setTransactionDate(java.sql.Timestamp.valueOf(ldt));
                } catch (Exception e) {
                    log.warn("[Sepay Retry] Could not parse transaction date: {}", txDate);
                }
            }

            // Update user balance
            Long oldBalance = user.getCoins();
            Long newBalance = oldBalance + amount;
            user.setCoins(newBalance);

            // Save changes
            coinDepositRepository.save(deposit);
            userRepository.save(user);

            log.info("[Sepay Retry] Successfully updated deposit {} and user balance from {} to {}",
                    deposit.getId(), oldBalance, newBalance);

            return "Success! Deposit completed and coins added to user account.";

        } catch (Exception e) {
            log.error("[Sepay Retry] Error processing found transaction: ", e);
            return "Error processing transaction: " + e.getMessage();
        }
    }
}

