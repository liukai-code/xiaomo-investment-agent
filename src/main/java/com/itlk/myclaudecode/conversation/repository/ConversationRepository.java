package com.itlk.myclaudecode.conversation.repository;

import com.itlk.myclaudecode.conversation.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    List<Conversation> findAllByOrderByUpdatedAtDesc();

    List<Conversation> findByUserIdOrderByUpdatedAtDesc(Long userId);
}
