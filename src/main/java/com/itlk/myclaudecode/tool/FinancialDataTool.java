package com.itlk.myclaudecode.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Headers;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

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

    @Tool(description = "查询A股股票实时行情。当用户询问A股股价、涨跌幅、成交量时调用。股票代码格式：沪市6位数字如600519（贵州茅台），深市6位数字如000858（五粮液）。")
    public String getAShareQuote(
            @ToolParam(description = "A股股票代码，如600519、000858") String stockCode) {
        try {
            validateCode(stockCode, "股票代码");
            String symbol = buildAShareSymbol(stockCode);
            return fetchTencentQuote(symbol, stockCode, "A股");
        } catch (Exception e) {
            return "查询A股行情失败: " + e.getMessage();
        }
    }

    @Tool(description = "查询港股股票实时行情。当用户询问港股股价、涨跌幅时调用。股票代码格式：5位数字如00700（腾讯）、09988（阿里巴巴）。")
    public String getHKStockQuote(
            @ToolParam(description = "港股股票代码，如00700、09988") String stockCode) {
        try {
            validateCode(stockCode, "港股代码");
            String symbol = "hk" + stockCode;
            return fetchTencentQuote(symbol, stockCode, "港股");
        } catch (Exception e) {
            return "查询港股行情失败: " + e.getMessage();
        }
    }

    @Tool(description = "查询美股股票实时行情。当用户询问美股股价、涨跌幅时调用。股票代码格式：公司简称如AAPL（苹果）、MSFT（微软）、TSLA（特斯拉）。")
    public String getUSStockQuote(
            @ToolParam(description = "美股股票代码，如AAPL、MSFT、TSLA") String stockCode) {
        try {
            validateCode(stockCode, "美股代码");
            String symbol = "us" + stockCode.toUpperCase();
            return fetchTencentQuote(symbol, stockCode, "美股");
        } catch (Exception e) {
            return "查询美股行情失败: " + e.getMessage();
        }
    }

    @Tool(description = "查询基金净值信息。当用户询问基金净值、基金估值、基金涨跌时调用。基金代码为6位数字，如110011（易方达中小盘）、161725（招商中证白酒）。")
    public String getFundNav(
            @ToolParam(description = "基金代码，如110011、161725") String fundCode) {
        try {
            validateCode(fundCode, "基金代码");
            return fetchFundNav(fundCode);
        } catch (Exception e) {
            log.error("基金净值查询失败: {}", e.getMessage());
            return "查询基金净值失败: " + e.getMessage();
        }
    }

    private String fetchTencentQuote(String symbol, String stockCode, String marketName) {
        String url = "https://qt.gtimg.cn/q=" + symbol;

        Headers headers = new Headers.Builder()
                .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build();

        try {
            String body = httpGet(url, headers);
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

        Headers headers = new Headers.Builder()
                .add("Referer", "https://fund.eastmoney.com")
                .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build();

        try {
            String body = httpGet(url, headers);

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

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("HTTP 请求失败: " + response.code());
            }
            return response.body() != null ? response.body().string() : null;
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
