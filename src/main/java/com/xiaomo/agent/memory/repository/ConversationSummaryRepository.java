package com.xiaomo.agent.memory.repository;

import com.xiaomo.agent.memory.entity.ConversationSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationSummaryRepository extends JpaRepository<ConversationSummary, Long> {

    @Query("SELECT s FROM ConversationSummary s WHERE s.conversationId = :convId " +
           "ORDER BY s.createdAt DESC LIMIT 1")
    ConversationSummary findLatestByConversationId(@Param("convId") Long convId);
}
