package com.xiaomo.agent.tool.astock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AStockUtils 股票工具类测试")
class AStockUtilsTest {

    @Nested
    @DisplayName("normalizeCode 标准化股票代码")
    class NormalizeCodeTest {

        @Test
        @DisplayName("纯6位数字代码")
        void pureDigits() {
            assertEquals("600519", AStockUtils.normalizeCode("600519"));
            assertEquals("000858", AStockUtils.normalizeCode("000858"));
            assertEquals("300001", AStockUtils.normalizeCode("300001"));
        }

        @Test
        @DisplayName("带sh前缀")
        void withShPrefix() {
            assertEquals("600519", AStockUtils.normalizeCode("sh600519"));
            assertEquals("600519", AStockUtils.normalizeCode("SH600519"));
        }

        @Test
        @DisplayName("带sz前缀")
        void withSzPrefix() {
            assertEquals("000858", AStockUtils.normalizeCode("sz000858"));
            assertEquals("000858", AStockUtils.normalizeCode("SZ000858"));
        }

        @Test
        @DisplayName("带.SH后缀")
        void withShSuffix() {
            assertEquals("600519", AStockUtils.normalizeCode("600519.SH"));
            assertEquals("600519", AStockUtils.normalizeCode("600519.sh"));
        }

        @Test
        @DisplayName("带.SZ后缀")
        void withSzSuffix() {
            assertEquals("000858", AStockUtils.normalizeCode("000858.SZ"));
            assertEquals("000858", AStockUtils.normalizeCode("000858.sz"));
        }

        @Test
        @DisplayName("带.BJ后缀（北交所）")
        void withBjSuffix() {
            assertEquals("830799", AStockUtils.normalizeCode("830799.BJ"));
        }

        @Test
        @DisplayName("空前缀和后缀")
        void withSpaces() {
            assertEquals("600519", AStockUtils.normalizeCode("  600519  "));
        }

        @Test
        @DisplayName("空代码 → 抛异常")
        void emptyCode() {
            assertThrows(IllegalArgumentException.class, () -> AStockUtils.normalizeCode(""));
            assertThrows(IllegalArgumentException.class, () -> AStockUtils.normalizeCode("  "));
            assertThrows(IllegalArgumentException.class, () -> AStockUtils.normalizeCode(null));
        }

        @Test
        @DisplayName("无效格式 → 抛异常")
        void invalidFormat() {
            assertThrows(IllegalArgumentException.class, () -> AStockUtils.normalizeCode("12345"));
            assertThrows(IllegalArgumentException.class, () -> AStockUtils.normalizeCode("1234567"));
            assertThrows(IllegalArgumentException.class, () -> AStockUtils.normalizeCode("ABCDEF"));
        }
    }

    @Nested
    @DisplayName("toEastmoneySecId 东财secid格式")
    class ToEastmoneySecIdTest {

        @Test
        @DisplayName("沪市股票（6开头）→ 1.600519")
        void shanghaiStock() {
            assertEquals("1.600519", AStockUtils.toEastmoneySecId("600519"));
            assertEquals("1.601398", AStockUtils.toEastmoneySecId("601398"));
        }

        @Test
        @DisplayName("深市股票（0/3开头）→ 0.000858")
        void shenzhenStock() {
            assertEquals("0.000858", AStockUtils.toEastmoneySecId("000858"));
            assertEquals("0.300001", AStockUtils.toEastmoneySecId("300001"));
        }

        @Test
        @DisplayName("北交所（8/9开头）→ 0.830799")
        void bseStock() {
            assertEquals("0.830799", AStockUtils.toEastmoneySecId("830799"));
        }

        @Test
        @DisplayName("带前缀输入也能正确处理")
        void withPrefix() {
            assertEquals("1.600519", AStockUtils.toEastmoneySecId("sh600519"));
            assertEquals("0.000858", AStockUtils.toEastmoneySecId("sz000858"));
        }
    }

    @Nested
    @DisplayName("toMarketPrefix 腾讯/新浪格式")
    class ToMarketPrefixTest {

        @Test
        @DisplayName("沪市股票 → sh600519")
        void shanghaiStock() {
            assertEquals("sh600519", AStockUtils.toMarketPrefix("600519"));
        }

        @Test
        @DisplayName("深市股票 → sz000858")
        void shenzhenStock() {
            assertEquals("sz000858", AStockUtils.toMarketPrefix("000858"));
            assertEquals("sz300001", AStockUtils.toMarketPrefix("300001"));
        }

        @Test
        @DisplayName("带前缀输入也能正确处理")
        void withPrefix() {
            assertEquals("sh600519", AStockUtils.toMarketPrefix("sh600519"));
            assertEquals("sz000858", AStockUtils.toMarketPrefix("sz000858"));
        }
    }

    @Nested
    @DisplayName("formatYi 亿元格式化")
    class FormatYiTest {

        @Test
        @DisplayName("1亿")
        void oneYi() {
            assertEquals("1.00亿", AStockUtils.formatYi(1e8));
        }

        @Test
        @DisplayName("100亿")
        void hundredYi() {
            assertEquals("100.00亿", AStockUtils.formatYi(100e8));
        }

        @Test
        @DisplayName("小于1亿")
        void lessThanYi() {
            assertEquals("0.50亿", AStockUtils.formatYi(5e7));
        }
    }

    @Nested
    @DisplayName("formatWan 万元格式化")
    class FormatWanTest {

        @Test
        @DisplayName("1万")
        void oneWan() {
            assertEquals("1.00万", AStockUtils.formatWan(1e4));
        }

        @Test
        @DisplayName("100万")
        void hundredWan() {
            assertEquals("100.00万", AStockUtils.formatWan(100e4));
        }

        @Test
        @DisplayName("小于1万")
        void lessThanWan() {
            assertEquals("0.50万", AStockUtils.formatWan(5000));
        }
    }
}
