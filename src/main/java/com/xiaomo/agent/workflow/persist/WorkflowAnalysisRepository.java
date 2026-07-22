package com.xiaomo.agent.workflow.persist;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkflowAnalysisRepository extends JpaRepository<WorkflowAnalysis, Long> {
    List<WorkflowAnalysis> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<WorkflowAnalysis> findByConversationIdOrderByCreatedAtDesc(Long conversationId);

    // 按用户+状态查询（检查是否有运行中的分析）
    List<WorkflowAnalysis> findByUserIdAndWorkflowStatus(Long userId, String workflowStatus);

    // 按用户+标的代码查询（对话 Tool 使用）
    Optional<WorkflowAnalysis> findFirstByUserIdAndResolvedStockCodeOrderByCreatedAtDesc(Long userId, String stockCode);

    // 按标的代码查询已完成的分析（Tool 使用，不限 userId）
    Optional<WorkflowAnalysis> findFirstByResolvedStockCodeAndWorkflowStatusOrderByCreatedAtDesc(String stockCode, String status);

    // 按标的名称查询已完成的分析（模糊匹配）
    Optional<WorkflowAnalysis> findFirstByResolvedStockNameContainingAndWorkflowStatusOrderByCreatedAtDesc(String stockName, String status);
}
