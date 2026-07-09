package com.itlk.myclaudecode.analysis.service;

import com.itlk.myclaudecode.workflow.persist.WorkflowAnalysis;
import com.itlk.myclaudecode.workflow.persist.WorkflowAnalysisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnalysisService 分析服务测试")
class AnalysisServiceTest {

    @Mock
    private WorkflowAnalysisRepository analysisRepository;

    private AnalysisService analysisService;

    @BeforeEach
    void setUp() {
        analysisService = new AnalysisService(analysisRepository);
    }

    @Nested
    @DisplayName("createAnalysis 创建分析")
    class CreateAnalysisTest {

        @Test
        @DisplayName("已有运行中分析 → 抛出异常")
        void runningAnalysisExists() {
            WorkflowAnalysis running = new WorkflowAnalysis();
            running.setWorkflowStatus("RUNNING");
            when(analysisRepository.findByUserIdAndWorkflowStatus(1L, "RUNNING"))
                    .thenReturn(List.of(running));

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> analysisService.createAnalysis(1L, "分析茅台", "600519", "贵州茅台"));
            assertTrue(ex.getMessage().contains("已有分析正在运行中"));
        }

        @Test
        @DisplayName("正常创建 → 返回 PENDING 状态记录")
        void createSuccess() {
            when(analysisRepository.findByUserIdAndWorkflowStatus(1L, "RUNNING"))
                    .thenReturn(List.of());
            WorkflowAnalysis saved = new WorkflowAnalysis();
            saved.setId(100L);
            saved.setWorkflowStatus("PENDING");
            saved.setStartedAt(LocalDateTime.now());
            when(analysisRepository.save(any(WorkflowAnalysis.class))).thenReturn(saved);

            WorkflowAnalysis result = analysisService.createAnalysis(1L, "分析茅台", "600519", "贵州茅台");

            assertEquals("PENDING", result.getWorkflowStatus());
            assertEquals(100L, result.getId());
            verify(analysisRepository).save(any(WorkflowAnalysis.class));
        }
    }

    @Nested
    @DisplayName("getAnalysis 获取分析")
    class GetAnalysisTest {

        @Test
        @DisplayName("分析不存在 → 抛出异常")
        void notFound() {
            when(analysisRepository.findById(999L)).thenReturn(Optional.empty());

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> analysisService.getAnalysis(999L, 1L));
            assertTrue(ex.getMessage().contains("分析记录不存在"));
        }

        @Test
        @DisplayName("用户不匹配 → 抛出异常")
        void wrongUser() {
            WorkflowAnalysis analysis = new WorkflowAnalysis();
            analysis.setId(1L);
            analysis.setUserId(2L);
            when(analysisRepository.findById(1L)).thenReturn(Optional.of(analysis));

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> analysisService.getAnalysis(1L, 1L));
            assertTrue(ex.getMessage().contains("无权访问"));
        }

        @Test
        @DisplayName("正常获取 → 返回分析记录")
        void getSuccess() {
            WorkflowAnalysis analysis = new WorkflowAnalysis();
            analysis.setId(1L);
            analysis.setUserId(1L);
            analysis.setWorkflowStatus("COMPLETED");
            when(analysisRepository.findById(1L)).thenReturn(Optional.of(analysis));

            WorkflowAnalysis result = analysisService.getAnalysis(1L, 1L);

            assertEquals("COMPLETED", result.getWorkflowStatus());
            assertEquals(1L, result.getUserId());
        }
    }

    @Nested
    @DisplayName("deleteAnalysis 删除分析")
    class DeleteAnalysisTest {

        @Test
        @DisplayName("运行中的分析 → 抛出异常")
        void deleteRunning() {
            WorkflowAnalysis analysis = new WorkflowAnalysis();
            analysis.setId(1L);
            analysis.setUserId(1L);
            analysis.setWorkflowStatus("RUNNING");
            when(analysisRepository.findById(1L)).thenReturn(Optional.of(analysis));

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> analysisService.deleteAnalysis(1L, 1L));
            assertTrue(ex.getMessage().contains("不能删除正在运行的分析"));
        }

        @Test
        @DisplayName("已完成的分析 → 成功删除")
        void deleteSuccess() {
            WorkflowAnalysis analysis = new WorkflowAnalysis();
            analysis.setId(1L);
            analysis.setUserId(1L);
            analysis.setWorkflowStatus("COMPLETED");
            when(analysisRepository.findById(1L)).thenReturn(Optional.of(analysis));

            analysisService.deleteAnalysis(1L, 1L);

            verify(analysisRepository).delete(analysis);
        }
    }

    @Nested
    @DisplayName("findLatestByStockAny 按标的查询")
    class FindLatestByStockAnyTest {

        @Test
        @DisplayName("精确匹配 stockCode → 返回结果")
        void findByCode() {
            WorkflowAnalysis analysis = new WorkflowAnalysis();
            analysis.setId(1L);
            analysis.setResolvedStockCode("600519");
            analysis.setWorkflowStatus("COMPLETED");
            when(analysisRepository.findFirstByResolvedStockCodeAndWorkflowStatusOrderByCreatedAtDesc("600519", "COMPLETED"))
                    .thenReturn(Optional.of(analysis));

            var result = analysisService.findLatestByStockAny("600519");

            assertTrue(result.isPresent());
            assertEquals("600519", result.get().getResolvedStockCode());
        }

        @Test
        @DisplayName("stockCode 未找到 → 尝试 stockName 模糊匹配")
        void fallbackToName() {
            WorkflowAnalysis analysis = new WorkflowAnalysis();
            analysis.setId(2L);
            analysis.setResolvedStockName("贵州茅台");
            analysis.setWorkflowStatus("COMPLETED");
            when(analysisRepository.findFirstByResolvedStockCodeAndWorkflowStatusOrderByCreatedAtDesc("茅台", "COMPLETED"))
                    .thenReturn(Optional.empty());
            when(analysisRepository.findFirstByResolvedStockNameContainingAndWorkflowStatusOrderByCreatedAtDesc("茅台", "COMPLETED"))
                    .thenReturn(Optional.of(analysis));

            var result = analysisService.findLatestByStockAny("茅台");

            assertTrue(result.isPresent());
            assertEquals("贵州茅台", result.get().getResolvedStockName());
        }

        @Test
        @DisplayName("均未找到 → 返回空")
        void notFound() {
            when(analysisRepository.findFirstByResolvedStockCodeAndWorkflowStatusOrderByCreatedAtDesc("不存在", "COMPLETED"))
                    .thenReturn(Optional.empty());
            when(analysisRepository.findFirstByResolvedStockNameContainingAndWorkflowStatusOrderByCreatedAtDesc("不存在", "COMPLETED"))
                    .thenReturn(Optional.empty());

            var result = analysisService.findLatestByStockAny("不存在");

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("listAnalyses 列表查询")
    class ListAnalysesTest {

        @Test
        @DisplayName("正常返回用户分析列表")
        void listSuccess() {
            WorkflowAnalysis a1 = new WorkflowAnalysis();
            a1.setId(1L);
            a1.setUserId(1L);
            WorkflowAnalysis a2 = new WorkflowAnalysis();
            a2.setId(2L);
            a2.setUserId(1L);
            when(analysisRepository.findByUserIdOrderByCreatedAtDesc(1L))
                    .thenReturn(List.of(a2, a1));

            List<WorkflowAnalysis> result = analysisService.listAnalyses(1L);

            assertEquals(2, result.size());
            assertEquals(2L, result.get(0).getId());
        }
    }
}
