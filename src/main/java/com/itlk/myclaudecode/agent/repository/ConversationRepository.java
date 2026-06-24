package com.itlk.myclaudecode.agent.repository;

import com.itlk.myclaudecode.agent.Entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    List<Conversation> findAllByOrderByUpdatedAtDesc();
}
