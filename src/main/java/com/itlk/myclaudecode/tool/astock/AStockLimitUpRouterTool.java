package com.itlk.myclaudecode.tool.astock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itlk.myclaudecode.tool.annotation.ToolBehavior;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
public class AStockLimitUpRouterTool {

    private static final String ZTB_UT = "bd1d9ddb04089700cf9c27f6f7426281";
    private static final DateTimeFormatter HH_MM_SS = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final EastMoneyRateLimiter emRateLimiter;
    private final ObjectMapper objectMapper;

    public AStockLimitUpRouterTool(EastMoneyRateLimiter emRateLimiter) {
        this.emRateLimiter = emRateLimiter;
        this.objectMapper = new ObjectMapper();
    }

    @ToolBehavior(deterministic = false, cacheable = false)
    @Tool(description = """
            A股打板层查询。查询涨停池、炸板池、跌停池、昨涨停池、涨停揭秘、打板情绪。

            operation 可选值：
            - ztPool: 涨停池。参数: date（YYYYMMDD，默认今天）
            - zbPool: 炸板池。参数: date
            - dtPool: 跌停池。参数: date
            - yztPool: 昨日涨停池。参数: date
            - thsLimitUpPool: 同花顺涨停揭秘（含题材归因）。参数: date
            - sentimentOverview: 打板情绪速算（炸板率+连板梯队）。参数: date

            params 为 JSON 字符串。
            """)
    public String a_stock_limit_up(
            @ToolParam(description = "操作类型") String operation,
            @ToolParam(description = "JSON格式参数") String params) {
        log.info("[AStockLimitUpRouterTool] operation={}, params={}", operation, params);
        try {
            if (operation == null || operation.isBlank()) {
                return "操作类型不能为空。可用操作：ztPool, zbPool, dtPool, yztPool, thsLimitUpPool, sentimentOverview";
            }
            JsonNode p = parseParams(params);
            String date = getOptStr(p, "date", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
            return switch (operation.trim()) {
                case "ztPool" -> ztPool(date);
                case "zbPool" -> zbPool(date);
                case "dtPool" -> dtPool(date);
                case "yztPool" -> yztPool(date);
                case "thsLimitUpPool" -> thsLimitUpPool(date);
                case "sentimentOverview" -> sentimentOverview(date);
                default -> "未知操作: " + operation;
            };
        } catch (Exception e) {
            log.error("[AStockLimitUpRouterTool] 异常: operation={}, error={}", operation, e.getMessage(), e);
            return "操作失败（operation=" + operation + "）: " + e.getMessage();
        }
    }

    private String ztPool(String date) {
        try {
            List<Map<String, Object>> pool = emZtApi("getTopicZTPool", "fbt:asc", date);
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== 涨停池 %s 共 %d 只 ===\n\n", date, pool.size()));
            for (Map<String, Object> p : pool) {
                sb.append(String.format("  %s %s  价:%.2f  涨:%.2f%%  连板:%d  N天%d板\n",
                        p.get("c"), p.get("n"), numVal(p.get("p")) / 1000,
                        numVal(p.get("zdp")), intVal(p.get("lbc")),
                        intVal(p.get("zttj.days")), intVal(p.get("zttj.ct"))));
                sb.append(String.format("    封板时间:%s  炸板:%d次  行业:%s\n",
                        fmtTime(p.get("fbt")), intVal(p.get("zbc")), strVal(p.get("hybk"), "")));
            }
            return sb.toString();
        } catch (Exception e) {
            return "涨停池查询失败: " + e.getMessage();
        }
    }

    private String zbPool(String date) {
        try {
            List<Map<String, Object>> pool = emZtApi("getTopicZBPool", "fbt:asc", date);
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== 炸板池 %s 共 %d 只 ===\n\n", date, pool.size()));
            for (Map<String, Object> p : pool) {
                sb.append(String.format("  %s %s  价:%.2f  涨:%.2f%%  振幅:%.2f%%  涨速:%.2f\n",
                        p.get("c"), p.get("n"), numVal(p.get("p")) / 1000,
                        numVal(p.get("zdp")), numVal(p.get("zf")), numVal(p.get("zs"))));
                sb.append(String.format("    涨停价:%.2f  炸板:%d次  首封:%s\n",
                        numVal(p.get("ztp")) / 1000, intVal(p.get("zbc")), fmtTime(p.get("fbt"))));
            }
            return sb.toString();
        } catch (Exception e) {
            return "炸板池查询失败: " + e.getMessage();
        }
    }

    private String dtPool(String date) {
        try {
            List<Map<String, Object>> pool = emZtApi("getTopicDTPool", "fund:asc", date);
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== 跌停池 %s 共 %d 只 ===\n\n", date, pool.size()));
            for (Map<String, Object> p : pool) {
                sb.append(String.format("  %s %s  价:%.2f  跌:%.2f%%  连续跌停:%d天\n",
                        p.get("c"), p.get("n"), numVal(p.get("p")) / 1000,
                        numVal(p.get("zdp")), intVal(p.get("days"))));
                sb.append(String.format("    封单资金:%.0f元  开板:%d次  行业:%s\n",
                        numVal(p.get("fund")), intVal(p.get("oc")), strVal(p.get("hybk"), "")));
            }
            return sb.toString();
        } catch (Exception e) {
            return "跌停池查询失败: " + e.getMessage();
        }
    }

    private String yztPool(String date) {
        try {
            List<Map<String, Object>> pool = emZtApi("getYesterdayZTPool", "pct:asc", date);
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== 昨日涨停池 %s 共 %d 只 ===\n\n", date, pool.size()));
            for (Map<String, Object> p : pool) {
                sb.append(String.format("  %s %s  今日:%+.2f%%  昨连板:%d  N天%d板\n",
                        p.get("c"), p.get("n"), numVal(p.get("zdp")),
                        intVal(p.get("lbc")), intVal(p.get("zttj.days")), intVal(p.get("zttj.ct"))));
            }
            return sb.toString();
        } catch (Exception e) {
            return "昨涨停池查询失败: " + e.getMessage();
        }
    }

    private String thsLimitUpPool(String date) {
        try {
            String url = "https://data.10jqka.com.cn/dataapi/limit_up/limit_up_pool"
                    + "?page=1&limit=200"
                    + "&field=199112,10,9001,330323,330324,330325,9002,330329,133971,133970,1968584,3475914,9003,9004"
                    + "&filter=HS,GEM2STAR&order_field=330324&order_type=0&date=" + date;
            String body = emRateLimiter.get(url, Map.of());
            JsonNode root = objectMapper.readTree(body);
            JsonNode info = root.path("data").path("info");

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== 同花顺涨停揭秘 %s ===\n\n", date));
            if (info.isArray()) {
                for (JsonNode it : info) {
                    String code = it.path("code").asText("");
                    String name = it.path("name").asText("");
                    double pct = it.path("change_rate").asDouble(0);
                    String reason = it.path("reason_type").asText("");
                    String boardType = it.path("limit_up_type").asText("");
                    double sealRate = it.path("limit_up_suc_rate").asDouble(0);
                    int breakTimes = it.path("open_num").asInt(0);
                    String highDays = it.path("high_days").asText("");
                    long ft = it.path("first_limit_up_time").asLong(0);
                    String firstTime = ft > 0 ? LocalDateTime.ofInstant(Instant.ofEpochMilli(ft),
                            ZoneId.of("Asia/Shanghai")).format(HH_MM_SS) : "";

                    sb.append(String.format("  %s %s  %s  %+.2f%%\n", code, name, highDays, pct));
                    sb.append(String.format("    题材:%s  板型:%s  封板率:%.0f%%  炸板:%d次  首封:%s\n\n",
                            reason, boardType, sealRate * 100, breakTimes, firstTime));
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "同花顺涨停揭秘查询失败: " + e.getMessage();
        }
    }

    private String sentimentOverview(String date) {
        try {
            List<Map<String, Object>> ztPool = emZtApi("getTopicZTPool", "fbt:asc", date);
            List<Map<String, Object>> zbPool = emZtApi("getTopicZBPool", "fbt:asc", date);
            List<Map<String, Object>> dtPool = emZtApi("getTopicDTPool", "fund:asc", date);

            int ztCount = ztPool.size();
            int zbCount = zbPool.size();
            int dtCount = dtPool.size();
            int totalAttempt = ztCount + zbCount;
            double breakRate = totalAttempt > 0 ? (double) zbCount / totalAttempt * 100 : 0;

            // 连板梯队统计
            Map<Integer, Integer> ladderMap = new TreeMap<>(Collections.reverseOrder());
            for (Map<String, Object> p : ztPool) {
                int limitDays = intVal(p.get("lbc"));
                ladderMap.merge(limitDays, 1, Integer::sum);
            }

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== 打板情绪速算 %s ===\n\n", date));
            sb.append(String.format("  涨停: %d家  炸板: %d家  跌停: %d家\n", ztCount, zbCount, dtCount));
            sb.append(String.format("  炸板率: %.1f%% (炸板/(涨停+炸板))\n\n", breakRate));
            sb.append("连板梯队:\n");
            for (Map.Entry<Integer, Integer> e : ladderMap.entrySet()) {
                sb.append(String.format("  %d连板: %d家\n", e.getKey(), e.getValue()));
            }
            return sb.toString();
        } catch (Exception e) {
            return "打板情绪查询失败: " + e.getMessage();
        }
    }

    private List<Map<String, Object>> emZtApi(String endpoint, String sort, String date) {
        try {
            String url = "https://push2ex.eastmoney.com/" + endpoint
                    + "?ut=" + ZTB_UT + "&dpt=wz.ztzt&Pageindex=0&pagesize=10000&sort=" + sort + "&date=" + date;
            String body = emRateLimiter.get(url, Map.of("Referer", "https://quote.eastmoney.com/"));
            JsonNode root = objectMapper.readTree(body);
            JsonNode pool = root.path("data").path("pool");
            List<Map<String, Object>> result = new ArrayList<>();
            if (pool.isArray()) {
                pool.forEach(node -> result.add(objectMapper.convertValue(node, Map.class)));
            }
            return result;
        } catch (Exception e) {
            log.warn("[AStockLimitUp] 涨停板池 {} 请求失败: {}", endpoint, e.getMessage());
            return List.of();
        }
    }

    private String fmtTime(Object ts) {
        if (ts == null) return "";
        long millis = ts instanceof Number ? ((Number) ts).longValue() : 0;
        if (millis <= 0) return "";
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis),
                ZoneId.of("Asia/Shanghai")).format(HH_MM_SS);
    }

    private int intVal(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof Number) return ((Number) obj).intValue();
        try {
            return Integer.parseInt(obj.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private double numVal(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        try {
            return Double.parseDouble(obj.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
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

    private String getOptStr(JsonNode p, String key, String defaultVal) {
        if (p == null || !p.has(key) || p.get(key).isNull()) return defaultVal;
        String val = p.get(key).asText();
        return val.isEmpty() ? defaultVal : val;
    }
}
