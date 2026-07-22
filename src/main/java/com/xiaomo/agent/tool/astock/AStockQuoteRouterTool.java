package com.xiaomo.agent.tool.astock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaomo.agent.tool.annotation.ToolBehavior;
import com.xiaomo.agent.common.config.HttpClientService;
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
                case "baiduKline" -> {
                    String code = getStrOrAlt(p, "stockCode", "stockCodes");
                    if (code.contains(",")) code = code.split(",")[0].trim();
                    yield baiduKline(code, getOptStr(p, "startTime", ""));
                }
                case "mootdxKline", "mootdxQuotes", "mootdxTransaction" ->
                        "mootdx TCP 协议尚未在 Java 端实现，请使用 tencentQuote 或 baiduKline 替代";
                default -> "未知操作: " + operation + "。可用操作：tencentQuote, baiduKline";
            };
        } catch (IllegalArgumentException e) {
            log.error("[AStockQuoteRouterTool] 参数错误: operation={}, error={}", operation, e.getMessage());
            return "参数错误（operation=" + operation + "）: " + e.getMessage()
                    + "。请检查 params JSON 格式，例如: {\"stockCode\":\"430510\"}";
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
            String body = httpClientService.get(url, Headers.of(
                    "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            ));
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
            // 默认取最近120个交易日，避免返回全部历史数据
            if (startTime == null || startTime.isBlank()) {
                startTime = java.time.LocalDate.now().minusDays(180)
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            }
            String url = "https://finance.pae.baidu.com/selfselect/getstockquotation"
                    + "?all=1&isIndex=false&isBk=false&isBlock=false&isFutures=false"
                    + "&isStock=true&newFormat=1&group=quotation_kline_ab&finClientType=pc"
                    + "&code=" + code
                    + "&start_time=" + URLEncoder.encode(startTime, StandardCharsets.UTF_8)
                    + "&ktype=1";

            log.debug("[AStockQuoteRouterTool] 请求百度K线: code={}", code);
            String responseStr = httpClientService.getWithJdkClient(url, java.util.Map.of(
                    "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                    "Accept", "application/json, text/plain, */*",
                    "Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8",
                    "Origin", "https://gushitong.baidu.com",
                    "Referer", "https://gushitong.baidu.com/"
            ));

            JsonNode root = objectMapper.readTree(responseStr);
            String resultCode = root.path("ResultCode").asText("");
            JsonNode result = root.path("Result");
            JsonNode md = result.path("newMarketData");
            String rows = md.path("marketData").asText("");
            log.info("[AStockQuoteRouterTool] baiduKline 响应: code={}, resultCode={}, responseSize={}, rows={}",
                    code, resultCode, responseStr.length(), rows.isEmpty() ? 0 : rows.split(";").length);

            if (rows.isEmpty()) {
                log.warn("[AStockQuoteRouterTool] baiduKline 数据为空: code={}, 响应前200字符: {}",
                        code, responseStr.substring(0, Math.min(200, responseStr.length())));
                return "未找到 " + code + " 的K线数据（百度API返回 resultCode=" + resultCode + "）";
            }

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== %s K线数据（百度）===\n\n", code));

            // 显示最近5根K线
            // fields: timestamp,time,open,close,volume,high,low,amount,range,ratio,turnoverratio,preClose,
            //         ma5avgprice,ma5volume,ma10avgprice,ma10volume,ma20avgprice,ma20volume
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
                // MA均线: index 12=MA5均价, 14=MA10均价, 16=MA20均价
                if (fields.length >= 17) {
                    String ma5 = "MA5:" + fields[12];
                    String ma10 = "MA10:" + fields[14];
                    String ma20 = "MA20:" + fields[16];
                    sb.append(String.format("    %s  %s  %s\n", ma5, ma10, ma20));
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

    private String getStrOrAlt(JsonNode p, String key, String altKey) {
        if (p != null && p.has(key) && !p.get(key).isNull()) return p.get(key).asText();
        if (p != null && p.has(altKey) && !p.get(altKey).isNull()) return p.get(altKey).asText();
        throw new IllegalArgumentException("缺少参数: " + key);
    }
}
