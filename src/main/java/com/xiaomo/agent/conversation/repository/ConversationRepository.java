package com.xiaomo.agent.conversation.repository;

import com.xiaomo.agent.conversation.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    List<Conversation> findAllByOrderByUpdatedAtDesc();

    @Query("SELECT c FROM Conversation c WHERE c.userId = :userId ORDER BY COALESCE(c.pinned, false) DESC, c.updatedAt DESC")
    List<Conversation> findByUserIdOrderByPinnedAndUpdatedAt(@Param("userId") Long userId);

    Conversation findFirstByUserIdAndTitleOrderByUpdatedAtDesc(Long userId, String title);

    @Query("SELECT COUNT(c) FROM Conversation c WHERE c.userId = :userId")
    Long countByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(c) FROM Conversation c WHERE c.userId = :userId AND c.createdAt > :since")
    Long countByUserIdSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);
}
