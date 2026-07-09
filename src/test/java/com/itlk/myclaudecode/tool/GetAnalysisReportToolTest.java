package com.itlk.myclaudecode.tool;

import com.itlk.myclaudecode.analysis.service.AnalysisService;
import com.itlk.myclaudecode.workflow.persist.WorkflowAnalysis;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetAnalysisReportTool 分析报告查询工具测试")
class GetAnalysisReportToolTest {

    @Mock
    private AnalysisService analysisService;

    private GetAnalysisReportTool tool;

    @BeforeEach
    void setUp() {
        tool = new GetAnalysisReportTool(analysisService);
    }

    @Nested
    @DisplayName("getAnalysisReport 查询分析报告")
    class GetReportTest {

        @Test
        @DisplayName("analysisId 有效且存在 → 按 ID 返回报告")
        void findByAnalysisId() {
            WorkflowAnalysis analysis = new WorkflowAnalysis();
            analysis.setId(100L);
            analysis.setResolvedStockName("贵州茅台");
            analysis.setResolvedStockCode("600519");
            analysis.setWorkflowStatus("COMPLETED");
            analysis.setAction("BUY");
            analysis.setConfidence(0.9);
            analysis.setSummary("ID 查询测试");
            analysis.setCompletedAt(LocalDateTime.of(2026, 7, 9, 10, 0));
            analysis.setCreatedAt(LocalDateTime.of(2026, 7, 9, 9, 0));

            when(analysisService.findById(100L)).thenReturn(Optional.of(analysis));

            String result = tool.getAnalysisReport("任意", 100L);

            assertTrue(result.contains("贵州茅台"), "应包含股票名称");
            assertTrue(result.contains("BUY"), "应包含操作建议");
            assertTrue(result.contains("ID 查询测试"), "应包含摘要");
            assertTrue(result.contains("深度分析报告"), "应包含报告标题");
        }

        @Test
        @DisplayName("analysisId 指定但不存在 → 返回未找到提示")
        void analysisIdNotFound() {
            when(analysisService.findById(999L)).thenReturn(Optional.empty());

            String result = tool.getAnalysisReport("任意", 999L);

            assertTrue(result.contains("未找到"), "应返回未找到提示");
            assertTrue(result.contains("999"), "应包含查询的ID");
        }

        @Test
        @DisplayName("analysisId 指定时跳过 stockCode 查询")
        void analysisIdSkipsStockCode() {
            WorkflowAnalysis analysis = new WorkflowAnalysis();
            analysis.setId(100L);
            analysis.setResolvedStockName("贵州茅台");
            analysis.setWorkflowStatus("COMPLETED");
            analysis.setCreatedAt(LocalDateTime.of(2026, 7, 9, 9, 0));

            when(analysisService.findById(100L)).thenReturn(Optional.of(analysis));

            // stockCode 为空，但 analysisId 存在 → 不应报错
            String result = tool.getAnalysisReport("", 100L);

            assertTrue(result.contains("贵州茅台"), "应通过 analysisId 返回报告");
        }

        @Test
        @DisplayName("analysisId 为 null 且 stockCode 为空 → 返回错误提示")
        void bothNull() {
            String result = tool.getAnalysisReport(null, null);
            assertTrue(result.contains("请提供股票代码"), "应提示提供股票代码");
        }

        @Test
        @DisplayName("stockCode 为空 → 返回错误提示")
        void emptyStockCode() {
            String result = tool.getAnalysisReport("", null);
            assertTrue(result.contains("请提供股票代码"), "应提示提供股票代码");
        }

        @Test
        @DisplayName("stockCode 为 null → 返回错误提示")
        void nullStockCode() {
            String result = tool.getAnalysisReport(null, null);
            assertTrue(result.contains("请提供股票代码"), "应提示提供股票代码");
        }

        @Test
        @DisplayName("未找到分析 → 返回提示信息")
        void notFound() {
            when(analysisService.findLatestByStockAny("不存在"))
                    .thenReturn(Optional.empty());

            String result = tool.getAnalysisReport("不存在", null);

            assertTrue(result.contains("未找到"), "应返回未找到提示");
            assertTrue(result.contains("不存在"), "应包含查询的标的");
        }

        @Test
        @DisplayName("找到分析 → 返回包含操作建议和置信度的报告")
        void found() {
            WorkflowAnalysis analysis = new WorkflowAnalysis();
            analysis.setId(1L);
            analysis.setResolvedStockName("贵州茅台");
            analysis.setResolvedStockCode("600519");
            analysis.setWorkflowStatus("COMPLETED");
            analysis.setAction("BUY");
            analysis.setConfidence(0.85);
            analysis.setTargetPrice(2000.0);
            analysis.setSummary("基本面优秀，估值合理");
            analysis.setInvestmentPlan("分批建仓");
            analysis.setTradingProposal("1800元以下逐步买入");
            analysis.setCompletedAt(LocalDateTime.of(2026, 7, 9, 10, 0));
            analysis.setCreatedAt(LocalDateTime.of(2026, 7, 9, 9, 0));

            when(analysisService.findLatestByStockAny("600519"))
                    .thenReturn(Optional.of(analysis));

            String result = tool.getAnalysisReport("600519", null);

            assertTrue(result.contains("贵州茅台"), "应包含股票名称");
            assertTrue(result.contains("BUY"), "应包含操作建议");
            assertTrue(result.contains("85%"), "应包含置信度");
            assertTrue(result.contains("2000.0"), "应包含目标价");
            assertTrue(result.contains("基本面优秀"), "应包含摘要");
            assertTrue(result.contains("分批建仓"), "应包含投资计划");
            assertTrue(result.contains("1800元以下逐步买入"), "应包含交易方案");
            assertTrue(result.contains("深度分析报告"), "应包含报告标题");
        }

        @Test
        @DisplayName("分析无决策信息 → 仍然正常返回")
        void foundWithoutDecision() {
            WorkflowAnalysis analysis = new WorkflowAnalysis();
            analysis.setId(2L);
            analysis.setResolvedStockName("丰光精密");
            analysis.setResolvedStockCode("430510");
            analysis.setWorkflowStatus("COMPLETED");
            analysis.setCreatedAt(LocalDateTime.of(2026, 7, 9, 9, 0));

            when(analysisService.findLatestByStockAny("丰光精密"))
                    .thenReturn(Optional.of(analysis));

            String result = tool.getAnalysisReport("丰光精密", null);

            assertTrue(result.contains("丰光精密"), "应包含股票名称");
            assertTrue(result.contains("深度分析报告"), "应包含报告标题");
            assertFalse(result.contains("投资决策"), "无决策时不应包含投资决策段");
        }
    }
}
