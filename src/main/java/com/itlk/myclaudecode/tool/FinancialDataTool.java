package com.itlk.myclaudecode.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Headers;
import com.itlk.myclaudecode.tool.annotation.ToolBehavior;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class FinancialDataTool {

    private static final Pattern FUND_JSONP_PATTERN = Pattern.compile("jsonpgz\\((\\{.*\\})\\);?");
    private static final Pattern CODE_PATTERN = Pattern.compile("^[0-9a-zA-Z]{1,10}$");
    private static final Pattern TENCENT_QUOTE_PATTERN = Pattern.compile("v_\\w+=\"([^\"]+)\"");

    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;

    public FinancialDataTool() {
        this.okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    private static final Pattern PURE_DIGITS = Pattern.compile("^\\d{6}$");

    @ToolBehavior(deterministic = false, cacheable = false)
    @Tool(description = "查询A股股票实时行情。支持两种输入：6位数字代码（如600519）或股票名称/关键词（如茅台、亨通光电）。传入名称时会自动搜索代码再查询行情。")
    public String getAShareQuote(
            @ToolParam(description = "A股股票代码（6位数字）或股票名称/关键词，如600519、茅台、亨通光电") String stockCodeOrName) {
        log.info("[FinancialDataTool] getAShareQuote 入参: stockCodeOrName={}", stockCodeOrName);
        try {
            if (stockCodeOrName == null || stockCodeOrName.isBlank()) {
                return "股票代码或名称不能为空";
            }
            String input = stockCodeOrName.trim();

            // 判断是代码还是名称
            if (PURE_DIGITS.matcher(input).matches()) {
                // 6位数字，直接查行情
                String symbol = buildAShareSymbol(input);
                String result = fetchTencentQuote(symbol, input, "A股");
                log.info("[FinancialDataTool] getAShareQuote 出参: {}", result);
                return result;
            } else {
                // 非纯数字，当作名称搜索
                log.info("[FinancialDataTool] 输入非代码格式，自动搜索: {}", input);
                String searchResult = searchEastMoney(input);
                if (searchResult == null || searchResult.isBlank()) {
                    searchResult = searchSinaFallback(input);
                }
                if (searchResult == null || searchResult.isBlank()) {
                    return "未找到与「" + input + "」相关的A股股票，请确认名称是否正确。";
                }

                // 从搜索结果中提取第一个代码
                String firstCode = extractFirstCode(searchResult);
                if (firstCode == null) {
                    return "搜索到结果但无法提取代码，请尝试输入更精确的名称。\n搜索结果：\n" + searchResult;
                }

                // 用提取到的代码查行情
                log.info("[FinancialDataTool] 搜索到代码: {}，查询行情", firstCode);
                String symbol = buildAShareSymbol(firstCode);
                String quoteResult = fetchTencentQuote(symbol, firstCode, "A股");
                log.info("[FinancialDataTool] getAShareQuote 出参: {}", quoteResult);
                return quoteResult;
            }
        } catch (Exception e) {
            log.error("[FinancialDataTool] getAShareQuote 异常: {}", e.getMessage(), e);
            return "查询A股行情失败: " + e.getMessage();
        }
    }

    private String extractFirstCode(String searchResult) {
        // searchResult 格式: "600519 贵州茅台（沪市）\n000858 五粮液（深市）"
        String[] lines = searchResult.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.length() >= 6) {
                String candidate = trimmed.substring(0, 6);
                if (PURE_DIGITS.matcher(candidate).matches()) {
                    return candidate;
                }
            }
        }
        return null;
    }

    @ToolBehavior(deterministic = false, cacheable = false)
    @Tool(description = "查询港股股票实时行情。当用户询问港股股价、涨跌幅时调用。股票代码格式：5位数字如00700（腾讯）、09988（阿里巴巴）。")
    public String getHKStockQuote(
            @ToolParam(description = "港股股票代码，如00700、09988") String stockCode) {
        log.info("[FinancialDataTool] getHKStockQuote 入参: stockCode={}", stockCode);
        try {
            validateCode(stockCode, "港股代码");
            String symbol = "hk" + stockCode;
            String result = fetchTencentQuote(symbol, stockCode, "港股");
            log.info("[FinancialDataTool] getHKStockQuote 出参: {}", result);
            return result;
        } catch (Exception e) {
            log.error("[FinancialDataTool] getHKStockQuote 异常: {}", e.getMessage(), e);
            return "查询港股行情失败: " + e.getMessage();
        }
    }

    @ToolBehavior(deterministic = false, cacheable = false)
    @Tool(description = "查询美股股票实时行情。当用户询问美股股价、涨跌幅时调用。股票代码格式：公司简称如AAPL（苹果）、MSFT（微软）、TSLA（特斯拉）。")
    public String getUSStockQuote(
            @ToolParam(description = "美股股票代码，如AAPL、MSFT、TSLA") String stockCode) {
        log.info("[FinancialDataTool] getUSStockQuote 入参: stockCode={}", stockCode);
        try {
            validateCode(stockCode, "美股代码");
            String symbol = "us" + stockCode.toUpperCase();
            String result = fetchTencentQuote(symbol, stockCode, "美股");
            log.info("[FinancialDataTool] getUSStockQuote 出参: {}", result);
            return result;
        } catch (Exception e) {
            log.error("[FinancialDataTool] getUSStockQuote 异常: {}", e.getMessage(), e);
            return "查询美股行情失败: " + e.getMessage();
        }
    }

    @ToolBehavior(deterministic = false, cacheable = false)
    @Tool(description = "查询基金净值信息。当用户询问基金净值、基金估值、基金涨跌时调用。基金代码为6位数字，如110011（易方达中小盘）、161725（招商中证白酒）。")
    public String getFundNav(
            @ToolParam(description = "基金代码，如110011、161725") String fundCode) {
        log.info("[FinancialDataTool] getFundNav 入参: fundCode={}", fundCode);
        try {
            validateCode(fundCode, "基金代码");
            String result = fetchFundNav(fundCode);
            log.info("[FinancialDataTool] getFundNav 出参: {}", result);
            return result;
        } catch (Exception e) {
            log.error("[FinancialDataTool] getFundNav 异常: {}", e.getMessage(), e);
            return "查询基金净值失败: " + e.getMessage();
        }
    }

    @ToolBehavior(deterministic = false, cacheable = true)
    @Tool(description = "通过中文名称模糊搜索股票代码。返回匹配的股票列表。如果只需要查一只股票的行情，直接用 getAShareQuote 传名称即可，它会自动搜索。此工具适用于需要查看多个搜索结果的场景。")
    public String searchStockByName(
            @ToolParam(description = "股票中文名称或关键词,如亨通光电、茅台") String name) {
        log.info("[FinancialDataTool] searchStockByName 入参: name={}", name);
        try {
            if (name == null || name.isBlank()) {
                return "搜索关键词不能为空";
            }
            String result = searchEastMoney(name.trim());
            if (result == null || result.isBlank()) {
                result = searchSinaFallback(name.trim());
            }
            if (result == null || result.isBlank()) {
                result = "未找到与" + name + "相关的A股股票，请确认名称是否正确。";
            }
            log.info("[FinancialDataTool] searchStockByName 出参: {}", result);
            return result;
        } catch (Exception e) {
            log.error("[FinancialDataTool] searchStockByName 异常: {}", e.getMessage(), e);
            return "股票搜索失败: " + e.getMessage();
        }
    }

    private String searchEastMoney(String keyword) throws Exception {
        String url = "https://searchapi.eastmoney.com/api/suggest/get"
                + "?input=" + java.net.URLEncoder.encode(keyword, "UTF-8")
                + "&type=14&token=D43BF722C8E33BDC906FB84D85E326E8&count=5";
        log.info("[FinancialDataTool] searchEastMoney 请求URL: {}", url);

        Headers headers = new Headers.Builder()
                .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build();

        String body = httpGet(url, headers);
        if (body == null || body.isBlank()) return null;

        JsonNode root = objectMapper.readTree(body);
        JsonNode quotes = root.path("QuotationCodeTable").path("Data");
        if (!quotes.isArray() || quotes.isEmpty()) return null;

        List<String> results = new ArrayList<>();
        for (JsonNode item : quotes) {
            String code = item.path("Code").asText("");
            String name = item.path("Name").asText("");
            String mktNum = item.path("MktNum").asText("");
            if (code.length() == 6 && ("1".equals(mktNum) || "0".equals(mktNum))) {
                results.add(formatSearchResult(code, name));
            }
            if (results.size() >= 5) break;
        }
        return results.isEmpty() ? null : String.join("\n", results);
    }

    private String searchSinaFallback(String keyword) throws Exception {
        String url = "https://suggest3.sinajs.cn/suggest/type=&key="
                + java.net.URLEncoder.encode(keyword, "UTF-8") + "&name=suggest";
        log.info("[FinancialDataTool] searchSinaFallback 请求URL: {}", url);

        Headers headers = new Headers.Builder()
                .add("User-Agent", "Mozilla/5.0")
                .add("Referer", "https://finance.sina.com.cn")
                .build();

        String body = httpGet(url, headers);
        if (body == null || body.isBlank()) return null;

        List<String> results = new ArrayList<>();
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
                results.add(formatSearchResult(code, name));
            }
            if (results.size() >= 5) break;
        }
        return results.isEmpty() ? null : String.join("\n", results);
    }

    private String formatSearchResult(String code, String name) {
        String market = code.startsWith("6") ? "沪市" : "深市";
        return code + " " + name + "（" + market + "）";
    }

    private String fetchTencentQuote(String symbol, String stockCode, String marketName) {
        String url = "https://qt.gtimg.cn/q=" + symbol;
        log.info("[FinancialDataTool] fetchTencentQuote 请求URL: {}", url);

        Headers headers = new Headers.Builder()
                .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build();

        try {
            String body = httpGet(url, headers);
            log.info("[FinancialDataTool] fetchTencentQuote 响应体: {}", body);
            if (body == null || body.isBlank()) {
                return "接口返回为空，股票代码: " + stockCode;
            }

            Matcher matcher = TENCENT_QUOTE_PATTERN.matcher(body);
            if (!matcher.find()) {
                return "未找到股票数据，股票代码: " + stockCode;
            }

            String[] fields = matcher.group(1).split("~");
            if (fields.length < 35) {
                return "数据格式异常，股票代码: " + stockCode;
            }

            String name = fields[1];
            String code = fields[2];
            double currentPrice = parseDouble(fields[3]);
            double yesterdayClose = parseDouble(fields[4]);
            double open = parseDouble(fields[5]);
            long volume = (long) parseDouble(fields[6]);

            // 涨跌额和涨跌幅在不同市场的索引不同
            double changeAmount, changePercent, high, low;
            if ("A股".equals(marketName)) {
                changeAmount = parseDouble(fields[31]);
                changePercent = parseDouble(fields[32]);
                high = parseDouble(fields[33]);
                low = parseDouble(fields[34]);
            } else if ("港股".equals(marketName)) {
                changeAmount = parseDouble(fields[31]);
                changePercent = parseDouble(fields[32]);
                high = parseDouble(fields[33]);
                low = parseDouble(fields[34]);
            } else { // 美股
                changeAmount = parseDouble(fields[31]);
                changePercent = parseDouble(fields[32]);
                high = parseDouble(fields[33]);
                low = parseDouble(fields[34]);
            }

            if (currentPrice == 0) {
                return "未找到有效行情数据，股票代码: " + stockCode;
            }

            return String.format(
                    "%s行情 [%s %s]\n" +
                    "当前价: %.2f\n" +
                    "涨跌额: %+.2f\n" +
                    "涨跌幅: %+.2f%%\n" +
                    "今开: %.2f | 昨收: %.2f\n" +
                    "最高: %.2f | 最低: %.2f\n" +
                    "成交量: %d手",
                    marketName, name, code, currentPrice, changeAmount, changePercent,
                    open, yesterdayClose, high, low, volume
            );
        } catch (Exception e) {
            log.error("股票API请求失败: {}", e.getMessage(), e);
            return "股票数据获取失败: " + e.getMessage();
        }
    }

    private String fetchFundNav(String fundCode) {
        String url = "https://fundgz.1234567.com.cn/js/" + fundCode + ".js";
        log.info("[FinancialDataTool] fetchFundNav 请求URL: {}", url);

        Headers headers = new Headers.Builder()
                .add("Referer", "https://fund.eastmoney.com")
                .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build();

        try {
            String body = httpGet(url, headers);
            log.info("[FinancialDataTool] fetchFundNav 响应体: {}", body);

            if (body == null || body.isBlank()) {
                return "接口返回为空，基金代码: " + fundCode;
            }

            Matcher matcher = FUND_JSONP_PATTERN.matcher(body);

            if (!matcher.find()) {
                return "未找到基金数据，基金代码: " + fundCode;
            }

            JsonNode data = objectMapper.readTree(matcher.group(1));

            String name = data.path("name").asText("未知");
            String code = data.path("fundcode").asText(fundCode);
            double nav = data.path("dwjz").asDouble(0);
            double accNav = data.path("gsz").asDouble(0);
            double changePercent = data.path("gszzl").asDouble(0);
            String navDate = data.path("jzrq").asText("");
            String estimateDate = data.path("gztime").asText("");

            return String.format(
                    "基金净值 [%s %s]\n" +
                    "最新净值: %.4f（%s）\n" +
                    "估算净值: %.4f（%s）\n" +
                    "估算涨幅: %+.2f%%",
                    name, code, nav, navDate, accNav, estimateDate, changePercent
            );
        } catch (Exception e) {
            log.error("基金API请求失败: {}", e.getMessage(), e);
            return "基金数据获取失败: " + e.getMessage();
        }
    }

    private String buildAShareSymbol(String stockCode) {
        if (stockCode.startsWith("6")) {
            return "sh" + stockCode;
        } else {
            return "sz" + stockCode;
        }
    }

    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private String httpGet(String url, Headers headers) throws Exception {
        Request request = new Request.Builder()
                .url(url)
                .headers(headers)
                .get()
                .build();

        log.info("[FinancialDataTool] httpGet 发起请求: {} {}", request.method(), request.url());
        log.debug("[FinancialDataTool] httpGet 请求头: {}", request.headers());

        long startTime = System.currentTimeMillis();
        try (Response response = okHttpClient.newCall(request).execute()) {
            long costTime = System.currentTimeMillis() - startTime;
            log.info("[FinancialDataTool] httpGet 响应状态: {}, 耗时: {}ms", response.code(), costTime);

            if (!response.isSuccessful()) {
                log.error("[FinancialDataTool] httpGet HTTP错误: {}", response.code());
                throw new RuntimeException("HTTP 请求失败: " + response.code());
            }
            String body = response.body() != null ? response.body().string() : null;
            log.debug("[FinancialDataTool] httpGet 响应体长度: {}", body != null ? body.length() : 0);
            return body;
        }
    }

    private void validateCode(String code, String label) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        String trimmed = code.trim();
        if (!CODE_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(label + "格式不正确: " + code);
        }
    }
}
