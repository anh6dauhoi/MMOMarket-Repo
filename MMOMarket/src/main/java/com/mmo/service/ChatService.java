package com.mmo.service;

import com.mmo.dto.ChatMessageDto;
import com.mmo.dto.ConversationSummaryDto;
import com.mmo.entity.Chat;
import com.mmo.entity.User;
import com.mmo.repository.ChatRepository;
import com.mmo.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class ChatService {
    private final ChatRepository chatRepository;
    private final UserRepository userRepository;
    private final ChatSseService chatSseService;

    public ChatService(ChatRepository chatRepository, UserRepository userRepository, ChatSseService chatSseService) {
        this.chatRepository = chatRepository;
        this.userRepository = userRepository;
        this.chatSseService = chatSseService;
    }

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
            list.add(dto);
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

        Chat chat = new Chat();
        chat.setSender(sender);
        chat.setReceiver(receiver);
        chat.setMessage(message.trim());
        chat.setDelete(false);
        Chat saved = chatRepository.save(chat);
        ChatMessageDto dto = new ChatMessageDto(saved);
        // Broadcast to both sender and receiver via SSE
        chatSseService.broadcast(dto);
        return dto;
    }

    @Transactional
    public ChatMessageDto sendWithFile(Long senderId, Long receiverId, String message,
                                       String filePath, String fileType, String fileName, Long fileSize) {
        validatePair(senderId, receiverId);
        User sender = requireActiveUser(senderId);
        User receiver = requireActiveUser(receiverId);

        Chat chat = new Chat();
        chat.setSender(sender);
        chat.setReceiver(receiver);
        chat.setMessage(message != null && !message.trim().isEmpty() ? message.trim() : "Sent a file");
        chat.setFilePath(filePath);
        chat.setFileType(fileType);
        chat.setFileName(fileName);
        chat.setFileSize(fileSize);
        chat.setDelete(false);

        Chat saved = chatRepository.save(chat);
        ChatMessageDto dto = new ChatMessageDto(saved);
        chatSseService.broadcast(dto);
        return dto;
    }

    private void validatePair(Long a, Long b) {
        if (a == null || b == null) throw new IllegalArgumentException("Invalid user(s)");
        if (a.equals(b)) throw new IllegalArgumentException("Cannot chat with yourself");
        User u1 = requireActiveUser(a);
        User u2 = requireActiveUser(b);
        if (!isValidChatPair(u1, u2)) {
            throw new IllegalArgumentException("Chat not allowed between " + u1.getRole() + " and " + u2.getRole());
        }
    }

    private User requireActiveUser(Long id) {
        Optional<User> opt = userRepository.findById(id);
        User u = opt.orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        if (u.isDelete()) throw new IllegalArgumentException("User not found: " + id);
        return u;
    }

    private boolean isValidChatPair(User u1, User u2) {
        String r1 = (u1.getRole() == null ? "" : u1.getRole()).toUpperCase(Locale.ROOT);
        String r2 = (u2.getRole() == null ? "" : u2.getRole()).toUpperCase(Locale.ROOT);

        // Remove ROLE_ prefix if present
        if (r1.startsWith("ROLE_")) r1 = r1.substring(5);
        if (r2.startsWith("ROLE_")) r2 = r2.substring(5);

        // Allow ADMIN to chat with anyone
        if ("ADMIN".equals(r1) || "ADMIN".equals(r2)) {
            return true;
        }

        // Allow CUSTOMER-CUSTOMER chats (users can be both buyer and seller)
        if ("CUSTOMER".equals(r1) && "CUSTOMER".equals(r2)) {
            return true;
        }

        // Allow CUSTOMER-SELLER pairs
        boolean oneSeller = "SELLER".equals(r1) || "SELLER".equals(r2);
        boolean oneCustomer = "CUSTOMER".equals(r1) || "CUSTOMER".equals(r2);
        if (oneSeller && oneCustomer) {
            return true;
        }

        // Disallow SELLER-SELLER chats (if needed, you can enable this too)
        return false;
    }
}
