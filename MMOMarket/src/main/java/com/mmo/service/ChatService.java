package com.mmo.service;

import com.mmo.entity.Chat;
import com.mmo.entity.User;
import com.mmo.repository.ChatRepository;
import com.mmo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
<<<<<<< HEAD
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
=======
import java.util.*;
>>>>>>> parent of 3de4241 (Update Chat)

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

<<<<<<< HEAD
    public List<ConversationSummaryDto> listConversations(Long currentUserId) {
        List<ChatRepository.ConversationSummaryProjection> rows = chatRepository.findConversationSummaries(currentUserId);
        List<ConversationSummaryDto> list = new ArrayList<>();
        for (ChatRepository.ConversationSummaryProjection p : rows) {
            ConversationSummaryDto dto = new ConversationSummaryDto();
            dto.setPartnerId(p.getPartnerId());
            String name = p.getPartnerName();
            if (name == null || name.isBlank()) name = "User #" + p.getPartnerId();
            dto.setPartnerName(name);
            dto.setLastMessage(p.getLastMessage());
            dto.setLastMessageTime(p.getLastMessageTime());
            // Convert Integer (0/1) to boolean for MySQL TINYINT compatibility
            Integer sentByMe = p.getIsSentByMe();
            dto.setSentByMe(sentByMe != null && sentByMe != 0);
            dto.setAdmin(false);
            list.add(dto);
        }

        // Determine current user role to avoid injecting self-admin for admin accounts
        User me = requireActiveUser(currentUserId);
        String myRole = me.getRole() == null ? "" : me.getRole().trim();
        String roleUpper = myRole.toUpperCase(Locale.ROOT);
        if (roleUpper.startsWith("ROLE_")) roleUpper = roleUpper.substring(5);

        // Find first active admin (ADMIN or ROLE_ADMIN) by smallest ID
        List<User> admins = new ArrayList<>();
        admins.addAll(userRepository.findByRoleIgnoreCaseAndIsDelete("ADMIN", false));
        admins.addAll(userRepository.findByRoleIgnoreCaseAndIsDelete("ROLE_ADMIN", false));
        // Remove duplicates by ID
        Map<Long, User> adminMap = admins.stream().collect(Collectors.toMap(User::getId, u -> u, (a,b)->a));
        User firstAdmin = adminMap.values().stream()
                .sorted(Comparator.comparing(User::getId))
                .findFirst().orElse(null);

        if (firstAdmin == null) {
            // No admin accounts found; return the list as-is
            return list;
        }

        Long adminId = firstAdmin.getId();
        String adminName = Optional.ofNullable(firstAdmin.getFullName()).filter(s -> !s.isBlank()).orElse("Admin");

        // Mark admin entry if present
        int adminIndex = -1;
        for (int i = 0; i < list.size(); i++) {
            if (Objects.equals(list.get(i).getPartnerId(), adminId)) {
                list.get(i).setAdmin(true);
                adminIndex = i;
                break;
            }
        }

        // If current user is not admin, ensure admin conversation is pinned or injected at the top
        boolean currentIsAdmin = "ADMIN".equals(roleUpper);
        if (!currentIsAdmin) {
            if (adminIndex >= 0) {
                // Move existing admin conversation to top if not already there
                if (adminIndex != 0) {
                    ConversationSummaryDto adminDto = list.remove(adminIndex);
                    list.add(0, adminDto);
                }
            } else {
                // Inject synthetic admin conversation for first-time users or those without admin chat
                ConversationSummaryDto adminDto = new ConversationSummaryDto();
                adminDto.setPartnerId(adminId);
                adminDto.setPartnerName(adminName);
                adminDto.setLastMessage("Chat with Admin");
                adminDto.setLastMessageTime(new Date());
                adminDto.setSentByMe(false);
                adminDto.setAdmin(true);
                list.add(0, adminDto);
            }
        }

        return list;
    }

    public List<ChatMessageDto> getMessages(Long currentUserId, Long partnerId) {
        validatePair(currentUserId, partnerId);
        List<Chat> msgs = chatRepository.findConversation(currentUserId, partnerId);
        List<ChatMessageDto> out = new ArrayList<>();
        for (Chat c : msgs) out.add(new ChatMessageDto(c));
        return out;
    }

    @Transactional
    public ChatMessageDto send(Long senderId, Long receiverId, String message) {
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("Message must not be empty");
        }
        validatePair(senderId, receiverId);
        User sender = requireActiveUser(senderId);
        User receiver = requireActiveUser(receiverId);
=======
    // Gửi tin nhắn
    public Chat sendMessage(Long senderId, Long receiverId, String message) {
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new RuntimeException("Sender not found"));
        User receiver = userRepository.findById(receiverId)
                .orElseThrow(() -> new RuntimeException("Receiver not found"));
>>>>>>> parent of 3de4241 (Update Chat)

        Chat chat = new Chat();
        chat.setSender(sender);
        chat.setReceiver(receiver);
        chat.setMessage(message);
        chat.setCreatedAt(new Date());
        chat.setCreatedBy(senderId);
        chat.setDelete(false);
        return chatRepository.save(chat);
    }

    // Lấy danh sách conversations với thông tin user
    public List<Map<String, Object>> getConversationList(Long userId) {
        List<Chat> lastMessages = chatRepository.findConversationList(userId);
        Map<Long, Chat> conversations = new LinkedHashMap<>();

        // Sắp xếp theo thời gian mới nhất
        lastMessages.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));

        for (Chat chat : lastMessages) {
            Long partnerId = chat.getSender().getId().equals(userId)
                    ? chat.getReceiver().getId()
                    : chat.getSender().getId();

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
            conv.put("isSentByMe", lastChat.getSender().getId().equals(userId));

            result.add(conv);
        }

        return result; // Đã sort rồi nên hiển thị theo thứ tự mới nhất
    }
}