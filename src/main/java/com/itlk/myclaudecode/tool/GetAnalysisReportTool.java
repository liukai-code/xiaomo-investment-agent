package com.itlk.myclaudecode.tool;

import com.itlk.myclaudecode.analysis.service.AnalysisService;
import com.itlk.myclaudecode.workflow.persist.WorkflowAnalysis;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class GetAnalysisReportTool {

    private final AnalysisService analysisService;

    public GetAnalysisReportTool(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @Tool(description = "查询指定股票的深度分析报告。当用户询问某只股票的深度分析结论、分析报告、投资建议时调用此工具。返回最近一次深度分析的完整结论，包括操作建议、置信度、目标价、综合摘要和交易方案。")
    public String getAnalysisReport(
            @ToolParam(description = "股票代码（如688398）或股票名称（如丰光精密）") String stockCode,
            @ToolParam(description = "指定分析记录ID，可选，不填则查询该股票最近一次分析") Long analysisId) {
        try {
            // 优先按 analysisId 查询
            if (analysisId != null) {
                // 通过 analysisId 直接查询，权限校验在 Controller 层
            }

            // 通过 stockCode 查询最近一次分析
            if (stockCode == null || stockCode.isBlank()) {
                return "错误：请提供股票代码或股票名称";
            }

            var analyses = analysisService.findLatestByStockAny(stockCode);
            if (analyses.isEmpty()) {
                return "未找到「" + stockCode + "」的深度分析记录。请先在深度分析页面（/analysis）发起分析。";
            }

            var analysis = analyses.get();
            StringBuilder sb = new StringBuilder();
            sb.append("## ").append(analysis.getResolvedStockName() != null ? analysis.getResolvedStockName() : stockCode);
            sb.append(" 深度分析报告\n\n");
            sb.append("- 分析时间: ").append(analysis.getCompletedAt() != null ? analysis.getCompletedAt() : analysis.getCreatedAt()).append("\n");
            sb.append("- 状态: ").append(analysis.getWorkflowStatus()).append("\n\n");

            if (analysis.getAction() != null) {
                sb.append("### 投资决策\n");
                sb.append("- 操作建议: **").append(analysis.getAction()).append("**\n");
                if (analysis.getConfidence() != null) {
                    sb.append("- 置信度: ").append(String.format("%.0f%%", analysis.getConfidence() * 100)).append("\n");
                }
                if (analysis.getTargetPrice() != null) {
                    sb.append("- 目标价: ¥").append(analysis.getTargetPrice()).append("\n");
                }
                sb.append("\n");
            }

            if (analysis.getSummary() != null && !analysis.getSummary().isBlank()) {
                sb.append("### 综合摘要\n").append(analysis.getSummary()).append("\n\n");
            }

            if (analysis.getInvestmentPlan() != null && !analysis.getInvestmentPlan().isBlank()) {
                sb.append("### 投资计划\n").append(analysis.getInvestmentPlan()).append("\n\n");
            }

            if (analysis.getTradingProposal() != null && !analysis.getTradingProposal().isBlank()) {
                sb.append("### 交易方案\n").append(analysis.getTradingProposal()).append("\n\n");
            }

            sb.append("---\n*以上为深度分析系统自动生成，仅供参考，不构成投资建议*");
            return sb.toString();
        } catch (Exception e) {
            return "查询分析报告时出错: " + e.getMessage();
        }
    }
}
