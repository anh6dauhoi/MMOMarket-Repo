package com.mmo.util;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM Encryption utility for securing sensitive account data
 * Uses AES-256-GCM for authenticated encryption
 */
public class EncryptionUtil {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128; // bits
    private static final int GCM_IV_LENGTH = 12; // bytes
    private static final int AES_KEY_SIZE = 256; // bits

    // Secret key - In production, this should be loaded from environment variable or key management service
    // DO NOT hardcode in production! Use: System.getenv("ENCRYPTION_KEY")
    private static final String SECRET_KEY_BASE64 = getSecretKey();

    /**
     * Get encryption key from environment variable or generate a default one for development
     * In production, MUST use environment variable: ENCRYPTION_KEY
     */
    private static String getSecretKey() {
        String key = System.getenv("ENCRYPTION_KEY");
        if (key != null && !key.isEmpty()) {
            return key;
        }

        // Development only - generate a consistent key based on a seed
        // WARNING: In production, you MUST set ENCRYPTION_KEY environment variable
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(AES_KEY_SIZE, new SecureRandom("MMOMarket2024".getBytes()));
            SecretKey secretKey = keyGen.generateKey();
            return Base64.getEncoder().encodeToString(secretKey.getEncoded());
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate encryption key", e);
        }
    }

    /**
     * Encrypt sensitive data using AES-GCM
     * @param plainText The data to encrypt
     * @return Base64 encoded encrypted data with IV prepended
     */
    public static String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }

        try {
            // Decode the secret key
            byte[] keyBytes = Base64.getDecoder().decode(SECRET_KEY_BASE64);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");

            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, parameterSpec);

            // Encrypt the data
            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // Combine IV and encrypted data: [IV][Encrypted Data][Auth Tag]
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + encryptedBytes.length);
            byteBuffer.put(iv);
            byteBuffer.put(encryptedBytes);

            // Encode to Base64 for storage
            return Base64.getEncoder().encodeToString(byteBuffer.array());

        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * Decrypt sensitive data using AES-GCM
     * @param encryptedText Base64 encoded encrypted data with IV prepended
     * @return Decrypted plain text
     */
    public static String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }

        try {
            // Decode from Base64
            byte[] decodedBytes = Base64.getDecoder().decode(encryptedText);

            // Extract IV and encrypted data
            ByteBuffer byteBuffer = ByteBuffer.wrap(decodedBytes);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            byte[] encryptedBytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(encryptedBytes);

            // Decode the secret key
            byte[] keyBytes = Base64.getDecoder().decode(SECRET_KEY_BASE64);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);

            // Decrypt the data
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);

        } catch (Exception e) {
            throw new RuntimeException("Decryption failed - data may be corrupted or key is incorrect", e);
        }
    }

    /**
     * Check if a string appears to be encrypted (is valid Base64 and has expected structure)
     */
    public static boolean isEncrypted(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(text);
            // Encrypted data should have at least IV length + some encrypted bytes
            return decoded.length > GCM_IV_LENGTH;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Generate a new encryption key (for initial setup)
     * This should be run once and the key stored securely
     */
    public static String generateNewKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(AES_KEY_SIZE);
            SecretKey secretKey = keyGen.generateKey();
            return Base64.getEncoder().encodeToString(secretKey.getEncoded());
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate key", e);
        }
    }

    // For testing purposes
    public static void main(String[] args) {
        // Example usage
        String originalText = "Username: admin\nPassword: secret123\nEmail: test@example.com";

        System.out.println("Original: " + originalText);

        String encrypted = encrypt(originalText);
        System.out.println("\nEncrypted (Base64): " + encrypted);
        System.out.println("Encrypted length: " + encrypted.length());

        String decrypted = decrypt(encrypted);
        System.out.println("\nDecrypted: " + decrypted);

        System.out.println("\nMatch: " + originalText.equals(decrypted));

        // Generate a new key for production use
        System.out.println("\n=== Generate New Key for Production ===");
        System.out.println("Set this as environment variable ENCRYPTION_KEY:");
        System.out.println(generateNewKey());
    }
}

