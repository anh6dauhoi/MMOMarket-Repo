package com.mmo.service;

import com.mmo.entity.Blog;
import com.mmo.entity.Chat;
import com.mmo.entity.Complaint;
import com.mmo.entity.Product;
import com.mmo.repository.BlogRepository;
import com.mmo.repository.ChatRepository;
import com.mmo.repository.ComplaintRepository;
import com.mmo.repository.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Async file upload task service
 * Handles background upload to Google Drive and database update
 */
@Service
@Slf4j
public class FileUploadTask {

    @Autowired
    private GoogleDriveService googleDriveService;

    @Autowired
    private ChatRepository chatRepository;

    @Autowired
    private ComplaintRepository complaintRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BlogRepository blogRepository;

    /**
     * Upload file to Google Drive asynchronously and update database
     *
     * @param localFilePath Local file path
     * @param folderType Folder type (chat, complaints, products, blogs, withdrawals)
     * @param entityType Entity type (Chat, Complaint, Product, Blog)
     * @param entityId Entity ID to update
     * @param customFilename Custom filename
     */
    @Async("fileUploadExecutor")
    @Transactional
    public void uploadToGoogleDriveAsync(String localFilePath, String folderType, String entityType, Long entityId, String customFilename) {
        log.info("=== Starting Async Google Drive Upload ===");
        log.debug("Local file: {}", localFilePath);
        log.debug("Folder type: {}", folderType);
        log.debug("Entity type: {}", entityType);
        log.debug("Entity ID: {}", entityId);

        try {
            // Check if Google Drive is enabled
            if (!googleDriveService.isEnabled()) {
                log.info("Google Drive is disabled, keeping local file: {}", localFilePath);
                return;
            }

            // Upload to Google Drive
            File localFile = new File(localFilePath);
            if (!localFile.exists()) {
                log.error("Local file not found: {}", localFilePath);
                return;
            }

            log.debug("Uploading file to Google Drive...");
            String driveUrl = googleDriveService.uploadFile(localFile, folderType, customFilename);
            log.info("✓ File uploaded to Google Drive: {}", driveUrl);

            // Update database with Google Drive URL
            boolean updated = updateEntityFileUrl(entityType, entityId, driveUrl);

            if (updated) {
                log.info("✓ Database updated with Drive URL");

                // Delete local file after successful upload
                try {
                    Path localPath = Paths.get(localFilePath);
                    if (Files.exists(localPath)) {
                        Files.delete(localPath);
                        log.info("✓ Local file deleted: {}", localFilePath);
                    }
                } catch (Exception e) {
                    log.warn("Failed to delete local file: {}", localFilePath, e);
                }
            } else {
                log.warn("Failed to update database, keeping local file: {}", localFilePath);
            }

            log.info("=== Async Upload Complete ===");

        } catch (Exception e) {
            log.error("=== Async Upload Failed ===", e);
            log.error("Error details: {}", e.getMessage());
            log.warn("Keeping local file: {}", localFilePath);
        }
    }

    /**
     * Update entity file URL in database
     */
    private boolean updateEntityFileUrl(String entityType, Long entityId, String newUrl) {
        log.debug("Updating {} ID {} with URL: {}", entityType, entityId, newUrl);

        try {
            switch (entityType.toUpperCase()) {
                case "CHAT":
                    return chatRepository.findById(entityId)
                            .map(chat -> {
                                chat.setFilePath(newUrl);
                                chatRepository.save(chat);
                                log.debug("✓ Chat updated");
                                return true;
                            })
                            .orElse(false);

                case "COMPLAINT":
                    return complaintRepository.findById(entityId)
                            .map(complaint -> {
                                // Update evidence JSON with new URL
                                String evidence = complaint.getEvidence();
                                if (evidence != null) {
                                    // Replace local URL with Drive URL in JSON
                                    String updatedEvidence = evidence.replaceAll("/uploads/[^\"]+", newUrl);
                                    complaint.setEvidence(updatedEvidence);
                                    complaintRepository.save(complaint);
                                    log.debug("✓ Complaint evidence updated");
                                    return true;
                                }
                                return false;
                            })
                            .orElse(false);

                case "PRODUCT":
                    return productRepository.findById(entityId)
                            .map(product -> {
                                product.setImage(newUrl);
                                productRepository.save(product);
                                log.debug("✓ Product image updated");
                                return true;
                            })
                            .orElse(false);

                case "BLOG":
                    return blogRepository.findById(entityId)
                            .map(blog -> {
                                // Update blog content with new image URL
                                String content = blog.getContent();
                                if (content != null) {
                                    String updatedContent = content.replaceAll("/uploads/[^\"]+", newUrl);
                                    blog.setContent(updatedContent);
                                    blogRepository.save(blog);
                                    log.debug("✓ Blog content updated");
                                    return true;
                                }
                                return false;
                            })
                            .orElse(false);

                default:
                    log.warn("Unknown entity type: {}", entityType);
                    return false;
            }
        } catch (Exception e) {
            log.error("Failed to update {} ID {}", entityType, entityId, e);
            return false;
        }
    }

    /**
     * Upload file to Google Drive asynchronously without database update
     * Used for files like withdrawal proofs that don't have a direct entity
     */
    @Async("fileUploadExecutor")
    public void uploadToGoogleDriveAsync(String localFilePath, String folderType, String customFilename) {
        log.info("=== Starting Async Google Drive Upload (No DB Update) ===");
        log.debug("Local file: {}", localFilePath);

        try {
            if (!googleDriveService.isEnabled()) {
                log.info("Google Drive is disabled, keeping local file");
                return;
            }

            File localFile = new File(localFilePath);
            if (!localFile.exists()) {
                log.error("Local file not found: {}", localFilePath);
                return;
            }

            String driveUrl = googleDriveService.uploadFile(localFile, folderType, customFilename);
            log.info("✓ File uploaded to Google Drive: {}", driveUrl);

            // Delete local file
            try {
                Path localPath = Paths.get(localFilePath);
                if (Files.exists(localPath)) {
                    Files.delete(localPath);
                    log.info("✓ Local file deleted: {}", localFilePath);
                }
            } catch (Exception e) {
                log.warn("Failed to delete local file: {}", localFilePath, e);
            }

            log.info("=== Async Upload Complete ===");

        } catch (Exception e) {
            log.error("Async upload failed", e);
        }
    }
}

