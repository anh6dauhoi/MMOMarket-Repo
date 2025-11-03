package com.mmo.service;

import com.mmo.entity.Chat;
import com.mmo.entity.User;
import com.mmo.repository.ChatRepository;
import com.mmo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChatService {

    @Autowired
    private ChatRepository chatRepository;

    @Autowired
    private UserRepository userRepository;

    // Lấy tin nhắn giữa 2 user
    public List<Chat> getConversation(Long userId1, Long userId2) {
        return chatRepository.findConversation(userId1, userId2);
    }

    // Gửi tin nhắn
    public Chat sendMessage(Long senderId, Long receiverId, String message) {
        Chat chat = new Chat();
        chat.setSenderId(senderId);
        chat.setReceiverId(receiverId);
        chat.setMessage(message);
        chat.setCreatedAt(new Date());
        chat.setCreatedBy(senderId);
        chat.setDelete(false);
        return chatRepository.save(chat);
    }

    // Lấy danh sách conversations với thông tin user
    public List<Map<String, Object>> getConversationList(Long userId) {
        List<Chat> lastMessages = chatRepository.findConversationList(userId);

        // Group by conversation partner và sort by time DESC
        Map<Long, Chat> conversations = new LinkedHashMap<>();

        // Sắp xếp theo thời gian mới nhất
        lastMessages.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));

        for (Chat chat : lastMessages) {
            Long partnerId = chat.getSenderId().equals(userId)
                    ? chat.getReceiverId()
                    : chat.getSenderId();

            if (!conversations.containsKey(partnerId)) {
                conversations.put(partnerId, chat);
            }
        }

        // Build response
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, Chat> entry : conversations.entrySet()) {
            Long partnerId = entry.getKey();
            Chat lastChat = entry.getValue();

            User partner = userRepository.findById(partnerId).orElse(null);
            if (partner == null) continue;

            Map<String, Object> conv = new HashMap<>();
            conv.put("partnerId", partnerId);
            conv.put("partnerName", partner.getFullName());
            conv.put("partnerEmail", partner.getEmail());
            conv.put("lastMessage", lastChat.getMessage());
            conv.put("lastMessageTime", lastChat.getCreatedAt());
            conv.put("isSentByMe", lastChat.getSenderId().equals(userId));

            result.add(conv);
        }

        return result; // Đã sort rồi nên hiển thị theo thứ tự mới nhất
    }
}