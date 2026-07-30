package com.xiaomo.agent.agent.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScratchpadTest {

    @Test
    @DisplayName("新建时为空")
    void isEmpty新建时返回True() {
        Scratchpad sp = new Scratchpad(200);
        assertTrue(sp.isEmpty());
    }

    @Test
    @DisplayName("记录后不为空")
    void record后isEmpty返回False() {
        Scratchpad sp = new Scratchpad(200);
        sp.record(1, "a_stock_quote", "茅台当前价1688.00");
        assertFalse(sp.isEmpty());
    }

    @Test
    @DisplayName("format 输出包含步骤信息")
    void format包含步骤号和工具名() {
        Scratchpad sp = new Scratchpad(200);
        sp.record(1, "a_stock_quote", "茅台当前价1688.00，PE 28.5");
        sp.record(2, "a_stock_report", "ROE 32.1%，营收增长15.3%");

        String output = sp.format();
        assertTrue(output.contains("## 已完成步骤的结果摘要"));
        assertTrue(output.contains("[步骤1]"));
        assertTrue(output.contains("a_stock_quote"));
        assertTrue(output.contains("[步骤2]"));
        assertTrue(output.contains("a_stock_report"));
    }

    @Test
    @DisplayName("摘要超过 maxLength 时截断")
    void record截断长文本() {
        Scratchpad sp = new Scratchpad(50);
        String longText = "A".repeat(100);
        sp.record(1, "tool", longText);

        String output = sp.format();
        // 截断到50字符 + "..."
        assertTrue(output.contains("A".repeat(50) + "..."));
        assertFalse(output.contains("A".repeat(51)));
    }

    @Test
    @DisplayName("null 结果显示为无数据")
    void record处理Null结果() {
        Scratchpad sp = new Scratchpad(200);
        sp.record(1, "tool", null);

        String output = sp.format();
        assertTrue(output.contains("(无数据)"));
    }

    @Test
    @DisplayName("空白结果压缩为空格")
    void record处理空白结果() {
        Scratchpad sp = new Scratchpad(200);
        sp.record(1, "tool", "  多\n行\n文本  ");

        String output = sp.format();
        assertTrue(output.contains("多 行 文本"));
    }

    @Test
    @DisplayName("空 scratchpad 的 format 返回空字符串")
    void format空Scratchpad返回空() {
        Scratchpad sp = new Scratchpad(200);
        assertEquals("", sp.format());
    }
}
