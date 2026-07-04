package com.itlk.myclaudecode.tool;

import com.itlk.myclaudecode.common.config.HttpClientService;
import okhttp3.Headers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FinancialDataTool 金融数据查询工具测试")
class FinancialDataToolTest {

    @Mock
    private HttpClientService httpClientService;

    private FinancialDataTool tool;

    // 模拟腾讯行情API返回
    private static final String TENCENT_QUOTE_A_SHARE = """
            v_sh600519="51~贵州茅台~600519~1800.00~1790.00~1785.00~50000~25000~25000~1800.00~100~1799.90~50~1799.80~30~1799.70~20~1799.60~10~1800.10~80~1800.20~60~1800.30~40~1800.40~20~1800.50~10~~20250102150000~10.00~0.56~1810.00~1780.00~1800.00/50000/9000000000~50000~90000~1.50~25.50~~1810.00~1780.00~1.68~22600.00~22600.00~2.50~1970.00~1610.00~0.80~1790~1800.00~18.50~50.00~~~1.20~90000000.00~50000.00~20~GP-A~0.13~-0.10~-0.50~29.50~15.20~22500.00~22600.00~7.80~6.50~1970.00~281851892~423061007~60.50~25.00~281851892~~~1805.00~-0.20~~CNY~0~~1799.90~5~";""";

    // 模拟腾讯行情港股返回
    private static final String TENCENT_QUOTE_HK = """
            v_hk00700="51~腾讯控股~00700~400.00~395.00~393.00~20000~10000~10000~400.00~50~399.90~30~399.80~20~400.10~40~400.20~30~~20250102160000~5.00~1.27~405.00~390.00~400.00/20000/8000000000~20000~80000~2.00~15.00~~405.00~390.00~3.80~38000.00~38000.00~2.00~435.00~355.00~1.00~395~400.00~12.00~30.00~~~0.80~80000000.00~20000.00~10~GP-HK~0.10~-0.05~-0.20~30.00~12.00~37500.00~38000.00~8.00~6.00~355.00~9567890123~12345678901~50.00~20.00~9567890123~~~402.00~-0.30~~HKD~0~~399.90~3~";""";

    // 模拟东财搜索API返回
    private static final String EASTMONEY_SEARCH_RESPONSE = """
            {"QuotationCodeTable":{"Data":[{"Code":"600519","Name":"贵州茅台","MktNum":"1","SecurityTypeName":"沪A"}]}}""";

    // 模拟新浪基金净值API返回
    private static final String SINA_FUND_NAV = """
            jsonpgz({"fundcode":"110011","name":"易方达中小盘混合","jzrq":"2025-01-02","dwjz":"3.5000","gsz":"3.5100","gszzl":"0.29","gztime":"2025-01-02 15:00"});""";

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
        @DisplayName("纯数字代码 → 查询行情并解析")
        void numericCode() throws Exception {
            when(httpClientService.get(anyString(), any(Headers.class)))
                    .thenReturn(TENCENT_QUOTE_A_SHARE);
            String result = tool.getAShareQuote("600519");
            assertTrue(result.contains("贵州茅台"), "应包含股票名称");
            assertTrue(result.contains("1800") || result.contains("当前价"), "应包含价格");
            assertTrue(result.contains("涨跌") || result.contains("+"), "应包含涨跌信息");
        }

        @Test
        @DisplayName("股票名称 → 搜索后查询行情")
        void stockName() throws Exception {
            // 搜索API返回代码
            when(httpClientService.get(anyString(), any(Headers.class)))
                    .thenReturn(EASTMONEY_SEARCH_RESPONSE)
                    .thenReturn(TENCENT_QUOTE_A_SHARE);
            String result = tool.getAShareQuote("茅台");
            assertTrue(result.contains("贵州茅台") || result.contains("600519"),
                    "应包含搜索到的股票信息");
        }

        @Test
        @DisplayName("不存在的股票 → 返回提示")
        void nonExistentStock() throws Exception {
            when(httpClientService.get(anyString(), any(Headers.class)))
                    .thenReturn("{\"QuotationCodeTable\":{\"Data\":[]}}");
            String result = tool.getAShareQuote("xyzxyzxyz");
            assertTrue(result.contains("未找到") || result.contains("没有"),
                    "不存在的股票应返回提示");
        }

        @Test
        @DisplayName("网络异常 → 返回失败信息")
        void networkError() throws Exception {
            when(httpClientService.get(anyString(), any(Headers.class)))
                    .thenThrow(new RuntimeException("Connection refused"));
            String result = tool.getAShareQuote("600519");
            assertTrue(result.contains("失败") || result.contains("异常"),
                    "网络异常应返回失败信息");
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
        @DisplayName("有效港股代码 → 解析行情")
        void validCode() throws Exception {
            when(httpClientService.get(anyString(), any(Headers.class)))
                    .thenReturn(TENCENT_QUOTE_HK);
            String result = tool.getHKStockQuote("00700");
            assertTrue(result.contains("腾讯控股"), "应包含股票名称");
            assertTrue(result.contains("400") || result.contains("当前价"), "应包含价格");
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
        @DisplayName("有效美股代码 → 查询行情")
        void validCode() throws Exception {
            String usResponse = """
                    v_us_AAPL="51~苹果~AAPL~200.00~198.00~197.00~10000~5000~5000~200.00~50~199.90~30~200.10~40~~20250102160000~2.00~1.01~205.00~195.00~200.00/10000/2000000000~10000~20000~1.50~30.00~~205.00~195.00~5.13~30000.00~30000.00~2.00~220.00~180.00~0.80~198~200.00~15.00~40.00~~~0.90~20000000.00~10000.00~15~GP-US~0.10~-0.05~-0.25~25.00~12.00~29500.00~30000.00~7.50~6.00~180.00~15000000000~18000000000~55.00~22.00~15000000000~~~202.00~-0.20~~USD~0~~199.90~3~";""";
            when(httpClientService.get(anyString(), any(Headers.class)))
                    .thenReturn(usResponse);
            String result = tool.getUSStockQuote("AAPL");
            assertTrue(result.contains("苹果") || result.contains("AAPL"),
                    "应包含股票名称或代码");
            assertTrue(result.contains("200") || result.contains("当前价"), "应包含价格");
        }

        @Test
        @DisplayName("小写代码 → 自动转大写")
        void lowercaseCode() throws Exception {
            String usResponse = """
                    v_us_AAPL="51~苹果~AAPL~200.00~198.00~197.00~10000~5000~5000~200.00~50~199.90~30~200.10~40~~20250102160000~2.00~1.01~205.00~195.00~200.00/10000/2000000000~10000~20000~1.50~30.00~~205.00~195.00~5.13~30000.00~30000.00~2.00~220.00~180.00~0.80~198~200.00~15.00~40.00~~~0.90~20000000.00~10000.00~15~GP-US~0.10~-0.05~-0.25~25.00~12.00~29500.00~30000.00~7.50~6.00~180.00~15000000000~18000000000~55.00~22.00~15000000000~~~202.00~-0.20~~USD~0~~199.90~3~";""";
            when(httpClientService.get(anyString(), any(Headers.class)))
                    .thenReturn(usResponse);
            String result = tool.getUSStockQuote("aapl");
            assertTrue(result.contains("苹果") || result.contains("AAPL"),
                    "小写应能正常处理");
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
        @DisplayName("有效基金代码 → 解析净值数据")
        void validCode() throws Exception {
            when(httpClientService.get(anyString(), any(Headers.class)))
                    .thenReturn(SINA_FUND_NAV);
            String result = tool.getFundNav("110011");
            assertTrue(result.contains("易方达") || result.contains("110011"),
                    "应包含基金名称或代码");
            assertTrue(result.contains("净值") || result.contains("3.5"),
                    "应包含净值数据");
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
        @DisplayName("有效关键词 → 解析搜索结果")
        void validKeyword() throws Exception {
            when(httpClientService.get(anyString(), any(Headers.class)))
                    .thenReturn(EASTMONEY_SEARCH_RESPONSE);
            String result = tool.searchStockByName("茅台");
            assertTrue(result.contains("600519") || result.contains("贵州茅台"),
                    "应包含搜索到的代码和名称");
        }

        @Test
        @DisplayName("无结果关键词 → 返回提示")
        void noResultKeyword() throws Exception {
            when(httpClientService.get(anyString(), any(Headers.class)))
                    .thenReturn("{\"QuotationCodeTable\":{\"Data\":[]}}");
            String result = tool.searchStockByName("xyzxyzxyz");
            assertTrue(result.contains("未找到") || result.contains("没有") || result.contains("无"),
                    "无结果应返回提示");
        }
    }
}
