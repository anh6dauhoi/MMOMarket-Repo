package com.mmo.controller;

import com.mmo.dto.ChatMessageDto;
import com.mmo.dto.ConversationSummaryDto;
import com.mmo.entity.User;
import com.mmo.service.AuthService;
import com.mmo.service.ChatService;
import com.mmo.service.ChatSseService;
import com.mmo.service.FileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping
public class ChatController {
    private final ChatService chatService;
    private final AuthService authService;
    private final ChatSseService chatSseService;
    private final FileStorageService fileStorageService;

    @Value("${upload.chat.dir:uploads/chat}")
    private String uploadDir;

    public ChatController(ChatService chatService, AuthService authService, ChatSseService chatSseService,
                         FileStorageService fileStorageService) {
        this.chatService = chatService;
        this.authService = authService;
        this.chatSseService = chatSseService;
        this.fileStorageService = fileStorageService;
    }

    /**
     * Helper method to get current user from authentication
     * Handles both OAuth2 (Google) and form-based login
     */
    private User getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();

        // Case 1: Form-based login - principal is User object
        if (principal instanceof User) {
            return (User) principal;
        }

        // Case 2: OAuth2 login - principal is OAuth2User
        if (principal instanceof OAuth2User) {
            OAuth2User oauth2User = (OAuth2User) principal;
            String email = oauth2User.getAttribute("email");
            if (email != null) {
                return authService.findByEmail(email);
            }
        }

        // Case 3: Principal is string (username/email)
        if (principal instanceof String) {
            String identifier = (String) principal;
            return authService.findByEmail(identifier);
        }

        // Fallback: try to get email from authentication name
        return authService.findByEmail(authentication.getName());
    }

    // Chat page
    @GetMapping("/chat")
    public String chatPage(@RequestParam(value = "sellerId", required = false) Long sellerId,
                           Authentication authentication,
                           Model model) {
        User current = getCurrentUser(authentication);

        if (current == null) {
            return "redirect:/authen/login";
        }

        List<ConversationSummaryDto> conversations = chatService.listConversations(current.getId());
        model.addAttribute("conversations", conversations);
        model.addAttribute("currentUser", current);
        if (sellerId != null) model.addAttribute("sellerId", sellerId);

        return "chat/chat";
    }

    // SSE stream for real-time chat updates
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public SseEmitter subscribe(Authentication authentication) {
        User current = getCurrentUser(authentication);

        if (current == null) {
            SseEmitter emitter = new SseEmitter();
            emitter.completeWithError(new IllegalStateException("User not authenticated"));
            return emitter;
        }

        return chatSseService.subscribe(current.getId());
    }

    // Messages API
    @GetMapping("/chat/messages/{partnerId}")
    @ResponseBody
    public ResponseEntity<List<ChatMessageDto>> loadMessages(@PathVariable Long partnerId, Authentication authentication) {
        User current = getCurrentUser(authentication);

        if (current == null) {
            return ResponseEntity.status(401).build();
        }

        List<ChatMessageDto> messages = chatService.getMessages(current.getId(), partnerId);
        return ResponseEntity.ok(messages);
    }

    // Send API
    @PostMapping("/chat/send")
    @ResponseBody
    public ResponseEntity<?> send(@RequestParam Long receiverId,
                                  @RequestParam String message,
                                  Authentication authentication) {
        Map<String, Object> res = new HashMap<>();
        try {
            User current = getCurrentUser(authentication);

            if (current == null) {
                res.put("error", "User not authenticated");
                return ResponseEntity.status(401).body(res);
            }

            ChatMessageDto dto = chatService.send(current.getId(), receiverId, message);
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException ex) {
            res.put("error", ex.getMessage());
            return ResponseEntity.badRequest().body(res);
        }
    }

    // Send with file attachment
    @PostMapping("/chat/send-file")
    @ResponseBody
    public ResponseEntity<?> sendFile(@RequestParam Long receiverId,
                                      @RequestParam(required = false) String message,
                                      @RequestParam("file") MultipartFile file,
                                      Authentication authentication) {
        Map<String, Object> res = new HashMap<>();
        try {
            User current = getCurrentUser(authentication);

            if (current == null) {
                res.put("error", "User not authenticated");
                return ResponseEntity.status(401).body(res);
            }

            // Validate file using FileStorageService
            fileStorageService.validateFile(file, 50 * 1024 * 1024); // 50MB max

            // Upload file using FileStorageService (automatically uses Google Drive or local)
            String fileUrl = fileStorageService.uploadFile(file, "chat", current.getId());

            // Determine file type
            String contentType = file.getContentType();
            String fileType = "document";
            if (contentType != null) {
                if (contentType.startsWith("image/")) {
                    fileType = "image";
                } else if (contentType.startsWith("video/")) {
                    fileType = "video";
                }
            }

            // Save to database with file URL (can be Google Drive URL or local path)
            ChatMessageDto dto = chatService.sendWithFile(
                current.getId(),
                receiverId,
                message,
                fileUrl,
                fileType,
                file.getOriginalFilename(),
                file.getSize()
            );

            return ResponseEntity.ok(dto);
        } catch (IOException ex) {
            res.put("error", "Failed to upload file: " + ex.getMessage());
            return ResponseEntity.status(500).body(res);
        } catch (IllegalArgumentException ex) {
            res.put("error", ex.getMessage());
            return ResponseEntity.badRequest().body(res);
        }
    }
}
