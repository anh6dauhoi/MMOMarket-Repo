package com.mmo.repository;

import com.mmo.entity.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ChatRepository extends JpaRepository<Chat, Long> {

    // Lấy tin nhắn giữa 2 user (sắp xếp theo thời gian)
    @Query("SELECT c FROM Chat c WHERE " +
            "((c.sender.id = ?1 AND c.receiver.id = ?2) OR " +
            "(c.sender.id = ?2 AND c.receiver.id = ?1)) " +
            "AND c.isDelete = false " +
            "ORDER BY c.createdAt ASC")
    List<Chat> findConversation(Long userId1, Long userId2);

    // Lấy danh sách conversations (tin nhắn cuối cùng với mỗi người)
    @Query("SELECT c FROM Chat c WHERE " +
            "(c.sender.id = ?1 OR c.receiver.id = ?1) " +
            "AND c.isDelete = false " +
            "AND c.createdAt = (" +
            "    SELECT MAX(c2.createdAt) FROM Chat c2 WHERE " +
            "    ((c2.sender.id = c.sender.id AND c2.receiver.id = c.receiver.id) OR " +
            "     (c2.sender.id = c.receiver.id AND c2.receiver.id = c.sender.id)) " +
            "    AND c2.isDelete = false" +
            ") " +
            "ORDER BY c.createdAt DESC")
    List<Chat> findConversationList(Long userId);
}