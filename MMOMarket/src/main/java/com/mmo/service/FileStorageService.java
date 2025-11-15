package com.mmo.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Local file storage service
 * Stores all files in local filesystem
 */
@Service
@Slf4j
public class FileStorageService {


    /**
     * Upload file to local storage
     *
     * @param file MultipartFile to upload
     * @param folderType Type of folder (chat, complaints, products, blogs, withdrawals)
     * @param userId Optional user ID for subfolder organization
     * @return URL or path to access the uploaded file
     */
    public String uploadFile(MultipartFile file, String folderType, Long userId) throws IOException {
        log.info("=== File Upload Request ===");
        log.debug("File: {}", file.getOriginalFilename());
        log.debug("Size: {} bytes", file.getSize());
        log.debug("Type: {}", file.getContentType());
        log.debug("Folder: {}", folderType);
        log.debug("User ID: {}", userId);

        // Save to local storage
        String localUrl = uploadToLocal(file, folderType, userId);
        log.info("✓ File saved locally: {}", localUrl);

        return localUrl;
    }

    /**
     * Upload file with entity tracking (simplified for local storage only)
     *
     * @param file MultipartFile to upload
     * @param folderType Type of folder (chat, complaints, products, blogs, withdrawals)
     * @param userId Optional user ID for subfolder organization
     * @param entityType Entity type (Chat, Complaint, Product, Blog)
     * @param entityId Entity ID
     * @return Local URL
     */
    public String uploadFileWithEntityTracking(MultipartFile file, String folderType, Long userId, String entityType, Long entityId) throws IOException {
        log.info("=== File Upload Request with Entity Tracking ===");
        log.debug("Entity: {} ID: {}", entityType, entityId);

        // Save to local storage
        String localUrl = uploadToLocal(file, folderType, userId);
        log.info("✓ File saved locally: {}", localUrl);

        return localUrl;
    }

    /**
     * Upload file to local storage
     */
    private String uploadToLocal(MultipartFile file, String folderType, Long userId) throws IOException {
        log.debug("=== Local Upload ===");

        // Determine local directory based on folder type
        String baseDir = "uploads/" + folderType;
        if (userId != null && folderType.equals("products")) {
            baseDir = baseDir + "/" + userId;
        }

        log.debug("Base directory: {}", baseDir);

        // Create directory if not exists
        Path uploadDir = Paths.get(baseDir);
        if (!Files.exists(uploadDir)) {
            log.debug("Creating directory: {}", uploadDir);
            Files.createDirectories(uploadDir);
        }

        // Generate unique filename
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String uniqueFilename = System.currentTimeMillis() + "-" + UUID.randomUUID() + extension;

        log.debug("Generated filename: {}", uniqueFilename);

        // Save file
        Path filePath = uploadDir.resolve(uniqueFilename);
        Files.write(filePath, file.getBytes());

        String publicUrl = "/" + baseDir + "/" + uniqueFilename;
        log.info("✓ File saved locally: {}", publicUrl);

        return publicUrl;
    }



    /**
     * Delete local file
     */
    public void deleteFile(String fileUrl) {
        log.info("=== File Delete Request ===");
        log.debug("File URL: {}", fileUrl);

        try {
            // Local file only
            Path filePath = Paths.get(fileUrl.startsWith("/") ? fileUrl.substring(1) : fileUrl);
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("✓ File deleted from local storage");
            } else {
                log.warn("File not found: {}", filePath);
            }
        } catch (Exception e) {
            log.error("Failed to delete file: {}", fileUrl, e);
        }
    }


    /**
     * Validate file before upload
     */
    public void validateFile(MultipartFile file, long maxSizeBytes, String... allowedContentTypes) throws IOException {
        log.debug("=== Validating File ===");

        if (file == null || file.isEmpty()) {
            log.error("Validation failed: File is empty");
            throw new IOException("File is empty");
        }

        log.debug("File size: {} bytes (max: {})", file.getSize(), maxSizeBytes);
        if (file.getSize() > maxSizeBytes) {
            log.error("Validation failed: File too large");
            throw new IOException("File size exceeds maximum allowed size of " + maxSizeBytes + " bytes");
        }

        if (allowedContentTypes.length > 0) {
            String contentType = file.getContentType();
            log.debug("File content type: {}", contentType);

            boolean allowed = false;
            for (String type : allowedContentTypes) {
                if (contentType != null && contentType.toLowerCase().contains(type.toLowerCase())) {
                    allowed = true;
                    break;
                }
            }

            if (!allowed) {
                log.error("Validation failed: Invalid content type");
                throw new IOException("File type not allowed. Allowed types: " + String.join(", ", allowedContentTypes));
            }
        }

        log.debug("✓ File validation passed");
    }
}

