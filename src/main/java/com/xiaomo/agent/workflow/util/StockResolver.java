package com.xiaomo.agent.workflow.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaomo.agent.common.config.HttpClientService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Headers;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 标的解析器：在工作流启动前将用户查询解析为确定的 {code, name}。
 * 复用东财 suggest API + 新浪 fallback。
 */
@Slf4j
public class StockResolver {

    private static final Pattern CODE_PATTERN = Pattern.compile("\\b(\\d{6})\\b");
    private static final Pattern CHINESE_NAME_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]{2,}");
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // 常见查询前缀，需要剥离后才能作为股票名称搜索
    private static final String[] QUERY_PREFIXES = {
            "深度分析", "深入分析", "全面分析", "详细分析", "深度研究", "深度调研",
            "深度剖析", "深入研究", "全面研究", "详细研究", "个股分析", "个股研究",
            "帮我分析", "帮我看看", "帮我研究", "分析一下", "研究一下",
            "分析", "研究", "调研", "估值"
    };

    // 常见查询后缀，需要剥离后才能作为股票名称搜索
    private static final String[] QUERY_SUFFIXES = {
            "值得入手吗", "值得买吗", "可以买吗", "可以入手吗", "能买吗",
            "怎么样", "如何", "好不好", "行不行", "可以买", "看好吗",
            "值得投资吗", "有前途吗", "前景如何", "还能涨吗", "还能买吗",
            "值得持有吗", "现在能买吗", "现在可以买吗", "目前怎么样",
            "吗", "呢", "吧"
    };

    private StockResolver() {
    }

    /**
     * 从用户查询中解析标的。
     *
     * @param query 用户原始输入，如 "分析丰光精密"、"600519"、"茅台"
     * @param httpClientService HTTP 客户端
     * @return 解析结果 {code, name}
     * @throws IllegalArgumentException 解析失败时抛出
     */
    public static ResolvedStock resolve(String query, HttpClientService httpClientService) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("查询内容不能为空");
        }

        String trimmed = query.trim();

        // 1. 先尝试提取 6 位数字代码
        Matcher codeMatcher = CODE_PATTERN.matcher(trimmed);
        if (codeMatcher.find()) {
            String code = codeMatcher.group(1);
            log.info("[StockResolver] 从查询中提取到数字代码: {}", code);
            // 用代码查东财 API 获取股票名称
            try {
                List<ResolvedStock> results = searchEastMoneyAll(code, httpClientService);
                for (ResolvedStock stock : results) {
                    if (code.equals(stock.code()) && stock.name() != null) {
                        log.info("[StockResolver] 代码反查名称成功: {} -> {}", code, stock.name());
                        return stock;
                    }
                }
            } catch (Exception e) {
                log.warn("[StockResolver] 代码反查名称失败: {}", e.getMessage());
            }
            throw new IllegalArgumentException("未找到代码「" + code + "」对应的A股股票，请确认代码是否正确");
        }

        // 2. 提取中文名称关键词
        Matcher nameMatcher = CHINESE_NAME_PATTERN.matcher(trimmed);
        if (!nameMatcher.find()) {
            throw new IllegalArgumentException("无法从查询「" + trimmed + "」中识别股票名称或代码，请输入股票名称（如丰光精密）或6位代码（如430510）");
        }
        String name = nameMatcher.group();

        // 剥离常见查询前缀（如"深度分析三花智控" → "三花智控"）
        for (String prefix : QUERY_PREFIXES) {
            if (name.startsWith(prefix) && name.length() > prefix.length()) {
                name = name.substring(prefix.length());
                break;
            }
        }

        // 剥离常见查询后缀（如"立讯精密值得入手吗" → "立讯精密"）
        for (String suffix : QUERY_SUFFIXES) {
            if (name.endsWith(suffix) && name.length() > suffix.length()) {
                name = name.substring(0, name.length() - suffix.length());
                break;
            }
        }

        log.info("[StockResolver] 提取到股票名称: {}", name);

        // 3. 调用东财 suggest API 解析（遍历多个结果，优先名称精确匹配）
        try {
            List<ResolvedStock> results = searchEastMoneyAll(name, httpClientService);
            ResolvedStock matched = pickBestMatch(name, results);
            if (matched != null) {
                log.info("[StockResolver] 东财解析成功: {} -> {}({})", name, matched.name(), matched.code());
                return matched;
            }
        } catch (Exception e) {
            log.warn("[StockResolver] 东财搜索异常: {}", e.getMessage());
        }

        // 4. 新浪 fallback（同样遍历多个结果）
        try {
            List<ResolvedStock> results = searchSinaFallbackAll(name, httpClientService);
            ResolvedStock matched = pickBestMatch(name, results);
            if (matched != null) {
                log.info("[StockResolver] 新浪解析成功: {} -> {}({})", name, matched.name(), matched.code());
                return matched;
            }
        } catch (Exception e) {
            log.warn("[StockResolver] 新浪搜索异常: {}", e.getMessage());
        }

        throw new IllegalArgumentException("未找到与「" + name + "」相关的A股股票，请确认名称是否正确，或直接输入6位股票代码");
    }

    /**
     * 搜索东财 suggest API，返回所有匹配的 A 股结果
     */
    public static List<ResolvedStock> searchEastMoneyAll(String keyword, HttpClientService httpClientService) throws Exception {
        String url = "https://searchapi.eastmoney.com/api/suggest/get"
                + "?input=" + java.net.URLEncoder.encode(keyword, "UTF-8")
                + "&type=14&token=D43BF722C8E33BDC906FB84D85E326E8&count=5";
        log.info("[StockResolver] searchEastMoney 请求: {}", url);

        Headers headers = new Headers.Builder()
                .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build();

        List<ResolvedStock> results = new ArrayList<>();
        String body = httpClientService.get(url, headers);
        if (body == null || body.isBlank()) return results;

        JsonNode root = objectMapper.readTree(body);
        JsonNode quotes = root.path("QuotationCodeTable").path("Data");
        if (!quotes.isArray() || quotes.isEmpty()) return results;

        for (JsonNode item : quotes) {
            String code = item.path("Code").asText("");
            String name = item.path("Name").asText("");
            String mktNum = item.path("MktNum").asText("");
            if (code.length() == 6 && ("1".equals(mktNum) || "0".equals(mktNum))) {
                results.add(new ResolvedStock(code, name));
            }
        }
        return results;
    }

    /**
     * 搜索新浪 suggest API（fallback），返回所有匹配的 A 股结果
     */
    public static List<ResolvedStock> searchSinaFallbackAll(String keyword, HttpClientService httpClientService) throws Exception {
        String url = "https://suggest3.sinajs.cn/suggest/type=&key="
                + java.net.URLEncoder.encode(keyword, "UTF-8") + "&name=suggest";
        log.info("[StockResolver] searchSinaFallback 请求: {}", url);

        Headers headers = new Headers.Builder()
                .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .add("Referer", "https://finance.sina.com.cn")
                .build();

        List<ResolvedStock> results = new ArrayList<>();
        String body = httpClientService.get(url, headers);
        if (body == null || body.isBlank()) return results;

        String data = body.contains("\"") ? body.split("\"")[1] : "";
        for (String entry : data.split(";")) {
            if (entry.length() < 5) continue;
            String[] parts = entry.split(",");
            if (parts.length < 2) continue;
            String id = parts[0];
            String name = parts[1];
            if (id.length() < 8) continue;
            String market = id.substring(0, 2);
            String code = id.substring(2);
            if (("sh".equals(market) || "sz".equals(market)) && code.length() == 6
                    && (code.startsWith("6") || code.startsWith("0") || code.startsWith("3"))) {
                results.add(new ResolvedStock(code, name));
            }
        }
        return results;
    }

    /**
     * 从候选结果中选择最佳匹配。
     * 优先选择名称包含输入关键词的结果，避免错标。
     * 如果没有任何名称匹配，返回第一个结果（兜底）。
     */
    static ResolvedStock pickBestMatch(String inputName, List<ResolvedStock> candidates) {
        if (candidates == null || candidates.isEmpty()) return null;

        // 优先：名称包含输入关键词
        for (ResolvedStock stock : candidates) {
            if (stock.name() != null && stock.name().contains(inputName)) {
                return stock;
            }
        }

        // 次优：输入关键词包含返回名称（如输入"茅台"返回"贵州茅台"）
        for (ResolvedStock stock : candidates) {
            if (stock.name() != null && inputName.contains(stock.name())) {
                return stock;
            }
        }

        // 兜底：取第一个结果，但记录警告
        log.warn("[StockResolver] 无精确名称匹配，使用第一个结果。输入: {}, 候选: {}",
                inputName, candidates.stream().map(ResolvedStock::name).toList());
        return candidates.get(0);
    }

    /**
     * 解析结果
     *
     * @param code 6位股票代码
     * @param name 股票名称（可为 null，表示仅从数字代码提取时未查名称）
     */
    public record ResolvedStock(String code, String name) {
    }
}
