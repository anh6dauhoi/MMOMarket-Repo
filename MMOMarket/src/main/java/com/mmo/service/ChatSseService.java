package com.mmo.service;

import com.mmo.dto.ChatMessageDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class ChatSseService {
    private static final Logger log = LoggerFactory.getLogger(ChatSseService.class);
    private static final long DEFAULT_TIMEOUT = 60L * 60L * 1000L; // 1 hour

    // userId -> list of emitters (support multiple tabs/devices)
    private final Map<Long, List<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);
        emittersByUser.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> {
            log.debug("SSE completed for user: {}", userId);
            removeEmitter(userId, emitter);
        });
        emitter.onTimeout(() -> {
            log.debug("SSE timeout for user: {}", userId);
            removeEmitter(userId, emitter);
        });
        emitter.onError(ex -> {
            log.debug("SSE error for user {}: {}", userId, ex.getMessage());
            removeEmitter(userId, emitter);
        });

        // Send initial event to confirm connection
        try {
            emitter.send(SseEmitter.event()
                    .name("init")
                    .data("connected", MediaType.TEXT_PLAIN));
        } catch (IOException e) {
            log.warn("Failed to send init event to user {}: {}", userId, e.getMessage());
            removeEmitter(userId, emitter);
        }

        return emitter;
    }

    public void broadcast(ChatMessageDto dto) {
        if (dto == null) return;
        sendToUser(dto.getSenderId(), dto);
        sendToUser(dto.getReceiverId(), dto);
    }

    public void sendToUser(Long userId, ChatMessageDto dto) {
        if (userId == null || dto == null) return;
        List<SseEmitter> list = emittersByUser.get(userId);
        if (list == null || list.isEmpty()) return;

        // Create event once to avoid repeated serialization
        SseEmitter.SseEventBuilder event = SseEmitter.event()
                .name("message")
                .data(dto, MediaType.APPLICATION_JSON);

        // Use iterator to safely remove during iteration
        list.removeIf(emitter -> {
            try {
                emitter.send(event);
                return false; // keep this emitter
            } catch (IOException e) {
                // Connection closed by client - this is normal, use debug level
                String errorMsg = e.getMessage();
                if (errorMsg != null && (errorMsg.contains("Broken pipe") ||
                    errorMsg.contains("Connection reset") ||
                    errorMsg.contains("connection was aborted") ||
                    errorMsg.contains("Connection closed"))) {
                    log.debug("Client disconnected for user {}: {}", userId, errorMsg);
                } else {
                    log.warn("IO error sending message to user {}: {}", userId, errorMsg);
                }
                cleanupEmitter(emitter);
                return true; // remove this emitter
            } catch (IllegalStateException e) {
                log.debug("Emitter already completed for user {}: {}", userId, e.getMessage());
                cleanupEmitter(emitter);
                return true; // remove this emitter
            } catch (Exception e) {
                log.error("Unexpected error sending message to user {}: {}", userId, e.getMessage(), e);
                cleanupEmitter(emitter);
                return true; // remove this emitter
            }
        });

        // Clean up user entry if no emitters left
        if (list.isEmpty()) {
            emittersByUser.remove(userId);
        }
    }

    private void cleanupEmitter(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // Emitter already completed or error - ignore silently
        }
    }

    private void removeEmitter(Long userId, SseEmitter emitter) {
        List<SseEmitter> list = emittersByUser.get(userId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emittersByUser.remove(userId);
            }
        }
        cleanupEmitter(emitter);
    }
}
