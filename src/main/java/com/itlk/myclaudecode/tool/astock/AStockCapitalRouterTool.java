package com.itlk.myclaudecode.tool.astock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itlk.myclaudecode.tool.annotation.ToolBehavior;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
public class AStockCapitalRouterTool {

    private static final String DATACENTER_URL = "https://datacenter-web.eastmoney.com/api/data/v1/get";
    private static final DateTimeFormatter YYYY_MM_DD = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final EastMoneyRateLimiter emRateLimiter;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    public AStockCapitalRouterTool(EastMoneyRateLimiter emRateLimiter,
                                    StringRedisTemplate redisTemplate) {
        this.emRateLimiter = emRateLimiter;
        this.objectMapper = new ObjectMapper();
        this.redisTemplate = redisTemplate;
    }

    @ToolBehavior(deterministic = false, cacheable = false)
    @Tool(description = """
            A股资金面查询。查询融资融券、大宗交易、股东户数、分红送转、资金流向、北向资金等。

            operation 可选值：
            - marginTrading: 融资融券明细。参数: stockCode, pageSize（可选，默认30）
            - blockTrade: 大宗交易记录。参数: stockCode, pageSize（可选，默认20）
            - holderNumChange: 股东户数变化。参数: stockCode, pageSize（可选，默认10）
            - dividendHistory: 分红送转历史。参数: stockCode, pageSize（可选，默认20）
            - fundFlow120d: 120日资金流向。参数: stockCode
            - northboundFlow: 北向资金（实时+历史缓存）。参数: historyDays（可选，默认30）

            params 为 JSON 字符串。
            """)
    public String a_stock_capital(
            @ToolParam(description = "操作类型") String operation,
            @ToolParam(description = "JSON格式参数") String params) {
        log.info("[AStockCapitalRouterTool] operation={}, params={}", operation, params);
        try {
            if (operation == null || operation.isBlank()) {
                return "操作类型不能为空。可用操作：marginTrading, blockTrade, holderNumChange, dividendHistory, fundFlow120d, northboundFlow";
            }
            JsonNode p = parseParams(params);
            return switch (operation.trim()) {
                case "marginTrading" -> marginTrading(getStr(p, "stockCode"), getOptInt(p, "pageSize", 30));
                case "blockTrade" -> blockTrade(getStr(p, "stockCode"), getOptInt(p, "pageSize", 20));
                case "holderNumChange" -> holderNumChange(getStr(p, "stockCode"), getOptInt(p, "pageSize", 10));
                case "dividendHistory" -> dividendHistory(getStr(p, "stockCode"), getOptInt(p, "pageSize", 20));
                case "fundFlow120d" -> fundFlow120d(getStr(p, "stockCode"));
                case "northboundFlow" -> northboundFlow(getOptInt(p, "historyDays", 30));
                default -> "未知操作: " + operation;
            };
        } catch (Exception e) {
            log.error("[AStockCapitalRouterTool] 异常: operation={}, error={}", operation, e.getMessage(), e);
            return "操作失败（operation=" + operation + "）: " + e.getMessage();
        }
    }

    private String marginTrading(String stockCode, int pageSize) {
        try {
            String code = AStockUtils.normalizeCode(stockCode);
            String filter = String.format("(SCODE=\"%s\")", code);
            String url = DATACENTER_URL + "?reportName=RPTA_WEB_RZRQ_GGMX&columns=ALL"
                    + "&filter=" + filter + "&pageNumber=1&pageSize=" + pageSize
                    + "&sortColumns=DATE&sortTypes=-1&source=WEB&client=WEB";
            log.debug("[AStockCapitalRouterTool] 请求融资融券: code={}", code);
            String body = emRateLimiter.get(url, Map.of());
            JsonNode data = objectMapper.readTree(body).path("result").path("data");

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== %s 融资融券明细 ===\n\n", code));
            if (data.isArray()) {
                for (JsonNode row : data) {
                    String date = strVal(row.get("DATE"), "").substring(0, 10);
                    double rzye = numVal(row.get("RZYE")) / 1e8;
                    double rzmre = numVal(row.get("RZMRE")) / 1e8;
                    double rqye = numVal(row.get("RQYE")) / 1e8;
                    sb.append(String.format("  %s  融资余额:%.2f亿  融资买入:%.2f亿  融券余额:%.2f亿\n",
                            date, rzye, rzmre, rqye));
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "融资融券查询失败: " + e.getMessage();
        }
    }

    private String blockTrade(String stockCode, int pageSize) {
        try {
            String code = AStockUtils.normalizeCode(stockCode);
            String filter = String.format("(SECURITY_CODE=\"%s\")", code);
            String url = DATACENTER_URL + "?reportName=RPT_DATA_BLOCKTRADE&columns=ALL"
                    + "&filter=" + filter + "&pageNumber=1&pageSize=" + pageSize
                    + "&sortColumns=TRADE_DATE&sortTypes=-1&source=WEB&client=WEB";
            log.debug("[AStockCapitalRouterTool] 请求大宗交易: code={}", code);
            String body = emRateLimiter.get(url, Map.of());
            JsonNode data = objectMapper.readTree(body).path("result").path("data");

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== %s 大宗交易记录 ===\n\n", code));
            if (data.isArray()) {
                for (JsonNode row : data) {
                    String date = strVal(row.get("TRADE_DATE"), "").substring(0, 10);
                    double dealPrice = numVal(row.get("DEAL_PRICE"));
                    double closePrice = numVal(row.get("CLOSE_PRICE"));
                    double premium = closePrice > 0 ? ((dealPrice / closePrice - 1) * 100) : 0;
                    double amount = numVal(row.get("DEAL_AMT")) / 1e8;
                    String buyer = strVal(row.get("BUYER_NAME"), "");
                    String seller = strVal(row.get("SELLER_NAME"), "");
                    sb.append(String.format("  %s  价:%.2f元  溢价:%.2f%%  额:%.2f亿\n    买:%s → 卖:%s\n",
                            date, dealPrice, premium, amount, buyer, seller));
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "大宗交易查询失败: " + e.getMessage();
        }
    }

    private String holderNumChange(String stockCode, int pageSize) {
        try {
            String code = AStockUtils.normalizeCode(stockCode);
            String filter = String.format("(SECURITY_CODE=\"%s\")", code);
            String url = DATACENTER_URL + "?reportName=RPT_HOLDERNUMLATEST&columns=ALL"
                    + "&filter=" + filter + "&pageNumber=1&pageSize=" + pageSize
                    + "&sortColumns=END_DATE&sortTypes=-1&source=WEB&client=WEB";
            log.debug("[AStockCapitalRouterTool] 请求股东户数: code={}", code);
            String body = emRateLimiter.get(url, Map.of());
            JsonNode data = objectMapper.readTree(body).path("result").path("data");

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== %s 股东户数变化 ===\n\n", code));
            if (data.isArray()) {
                for (JsonNode row : data) {
                    String date = strVal(row.get("END_DATE"), "").substring(0, 10);
                    long holderNum = row.get("HOLDER_NUM") != null ? row.get("HOLDER_NUM").asLong() : 0;
                    long changeNum = row.get("HOLDER_NUM_CHANGE") != null ? row.get("HOLDER_NUM_CHANGE").asLong() : 0;
                    double changeRatio = numVal(row.get("HOLDER_NUM_RATIO"));
                    double avgShares = numVal(row.get("AVG_FREE_SHARES"));
                    sb.append(String.format("  %s  股东:%d户  变化:%+d户(%.2f%%)  户均持股:%.0f股\n",
                            date, holderNum, changeNum, changeRatio, avgShares));
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "股东户数查询失败: " + e.getMessage();
        }
    }

    private String dividendHistory(String stockCode, int pageSize) {
        try {
            String code = AStockUtils.normalizeCode(stockCode);
            String filter = String.format("(SECURITY_CODE=\"%s\")", code);
            String url = DATACENTER_URL + "?reportName=RPT_SHAREBONUS_DET&columns=ALL"
                    + "&filter=" + filter + "&pageNumber=1&pageSize=" + pageSize
                    + "&sortColumns=EX_DIVIDEND_DATE&sortTypes=-1&source=WEB&client=WEB";
            log.debug("[AStockCapitalRouterTool] 请求分红送转: code={}", code);
            String body = emRateLimiter.get(url, Map.of());
            JsonNode data = objectMapper.readTree(body).path("result").path("data");

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== %s 分红送转历史 ===\n\n", code));
            if (data.isArray()) {
                for (JsonNode row : data) {
                    String date = strVal(row.get("EX_DIVIDEND_DATE"), "").substring(0, 10);
                    double bonusRmb = numVal(row.get("PRETAX_BONUS_RMB"));
                    double transferRatio = numVal(row.get("TRANSFER_RATIO"));
                    double bonusRatio = numVal(row.get("BONUS_RATIO"));
                    String progress = strVal(row.get("ASSIGN_PROGRESS"), "");
                    sb.append(String.format("  %s  每股派息:%.2f元  转增:%.0f/10股  送股:%.0f/10股  进度:%s\n",
                            date, bonusRmb, transferRatio, bonusRatio, progress));
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "分红送转查询失败: " + e.getMessage();
        }
    }

    private String fundFlow120d(String stockCode) {
        try {
            String code = AStockUtils.normalizeCode(stockCode);
            String secId = AStockUtils.toEastmoneySecId(code);
            String url = "https://push2his.eastmoney.com/api/qt/stock/fflow/daykline/get"
                    + "?secid=" + secId + "&lmt=120"
                    + "&fields1=f1,f2,f3,f7&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61,f62,f63,f64,f65";
            log.debug("[AStockCapitalRouterTool] 请求120日资金流: code={}", code);
            String body = emRateLimiter.get(url, Map.of(
                    "Referer", "https://quote.eastmoney.com/",
                    "Origin", "https://quote.eastmoney.com"
            ));
            JsonNode root = objectMapper.readTree(body);
            JsonNode klines = root.path("data").path("klines");

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== %s 120日资金流向 ===\n\n", code));
            double totalMain = 0;
            List<String> recentLines = new ArrayList<>();
            klines.forEach(k -> recentLines.add(k.asText()));
            int start = Math.max(0, recentLines.size() - 10);
            for (int i = start; i < recentLines.size(); i++) {
                String[] parts = recentLines.get(i).split(",");
                if (parts.length >= 6) {
                    double mainNet = parseDouble(parts[1]);
                    totalMain += mainNet;
                    sb.append(String.format("  %s  主力净流入:%s\n", parts[0], formatWan(mainNet)));
                }
            }
            sb.append(String.format("\n最近10日主力累计净流入: %s", formatWan(totalMain)));
            return sb.toString();
        } catch (Exception e) {
            return "资金流向查询失败: " + e.getMessage();
        }
    }

    private String northboundFlow(int historyDays) {
        try {
            // 1. 拉取实时数据
            String url = "https://data.hexin.cn/market/hsgtApi/method/dayChart/";
            log.debug("[AStockCapitalRouterTool] 请求北向资金实时数据");
            String body = emRateLimiter.get(url, Map.of());
            JsonNode root = objectMapper.readTree(body);

            StringBuilder sb = new StringBuilder();
            sb.append("=== 北向资金 ===\n\n");

            // 实时分钟数据
            JsonNode times = root.path("time");
            JsonNode hgt = root.path("hgt");
            JsonNode sgt = root.path("sgt");
            if (times.isArray() && !times.isEmpty()) {
                int lastIdx = times.size() - 1;
                double lastHgt = hgt.get(lastIdx) != null ? hgt.get(lastIdx).asDouble() : 0;
                double lastSgt = sgt.get(lastIdx) != null ? sgt.get(lastIdx).asDouble() : 0;
                sb.append(String.format("今日实时(%s): 沪股通 %+.2f亿  深股通 %+.2f亿  合计 %+.2f亿\n\n",
                        times.get(lastIdx).asText(), lastHgt, lastSgt, lastHgt + lastSgt));

                // 缓存今日数据到 Redis
                String today = LocalDate.now().format(YYYY_MM_DD);
                String key = "astock:northbound:" + today;
                redisTemplate.opsForValue().set(key, String.format("%.2f,%.2f", lastHgt, lastSgt), 30, TimeUnit.DAYS);
            }

            // 2. 从 Redis 读取历史
            sb.append("历史记录:\n");
            for (int i = 0; i < historyDays; i++) {
                String date = LocalDate.now().minusDays(i).format(YYYY_MM_DD);
                String key = "astock:northbound:" + date;
                String cached = redisTemplate.opsForValue().get(key);
                if (cached != null) {
                    String[] parts = cached.split(",");
                    if (parts.length == 2) {
                        sb.append(String.format("  %s  沪股通:%s亿  深股通:%s亿\n", date, parts[0], parts[1]));
                    }
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "北向资金查询失败: " + e.getMessage();
        }
    }

    private double parseDouble(String s) {
        try {
            return (s == null || s.isEmpty()) ? 0 : Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private double numVal(JsonNode node) {
        return node != null && !node.isNull() ? node.asDouble(0) : 0;
    }

    private String formatWan(double amount) {
        return String.format("%.0f万", amount / 1e4);
    }

    private String strVal(Object obj, String defaultVal) {
        return obj == null ? defaultVal : obj.toString();
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
}
