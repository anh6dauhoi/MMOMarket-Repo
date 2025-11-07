package com.mmo.service;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.Permission;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.*;

/**
 * Service for uploading files to Google Drive API
 * This service handles file uploads to Google Drive instead of local storage
 * Includes comprehensive debug logging
 */
@Service
@Slf4j
public class GoogleDriveService {

    private static final String APPLICATION_NAME = "MMOMarket File Storage";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final List<String> SCOPES = Collections.singletonList(DriveScopes.DRIVE_FILE);

    @Value("${google.drive.enabled:false}")
    private boolean driveEnabled;

    @Value("${google.drive.credentials.file:credentials.json}")
    private String credentialsFilePath;

    @Value("${google.drive.folder.id:}")
    private String rootFolderId;

    @Value("${google.drive.folder.chat:Chat Files}")
    private String chatFolderName;

    @Value("${google.drive.folder.complaints:Complaint Evidence}")
    private String complaintsFolderName;

    @Value("${google.drive.folder.products:Product Images}")
    private String productsFolderName;

    @Value("${google.drive.folder.blogs:Blog Images}")
    private String blogsFolderName;

    @Value("${google.drive.folder.withdrawals:Withdrawal Proofs}")
    private String withdrawalsFolderName;

    private Drive driveService;
    private Map<String, String> folderCache = new HashMap<>();

    @PostConstruct
    public void initialize() {
        if (!driveEnabled) {
            log.info("=== Google Drive Integration is DISABLED ===");
            log.info("Files will be stored locally. To enable Google Drive:");
            log.info("1. Set GOOGLE_DRIVE_ENABLED=true in environment variables");
            log.info("2. Configure GOOGLE_DRIVE_CREDENTIALS_FILE with valid credentials.json path");
            log.info("3. Optionally set GOOGLE_DRIVE_FOLDER_ID for root folder");
            return;
        }

        log.info("=== Initializing Google Drive Service ===");
        try {
            log.debug("Drive enabled: {}", driveEnabled);
            log.debug("Credentials file path: {}", credentialsFilePath);
            log.debug("Root folder ID: {}", rootFolderId);

            this.driveService = buildDriveService();

            if (this.driveService != null) {
                log.info("✓ Google Drive service initialized successfully");

                // Pre-create folder structure
                initializeFolders();

                log.info("=== Google Drive Integration Ready ===");
            } else {
                log.error("✗ Failed to initialize Google Drive service");
                this.driveEnabled = false;
            }
        } catch (Exception e) {
            log.error("=== Failed to initialize Google Drive ===", e);
            log.error("Error details: {}", e.getMessage());
            this.driveEnabled = false;
            log.warn("Falling back to local file storage");
        }
    }

    /**
     * Build Google Drive service with credentials
     */
    private Drive buildDriveService() throws IOException, GeneralSecurityException {
        log.debug("Building Google Drive service...");

        // Load client secrets
        InputStream in = getCredentialsStream();
        if (in == null) {
            log.error("Credentials file not found: {}", credentialsFilePath);
            throw new FileNotFoundException("Credentials file not found: " + credentialsFilePath);
        }

        log.debug("Reading credentials from input stream...");
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
        log.debug("Client secrets loaded successfully");

        // Build flow and trigger user authorization request
        log.debug("Creating authorization flow...");
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();

        log.debug("Creating local server receiver for authorization...");
        // Use a dynamic port that's less likely to conflict and with proper callback URL
        LocalServerReceiver receiver = new LocalServerReceiver.Builder()
                .setHost("localhost")
                .setPort(8080)  // Changed from 8888 to 8080
                .setCallbackPath("/Callback")
                .build();

        log.info("========================================");
        log.info("AUTHORIZATION REQUIRED");
        log.info("========================================");
        log.info("A browser window will open for authorization.");
        log.info("If it doesn't open automatically, copy the URL from console.");
        log.info("After authorization, you'll be redirected to: http://localhost:8080/Callback");
        log.info("========================================");

        log.debug("Requesting user authorization...");
        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
        log.debug("User authorized successfully");

        log.debug("Building Drive service with credentials...");
        Drive drive = new Drive.Builder(GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();

        log.info("✓ Drive service built successfully");
        return drive;
    }

    /**
     * Get credentials input stream
     */
    private InputStream getCredentialsStream() throws IOException {
        log.debug("Loading credentials from: {}", credentialsFilePath);

        // Try as classpath resource first
        InputStream stream = GoogleDriveService.class.getClassLoader().getResourceAsStream(credentialsFilePath);
        if (stream != null) {
            log.debug("✓ Credentials loaded from classpath: {}", credentialsFilePath);
            return stream;
        }

        // Try as absolute file path
        java.io.File file = new java.io.File(credentialsFilePath);
        if (file.exists() && file.isFile()) {
            log.debug("✓ Credentials loaded from file: {}", file.getAbsolutePath());
            return new FileInputStream(file);
        }

        // Try relative to project root
        String projectRoot = System.getProperty("user.dir");
        java.io.File relativeFile = new java.io.File(projectRoot, credentialsFilePath);
        if (relativeFile.exists() && relativeFile.isFile()) {
            log.debug("✓ Credentials loaded from project root: {}", relativeFile.getAbsolutePath());
            return new FileInputStream(relativeFile);
        }

        log.error("✗ Credentials file not found!");
        log.error("Searched locations:");
        log.error("  1. Classpath resource: {}", credentialsFilePath);
        log.error("  2. Absolute path: {}", file.getAbsolutePath());
        log.error("  3. Project relative: {}", relativeFile.getAbsolutePath());
        log.error("Please ensure {} exists in one of these locations", credentialsFilePath);

        return null;
    }

    /**
     * Initialize folder structure in Google Drive
     */
    private void initializeFolders() {
        log.info("=== Initializing Google Drive Folder Structure ===");
        try {
            String parentId = rootFolderId != null && !rootFolderId.isEmpty() ? rootFolderId : null;

            log.debug("Creating folder structure under parent: {}", parentId != null ? parentId : "root");

            String chatFolder = getOrCreateFolder(chatFolderName, parentId);
            folderCache.put("chat", chatFolder);
            log.info("✓ Chat folder ready: {} (ID: {})", chatFolderName, chatFolder);

            String complaintsFolder = getOrCreateFolder(complaintsFolderName, parentId);
            folderCache.put("complaints", complaintsFolder);
            log.info("✓ Complaints folder ready: {} (ID: {})", complaintsFolderName, complaintsFolder);

            String productsFolder = getOrCreateFolder(productsFolderName, parentId);
            folderCache.put("products", productsFolder);
            log.info("✓ Products folder ready: {} (ID: {})", productsFolderName, productsFolder);

            String blogsFolder = getOrCreateFolder(blogsFolderName, parentId);
            folderCache.put("blogs", blogsFolder);
            log.info("✓ Blogs folder ready: {} (ID: {})", blogsFolderName, blogsFolder);

            String withdrawalsFolder = getOrCreateFolder(withdrawalsFolderName, parentId);
            folderCache.put("withdrawals", withdrawalsFolder);
            log.info("✓ Withdrawals folder ready: {} (ID: {})", withdrawalsFolderName, withdrawalsFolder);

            log.info("=== Folder Structure Initialized ===");
        } catch (Exception e) {
            log.error("Failed to initialize folder structure", e);
            log.warn("Folders will be created on-demand");
        }
    }

    /**
     * Get or create a folder in Google Drive
     */
    private String getOrCreateFolder(String folderName, String parentId) throws IOException {
        log.debug("Getting or creating folder: {} under parent: {}", folderName, parentId);

        // Search for existing folder
        String query = "mimeType='application/vnd.google-apps.folder' and name='" + folderName + "' and trashed=false";
        if (parentId != null) {
            query += " and '" + parentId + "' in parents";
        }

        log.debug("Search query: {}", query);

        FileList result = driveService.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute();

        if (result.getFiles() != null && !result.getFiles().isEmpty()) {
            String folderId = result.getFiles().get(0).getId();
            log.debug("Found existing folder: {} (ID: {})", folderName, folderId);
            return folderId;
        }

        // Create new folder
        log.debug("Creating new folder: {}", folderName);
        File folderMetadata = new File();
        folderMetadata.setName(folderName);
        folderMetadata.setMimeType("application/vnd.google-apps.folder");

        if (parentId != null) {
            folderMetadata.setParents(Collections.singletonList(parentId));
        }

        File folder = driveService.files().create(folderMetadata)
                .setFields("id, name")
                .execute();

        log.info("✓ Created new folder: {} (ID: {})", folderName, folder.getId());
        return folder.getId();
    }

    /**
     * Check if Google Drive is enabled and ready
     */
    public boolean isEnabled() {
        return driveEnabled && driveService != null;
    }

    /**
     * Upload file to Google Drive
     *
     * @param multipartFile The file to upload
     * @param folderType Type of folder (chat, complaints, products, blogs, withdrawals)
     * @param customFilename Optional custom filename
     * @return Public URL or file ID of uploaded file
     */
    public String uploadFile(MultipartFile multipartFile, String folderType, String customFilename) throws IOException {
        if (!isEnabled()) {
            log.warn("Google Drive is not enabled. Cannot upload file.");
            throw new IllegalStateException("Google Drive integration is not enabled");
        }

        log.info("=== Uploading File to Google Drive ===");
        log.debug("Folder type: {}", folderType);
        log.debug("Original filename: {}", multipartFile.getOriginalFilename());
        log.debug("Custom filename: {}", customFilename);
        log.debug("File size: {} bytes", multipartFile.getSize());
        log.debug("Content type: {}", multipartFile.getContentType());

        try {
            // Get folder ID
            String folderId = folderCache.get(folderType);
            if (folderId == null) {
                log.debug("Folder not in cache, creating...");
                String folderName = getFolderNameByType(folderType);
                String parentId = rootFolderId != null && !rootFolderId.isEmpty() ? rootFolderId : null;
                folderId = getOrCreateFolder(folderName, parentId);
                folderCache.put(folderType, folderId);
            }
            log.debug("Target folder ID: {}", folderId);

            // Prepare filename
            String filename = customFilename != null ? customFilename : generateUniqueFilename(multipartFile);
            log.debug("Final filename: {}", filename);

            // Create temporary file
            Path tempFile = Files.createTempFile("upload-", filename);
            multipartFile.transferTo(tempFile.toFile());
            log.debug("Temporary file created: {}", tempFile);

            // Upload to Drive
            File fileMetadata = new File();
            fileMetadata.setName(filename);
            fileMetadata.setParents(Collections.singletonList(folderId));

            FileContent mediaContent = new FileContent(
                    multipartFile.getContentType() != null ? multipartFile.getContentType() : "application/octet-stream",
                    tempFile.toFile()
            );

            log.debug("Starting upload to Google Drive...");
            File uploadedFile = driveService.files().create(fileMetadata, mediaContent)
                    .setFields("id, name, webViewLink, webContentLink")
                    .execute();

            log.info("✓ File uploaded successfully");
            log.debug("File ID: {}", uploadedFile.getId());
            log.debug("File name: {}", uploadedFile.getName());
            log.debug("Web view link: {}", uploadedFile.getWebViewLink());

            // Make file publicly accessible
            try {
                log.debug("Setting file permissions to public...");
                Permission permission = new Permission();
                permission.setType("anyone");
                permission.setRole("reader");
                driveService.permissions().create(uploadedFile.getId(), permission).execute();
                log.debug("✓ File set to public access");
            } catch (Exception e) {
                log.warn("Failed to set public permission: {}", e.getMessage());
            }

            // Clean up temp file
            Files.deleteIfExists(tempFile);
            log.debug("Temporary file deleted");

            // Return direct view link (works for images and most files)
            // Use uc?export=view instead of download for direct display in browser
            String viewLink = "https://drive.google.com/uc?export=view&id=" + uploadedFile.getId();
            log.info("=== Upload Complete ===");
            log.info("View link: {}", viewLink);

            return viewLink;

        } catch (Exception e) {
            log.error("=== Upload Failed ===", e);
            log.error("Error type: {}", e.getClass().getName());
            log.error("Error message: {}", e.getMessage());
            throw new IOException("Failed to upload file to Google Drive: " + e.getMessage(), e);
        }
    }

    /**
     * Upload file from local path to Google Drive
     */
    public String uploadFile(java.io.File file, String folderType, String customFilename) throws IOException {
        if (!isEnabled()) {
            log.warn("Google Drive is not enabled. Cannot upload file.");
            throw new IllegalStateException("Google Drive integration is not enabled");
        }

        log.info("=== Uploading File to Google Drive (from local) ===");
        log.debug("Local file: {}", file.getAbsolutePath());
        log.debug("Folder type: {}", folderType);

        try {
            String folderId = folderCache.get(folderType);
            if (folderId == null) {
                String folderName = getFolderNameByType(folderType);
                String parentId = rootFolderId != null && !rootFolderId.isEmpty() ? rootFolderId : null;
                folderId = getOrCreateFolder(folderName, parentId);
                folderCache.put(folderType, folderId);
            }

            String filename = customFilename != null ? customFilename : file.getName();
            log.debug("Filename: {}", filename);

            File fileMetadata = new File();
            fileMetadata.setName(filename);
            fileMetadata.setParents(Collections.singletonList(folderId));

            String mimeType = Files.probeContentType(file.toPath());
            if (mimeType == null) mimeType = "application/octet-stream";

            FileContent mediaContent = new FileContent(mimeType, file);

            log.debug("Uploading...");
            File uploadedFile = driveService.files().create(fileMetadata, mediaContent)
                    .setFields("id, name, webViewLink")
                    .execute();

            log.info("✓ File uploaded: {} (ID: {})", uploadedFile.getName(), uploadedFile.getId());

            // Make public
            try {
                Permission permission = new Permission();
                permission.setType("anyone");
                permission.setRole("reader");
                driveService.permissions().create(uploadedFile.getId(), permission).execute();
            } catch (Exception e) {
                log.warn("Failed to set public permission: {}", e.getMessage());
            }

            String viewLink = "https://drive.google.com/uc?export=view&id=" + uploadedFile.getId();
            log.info("View link: {}", viewLink);

            return viewLink;

        } catch (Exception e) {
            log.error("Upload failed", e);
            throw new IOException("Failed to upload file to Google Drive: " + e.getMessage(), e);
        }
    }

    /**
     * Delete file from Google Drive
     */
    public void deleteFile(String fileId) throws IOException {
        if (!isEnabled()) {
            log.warn("Google Drive is not enabled. Cannot delete file.");
            return;
        }

        log.info("=== Deleting File from Google Drive ===");
        log.debug("File ID: {}", fileId);

        try {
            driveService.files().delete(fileId).execute();
            log.info("✓ File deleted successfully");
        } catch (Exception e) {
            log.error("=== Delete Failed ===", e);
            throw new IOException("Failed to delete file from Google Drive: " + e.getMessage(), e);
        }
    }

    /**
     * Generate unique filename with timestamp and UUID
     */
    private String generateUniqueFilename(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String extension = "";

        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        return System.currentTimeMillis() + "-" + UUID.randomUUID() + extension;
    }

    /**
     * Get folder name by type
     */
    private String getFolderNameByType(String type) {
        return switch (type.toLowerCase()) {
            case "chat" -> chatFolderName;
            case "complaints" -> complaintsFolderName;
            case "products" -> productsFolderName;
            case "blogs" -> blogsFolderName;
            case "withdrawals" -> withdrawalsFolderName;
            default -> "Others";
        };
    }

    /**
     * Extract file ID from Google Drive URL
     */
    public String extractFileIdFromUrl(String url) {
        if (url == null) return null;

        // Pattern: https://drive.google.com/uc?export=download&id=FILE_ID
        if (url.contains("id=")) {
            String[] parts = url.split("id=");
            if (parts.length > 1) {
                return parts[1].split("&")[0];
            }
        }

        return null;
    }
}

