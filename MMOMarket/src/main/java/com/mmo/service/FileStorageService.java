package com.mmo.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Unified file storage service that handles both local and Google Drive storage
 * Automatically switches between storage methods based on configuration
 */
@Service
@Slf4j
public class FileStorageService {

    @Autowired
    private GoogleDriveService googleDriveService;

    @Autowired
    private FileUploadTask fileUploadTask;

    @Value("${google.drive.enabled:false}")
    private boolean driveEnabled;

    /**
     * Upload file with automatic storage selection
     * Strategy: Save locally first (fast), then upload to Drive asynchronously
     *
     * @param file MultipartFile to upload
     * @param folderType Type of folder (chat, complaints, products, blogs, withdrawals)
     * @param userId Optional user ID for subfolder organization
     * @return URL or path to access the uploaded file (local first, Drive later)
     */
    public String uploadFile(MultipartFile file, String folderType, Long userId) throws IOException {
        log.info("=== File Upload Request ===");
        log.debug("File: {}", file.getOriginalFilename());
        log.debug("Size: {} bytes", file.getSize());
        log.debug("Type: {}", file.getContentType());
        log.debug("Folder: {}", folderType);
        log.debug("User ID: {}", userId);
        log.debug("Drive enabled: {}", driveEnabled);

        // Always save to local storage first for instant response
        String localUrl = uploadToLocal(file, folderType, userId);
        log.info("✓ File saved locally: {}", localUrl);

        // If Google Drive is enabled, upload asynchronously in background
        if (driveEnabled && googleDriveService.isEnabled()) {
            log.info("→ Scheduling async upload to Google Drive");
            try {
                String customFilename = generateCustomFilename(file, userId);
                String localPath = localUrl.startsWith("/") ? localUrl.substring(1) : localUrl;

                // Upload to Drive asynchronously (no entity update for now - will be handled separately)
                fileUploadTask.uploadToGoogleDriveAsync(localPath, folderType, customFilename);
                log.debug("✓ Async upload scheduled");
            } catch (Exception e) {
                log.warn("Failed to schedule async upload, keeping local file", e);
            }
        }

        // Return local URL immediately for fast response
        return localUrl;
    }

    /**
     * Upload file with entity tracking for automatic database update
     * This method saves locally first, returns immediately, then uploads to Drive
     * and updates the database with the Drive URL
     *
     * @param file MultipartFile to upload
     * @param folderType Type of folder (chat, complaints, products, blogs, withdrawals)
     * @param userId Optional user ID for subfolder organization
     * @param entityType Entity type (Chat, Complaint, Product, Blog)
     * @param entityId Entity ID to update after Drive upload
     * @return Local URL (will be updated to Drive URL asynchronously)
     */
    public String uploadFileWithEntityTracking(MultipartFile file, String folderType, Long userId, String entityType, Long entityId) throws IOException {
        log.info("=== File Upload Request with Entity Tracking ===");
        log.debug("Entity: {} ID: {}", entityType, entityId);

        // Always save to local storage first for instant response
        String localUrl = uploadToLocal(file, folderType, userId);
        log.info("✓ File saved locally: {}", localUrl);

        // If Google Drive is enabled, upload asynchronously and update database
        if (driveEnabled && googleDriveService.isEnabled()) {
            log.info("→ Scheduling async upload to Google Drive with DB update");
            try {
                String customFilename = generateCustomFilename(file, userId);
                String localPath = localUrl.startsWith("/") ? localUrl.substring(1) : localUrl;

                // Upload to Drive asynchronously WITH database update
                fileUploadTask.uploadToGoogleDriveAsync(localPath, folderType, entityType, entityId, customFilename);
                log.debug("✓ Async upload with DB update scheduled");
            } catch (Exception e) {
                log.warn("Failed to schedule async upload, keeping local file", e);
            }
        }

        // Return local URL immediately for fast response
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
     * Upload file from local File object
     */
    public String uploadFile(File localFile, String folderType, String customFilename) throws IOException {
        log.info("=== File Upload Request (from local file) ===");
        log.debug("File: {}", localFile.getAbsolutePath());
        log.debug("Folder: {}", folderType);
        log.debug("Custom filename: {}", customFilename);

        if (driveEnabled && googleDriveService.isEnabled()) {
            log.info("→ Using Google Drive storage");
            try {
                String driveUrl = googleDriveService.uploadFile(localFile, folderType, customFilename);
                log.info("✓ File uploaded to Google Drive: {}", driveUrl);
                return driveUrl;
            } catch (Exception e) {
                log.error("✗ Google Drive upload failed", e);
                throw e;
            }
        } else {
            log.warn("Google Drive is not enabled, file already exists locally");
            return localFile.getPath().replace('\\', '/');
        }
    }

    /**
     * Delete file (works for both local and Google Drive)
     */
    public void deleteFile(String fileUrl) {
        log.info("=== File Delete Request ===");
        log.debug("File URL: {}", fileUrl);

        try {
            if (fileUrl.contains("drive.google.com")) {
                // Google Drive file
                log.debug("Detected Google Drive file");
                String fileId = googleDriveService.extractFileIdFromUrl(fileUrl);
                if (fileId != null && googleDriveService.isEnabled()) {
                    googleDriveService.deleteFile(fileId);
                    log.info("✓ File deleted from Google Drive");
                } else {
                    log.warn("Cannot delete: Drive not enabled or invalid file ID");
                }
            } else {
                // Local file
                log.debug("Detected local file");
                Path filePath = Paths.get(fileUrl.startsWith("/") ? fileUrl.substring(1) : fileUrl);
                if (Files.exists(filePath)) {
                    Files.delete(filePath);
                    log.info("✓ File deleted from local storage");
                } else {
                    log.warn("File not found: {}", filePath);
                }
            }
        } catch (Exception e) {
            log.error("Failed to delete file: {}", fileUrl, e);
        }
    }

    /**
     * Generate custom filename with user ID and timestamp
     */
    private String generateCustomFilename(MultipartFile file, Long userId) {
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        String prefix = userId != null ? "user" + userId + "-" : "";
        return prefix + System.currentTimeMillis() + "-" + UUID.randomUUID() + extension;
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

    /**
     * Check if Google Drive is enabled
     */
    public boolean isDriveEnabled() {
        return driveEnabled && googleDriveService.isEnabled();
    }
}

