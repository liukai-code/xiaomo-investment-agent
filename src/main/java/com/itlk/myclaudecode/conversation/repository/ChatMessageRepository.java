package com.itlk.myclaudecode.conversation.repository;

import com.itlk.myclaudecode.conversation.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByConversationIdOrderByIdAsc(Long conversationId);

    @Query("SELECT m FROM ChatMessage m WHERE m.conversation.id = :convId ORDER BY m.id DESC LIMIT :limit")
    List<ChatMessage> findRecentByConversationId(@Param("convId") Long conversationId, @Param("limit") int limit);

    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.conversation.userId = :userId")
    Long countByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.conversation.userId = :userId AND m.createdAt > :since")
    Long countByUserIdSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);
}
