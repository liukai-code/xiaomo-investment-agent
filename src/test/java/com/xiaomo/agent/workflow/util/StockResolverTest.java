package com.xiaomo.agent.workflow.util;

import com.xiaomo.agent.common.config.HttpClientService;
import com.xiaomo.agent.workflow.util.StockResolver.ResolvedStock;
import okhttp3.Headers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("StockResolver 标的解析器测试")
class StockResolverTest {

    @Mock
    private HttpClientService httpClientService;

    // ========== pickBestMatch 纯逻辑测试 ==========

    @Nested
    @DisplayName("pickBestMatch 最佳匹配选择")
    class PickBestMatchTest {

        @Test
        @DisplayName("名称包含输入关键词 → 优先选择")
        void nameContainsInput() {
            List<ResolvedStock> candidates = List.of(
                    new ResolvedStock("600519", "贵州茅台"),
                    new ResolvedStock("600518", "贵州茅台酒股份")
            );
            ResolvedStock result = StockResolver.pickBestMatch("茅台", candidates);
            assertNotNull(result, "应返回匹配结果");
            assertEquals("600519", result.code(), "应选择名称包含'茅台'的结果");
        }

        @Test
        @DisplayName("输入包含返回名称 → 次优匹配")
        void inputContainsName() {
            List<ResolvedStock> candidates = List.of(
                    new ResolvedStock("000858", "五粮液")
            );
            ResolvedStock result = StockResolver.pickBestMatch("五粮液股份", candidates);
            assertNotNull(result, "应返回匹配结果");
            assertEquals("000858", result.code(), "输入包含名称时应匹配");
        }

        @Test
        @DisplayName("无精确匹配 → 兜底取第一个")
        void fallbackToFirst() {
            List<ResolvedStock> candidates = List.of(
                    new ResolvedStock("600000", "浦发银行"),
                    new ResolvedStock("600001", "邯郸钢铁")
            );
            ResolvedStock result = StockResolver.pickBestMatch("茅台", candidates);
            assertNotNull(result, "兜底时应返回第一个结果");
            assertEquals("600000", result.code(), "兜底应取第一个");
        }

        @Test
        @DisplayName("空列表 → 返回 null")
        void emptyList() {
            ResolvedStock result = StockResolver.pickBestMatch("茅台", Collections.emptyList());
            assertNull(result, "空列表应返回null");
        }

        @Test
        @DisplayName("null 列表 → 返回 null")
        void nullList() {
            ResolvedStock result = StockResolver.pickBestMatch("茅台", null);
            assertNull(result, "null列表应返回null");
        }
    }

    // ========== resolve 解析测试 ==========

    @Nested
    @DisplayName("resolve 标的解析")
    class ResolveTest {

        @Test
        @DisplayName("null 输入 → 抛出 IllegalArgumentException")
        void nullInput() {
            assertThrows(IllegalArgumentException.class,
                    () -> StockResolver.resolve(null, httpClientService),
                    "null输入应抛出异常");
        }

        @Test
        @DisplayName("空字符串输入 → 抛出 IllegalArgumentException")
        void emptyInput() {
            assertThrows(IllegalArgumentException.class,
                    () -> StockResolver.resolve("", httpClientService),
                    "空字符串应抛出异常");
        }

        @Test
        @DisplayName("6位数字代码 → 通过东财API反查名称")
        void numericCodeResolve() throws Exception {
            String eastMoneyResponse = """
                    {"QuotationCodeTable":{"Data":[
                    {"Code":"600519","Name":"贵州茅台","MktNum":"1"}
                    ]}}""";
            when(httpClientService.get(anyString(), any(Headers.class)))
                    .thenReturn(eastMoneyResponse);

            ResolvedStock result = StockResolver.resolve("600519", httpClientService);
            assertEquals("600519", result.code(), "代码应为600519");
            assertEquals("贵州茅台", result.name(), "名称应通过东财API反查");
        }

        @Test
        @DisplayName("中文名称查询 → 东财API解析成功")
        void chineseNameResolve() throws Exception {
            String eastMoneyResponse = """
                    {"QuotationCodeTable":{"Data":[
                    {"Code":"600519","Name":"贵州茅台","MktNum":"1"}
                    ]}}""";
            when(httpClientService.get(anyString(), any(Headers.class)))
                    .thenReturn(eastMoneyResponse);

            ResolvedStock result = StockResolver.resolve("茅台", httpClientService);
            assertEquals("600519", result.code(), "应解析出代码");
            assertEquals("贵州茅台", result.name(), "应解析出名称");
        }

        @Test
        @DisplayName("带前缀查询 → 剥离前缀后解析")
        void stripPrefix() throws Exception {
            String eastMoneyResponse = """
                    {"QuotationCodeTable":{"Data":[
                    {"Code":"600519","Name":"贵州茅台","MktNum":"1"}
                    ]}}""";
            when(httpClientService.get(anyString(), any(Headers.class)))
                    .thenReturn(eastMoneyResponse);

            ResolvedStock result = StockResolver.resolve("深度分析茅台", httpClientService);
            assertEquals("600519", result.code(), "应剥离'深度分析'前缀后解析");
        }

        @Test
        @DisplayName("带后缀查询 → 剥离后缀后解析")
        void stripSuffix() throws Exception {
            String eastMoneyResponse = """
                    {"QuotationCodeTable":{"Data":[
                    {"Code":"600519","Name":"贵州茅台","MktNum":"1"}
                    ]}}""";
            when(httpClientService.get(anyString(), any(Headers.class)))
                    .thenReturn(eastMoneyResponse);

            ResolvedStock result = StockResolver.resolve("茅台值得买吗", httpClientService);
            assertEquals("600519", result.code(), "应剥离'值得买吗'后缀后解析");
        }

        @Test
        @DisplayName("东财失败 → 走新浪 fallback")
        void sinaFallback() throws Exception {
            // 东财返回空
            when(httpClientService.get(anyString(), any(Headers.class)))
                    .thenThrow(new RuntimeException("东财API超时"))
                    .thenReturn("suggest=\"sh600519,贵州茅台,1;\"");

            ResolvedStock result = StockResolver.resolve("茅台", httpClientService);
            assertEquals("600519", result.code(), "东财失败应走新浪fallback");
        }

        @Test
        @DisplayName("无法识别的输入 → 抛出 IllegalArgumentException")
        void unresolvableInput() throws Exception {
            when(httpClientService.get(anyString(), any(Headers.class)))
                    .thenReturn("{\"QuotationCodeTable\":{\"Data\":[]}}");

            assertThrows(IllegalArgumentException.class,
                    () -> StockResolver.resolve("不存在的股票名xyz", httpClientService),
                    "无法解析的输入应抛出异常");
        }

        @Test
        @DisplayName("不存在的6位代码 → 抛出 IllegalArgumentException")
        void invalidNumericCode() throws Exception {
            // 东财返回空结果，说明该代码不是有效股票
            when(httpClientService.get(anyString(), any(Headers.class)))
                    .thenReturn("{\"QuotationCodeTable\":{\"Data\":[]}}");

            assertThrows(IllegalArgumentException.class,
                    () -> StockResolver.resolve("118118", httpClientService),
                    "不存在的6位代码应抛出异常");
        }
    }
}
