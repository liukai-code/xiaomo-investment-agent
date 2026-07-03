package com.itlk.myclaudecode.tool.astock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itlk.myclaudecode.tool.annotation.ToolBehavior;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.*;

@Slf4j
public class AStockSentimentRouterTool {

    private final EastMoneyRateLimiter emRateLimiter;
    private final ObjectMapper objectMapper;

    public AStockSentimentRouterTool(EastMoneyRateLimiter emRateLimiter) {
        this.emRateLimiter = emRateLimiter;
        this.objectMapper = new ObjectMapper();
    }

    @ToolBehavior(deterministic = false, cacheable = false)
    @Tool(description = """
            A股舆情互动查询。查询同花顺热榜、东财人气榜、个股概念命中。

            operation 可选值：
            - thsHotList: 同花顺热榜（人气+概念标签）。参数: period（"hour"或"day"，默认"hour"）
            - emHotRank: 东财人气榜。参数: top（前N名，默认50）
            - emConceptHit: 个股概念命中（市场归类+热度）。参数: stockCode

            params 为 JSON 字符串。
            """)
    public String a_stock_sentiment(
            @ToolParam(description = "操作类型") String operation,
            @ToolParam(description = "JSON格式参数") String params) {
        log.info("[AStockSentimentRouterTool] operation={}, params={}", operation, params);
        try {
            if (operation == null || operation.isBlank()) {
                return "操作类型不能为空。可用操作：thsHotList, emHotRank, emConceptHit";
            }
            JsonNode p = parseParams(params);
            return switch (operation.trim()) {
                case "thsHotList" -> thsHotList(getOptStr(p, "period", "hour"));
                case "emHotRank" -> emHotRank(getOptInt(p, "top", 50));
                case "emConceptHit" -> emConceptHit(getStr(p, "stockCode"));
                default -> "未知操作: " + operation;
            };
        } catch (Exception e) {
            log.error("[AStockSentimentRouterTool] 异常: operation={}, error={}", operation, e.getMessage(), e);
            return "操作失败（operation=" + operation + "）: " + e.getMessage();
        }
    }

    private String thsHotList(String period) {
        try {
            String url = "https://dq.10jqka.com.cn/fuyao/hot_list_data/out/hot_list/v1/stock"
                    + "?stock_type=a&type=" + period + "&list_type=normal";
            String body = emRateLimiter.get(url, Map.of());
            JsonNode root = objectMapper.readTree(body);
            JsonNode stockList = root.path("data").path("stock_list");

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== 同花顺热榜（%s）===\n\n", "hour".equals(period) ? "小时榜" : "日榜"));
            if (stockList.isArray()) {
                for (JsonNode it : stockList) {
                    int rank = it.path("order").asInt(0);
                    String code = it.path("code").asText("");
                    String name = it.path("name").asText("");
                    double heat = it.path("rate").asDouble(0);
                    double pct = it.path("rise_and_fall").asDouble(0);
                    int rankChg = it.path("hot_rank_chg").asInt(0);
                    JsonNode tag = it.path("tag");
                    String popularityTag = tag.path("popularity_tag").asText("");
                    sb.append(String.format("  #%d %s %s  人气:%.0f  涨跌:%+.2f%%  排名变化:%+d  %s\n",
                            rank, code, name, heat, pct, rankChg, popularityTag));
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "同花顺热榜查询失败: " + e.getMessage();
        }
    }

    private String emHotRank(int top) {
        try {
            Map<String, Object> bodyMap = new HashMap<>();
            bodyMap.put("appId", "appId01");
            bodyMap.put("globalId", "786e4c21-70dc-435a-93bb-38");
            bodyMap.put("marketType", "");
            bodyMap.put("pageNo", 1);
            bodyMap.put("pageSize", top);
            String jsonBody = objectMapper.writeValueAsString(bodyMap);

            String response = emRateLimiter.post("https://emappdata.eastmoney.com/stockrank/getAllCurrentList",
                    jsonBody, Map.of());
            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) {
                return "东财人气榜无数据";
            }

            // 收集 secids 用于批量查名称/价格
            List<String> secids = new ArrayList<>();
            List<Map<String, Object>> items = new ArrayList<>();
            for (JsonNode it : data) {
                String sc = it.path("sc").asText("");
                String code = sc.length() > 2 ? sc.substring(2) : "";
                String prefix = sc.startsWith("SZ") ? "0." : "1.";
                secids.add(prefix + code);
                Map<String, Object> item = new HashMap<>();
                item.put("rank", it.path("rk").asInt());
                item.put("code", code);
                item.put("rankChg", it.path("hisRc").asInt(0));
                items.add(item);
            }

            // 批量查名称/价格
            String secidsStr = String.join(",", secids);
            String listUrl = "https://push2.eastmoney.com/api/qt/ulist.np/get"
                    + "?ut=f057cbcbce2a86e2866ab8877db1d059&fltt=2&invt=2"
                    + "&fields=f14,f3,f12,f2&secids=" + secidsStr;
            String listBody = emRateLimiter.get(listUrl, Map.of("Referer", "https://quote.eastmoney.com/"));
            JsonNode listRoot = objectMapper.readTree(listBody);
            JsonNode diff = listRoot.path("data").path("diff");
            Map<String, String[]> nameMap = new HashMap<>();
            if (diff.isObject()) {
                diff.fields().forEachRemaining(entry -> {
                    JsonNode v = entry.getValue();
                    nameMap.put(v.path("f12").asText(), new String[]{
                            v.path("f14").asText(), String.valueOf(v.path("f2").asDouble(0)),
                            String.valueOf(v.path("f3").asDouble(0))
                    });
                });
            }

            StringBuilder sb = new StringBuilder();
            sb.append("=== 东财人气榜 TOP" + top + " ===\n\n");
            for (Map<String, Object> item : items) {
                String code = (String) item.get("code");
                String[] info = nameMap.getOrDefault(code, new String[]{"", "0", "0"});
                sb.append(String.format("  #%d %s %s  价格:%s  涨跌:%s%%  排名变化:%+d\n",
                        item.get("rank"), code, info[0], info[1], info[2], item.get("rankChg")));
            }
            return sb.toString();
        } catch (Exception e) {
            return "东财人气榜查询失败: " + e.getMessage();
        }
    }

    private String emConceptHit(String stockCode) {
        try {
            String code = AStockUtils.normalizeCode(stockCode);
            Map<String, Object> bodyMap = new HashMap<>();
            bodyMap.put("appId", "appId01");
            bodyMap.put("globalId", "786e4c21-70dc-435a-93bb-38");
            bodyMap.put("code", code);
            String jsonBody = objectMapper.writeValueAsString(bodyMap);

            String response = emRateLimiter.post("https://emappdata.eastmoney.com/stockrank/getHotStockRankList",
                    jsonBody, Map.of());
            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.path("data");

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== %s 概念命中 ===\n\n", code));
            if (data.isArray()) {
                for (JsonNode item : data) {
                    String concept = item.path("concept").asText("");
                    String bk = item.path("bk").asText("");
                    int hit = item.path("hit").asInt(0);
                    sb.append(String.format("  %s (BK%s)  热度:%d\n", concept, bk, hit));
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "概念命中查询失败: " + e.getMessage();
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

    private int getOptInt(JsonNode p, String key, int defaultVal) {
        if (p == null || !p.has(key) || p.get(key).isNull()) return defaultVal;
        return p.get(key).asInt(defaultVal);
    }
}
