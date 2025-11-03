package com.mmo.controller;

import com.mmo.dto.ChatMessageDto;
import com.mmo.dto.ConversationSummaryDto;
import com.mmo.entity.User;
import com.mmo.service.AuthService;
import com.mmo.service.ChatService;
import com.mmo.service.ChatSseService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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

    @Value("${upload.chat.dir:uploads/chat}")
    private String uploadDir;

    public ChatController(ChatService chatService, AuthService authService, ChatSseService chatSseService) {
        this.chatService = chatService;
        this.authService = authService;
        this.chatSseService = chatSseService;
    }

    // Chat page
    @GetMapping("/chat")
    public String chatPage(@RequestParam(value = "sellerId", required = false) Long sellerId,
                           Authentication authentication,
                           Model model) {
        User current = authService.findByEmail(authentication.getName());
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
        User current = authService.findByEmail(authentication.getName());
        return chatSseService.subscribe(current.getId());
    }

    // Messages API
    @GetMapping("/chat/messages/{partnerId}")
    @ResponseBody
    public ResponseEntity<List<ChatMessageDto>> loadMessages(@PathVariable Long partnerId, Authentication authentication) {
        User current = authService.findByEmail(authentication.getName());
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
            User current = authService.findByEmail(authentication.getName());
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
            if (file.isEmpty()) {
                res.put("error", "File is empty");
                return ResponseEntity.badRequest().body(res);
            }

            // Validate file size (max 50MB)
            if (file.getSize() > 50 * 1024 * 1024) {
                res.put("error", "File size must be less than 50MB");
                return ResponseEntity.badRequest().body(res);
            }

            User current = authService.findByEmail(authentication.getName());

            // Create upload directory if not exists
            File uploadDirFile = new File(uploadDir);
            if (!uploadDirFile.exists()) {
                boolean created = uploadDirFile.mkdirs();
                if (!created) {
                    res.put("error", "Failed to create upload directory");
                    return ResponseEntity.status(500).body(res);
                }
            }

            // Generate unique filename
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String uniqueFilename = UUID.randomUUID() + extension;
            Path filePath = Paths.get(uploadDir, uniqueFilename);

            // Save file
            Files.write(filePath, file.getBytes());

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

            // Save to database
            String filePathStr = "/" + uploadDir + "/" + uniqueFilename;
            ChatMessageDto dto = chatService.sendWithFile(
                current.getId(),
                receiverId,
                message,
                filePathStr,
                fileType,
                originalFilename,
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
