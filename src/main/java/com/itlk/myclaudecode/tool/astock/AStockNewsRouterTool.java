package com.itlk.myclaudecode.tool.astock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itlk.myclaudecode.tool.annotation.ToolBehavior;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class AStockNewsRouterTool {

    private static final DateTimeFormatter YYYY_MM_DD = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Map<String, String> CNINFO_ORGID_CACHE = new ConcurrentHashMap<>();

    private final EastMoneyRateLimiter emRateLimiter;
    private final ObjectMapper objectMapper;

    public AStockNewsRouterTool(EastMoneyRateLimiter emRateLimiter) {
        this.emRateLimiter = emRateLimiter;
        this.objectMapper = new ObjectMapper();
    }

    @ToolBehavior(deterministic = false, cacheable = false)
    @Tool(description = """
            A股新闻公告查询。查询个股新闻、全球资讯、公告全文、互动易问答、财报三表。

            operation 可选值：
            - stockNews: 个股新闻。参数: stockCode, pageSize（可选，默认20）
            - globalNews: 全球财经资讯。参数: pageSize（可选，默认50）
            - cninfoAnnouncements: 巨潮公告全文。参数: stockCode, pageSize（可选，默认30）
            - irmQA: 互动易问答（公司回应投资者）。参数: stockCode, pageSize（可选，默认30）
            - sinaFinancialReport: 新浪财报三表。参数: stockCode, reportType（"lrb"利润表/"fzb"资产负债表/"llb"现金流量表）, num（可选，默认8期）

            params 为 JSON 字符串。
            """)
    public String a_stock_news(
            @ToolParam(description = "操作类型") String operation,
            @ToolParam(description = "JSON格式参数") String params) {
        log.info("[AStockNewsRouterTool] operation={}, params={}", operation, params);
        try {
            if (operation == null || operation.isBlank()) {
                return "操作类型不能为空。可用操作：stockNews, globalNews, cninfoAnnouncements, irmQA, sinaFinancialReport";
            }
            JsonNode p = parseParams(params);
            return switch (operation.trim()) {
                case "stockNews" -> stockNews(getStr(p, "stockCode"), getOptInt(p, "pageSize", 20));
                case "globalNews" -> globalNews(getOptInt(p, "pageSize", 50));
                case "cninfoAnnouncements" -> cninfoAnnouncements(getStr(p, "stockCode"), getOptInt(p, "pageSize", 30));
                case "irmQA" -> irmQA(getStr(p, "stockCode"), getOptInt(p, "pageSize", 30));
                case "sinaFinancialReport" -> sinaFinancialReport(getStr(p, "stockCode"),
                        getOptStr(p, "reportType", "lrb"), getOptInt(p, "num", 8));
                default -> "未知操作: " + operation;
            };
        } catch (Exception e) {
            log.error("[AStockNewsRouterTool] 异常: operation={}, error={}", operation, e.getMessage(), e);
            return "操作失败（operation=" + operation + "）: " + e.getMessage();
        }
    }

    private String stockNews(String stockCode, int pageSize) {
        try {
            String code = AStockUtils.normalizeCode(stockCode);
            String innerParams = objectMapper.writeValueAsString(Map.of(
                    "uid", "",
                    "keyword", code,
                    "type", List.of("cmsArticleWebOld"),
                    "client", "web",
                    "clientType", "web",
                    "clientVersion", "curr",
                    "param", Map.of("cmsArticleWebOld", Map.of(
                            "searchScope", "default", "sort", "default",
                            "pageIndex", 1, "pageSize", pageSize,
                            "preTag", "", "postTag", ""
                    ))
            ));
            String url = "https://search-api-web.eastmoney.com/search/jsonp?cb=jQuery_news&param="
                    + URLEncoder.encode(innerParams, StandardCharsets.UTF_8);
            String body = emRateLimiter.get(url, Map.of("Referer", "https://so.eastmoney.com/"));

            // 解析 JSONP
            int start = body.indexOf("(");
            int end = body.lastIndexOf(")");
            if (start < 0 || end < 0) return "东财新闻JSONP解析失败";
            String jsonStr = body.substring(start + 1, end);
            JsonNode root = objectMapper.readTree(jsonStr);
            JsonNode articles = root.path("result").path("cmsArticleWebOld");

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== %s 个股新闻 共 %d 条 ===\n\n", code, articles.size()));
            if (articles.isArray()) {
                for (JsonNode a : articles) {
                    String title = a.path("title").asText("").replaceAll("<[^>]+>", "");
                    String date = a.path("date").asText("");
                    String source = a.path("mediaName").asText("");
                    String url2 = a.path("url").asText("");
                    sb.append(String.format("- %s\n  %s | %s\n  %s\n\n", title, date, source, url2));
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "个股新闻查询失败: " + e.getMessage();
        }
    }

    private String globalNews(int pageSize) {
        try {
            String url = "https://np-weblist.eastmoney.com/comm/web/getFastNewsList"
                    + "?client=web&biz=web_724&fastColumn=102&sortEnd=&pageSize=" + pageSize
                    + "&req_trace=" + UUID.randomUUID();
            String body = emRateLimiter.get(url, Map.of("Referer", "https://kuaixun.eastmoney.com/"));
            JsonNode root = objectMapper.readTree(body);
            JsonNode items = root.path("data").path("fastNewsList");

            StringBuilder sb = new StringBuilder();
            sb.append("=== 全球财经资讯 7x24 ===\n\n");
            if (items.isArray()) {
                for (JsonNode item : items) {
                    String title = item.path("title").asText("");
                    String summary = item.path("summary").asText("");
                    if (summary.length() > 100) summary = summary.substring(0, 100) + "...";
                    String time = item.path("showTime").asText("");
                    sb.append(String.format("- [%s] %s\n  %s\n\n", time, title, summary));
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "全球资讯查询失败: " + e.getMessage();
        }
    }

    private String cninfoAnnouncements(String stockCode, int pageSize) {
        try {
            String code = AStockUtils.normalizeCode(stockCode);
            String orgId = getCninfoOrgId(code);

            String url = "https://www.cninfo.com.cn/new/hisAnnouncement/query";
            String formBody = "stock=" + code + "," + orgId
                    + "&tabName=fulltext&pageSize=" + pageSize + "&pageNum=1"
                    + "&column=&category=&plate=&seDate=&searchkey=&secid="
                    + "&sortName=&sortType=&isHLtitle=true";

            RequestBody body = RequestBody.create(formBody,
                    okhttp3.MediaType.parse("application/x-www-form-urlencoded"));
            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Referer", "https://www.cninfo.com.cn/new/disclosure")
                    .header("Origin", "https://www.cninfo.com.cn")
                    .build();

            // cninfo 不走东财限流器，需要直接用 emRateLimiter 的 post 方法
            String responseBody = emRateLimiter.post(url, formBody, Map.of(
                    "Content-Type", "application/x-www-form-urlencoded",
                    "Referer", "https://www.cninfo.com.cn/new/disclosure"
            ));
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode announcements = root.path("announcements");

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== %s 公告全文 ===\n\n", code));
            if (announcements.isArray()) {
                for (JsonNode a : announcements) {
                    String title = a.path("announcementTitle").asText("");
                    String date = a.path("announcementTime") != null ?
                            LocalDateTime.ofInstant(Instant.ofEpochMilli(a.path("announcementTime").asLong(0)),
                                    ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) : "";
                    sb.append(String.format("- %s  %s\n", date, title));
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "公告查询失败: " + e.getMessage();
        }
    }

    private String getCninfoOrgId(String code) {
        return CNINFO_ORGID_CACHE.computeIfAbsent(code, c -> {
            try {
                String url = "https://www.cninfo.com.cn/new/data/szse_stock.json";
                String body = emRateLimiter.get(url, Map.of());
                JsonNode root = objectMapper.readTree(body);
                JsonNode stockList = root.path("stockList");
                if (stockList.isArray()) {
                    for (JsonNode stock : stockList) {
                        if (c.equals(stock.path("code").asText(""))) {
                            return stock.path("orgId").asText("gssx0" + c);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("CNInfo orgId 查询失败，使用 fallback: {}", e.getMessage());
            }
            return "gssx0" + c;
        });
    }

    private String irmQA(String stockCode, int pageSize) {
        try {
            String code = AStockUtils.normalizeCode(stockCode);
            // 第一步：查询 orgId
            String url1 = "https://irm.cninfo.com.cn/newircs/index/queryKeyboardInfo";
            String body1 = emRateLimiter.post(url1, "keyWord=" + code, Map.of());
            JsonNode d1 = objectMapper.readTree(body1).path("data");
            if (!d1.isArray() || d1.isEmpty()) {
                return "互动易：未找到 " + code + " 的公司信息";
            }
            String orgId = d1.get(0).path("secid").asText();

            // 第二步：查询问答
            String url2 = "https://irm.cninfo.com.cn/newircs/company/question"
                    + "?_t=1&stockcode=" + code + "&orgId=" + orgId
                    + "&pageSize=" + pageSize + "&pageNum=1&keyWord=&startDay=&endDay=";
            String body2 = emRateLimiter.post(url2, "", Map.of());
            JsonNode rows = objectMapper.readTree(body2).path("rows");

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== %s 互动易问答 ===\n\n", code));
            if (rows.isArray()) {
                for (JsonNode row : rows) {
                    String question = row.path("mainContent").asText("");
                    String answer = row.path("attachedContent").asText("（未回复）");
                    String answerer = row.path("attachedAuthor").asText("");
                    long pubDate = row.path("pubDate").asLong(0);
                    String time = pubDate > 0 ? LocalDateTime.ofInstant(Instant.ofEpochMilli(pubDate),
                            ZoneId.of("Asia/Shanghai")).format(YYYY_MM_DD) : "";
                    sb.append(String.format("提问(%s): %s\n回答(%s): %s\n\n", time, question, answerer, answer));
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "互动易查询失败: " + e.getMessage();
        }
    }

    private String sinaFinancialReport(String stockCode, String reportType, int num) {
        try {
            String code = AStockUtils.normalizeCode(stockCode);
            String prefix = code.startsWith("6") ? "sh" : "sz";
            String paperCode = prefix + code;
            String url = "https://quotes.sina.cn/cn/api/openapi.php/CompanyFinanceService.getFinanceReport2022"
                    + "?paperCode=" + paperCode + "&source=" + reportType + "&type=0&page=1&num=" + num;
            String body = emRateLimiter.get(url, Map.of());
            JsonNode root = objectMapper.readTree(body);
            JsonNode reportList = root.path("result").path("data").path("report_list");

            String typeName = switch (reportType) {
                case "lrb" -> "利润表";
                case "fzb" -> "资产负债表";
                case "llb" -> "现金流量表";
                default -> reportType;
            };
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== %s 新浪%s ===\n\n", code, typeName));

            List<String> periods = new ArrayList<>();
            reportList.fields().forEachRemaining(e -> periods.add(e.getKey()));
            Collections.sort(periods, Collections.reverseOrder());

            for (String period : periods.stream().limit(num).toList()) {
                String formattedPeriod = period.substring(0, 4) + "-" + period.substring(4, 6) + "-" + period.substring(6, 8);
                JsonNode periodData = reportList.path(period).path("data");
                sb.append(String.format("--- %s ---\n", formattedPeriod));
                if (periodData.isArray()) {
                    for (JsonNode item : periodData) {
                        String title = item.path("item_title").asText("");
                        String value = item.path("item_value").asText("");
                        sb.append(String.format("  %s: %s\n", title, value));
                    }
                }
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "财报查询失败: " + e.getMessage();
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
