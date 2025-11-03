package com.mmo.controller;

import com.mmo.entity.Chat;
import com.mmo.entity.User;
import com.mmo.service.ChatService;
import com.mmo.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @Autowired
    private UserService userService;

    // Trang danh sách conversations
    @GetMapping
    public String chatList(@RequestParam(required = false) Long sellerId,
                           Model model,
                           Authentication authentication) {
        User currentUser = userService.findByEmail(authentication.getName());
        List<Map<String, Object>> conversations = chatService.getConversationList(currentUser.getId());

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("conversations", conversations);
        model.addAttribute("sellerId", sellerId); // Truyền sellerId vào view
        return "chat/chat";
    }

    // API: Gửi tin nhắn (AJAX)
    @PostMapping("/send")
    @ResponseBody
    public Map<String, Object> sendMessage(@RequestParam Long receiverId,
                                           @RequestParam String message,
                                           Authentication authentication) {
        User sender = userService.findByEmail(authentication.getName());
        Chat chat = chatService.sendMessage(sender.getId(), receiverId, message);

        return Map.of(
                "success", true,
                "message", chat
        );
    }

    // API: Polling - Lấy tin nhắn mới (AJAX)
    @GetMapping("/messages/{receiverId}")
    @ResponseBody
    public List<Chat> getMessages(@PathVariable Long receiverId,
                                  Authentication authentication) {
        User currentUser = userService.findByEmail(authentication.getName());
        return chatService.getConversation(currentUser.getId(), receiverId);
    }
}