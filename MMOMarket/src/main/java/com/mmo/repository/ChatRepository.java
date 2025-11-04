package com.mmo.repository;

import com.mmo.entity.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;

public interface ChatRepository extends JpaRepository<Chat, Long> {
    // Fetch full conversation between two users (both directions), excluding soft-deleted rows
    @Query("SELECT c FROM Chat c WHERE c.isDelete = false AND ((c.sender.id = :u1 AND c.receiver.id = :u2) OR (c.sender.id = :u2 AND c.receiver.id = :u1)) ORDER BY c.createdAt ASC, c.id ASC")
    List<Chat> findConversation(@Param("u1") Long user1Id, @Param("u2") Long user2Id);

    // Sidebar: latest message per partner for a given user
    interface ConversationSummaryProjection {
        Long getPartnerId();
        String getPartnerName();
        String getLastMessage();
        Date getLastMessageTime();
        Integer getIsSentByMe();  // Changed from Boolean to Integer for MySQL TINYINT
    }

    @Query(value = """
            WITH latest AS (
              SELECT
                CASE WHEN c.sender_id = :userId THEN c.receiver_id ELSE c.sender_id END AS partner_id,
                MAX(c.id) AS last_chat_id
              FROM Chats c
              WHERE c.isDelete = 0 AND (c.sender_id = :userId OR c.receiver_id = :userId)
              GROUP BY partner_id
            )
            SELECT
              l.partner_id AS partnerId,
              u.full_name AS partnerName,
              ch.message AS lastMessage,
              ch.created_at AS lastMessageTime,
              (ch.sender_id = :userId) AS isSentByMe
            FROM latest l
            JOIN Chats ch ON ch.id = l.last_chat_id
            JOIN Users u ON u.id = l.partner_id
            ORDER BY ch.created_at DESC, ch.id DESC
            """,
            nativeQuery = true)
    List<ConversationSummaryProjection> findConversationSummaries(@Param("userId") Long userId);
}
