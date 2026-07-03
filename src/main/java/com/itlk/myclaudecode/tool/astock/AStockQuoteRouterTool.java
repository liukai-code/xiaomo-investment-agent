package com.itlk.myclaudecode.tool.astock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itlk.myclaudecode.tool.annotation.ToolBehavior;
import com.itlk.myclaudecode.common.config.HttpClientService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Headers;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
public class AStockQuoteRouterTool {

    private final HttpClientService httpClientService;
    private final ObjectMapper objectMapper;

    public AStockQuoteRouterTool(HttpClientService httpClientService) {
        this.httpClientService = httpClientService;
        this.objectMapper = new ObjectMapper();
    }

    @ToolBehavior(deterministic = false, cacheable = false)
    @Tool(description = """
            A股行情深度查询。查询股票、指数、ETF的详细行情数据，包含PE/PB/市值/换手率等估值指标。

            operation 可选值：
            - tencentQuote: 批量查询实时行情（PE/PB/市值/换手率/涨跌停）。参数: stockCodes（逗号分隔代码，如"600519,000858,510050"）
            - baiduKline: 查询K线数据（含MA5/10/20均线）。参数: stockCode, startTime（可选，默认""取最近）
            - mootdxKline: TODO 占位（mootdx TCP 协议未实现，请用 baiduKline 替代）
            - mootdxQuotes: TODO 占位（请用 tencentQuote 替代）
            - mootdxTransaction: TODO 占位（逐笔成交暂未实现）

            params 为 JSON 字符串。
            """)
    public String a_stock_quote(
            @ToolParam(description = "操作类型") String operation,
            @ToolParam(description = "JSON格式参数") String params) {
        log.info("[AStockQuoteRouterTool] operation={}, params={}", operation, params);
        try {
            if (operation == null || operation.isBlank()) {
                return "操作类型不能为空。可用操作：tencentQuote, baiduKline";
            }
            JsonNode p = parseParams(params);
            return switch (operation.trim()) {
                case "tencentQuote" -> tencentQuote(getStr(p, "stockCodes"));
                case "baiduKline" -> baiduKline(getStr(p, "stockCode"), getOptStr(p, "startTime", ""));
                case "mootdxKline", "mootdxQuotes", "mootdxTransaction" ->
                        "mootdx TCP 协议尚未在 Java 端实现，请使用 tencentQuote 或 baiduKline 替代";
                default -> "未知操作: " + operation + "。可用操作：tencentQuote, baiduKline";
            };
        } catch (Exception e) {
            log.error("[AStockQuoteRouterTool] 异常: operation={}, error={}", operation, e.getMessage(), e);
            return "操作失败（operation=" + operation + "）: " + e.getMessage();
        }
    }

    private String tencentQuote(String stockCodes) {
        try {
            String[] codes = stockCodes.split(",");
            List<String> prefixed = new ArrayList<>();
            for (String c : codes) {
                String code = AStockUtils.normalizeCode(c.trim());
                prefixed.add(AStockUtils.toMarketPrefix(code));
            }
            String url = "https://qt.gtimg.cn/q=" + String.join(",", prefixed);
            log.debug("[AStockQuoteRouterTool] 请求腾讯行情: codes={}", String.join(",", prefixed));
            String body = httpClientService.get(url, Headers.of("User-Agent", "Mozilla/5.0"));
            // 腾讯返回 GBK 编码
            return parseTencentQuote(body, codes);
        } catch (Exception e) {
            return "腾讯行情查询失败: " + e.getMessage();
        }
    }

    private String parseTencentQuote(String data, String[] inputCodes) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== A股实时行情 ===\n\n");

        Map<String, String> codeMap = new LinkedHashMap<>();
        for (String c : inputCodes) {
            codeMap.put(AStockUtils.normalizeCode(c.trim()), c.trim());
        }

        for (String line : data.strip().split(";")) {
            if (line.isBlank() || !line.contains("=") || !line.contains("\"")) continue;
            String[] keyParts = line.split("=")[0].split("_");
            String key = keyParts[keyParts.length - 1];
            String[] vals = line.split("\"")[1].split("~");
            if (vals.length < 53) continue;
            String code = key.length() > 2 ? key.substring(2) : key;

            try {
                String name = vals[1];
                double price = parseDouble(vals[3]);
                double peTtm = parseDouble(vals[39]);
                double pb = parseDouble(vals[46]);
                double mcapYi = parseDouble(vals[44]);
                double floatMcapYi = parseDouble(vals[45]);
                double changePct = parseDouble(vals[32]);
                double turnoverPct = parseDouble(vals[38]);
                double amountWan = parseDouble(vals[37]);
                double limitUp = parseDouble(vals[47]);
                double limitDown = parseDouble(vals[48]);
                double volRatio = parseDouble(vals[49]);

                sb.append(String.format("%s(%s): %.2f元  涨跌: %+.2f%%\n", name, code, price, changePct));
                sb.append(String.format("  PE(TTM): %.2f  PB: %.2f  市值: %.2f亿  流通市值: %.2f亿\n", peTtm, pb, mcapYi, floatMcapYi));
                sb.append(String.format("  换手率: %.2f%%  量比: %.2f  成交额: %.2f万\n", turnoverPct, volRatio, amountWan));
                sb.append(String.format("  涨停价: %.2f  跌停价: %.2f\n\n", limitUp, limitDown));
            } catch (Exception e) {
                sb.append(String.format("代码 %s 数据解析异常: %s\n\n", code, e.getMessage()));
            }
        }
        return sb.toString();
    }

    private String baiduKline(String stockCode, String startTime) {
        try {
            String code = AStockUtils.normalizeCode(stockCode);
            String url = "https://finance.pae.baidu.com/selfselect/getstockquotation"
                    + "?all=1&isIndex=false&isBk=false&isBlock=false&isFutures=false"
                    + "&isStock=true&newFormat=1&group=quotation_kline_ab&finClientType=pc"
                    + "&code=" + code
                    + "&start_time=" + URLEncoder.encode(startTime, StandardCharsets.UTF_8)
                    + "&ktype=1";

            log.debug("[AStockQuoteRouterTool] 请求百度K线: code={}", code);
            String responseStr = httpClientService.get(url, Headers.of(
                    "User-Agent", "Mozilla/5.0",
                    "Accept", "application/vnd.finance-web.v1+json",
                    "Origin", "https://gushitong.baidu.com",
                    "Referer", "https://gushitong.baidu.com/"
            ));

            JsonNode root = objectMapper.readTree(responseStr);
            JsonNode result = root.path("Result");
            JsonNode md = result.path("newMarketData");
            JsonNode keysNode = md.path("keys");
            String rows = md.path("marketData").asText("");

            if (rows.isEmpty()) {
                return "未找到 " + code + " 的K线数据";
            }

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== %s K线数据（百度）===\n\n", code));

            // 显示字段名
            List<String> keys = new ArrayList<>();
            keysNode.forEach(k -> keys.add(k.asText()));
            sb.append("字段: ").append(String.join(", ", keys.stream().limit(10).toList())).append("\n\n");

            // 显示最近5根K线
            String[] klines = rows.split(";");
            int start = Math.max(0, klines.length - 5);
            sb.append("最近").append(klines.length - start).append("根K线:\n");
            for (int i = start; i < klines.length; i++) {
                String[] fields = klines[i].split(",");
                if (fields.length >= 7) {
                    sb.append(String.format("  %s  开:%.2f  收:%.2f  高:%.2f  低:%.2f  量:%s  额:%s\n",
                            fields[0], parseDouble(fields[1]), parseDouble(fields[2]),
                            parseDouble(fields[3]), parseDouble(fields[4]),
                            fields[5], fields[6]));
                }
                // 如果有 MA 均线数据
                if (fields.length >= 10) {
                    sb.append(String.format("    MA5:%.2f  MA10:%.2f  MA20:%.2f\n",
                            parseDouble(fields[7]), parseDouble(fields[8]), parseDouble(fields[9])));
                }
            }
            sb.append(String.format("\n共 %d 根K线\n", klines.length));
            return sb.toString();
        } catch (Exception e) {
            return "百度K线查询失败: " + e.getMessage();
        }
    }

    private double parseDouble(String s) {
        try {
            return (s == null || s.isEmpty()) ? 0 : Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0;
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
        if (p == null || !p.has(key) || p.get(key).isNull()) {
            throw new IllegalArgumentException("缺少参数: " + key);
        }
        return p.get(key).asText();
    }

    private String getOptStr(JsonNode p, String key, String defaultVal) {
        if (p == null || !p.has(key) || p.get(key).isNull()) return defaultVal;
        String val = p.get(key).asText();
        return val.isEmpty() ? defaultVal : val;
    }
}
