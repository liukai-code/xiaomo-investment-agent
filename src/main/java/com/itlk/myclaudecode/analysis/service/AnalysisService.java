package com.itlk.myclaudecode.analysis.service;

import com.itlk.myclaudecode.workflow.persist.WorkflowAnalysis;
import com.itlk.myclaudecode.workflow.persist.WorkflowAnalysisRepository;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class AnalysisService {

    @Resource
    private WorkflowAnalysisRepository analysisRepository;

    public AnalysisService() {
    }

    public AnalysisService(WorkflowAnalysisRepository analysisRepository) {
        this.analysisRepository = analysisRepository;
    }

    /**
     * 创建分析记录（PENDING 状态）
     */
    @Transactional
    public WorkflowAnalysis createAnalysis(Long userId, String query, String stockCode, String stockName) {
        // 检查是否有运行中的分析
        List<WorkflowAnalysis> running = analysisRepository.findByUserIdAndWorkflowStatus(userId, "RUNNING");
        if (!running.isEmpty()) {
            throw new IllegalStateException("已有分析正在运行中，请等待完成后再发起新的分析");
        }

        WorkflowAnalysis analysis = new WorkflowAnalysis();
        analysis.setUserId(userId);
        analysis.setOriginalQuery(query);
        analysis.setResolvedStockCode(stockCode);
        analysis.setResolvedStockName(stockName);
        analysis.setWorkflowStatus("PENDING");
        analysis.setStartedAt(LocalDateTime.now());
        return analysisRepository.save(analysis);
    }

    /**
     * 获取用户的分析列表
     */
    public List<WorkflowAnalysis> listAnalyses(Long userId) {
        return analysisRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * 获取单条分析详情
     */
    public WorkflowAnalysis getAnalysis(Long id, Long userId) {
        WorkflowAnalysis analysis = analysisRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("分析记录不存在"));
        if (!analysis.getUserId().equals(userId)) {
            throw new RuntimeException("无权访问该分析记录");
        }
        return analysis;
    }

    /**
     * 删除分析记录
     */
    @Transactional
    public void deleteAnalysis(Long id, Long userId) {
        WorkflowAnalysis analysis = getAnalysis(id, userId);
        if ("RUNNING".equals(analysis.getWorkflowStatus())) {
            throw new IllegalStateException("不能删除正在运行的分析");
        }
        analysisRepository.delete(analysis);
    }

    /**
     * 按 ID 查询分析记录（供 Tool 使用，不校验 userId，权限由 Controller 层负责）
     */
    public Optional<WorkflowAnalysis> findById(Long id) {
        return analysisRepository.findById(id);
    }

    /**
     * 按标的查询最近一次分析（供 Tool 使用）
     */
    public Optional<WorkflowAnalysis> findLatestByStock(Long userId, String stockCode) {
        return analysisRepository.findFirstByUserIdAndResolvedStockCodeOrderByCreatedAtDesc(userId, stockCode);
    }

    /**
     * 按标的名称模糊查询最近一次已完成分析（Tool 使用，不指定 userId）
     * 先尝试精确匹配 stockCode，再尝试按 stockName 模糊匹配
     */
    public Optional<WorkflowAnalysis> findLatestByStockAny(String stockCodeOrName) {
        // 先尝试精确匹配 stockCode
        var byCode = analysisRepository.findFirstByResolvedStockCodeAndWorkflowStatusOrderByCreatedAtDesc(stockCodeOrName, "COMPLETED");
        if (byCode.isPresent()) return byCode;
        // 再尝试按 stockName 模糊匹配
        return analysisRepository.findFirstByResolvedStockNameContainingAndWorkflowStatusOrderByCreatedAtDesc(stockCodeOrName, "COMPLETED");
    }
}
