package com.itlk.myclaudecode.agent.repository;

import com.itlk.myclaudecode.agent.Entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByConversationIdOrderByIdAsc(Long conversationId);

    @Query("SELECT m FROM ChatMessage m WHERE m.conversation.id = :convId ORDER BY m.id DESC LIMIT :limit")
    List<ChatMessage> findRecentByConversationId(@Param("convId") Long conversationId, @Param("limit") int limit);
}
