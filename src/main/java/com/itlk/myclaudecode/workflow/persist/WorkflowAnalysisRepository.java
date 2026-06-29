package com.itlk.myclaudecode.workflow.persist;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowAnalysisRepository extends JpaRepository<WorkflowAnalysis, Long> {
    List<WorkflowAnalysis> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<WorkflowAnalysis> findByConversationIdOrderByCreatedAtDesc(Long conversationId);
}
