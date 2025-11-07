package com.mmo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class RecaptchaService {

    @Value("${recaptcha.secret.key:}")
    private String secretKey;

    @Value("${recaptcha.verify.url:https://www.google.com/recaptcha/api/siteverify}")
    private String verifyUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Verify reCAPTCHA response token
     * @param recaptchaResponse The token from client
     * @return true if valid, false otherwise
     */
    public boolean verifyRecaptcha(String recaptchaResponse) {
        if (recaptchaResponse == null || recaptchaResponse.trim().isEmpty()) {
            return false;
        }

        if (secretKey == null || secretKey.trim().isEmpty()) {
            // If no secret key configured, skip verification (for development)
            System.err.println("WARNING: reCAPTCHA secret key is not configured. Skipping verification.");
            return true;
        }

        try {
            String url = String.format("%s?secret=%s&response=%s", verifyUrl, secretKey, recaptchaResponse);
            String response = restTemplate.postForObject(url, null, String.class);

            JsonNode jsonNode = objectMapper.readTree(response);
            return jsonNode.has("success") && jsonNode.get("success").asBoolean();
        } catch (Exception e) {
            System.err.println("Error verifying reCAPTCHA: " + e.getMessage());
            return false;
        }
    }
}

