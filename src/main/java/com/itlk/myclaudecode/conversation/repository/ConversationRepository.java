package com.itlk.myclaudecode.conversation.repository;

import com.itlk.myclaudecode.conversation.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    List<Conversation> findAllByOrderByUpdatedAtDesc();

    List<Conversation> findByUserIdOrderByUpdatedAtDesc(Long userId);

    Conversation findFirstByUserIdAndTitleOrderByUpdatedAtDesc(Long userId, String title);

    @Query("SELECT COUNT(c) FROM Conversation c WHERE c.userId = :userId")
    Long countByUserId(@Param("userId") Long userId);
}
