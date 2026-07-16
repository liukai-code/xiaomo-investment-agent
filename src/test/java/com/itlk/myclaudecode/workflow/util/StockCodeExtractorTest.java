package com.itlk.myclaudecode.workflow.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StockCodeExtractor 股票代码提取测试")
class StockCodeExtractorTest {

    @Nested
    @DisplayName("extract 提取股票代码")
    class ExtractTest {

        @Test
        @DisplayName("纯数字6位代码 → 正确提取")
        void numericCode() {
            Set<String> codes = StockCodeExtractor.extract("帮我分析600519");
            assertTrue(codes.contains("600519"), "应提取到600519");
        }

        @Test
        @DisplayName("带sh前缀的代码 → 提取并去除前缀")
        void prefixedCodeSh() {
            Set<String> codes = StockCodeExtractor.extract("看看sh600519的行情");
            assertTrue(codes.contains("600519"), "应提取到600519并去除sh前缀");
        }

        @Test
        @DisplayName("带sz前缀的代码 → 提取并去除前缀")
        void prefixedCodeSz() {
            Set<String> codes = StockCodeExtractor.extract("sz000858怎么样");
            assertTrue(codes.contains("000858"), "应提取到000858并去除sz前缀");
        }

        @Test
        @DisplayName("带bj前缀的代码 → 提取并去除前缀")
        void prefixedCodeBj() {
            Set<String> codes = StockCodeExtractor.extract("bj830799");
            assertTrue(codes.contains("830799"), "应提取到830799并去除bj前缀");
        }

        @Test
        @DisplayName("混合输入：多个代码 → 全部提取")
        void multipleCodes() {
            Set<String> codes = StockCodeExtractor.extract("对比600519和sh000858");
            assertTrue(codes.contains("600519"), "应提取到600519");
            assertTrue(codes.contains("000858"), "应提取到000858");
        }

        @Test
        @DisplayName("null 输入 → 返回空集合")
        void nullInput() {
            Set<String> codes = StockCodeExtractor.extract(null);
            assertNotNull(codes, "不应返回null");
            assertTrue(codes.isEmpty(), "null输入应返回空集合");
        }

        @Test
        @DisplayName("空字符串 → 返回空集合")
        void emptyInput() {
            Set<String> codes = StockCodeExtractor.extract("");
            assertTrue(codes.isEmpty(), "空字符串应返回空集合");
        }

        @Test
        @DisplayName("纯空白字符串 → 返回空集合")
        void blankInput() {
            Set<String> codes = StockCodeExtractor.extract("   ");
            assertTrue(codes.isEmpty(), "空白字符串应返回空集合");
        }

        @Test
        @DisplayName("无股票代码的文本 → 返回空集合")
        void noCodeInText() {
            Set<String> codes = StockCodeExtractor.extract("今天天气不错");
            assertTrue(codes.isEmpty(), "无代码文本应返回空集合");
        }

        @Test
        @DisplayName("带前缀代码优先于纯数字提取，不重复")
        void prefixAndBareCodeDedup() {
            // "sh600519" 同时匹配前缀模式和纯数字模式，应去重
            Set<String> codes = StockCodeExtractor.extract("sh600519");
            assertEquals(1, codes.size(), "同一代码不应重复提取");
            assertTrue(codes.contains("600519"));
        }

        @ParameterizedTest
        @DisplayName("大写前缀也能识别")
        @ValueSource(strings = {"SH600519", "SZ000858", "BJ830799"})
        void uppercasePrefix(String input) {
            Set<String> codes = StockCodeExtractor.extract(input);
            assertFalse(codes.isEmpty(), "大写前缀 " + input + " 应能识别");
        }
    }
}
