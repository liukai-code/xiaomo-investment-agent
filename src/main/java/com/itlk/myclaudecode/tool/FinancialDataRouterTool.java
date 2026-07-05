package com.itlk.myclaudecode.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itlk.myclaudecode.tool.annotation.ToolBehavior;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

@Slf4j
public class FinancialDataRouterTool {

    private final FinancialDataTool delegate;
    private final ObjectMapper objectMapper;

    public FinancialDataRouterTool(FinancialDataTool delegate) {
        this.delegate = delegate;
        this.objectMapper = new ObjectMapper();
    }

    @ToolBehavior(deterministic = false, cacheable = false)
    @Tool(description = """
            行情数据查询。查询股票、基金的实时行情和基本信息。

            operation 可选值：
            - aShareQuote: 查询A股行情。参数: stockCodeOrName（代码或名称，如600519）
            - hkStockQuote: 查询港股行情。参数: stockCode（5位数字如00700）
            - usStockQuote: 查询美股行情。参数: stockCode（如AAPL）
            - fundNav: 查询基金净值。参数: fundCode（6位数字）
            - searchStock: 按名称搜索股票代码。参数: name

            params 为 JSON 字符串。
            """)
    public String market_data(
            @ToolParam(description = "操作类型，可选值：aShareQuote, hkStockQuote, usStockQuote, fundNav, searchStock") String operation,
            @ToolParam(description = "JSON格式参数，如 {\"stockCodeOrName\":\"茅台\"}") String params) {
        log.info("[FinancialDataRouterTool] operation={}, params={}", operation, params);
        try {
            if (operation == null || operation.isBlank()) {
                return "操作类型不能为空。可用操作：aShareQuote, hkStockQuote, usStockQuote, fundNav, searchStock";
            }
            JsonNode p = parseParams(params);
            return switch (operation.trim()) {
                case "aShareQuote" -> delegate.getAShareQuote(getStr(p, "stockCodeOrName"));
                case "hkStockQuote" -> delegate.getHKStockQuote(getStr(p, "stockCode"));
                case "usStockQuote" -> delegate.getUSStockQuote(getStr(p, "stockCode"));
                case "fundNav" -> delegate.getFundNav(getStr(p, "fundCode"));
                case "searchStock" -> delegate.searchStockByName(getStr(p, "name"));
                default -> "未知操作: " + operation + "。可用操作：aShareQuote, hkStockQuote, usStockQuote, fundNav, searchStock";
            };
        } catch (Exception e) {
            log.error("[FinancialDataRouterTool] 异常: operation={}, error={}", operation, e.getMessage(), e);
            return "行情数据查询失败（operation=" + operation + "）: " + e.getMessage();
        }
    }

    private JsonNode parseParams(String params) {
        if (params == null || params.isBlank()) return null;
        try {
            return objectMapper.readTree(params);
        } catch (Exception e) {
            throw new IllegalArgumentException("params JSON 解析失败: " + e.getMessage());
        }
    }

    private String getStr(JsonNode p, String key) {
        if (p == null || !p.has(key)) throw new IllegalArgumentException("缺少参数: " + key);
        return p.get(key).asText();
    }
}
