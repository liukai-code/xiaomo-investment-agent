package com.xiaomo.agent.tool.astock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaomo.agent.tool.annotation.ToolBehavior;
import com.xiaomo.agent.common.config.HttpClientService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Headers;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
public class AStockOptionRouterTool {

    private final HttpClientService httpClientService;
    private final ObjectMapper objectMapper;

    public AStockOptionRouterTool(HttpClientService httpClientService) {
        this.httpClientService = httpClientService;
        this.objectMapper = new ObjectMapper();
    }

    @ToolBehavior(deterministic = false, cacheable = false)
    @Tool(description = """
            ETF期权查询。查询期权合约清单、T型报价、希腊字母+隐含波动率。

            operation 可选值：
            - optionCodes: ETF期权合约清单。参数: underlying（"510050"/"510300"/"588000"/"510500"）, call（true=认购/false=认沽）
            - optionTQuote: 期权T型报价。参数: contractCode（如"10004857"）
            - optionGreeks: 期权希腊字母+IV。参数: contractCode

            params 为 JSON 字符串。
            """)
    public String a_stock_option(
            @ToolParam(description = "操作类型") String operation,
            @ToolParam(description = "JSON格式参数") String params) {
        log.info("[AStockOptionRouterTool] operation={}, params={}", operation, params);
        try {
            if (operation == null || operation.isBlank()) {
                return "操作类型不能为空。可用操作：optionCodes, optionTQuote, optionGreeks";
            }
            JsonNode p = parseParams(params);
            return switch (operation.trim()) {
                case "optionCodes" -> optionCodes(getOptStr(p, "underlying", "510050"), getOptBool(p, "call", true));
                case "optionTQuote" -> optionTQuote(getStr(p, "contractCode"));
                case "optionGreeks" -> optionGreeks(getStr(p, "contractCode"));
                default -> "未知操作: " + operation;
            };
        } catch (Exception e) {
            log.error("[AStockOptionRouterTool] 异常: operation={}, error={}", operation, e.getMessage(), e);
            return "操作失败（operation=" + operation + "）: " + e.getMessage();
        }
    }

    private String optionCodes(String underlying, boolean isCall) {
        try {
            Map<String, String> cateMap = Map.of(
                    "510050", "50ETF", "510300", "300ETF",
                    "588000", "科创50ETF", "510500", "500ETF"
            );
            String cate = cateMap.getOrDefault(underlying, "50ETF");
            String url = "https://stock.finance.sina.com.cn/futures/api/openapi.php/"
                    + "StockOptionService.getStockName?exchange=null&cate=" + cate;
            log.debug("[AStockOptionRouterTool] 请求期权合约清单: underlying={}, cate={}", underlying, cate);
            String body = httpClientService.get(url, Headers.of(
                    "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            ));
            JsonNode root = objectMapper.readTree(body);
            JsonNode months = root.path("result").path("data").path("contractMonth");

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== %s 期权合约清单 (%s) ===\n\n", cate, isCall ? "认购" : "认沽"));

            if (months.isArray() && months.size() > 1) {
                String flag = isCall ? "OP_UP_" : "OP_DOWN_";
                for (int i = 1; i < months.size(); i++) {
                    String month = months.get(i).asText().replace("-", "").substring(2);
                    String listUrl = "https://hq.sinajs.cn/list=" + flag + underlying + month;
                    log.debug("[AStockOptionRouterTool] 请求期权月份合约: flag={}, underlying={}, month={}", flag, underlying, month);
                    String listBody = httpClientService.get(listUrl, Headers.of(
                            "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                            "Referer", "https://finance.sina.com.cn/"
                    ));
                    // GBK 解码
                    String decoded = new String(listBody.getBytes(StandardCharsets.ISO_8859_1), Charset.forName("GBK"));
                    List<String> codes = new ArrayList<>();
                    for (String line : decoded.split(";")) {
                        if (line.contains("CON_OP_")) {
                            String code = line.substring(line.indexOf("CON_OP_") + 8).replace("\"", "").trim();
                            codes.add(code);
                        }
                    }
                    if (!codes.isEmpty()) {
                        sb.append(String.format("  %s月: %s\n", month, String.join(", ", codes)));
                    }
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "期权合约清单查询失败: " + e.getMessage();
        }
    }

    private String optionTQuote(String contractCode) {
        try {
            String url = "https://hq.sinajs.cn/list=CON_OP_" + contractCode;
            log.debug("[AStockOptionRouterTool] 请求期权T型报价: contractCode={}", contractCode);
            String body = httpClientService.get(url, Headers.of(
                    "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                    "Referer", "https://finance.sina.com.cn/"
            ));
            String decoded = new String(body.getBytes(StandardCharsets.ISO_8859_1), Charset.forName("GBK"));
            String[] parts = decoded.split("=")[1].replace("\"", "").split(",");
            if (parts.length < 43) {
                return "期权数据不足";
            }

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== 期权T型报价 %s ===\n\n", contractCode));
            sb.append(String.format("  名称: %s\n", parts[37]));
            sb.append(String.format("  最新: %s  涨跌: %s%%\n", parts[2], parts[6]));
            sb.append(String.format("  买一: %s x %s  卖一: %s x %s\n", parts[1], parts[0], parts[3], parts[4]));
            sb.append(String.format("  行权价: %s  持仓量: %s\n", parts[7], parts[5]));
            sb.append(String.format("  今开: %s  昨收: %s  最高: %s  最低: %s\n", parts[9], parts[8], parts[39], parts[40]));
            sb.append(String.format("  成交量: %s  成交额: %s\n", parts[41], parts[42]));
            return sb.toString();
        } catch (Exception e) {
            return "期权T型报价查询失败: " + e.getMessage();
        }
    }

    private String optionGreeks(String contractCode) {
        try {
            String url = "https://hq.sinajs.cn/list=CON_SO_" + contractCode;
            log.debug("[AStockOptionRouterTool] 请求期权Greeks: contractCode={}", contractCode);
            String body = httpClientService.get(url, Headers.of(
                    "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                    "Referer", "https://finance.sina.com.cn/"
            ));
            String decoded = new String(body.getBytes(StandardCharsets.ISO_8859_1), Charset.forName("GBK"));
            String[] parts = decoded.split("=")[1].replace("\"", "").split(",");
            if (parts.length < 16) {
                return "希腊字母数据不足";
            }

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== 期权希腊字母 %s ===\n\n", contractCode));
            sb.append(String.format("  名称: %s\n", parts[0]));
            sb.append(String.format("  Delta: %s  Gamma: %s\n", parts[2], parts[3]));
            sb.append(String.format("  Theta: %s  Vega: %s\n", parts[4], parts[5]));
            sb.append(String.format("  隐含波动率(IV): %s\n", parts[6]));
            sb.append(String.format("  行权价: %s  最新价: %s\n", parts[7], parts[8]));
            sb.append(String.format("  理论价值: %s\n", parts[9]));
            return sb.toString();
        } catch (Exception e) {
            return "希腊字母查询失败: " + e.getMessage();
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

    private boolean getOptBool(JsonNode p, String key, boolean defaultVal) {
        if (p == null || !p.has(key) || p.get(key).isNull()) return defaultVal;
        return p.get(key).asBoolean(defaultVal);
    }
}
