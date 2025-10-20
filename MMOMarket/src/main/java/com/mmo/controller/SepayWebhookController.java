package com.mmo.controller;

import com.mmo.dto.SepayWebhookPayload;
import com.mmo.entity.User;
import com.mmo.service.SepayWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    @PostMapping(consumes = "application/json")
    public ResponseEntity<Map<String, Object>> receiveDepositWebhook(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody SepayWebhookPayload payload) {
        // Kiểm tra API Key
        if (authorization == null || !authorization.startsWith("Apikey ")) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Thiếu hoặc sai định dạng header Authorization"));
        }
        String apiKey = authorization.substring(7).trim();
        if (!sepayApiKey.equals(apiKey)) {
            return ResponseEntity.status(403).body(Map.of("success", false, "error", "API Key không hợp lệ"));
        }
        try {
            String error = null;
            // Xác thực transferType
            if (!"in".equalsIgnoreCase(payload.getTransferType())) {
                error = "Giao dịch không phải tiền vào";
            } else {
                // Tìm user theo depositCode
                String depositCode = payload.getCode();
                User user = null;
                try {
                    user = sepayWebhookService.findUserByDepositCode(depositCode);
                } catch (Exception e) {
                    error = "Lỗi khi truy vấn user: " + e.getMessage();
                }
                if (user == null) {
                    error = "Không tìm thấy user với depositCode: " + depositCode;
                } else {
                    // Chống trùng lặp giao dịch
                    Long sepayTransactionId = payload.getId();
                    if (sepayWebhookService.isTransactionProcessed(sepayTransactionId)) {
                        error = "Giao dịch đã xử lý: " + sepayTransactionId;
                    } else {
                        // Xử lý logic nạp coin
                        try {
                            sepayWebhookService.processSepayDepositWebhook(payload);
                        } catch (Exception e) {
                            error = "Lỗi xử lý nạp coin: " + e.getMessage();
                        }
                    }
                }
            }
            if (error != null) {
                return ResponseEntity.ok(Map.of("success", false, "error", error));
            }
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "error", "Lỗi hệ thống: " + e.getMessage()));
        }
        // Trả về thành công nếu không có lỗi
        return ResponseEntity.ok(Map.of("success", true));
    }
}
