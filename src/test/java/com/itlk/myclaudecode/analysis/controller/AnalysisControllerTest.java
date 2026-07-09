package com.itlk.myclaudecode.analysis.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itlk.myclaudecode.analysis.service.AnalysisService;
import com.itlk.myclaudecode.common.entity.Result;
import com.itlk.myclaudecode.workflow.persist.WorkflowAnalysis;
import com.itlk.myclaudecode.workflow.service.DeepAnalysisWorkflow;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnalysisController 分析控制器测试")
class AnalysisControllerTest {

    @Mock
    private AnalysisService analysisService;
    @Mock
    private DeepAnalysisWorkflow deepAnalysisWorkflow;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AnalysisController controller;

    @Mock
    private HttpServletRequest request;

    private final Long userId = 1L;

    @BeforeEach
    void setUp() {
        // 默认所有请求都已登录
        lenient().when(request.getAttribute("userId")).thenReturn(userId);
    }

    // ========== getUserId 认证检查 ==========

    @Nested
    @DisplayName("getUserId 认证检查")
    class AuthTest {

        @Test
        @DisplayName("未登录 → 抛出 RuntimeException")
        void notLoggedIn() {
            when(request.getAttribute("userId")).thenReturn(null);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> controller.listAnalyses(request));
            assertTrue(ex.getMessage().contains("未登录"), "应包含'未登录'提示");
        }
    }

    // ========== startAnalysis 发起分析 ==========

    @Nested
    @DisplayName("startAnalysis 发起分析")
    class StartAnalysisTest {

        @Test
        @DisplayName("query 为 null → 返回错误")
        void nullQuery() {
            Map<String, String> body = Map.of();

            Result<Map<String, Object>> result = controller.startAnalysis(body, request);

            assertEquals(0, result.getCode(), "code 应为 0（失败）");
            assertTrue(result.getMsg().contains("分析主题不能为空"), "应提示分析主题不能为空");
            verifyNoInteractions(analysisService);
        }

        @Test
        @DisplayName("query 为空字符串 → 返回错误")
        void emptyQuery() {
            Map<String, String> body = Map.of("query", "");

            Result<Map<String, Object>> result = controller.startAnalysis(body, request);

            assertEquals(0, result.getCode(), "code 应为 0（失败）");
            assertTrue(result.getMsg().contains("分析主题不能为空"), "应提示分析主题不能为空");
            verifyNoInteractions(analysisService);
        }

        @Test
        @DisplayName("query 为空白字符串 → 返回错误")
        void blankQuery() {
            Map<String, String> body = Map.of("query", "   ");

            Result<Map<String, Object>> result = controller.startAnalysis(body, request);

            assertEquals(0, result.getCode(), "code 应为 0（失败）");
            assertTrue(result.getMsg().contains("分析主题不能为空"), "应提示分析主题不能为空");
            verifyNoInteractions(analysisService);
        }

        @Test
        @DisplayName("已有运行中分析 → 抛出 IllegalStateException")
        void alreadyRunning() {
            Map<String, String> body = Map.of("query", "分析茅台");
            when(analysisService.createAnalysis(eq(userId), eq("分析茅台"), any(), any()))
                    .thenThrow(new IllegalStateException("已有分析正在运行中，请等待完成后再发起新的分析"));

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> controller.startAnalysis(body, request));
            assertTrue(ex.getMessage().contains("已有分析正在运行中"), "应提示已有分析运行中");
        }

        @Test
        @DisplayName("正常发起分析（含股票代码） → 返回分析 ID 和标的信息")
        void successWithStockCode() {
            Map<String, String> body = Map.of("query", "分析600519贵州茅台");

            WorkflowAnalysis saved = new WorkflowAnalysis();
            saved.setId(42L);
            saved.setWorkflowStatus("PENDING");
            saved.setStartedAt(LocalDateTime.now());
            when(analysisService.createAnalysis(eq(userId), eq("分析600519贵州茅台"), eq("600519"), any()))
                    .thenReturn(saved);
            // 异步工作流返回空 Flux（异步执行，可能不被调用，使用 lenient）
            lenient().when(deepAnalysisWorkflow.executeWithAnalysisId(userId, 42L, "分析600519贵州茅台"))
                    .thenReturn(Flux.empty());

            Result<Map<String, Object>> result = controller.startAnalysis(body, request);

            assertEquals(1, result.getCode(), "code 应为 1（成功）");
            Map<String, Object> data = result.getData();
            assertEquals(42L, data.get("analysisId"), "analysisId 应为 42");
            assertEquals("600519", data.get("stockCode"), "stockCode 应为 600519");
        }

        @Test
        @DisplayName("正常发起分析（无股票代码） → stockCode 为空字符串")
        void successWithoutStockCode() {
            Map<String, String> body = Map.of("query", "分析茅台行情走势");

            WorkflowAnalysis saved = new WorkflowAnalysis();
            saved.setId(43L);
            saved.setWorkflowStatus("PENDING");
            saved.setStartedAt(LocalDateTime.now());
            when(analysisService.createAnalysis(eq(userId), eq("分析茅台行情走势"), any(), any()))
                    .thenReturn(saved);
            // 异步工作流（可能不被调用，使用 lenient）
            lenient().when(deepAnalysisWorkflow.executeWithAnalysisId(eq(userId), eq(43L), any()))
                    .thenReturn(Flux.empty());

            Result<Map<String, Object>> result = controller.startAnalysis(body, request);

            assertEquals(1, result.getCode(), "code 应为 1（成功）");
            Map<String, Object> data = result.getData();
            assertEquals(43L, data.get("analysisId"), "analysisId 应为 43");
            assertEquals("", data.get("stockCode"), "无股票代码时 stockCode 应为空字符串");
        }
    }

    // ========== listAnalyses 分析列表 ==========

    @Nested
    @DisplayName("listAnalyses 分析列表")
    class ListAnalysesTest {

        @Test
        @DisplayName("正常返回用户分析列表")
        void success() {
            WorkflowAnalysis a1 = new WorkflowAnalysis();
            a1.setId(1L);
            a1.setUserId(userId);
            a1.setOriginalQuery("分析茅台");
            a1.setWorkflowStatus("COMPLETED");

            WorkflowAnalysis a2 = new WorkflowAnalysis();
            a2.setId(2L);
            a2.setUserId(userId);
            a2.setOriginalQuery("分析平安银行");
            a2.setWorkflowStatus("PENDING");

            when(analysisService.listAnalyses(userId)).thenReturn(List.of(a2, a1));

            Result<List<WorkflowAnalysis>> result = controller.listAnalyses(request);

            assertEquals(1, result.getCode(), "code 应为 1（成功）");
            assertEquals(2, result.getData().size(), "应返回 2 条分析记录");
            assertEquals(2L, result.getData().get(0).getId(), "第一条记录 ID 应为 2");
            assertEquals(1L, result.getData().get(1).getId(), "第二条记录 ID 应为 1");
        }

        @Test
        @DisplayName("无分析记录 → 返回空列表")
        void emptyList() {
            when(analysisService.listAnalyses(userId)).thenReturn(List.of());

            Result<List<WorkflowAnalysis>> result = controller.listAnalyses(request);

            assertEquals(1, result.getCode(), "code 应为 1（成功）");
            assertTrue(result.getData().isEmpty(), "应返回空列表");
        }
    }

    // ========== getAnalysis 分析详情 ==========

    @Nested
    @DisplayName("getAnalysis 分析详情")
    class GetAnalysisTest {

        @Test
        @DisplayName("正常获取分析详情")
        void success() {
            WorkflowAnalysis analysis = new WorkflowAnalysis();
            analysis.setId(10L);
            analysis.setUserId(userId);
            analysis.setOriginalQuery("分析600519");
            analysis.setWorkflowStatus("COMPLETED");
            analysis.setAction("BUY");
            analysis.setConfidence(0.85);
            analysis.setSummary("茅台基本面优秀");

            when(analysisService.getAnalysis(10L, userId)).thenReturn(analysis);

            Result<WorkflowAnalysis> result = controller.getAnalysis(10L, request);

            assertEquals(1, result.getCode(), "code 应为 1（成功）");
            assertEquals(10L, result.getData().getId(), "分析 ID 应为 10");
            assertEquals("BUY", result.getData().getAction(), "操作建议应为 BUY");
            assertEquals(0.85, result.getData().getConfidence(), "置信度应为 0.85");
            assertTrue(result.getData().getSummary().contains("茅台基本面优秀"), "摘要应包含预期内容");
        }

        @Test
        @DisplayName("分析不存在 → 抛出异常")
        void notFound() {
            when(analysisService.getAnalysis(999L, userId))
                    .thenThrow(new RuntimeException("分析记录不存在"));

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> controller.getAnalysis(999L, request));
            assertTrue(ex.getMessage().contains("分析记录不存在"), "应提示分析记录不存在");
        }

        @Test
        @DisplayName("无权访问他人分析 → 抛出异常")
        void accessDenied() {
            when(analysisService.getAnalysis(10L, userId))
                    .thenThrow(new RuntimeException("无权访问该分析记录"));

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> controller.getAnalysis(10L, request));
            assertTrue(ex.getMessage().contains("无权访问"), "应提示无权访问");
        }
    }

    // ========== deleteAnalysis 删除分析 ==========

    @Nested
    @DisplayName("deleteAnalysis 删除分析")
    class DeleteAnalysisTest {

        @Test
        @DisplayName("正常删除分析记录")
        void success() {
            doNothing().when(analysisService).deleteAnalysis(10L, userId);

            Result<Void> result = controller.deleteAnalysis(10L, request);

            assertEquals(1, result.getCode(), "code 应为 1（成功）");
            verify(analysisService).deleteAnalysis(10L, userId);
        }

        @Test
        @DisplayName("删除运行中的分析 → 抛出 IllegalStateException")
        void deleteRunning() {
            doThrow(new IllegalStateException("不能删除正在运行的分析"))
                    .when(analysisService).deleteAnalysis(10L, userId);

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> controller.deleteAnalysis(10L, request));
            assertTrue(ex.getMessage().contains("不能删除正在运行的分析"), "应提示不能删除运行中的分析");
        }

        @Test
        @DisplayName("删除不存在的分析 → 抛出异常")
        void deleteNotFound() {
            doThrow(new RuntimeException("分析记录不存在"))
                    .when(analysisService).deleteAnalysis(999L, userId);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> controller.deleteAnalysis(999L, request));
            assertTrue(ex.getMessage().contains("分析记录不存在"), "应提示分析记录不存在");
        }
    }
}
