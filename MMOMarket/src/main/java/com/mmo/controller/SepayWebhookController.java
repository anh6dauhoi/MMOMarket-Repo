package com.mmo.controller;

import com.mmo.dto.SepayWebhookPayload;
import com.mmo.service.SepayWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/webhook/sepay")
@RequiredArgsConstructor
public class SepayWebhookController {
    private final SepayWebhookService sepayWebhookService;

    @Value("${SEPAY_WEBHOOK_APIKEY}")
    private String sepayApiKey;

    // Response constants according to SePay standard
    private static final Map<String, Object> SUCCESS_RESPONSE = Map.of("success", true);

    private static Map<String, Object> errorResponse(String message) {
        return Map.of("success", false, "error", message);
    }

    @PostMapping(consumes = "application/json")
    public ResponseEntity<Map<String, Object>> receiveDepositWebhook(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody SepayWebhookPayload payload) {

        log.info("[SePay Webhook] Received request: sepayId={}, transferType={}, amount={}, code={}",
                payload.getId(), payload.getTransferType(), payload.getTransferAmount(), payload.getCode());

        // ========== STEP 1: AUTHENTICATE API KEY ==========
        // This is the only security layer SePay provides
        if (authorization == null || !authorization.startsWith("Apikey ")) {
            log.warn("[SePay Webhook] Missing or invalid Authorization header format");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorResponse("Missing or invalid Authorization header format"));
        }

        String apiKey = authorization.substring(7).trim();
        if (!sepayApiKey.equals(apiKey)) {
            log.error("[SePay Webhook] Invalid API Key");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(errorResponse("Invalid API Key"));
        }

        // ========== STEP 2: PROCESS LOGIC AND PREVENT DUPLICATION ==========
        try {
            // Call the processing service - the service will automatically check:
            // - transferType = "in"
            // - User exists
            // - Duplicate transaction
            // - Deposit coins + send notification
            sepayWebhookService.processSepayDepositWebhook(payload);

            // ========== STEP 3: RESPOND WITH SUCCESS ==========
            // According to SePay documentation: HTTP 200/201 + JSON {"success": true}
            // SePay will NOT retry if this response is received
            log.info("[SePay Webhook] Successfully processed sepayId={}", payload.getId());
            return ResponseEntity.status(HttpStatus.OK).body(SUCCESS_RESPONSE);

        } catch (IllegalArgumentException ex) {
            // Business logic error (e.g., not "in", user not found, duplicate)
            // Return 200 + success:false so SePay does NOT retry (as retrying would result in the same error)
            log.warn("[SePay Webhook] Business logic error for sepayId={}: {}", payload.getId(), ex.getMessage());
            return ResponseEntity.ok(errorResponse(ex.getMessage()));

        } catch (Exception ex) {
            // System error (DB error, network timeout, etc.)
            // Return 500 so SePay can RETRY (it might succeed next time)
            log.error("[SePay Webhook] System error while processing sepayId={}: {}", payload.getId(), ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse("Internal system error, please try again later"));
        }
    }
}
