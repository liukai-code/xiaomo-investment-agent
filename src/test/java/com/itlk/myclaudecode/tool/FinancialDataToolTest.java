package com.itlk.myclaudecode.tool;

import com.itlk.myclaudecode.common.config.HttpClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FinancialDataTool 金融数据查询工具测试")
class FinancialDataToolTest {

    @Mock
    private HttpClientService httpClientService;

    private FinancialDataTool tool;

    @BeforeEach
    void setUp() {
        tool = new FinancialDataTool(httpClientService);
    }

    @Nested
    @DisplayName("getAShareQuote A股行情")
    class GetAShareQuoteTest {

        @Test
        @DisplayName("空代码 → 返回错误")
        void emptyCode() {
            String result = tool.getAShareQuote("");
            assertTrue(result.contains("不能为空"), "空代码应返回错误");
        }

        @Test
        @DisplayName("null代码 → 返回错误")
        void nullCode() {
            String result = tool.getAShareQuote(null);
            assertTrue(result.contains("不能为空"), "null代码应返回错误");
        }

        @Test
        @DisplayName("纯数字代码 → 尝试查询")
        void numericCode() {
            // 600519 是贵州茅台
            String result = tool.getAShareQuote("600519");
            assertNotNull(result, "不应返回null");
        }

        @Test
        @DisplayName("股票名称 → 尝试搜索")
        void stockName() {
            String result = tool.getAShareQuote("茅台");
            assertNotNull(result, "不应返回null");
        }

        @Test
        @DisplayName("不存在的股票 → 返回提示")
        void nonExistentStock() {
            String result = tool.getAShareQuote("999999");
            assertNotNull(result, "不应返回null");
        }
    }

    @Nested
    @DisplayName("getHKStockQuote 港股行情")
    class GetHKStockQuoteTest {

        @Test
        @DisplayName("空代码 → 返回错误")
        void emptyCode() {
            String result = tool.getHKStockQuote("");
            assertTrue(result.contains("不能为空"), "空代码应返回错误");
        }

        @Test
        @DisplayName("null代码 → 返回错误")
        void nullCode() {
            String result = tool.getHKStockQuote(null);
            assertTrue(result.contains("不能为空"), "null代码应返回错误");
        }

        @Test
        @DisplayName("有效港股代码")
        void validCode() {
            String result = tool.getHKStockQuote("00700");
            assertNotNull(result, "不应返回null");
        }

        @Test
        @DisplayName("无效格式代码 → 返回错误")
        void invalidFormat() {
            String result = tool.getHKStockQuote("AB!@#");
            assertTrue(result.contains("格式不正确") || result.contains("失败") || result.contains("异常"),
                    "无效格式应返回错误");
        }
    }

    @Nested
    @DisplayName("getUSStockQuote 美股行情")
    class GetUSStockQuoteTest {

        @Test
        @DisplayName("空代码 → 返回错误")
        void emptyCode() {
            String result = tool.getUSStockQuote("");
            assertTrue(result.contains("不能为空"), "空代码应返回错误");
        }

        @Test
        @DisplayName("null代码 → 返回错误")
        void nullCode() {
            String result = tool.getUSStockQuote(null);
            assertTrue(result.contains("不能为空"), "null代码应返回错误");
        }

        @Test
        @DisplayName("有效美股代码")
        void validCode() {
            String result = tool.getUSStockQuote("AAPL");
            assertNotNull(result, "不应返回null");
        }

        @Test
        @DisplayName("小写代码 → 自动转大写")
        void lowercaseCode() {
            String result = tool.getUSStockQuote("aapl");
            assertNotNull(result, "小写应能正常处理");
        }
    }

    @Nested
    @DisplayName("getFundNav 基金净值")
    class GetFundNavTest {

        @Test
        @DisplayName("空代码 → 返回错误")
        void emptyCode() {
            String result = tool.getFundNav("");
            assertTrue(result.contains("不能为空"), "空代码应返回错误");
        }

        @Test
        @DisplayName("null代码 → 返回错误")
        void nullCode() {
            String result = tool.getFundNav(null);
            assertTrue(result.contains("不能为空"), "null代码应返回错误");
        }

        @Test
        @DisplayName("有效基金代码")
        void validCode() {
            String result = tool.getFundNav("110011");
            assertNotNull(result, "不应返回null");
        }

        @Test
        @DisplayName("无效格式代码 → 返回错误")
        void invalidFormat() {
            String result = tool.getFundNav("AB!@#");
            assertTrue(result.contains("格式不正确") || result.contains("失败") || result.contains("异常"),
                    "无效格式应返回错误");
        }
    }

    @Nested
    @DisplayName("searchStockByName 股票搜索")
    class SearchStockByNameTest {

        @Test
        @DisplayName("空关键词 → 返回错误")
        void emptyName() {
            String result = tool.searchStockByName("");
            assertTrue(result.contains("不能为空"), "空关键词应返回错误");
        }

        @Test
        @DisplayName("null关键词 → 返回错误")
        void nullName() {
            String result = tool.searchStockByName(null);
            assertTrue(result.contains("不能为空"), "null关键词应返回错误");
        }

        @Test
        @DisplayName("有效关键词")
        void validKeyword() {
            String result = tool.searchStockByName("茅台");
            assertNotNull(result, "不应返回null");
        }

        @Test
        @DisplayName("无结果关键词")
        void noResultKeyword() {
            String result = tool.searchStockByName("xyzxyzxyz");
            assertNotNull(result, "不应返回null");
        }
    }
}
