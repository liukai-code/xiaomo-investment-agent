package com.xiaomo.agent.tool.astock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaomo.agent.tool.annotation.ToolBehavior;
import com.xiaomo.agent.common.config.HttpClientService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Headers;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;

import java.util.*;

@Slf4j
public class AStockReportRouterTool {

    private static final String REPORT_API = "https://reportapi.eastmoney.com/report/list";
    private static final String PDF_TPL = "https://pdf.dfcfw.com/pdf/H3_%s_1.pdf";

    private final HttpClientService httpClientService;
    private final EastMoneyRateLimiter emRateLimiter;
    private final ObjectMapper objectMapper;
    private final String iwencaiApiKey;

    public AStockReportRouterTool(HttpClientService httpClientService,
                                   EastMoneyRateLimiter emRateLimiter,
                                   @Value("${astock.iwencai.api-key:}") String iwencaiApiKey) {
        this.httpClientService = httpClientService;
        this.emRateLimiter = emRateLimiter;
        this.objectMapper = new ObjectMapper();
        this.iwencaiApiKey = iwencaiApiKey;
    }

    @ToolBehavior(deterministic = false, cacheable = false)
    @Tool(description = """
            A股研报查询。查询个股/行业研报、一致预期EPS、NL语义搜索研报。

            operation 可选值：
            - stockReport: 查询个股研报列表。参数: stockCode, maxPages（可选，默认5）
            - industryReport: 查询行业研报列表。参数: industryCode（"*"=全行业，或东财行业码如"1238"）, maxPages
            - downloadReportPdf: 下载研报PDF（返回文件名提示，实际PDF需访问 https://pdf.dfcfw.com/pdf/H3_{infoCode}_1.pdf）。参数: infoCode
            - thsEpsForecast: 同花顺一致预期EPS。参数: stockCode
            - iwencaiSearch: iwencai语义搜索研报（需API Key）。参数: query, channel（可选，默认"report"）, size（可选，默认50）
            - iwencaiQuery: iwencai结构化数据查询（需API Key）。参数: query, page, limit

            params 为 JSON 字符串。
            """)
    public String a_stock_report(
            @ToolParam(description = "操作类型") String operation,
            @ToolParam(description = "JSON格式参数") String params) {
        log.info("[AStockReportRouterTool] operation={}, params={}", operation, params);
        try {
            if (operation == null || operation.isBlank()) {
                return "操作类型不能为空。可用操作：stockReport, industryReport, downloadReportPdf, thsEpsForecast, iwencaiSearch, iwencaiQuery";
            }
            JsonNode p = parseParams(params);
            return switch (operation.trim()) {
                case "stockReport" -> stockReport(getStr(p, "stockCode"), getOptInt(p, "maxPages", 5));
                case "industryReport" -> industryReport(getStr(p, "industryCode"), getOptInt(p, "maxPages", 5));
                case "downloadReportPdf" -> downloadReportPdf(getStr(p, "infoCode"));
                case "thsEpsForecast" -> thsEpsForecast(getStr(p, "stockCode"));
                case "iwencaiSearch" -> iwencaiSearch(getStr(p, "query"), getOptStr(p, "channel", "report"), getOptInt(p, "size", 50));
                case "iwencaiQuery" -> iwencaiQuery(getStr(p, "query"), getOptInt(p, "page", 1), getOptInt(p, "limit", 50));
                default -> "未知操作: " + operation;
            };
        } catch (IllegalArgumentException e) {
            log.error("[AStockReportRouterTool] 参数错误: operation={}, error={}", operation, e.getMessage());
            return "参数错误（operation=" + operation + "）: " + e.getMessage()
                    + "。请检查 params JSON 格式，例如: {\"stockCode\":\"430510\"}";
        } catch (Exception e) {
            log.error("[AStockReportRouterTool] 异常: operation={}, error={}", operation, e.getMessage(), e);
            return "操作失败（operation=" + operation + "）: " + e.getMessage();
        }
    }

    private String stockReport(String stockCode, int maxPages) {
        try {
            String code = AStockUtils.normalizeCode(stockCode);
            List<Map<String, Object>> allRecords = new ArrayList<>();
            for (int page = 1; page <= maxPages; page++) {
                String url = REPORT_API
                        + "?industryCode=*&pageSize=100&industry=*&rating=*&ratingChange=*"
                        + "&beginTime=2000-01-01&endTime=2030-01-01"
                        + "&pageNo=" + page + "&fields=&qType=0&orgCode=&code=" + code + "&rcode="
                        + "&p=" + page + "&pageNum=" + page + "&pageNumber=" + page;
                log.debug("[AStockReportRouterTool] 请求个股研报: code={}, page={}", code, page);
                String body = emRateLimiter.get(url, Map.of("Referer", "https://data.eastmoney.com/"));
                JsonNode root = objectMapper.readTree(body);
                JsonNode data = root.path("data");
                if (!data.isArray() || data.isEmpty()) break;
                data.forEach(node -> allRecords.add(objectMapper.convertValue(node, Map.class)));
                int totalPage = root.path("TotalPage").asInt(1);
                if (page >= totalPage) break;
            }
            return formatReportList(code, allRecords);
        } catch (Exception e) {
            return "个股研报查询失败: " + e.getMessage();
        }
    }

    private String industryReport(String industryCode, int maxPages) {
        try {
            List<Map<String, Object>> allRecords = new ArrayList<>();
            for (int page = 1; page <= maxPages; page++) {
                String url = REPORT_API
                        + "?industryCode=" + industryCode + "&pageSize=100&industry=*&rating=*&ratingChange=*"
                        + "&beginTime=2024-01-01&endTime=2030-01-01"
                        + "&pageNo=" + page + "&fields=&qType=1";
                log.debug("[AStockReportRouterTool] 请求行业研报: industryCode={}, page={}", industryCode, page);
                String body = emRateLimiter.get(url, Map.of("Referer", "https://data.eastmoney.com/"));
                JsonNode root = objectMapper.readTree(body);
                JsonNode data = root.path("data");
                if (!data.isArray() || data.isEmpty()) break;
                data.forEach(node -> allRecords.add(objectMapper.convertValue(node, Map.class)));
                int totalPage = root.path("TotalPage").asInt(1);
                if (page >= totalPage) break;
            }
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== 行业研报 [%s] 共 %d 篇 ===\n\n", industryCode, allRecords.size()));
            for (Map<String, Object> r : allRecords.stream().limit(20).toList()) {
                String date = strVal(r.get("publishDate"), "").substring(0, Math.min(10, strVal(r.get("publishDate"), "").length()));
                sb.append(String.format("%s | %s | %s | %s\n",
                        date, r.get("industryName"), r.get("orgSName"), truncate(strVal(r.get("title"), ""), 60)));
            }
            return sb.toString();
        } catch (Exception e) {
            return "行业研报查询失败: " + e.getMessage();
        }
    }

    private String downloadReportPdf(String infoCode) {
        try {
            String url = String.format(PDF_TPL, infoCode);
            return "研报PDF下载链接: " + url + "\n（请在浏览器中打开下载，或使用 webFetch 工具抓取内容）";
        } catch (Exception e) {
            return "PDF链接生成失败: " + e.getMessage();
        }
    }

    private String thsEpsForecast(String stockCode) {
        try {
            String code = AStockUtils.normalizeCode(stockCode);
            String url = "https://basic.10jqka.com.cn/new/" + code + "/worth.html";
            log.debug("[AStockReportRouterTool] 请求同花顺EPS预期: code={}", code);
            String body = httpClientService.get(url, Headers.of(
                    "User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36",
                    "Referer", "https://basic.10jqka.com.cn/"
            ));

            // Jsoup 解析 HTML 表格
            Document doc = Jsoup.parse(body);
            Elements tables = doc.select("table");

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== %s 一致预期EPS（同花顺）===\n\n", code));

            for (Element table : tables) {
                Elements headers = table.select("th");
                String headerText = headers.text();
                if (headerText.contains("每股收益") || headerText.contains("均值")) {
                    Elements rows = table.select("tr");
                    for (Element row : rows) {
                        Elements cells = row.select("td, th");
                        List<String> cellTexts = new ArrayList<>();
                        cells.forEach(c -> cellTexts.add(c.text().trim()));
                        sb.append(String.join(" | ", cellTexts)).append("\n");
                    }
                    return sb.toString();
                }
            }
            sb.append("未找到一致预期数据表格");
            return sb.toString();
        } catch (Exception e) {
            return "同花顺EPS预期查询失败: " + e.getMessage();
        }
    }

    private String iwencaiSearch(String query, String channel, int size) {
        if (iwencaiApiKey == null || iwencaiApiKey.isBlank()) {
            return "iwencai API Key 未配置，请设置环境变量 IWENCAI_API_KEY";
        }
        try {
            String url = "https://openapi.iwencai.com/v1/comprehensive/search";
            Map<String, Object> payload = new HashMap<>();
            payload.put("channels", List.of(channel));
            payload.put("app_id", "AIME_SKILL");
            payload.put("query", query);
            payload.put("size", size);

            String jsonBody = objectMapper.writeValueAsString(payload);
            Map<String, String> headers = Map.of(
                    "Authorization", "Bearer " + iwencaiApiKey,
                    "Content-Type", "application/json"
            );
            log.debug("[AStockReportRouterTool] iwencai语义搜索: query={}", query);
            String responseBody = emRateLimiter.post(url, jsonBody, headers);
            JsonNode root = objectMapper.readTree(responseBody);
            int statusCode = root.path("status_code").asInt(-1);
            if (statusCode != 0) {
                return "iwencai错误: " + root.path("status_msg").asText("未知错误");
            }
            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) {
                return "iwencai搜索无结果";
            }
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== iwencai语义搜索 [%s] 共 %d 条 ===\n\n", query, data.size()));
            for (JsonNode item : data) {
                sb.append(String.format("- %s\n  来源: %s | 日期: %s\n\n",
                        item.path("title").asText(""),
                        item.path("extra").asText(""),
                        item.path("publish_date").asText("")));
            }
            return sb.toString();
        } catch (Exception e) {
            return "iwencai搜索失败: " + e.getMessage();
        }
    }

    private String iwencaiQuery(String query, int page, int limit) {
        if (iwencaiApiKey == null || iwencaiApiKey.isBlank()) {
            return "iwencai API Key 未配置，请设置环境变量 IWENCAI_API_KEY";
        }
        try {
            String url = "https://openapi.iwencai.com/v1/query2data";
            Map<String, Object> payload = new HashMap<>();
            payload.put("query", query);
            payload.put("page", String.valueOf(page));
            payload.put("perpage", String.valueOf(limit));

            String jsonBody = objectMapper.writeValueAsString(payload);
            Map<String, String> headers = Map.of(
                    "Authorization", "Bearer " + iwencaiApiKey,
                    "Content-Type", "application/json"
            );
            log.debug("[AStockReportRouterTool] iwencai结构化查询: query={}", query);
            String responseBody = emRateLimiter.post(url, jsonBody, headers);
            JsonNode root = objectMapper.readTree(responseBody);
            int statusCode = root.path("status_code").asInt(-1);
            if (statusCode != 0) {
                return "iwencai错误: " + root.path("status_msg").asText("未知错误");
            }
            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) {
                return "iwencai查询无结果";
            }
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== iwencai结构化查询 [%s] 共 %d 条 ===\n\n", query, data.size()));
            for (JsonNode item : data) {
                sb.append(item.toString()).append("\n\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "iwencai查询失败: " + e.getMessage();
        }
    }

    private String formatReportList(String code, List<Map<String, Object>> records) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("=== %s 研报列表 共 %d 篇 ===\n\n", code, records.size()));
        for (Map<String, Object> r : records.stream().limit(20).toList()) {
            String date = strVal(r.get("publishDate"), "").substring(0, Math.min(10, strVal(r.get("publishDate"), "").length()));
            String title = truncate(strVal(r.get("title"), ""), 50);
            String org = strVal(r.get("orgSName"), "");
            String rating = strVal(r.get("emRatingName"), "");
            String eps = strVal(r.get("predictThisYearEps"), "");
            sb.append(String.format("%s | %s | %s | 评级:%s | 预期EPS:%s\n", date, org, title, rating, eps));
        }
        return sb.toString();
    }

    private String strVal(Object obj, String defaultVal) {
        return obj == null ? defaultVal : obj.toString();
    }

    private String truncate(String s, int maxLen) {
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
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

    private int getOptInt(JsonNode p, String key, int defaultVal) {
        if (p == null || !p.has(key) || p.get(key).isNull()) return defaultVal;
        return p.get(key).asInt(defaultVal);
    }

    private String getOptStr(JsonNode p, String key, String defaultVal) {
        if (p == null || !p.has(key) || p.get(key).isNull()) return defaultVal;
        String val = p.get(key).asText();
        return val.isEmpty() ? defaultVal : val;
    }
}
