package com.itlk.myclaudecode.tool.astock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itlk.myclaudecode.tool.annotation.ToolBehavior;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
public class AStockSignalRouterTool {

    private static final String DATACENTER_URL = "https://datacenter-web.eastmoney.com/api/data/v1/get";
    private static final DateTimeFormatter YYYY_MM_DD = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final EastMoneyRateLimiter emRateLimiter;
    private final ObjectMapper objectMapper;

    public AStockSignalRouterTool(EastMoneyRateLimiter emRateLimiter) {
        this.emRateLimiter = emRateLimiter;
        this.objectMapper = new ObjectMapper();
    }

    @ToolBehavior(deterministic = false, cacheable = false)
    @Tool(description = """
            A股信号查询。查询板块归属、资金流向、龙虎榜、解禁日历、行业排名等信号数据。

            operation 可选值：
            - conceptBlocks: 个股板块/概念归属。参数: stockCode
            - fundFlowMinute: 分钟级资金流向。参数: stockCode
            - dragonTigerBoard: 个股龙虎榜席位。参数: stockCode, tradeDate（YYYY-MM-DD）, lookBackDays（可选，默认30）
            - dailyDragonTiger: 全市场龙虎榜。参数: tradeDate（可选，默认今天）, minNetBuy（可选，最低净买入万元）
            - lockupExpiry: 限售解禁日历。参数: stockCode, tradeDate（可选，默认今天）, forwardDays（可选，默认90）
            - industryRanking: 行业板块排名。参数: topN（可选，默认20）

            params 为 JSON 字符串。
            """)
    public String a_stock_signal(
            @ToolParam(description = "操作类型") String operation,
            @ToolParam(description = "JSON格式参数") String params) {
        log.info("[AStockSignalRouterTool] operation={}, params={}", operation, params);
        try {
            if (operation == null || operation.isBlank()) {
                return "操作类型不能为空。可用操作：conceptBlocks, fundFlowMinute, dragonTigerBoard, dailyDragonTiger, lockupExpiry, industryRanking";
            }
            JsonNode p = parseParams(params);
            return switch (operation.trim()) {
                case "conceptBlocks" -> conceptBlocks(getStr(p, "stockCode"));
                case "fundFlowMinute" -> fundFlowMinute(getStr(p, "stockCode"));
                case "dragonTigerBoard" -> dragonTigerBoard(getStr(p, "stockCode"),
                        getOptStr(p, "tradeDate", LocalDate.now().format(YYYY_MM_DD)),
                        getOptInt(p, "lookBackDays", 30));
                case "dailyDragonTiger" -> dailyDragonTiger(
                        getOptStr(p, "tradeDate", LocalDate.now().format(YYYY_MM_DD)),
                        getOptDouble(p, "minNetBuy", 0));
                case "lockupExpiry" -> lockupExpiry(getStr(p, "stockCode"),
                        getOptStr(p, "tradeDate", LocalDate.now().format(YYYY_MM_DD)),
                        getOptInt(p, "forwardDays", 90));
                case "industryRanking" -> industryRanking(getOptInt(p, "topN", 20));
                default -> "未知操作: " + operation;
            };
        } catch (IllegalArgumentException e) {
            log.error("[AStockSignalRouterTool] 参数错误: operation={}, error={}", operation, e.getMessage());
            return "参数错误（operation=" + operation + "）: " + e.getMessage()
                    + "。请检查 params JSON 格式，例如: {\"stockCode\":\"430510\"}";
        } catch (Exception e) {
            log.error("[AStockSignalRouterTool] 异常: operation={}, error={}", operation, e.getMessage(), e);
            return "操作失败（operation=" + operation + "）: " + e.getMessage();
        }
    }

    private String conceptBlocks(String stockCode) {
        try {
            String code = AStockUtils.normalizeCode(stockCode);
            String secId = AStockUtils.toEastmoneySecId(code);
            String url = "https://push2.eastmoney.com/api/qt/slist/get"
                    + "?fltt=2&invt=2&secid=" + secId + "&spt=3&pi=0&pz=200&po=1"
                    + "&fields=f12,f14,f3,f128";
            log.debug("[AStockSignalRouterTool] 请求板块归属: code={}", code);
            String body = emRateLimiter.get(url, Map.of("Referer", "https://quote.eastmoney.com/"));
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.path("data");
            JsonNode diff = data.path("diff");

            List<Map<String, Object>> boards = new ArrayList<>();
            if (diff.isObject()) {
                diff.fields().forEachRemaining(entry -> boards.add(objectMapper.convertValue(entry.getValue(), Map.class)));
            } else if (diff.isArray()) {
                diff.forEach(node -> boards.add(objectMapper.convertValue(node, Map.class)));
            }

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== %s 板块归属 ===\n\n", code));
            List<String> conceptTags = new ArrayList<>();
            for (Map<String, Object> b : boards) {
                String name = strVal(b.get("f14"), "");
                String bkCode = strVal(b.get("f12"), "");
                Object changePct = b.get("f3");
                String leadStock = strVal(b.get("f128"), "");
                sb.append(String.format("  %s (BK%s) 涨跌:%s%% 龙头:%s\n", name, bkCode, changePct, leadStock));
                conceptTags.add(name);
            }
            sb.append(String.format("\n概念标签: %s", String.join(", ", conceptTags)));
            return sb.toString();
        } catch (Exception e) {
            return "板块归属查询失败: " + e.getMessage();
        }
    }

    private String fundFlowMinute(String stockCode) {
        try {
            String code = AStockUtils.normalizeCode(stockCode);
            String secId = AStockUtils.toEastmoneySecId(code);
            String url = "https://push2.eastmoney.com/api/qt/stock/fflow/kline/get"
                    + "?secid=" + secId + "&klt=1"
                    + "&fields1=f1,f2,f3,f7&fields2=f51,f52,f53,f54,f55,f56,f57";
            log.debug("[AStockSignalRouterTool] 请求分钟资金流: code={}", code);
            String body = emRateLimiter.get(url, Map.of(
                    "Referer", "https://quote.eastmoney.com/",
                    "Origin", "https://quote.eastmoney.com"
            ));
            JsonNode root = objectMapper.readTree(body);
            JsonNode klines = root.path("data").path("klines");

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== %s 分钟级资金流向 ===\n\n", code));
            double totalMain = 0, totalSuper = 0, totalLarge = 0, totalMid = 0, totalSmall = 0;
            List<String> recentLines = new ArrayList<>();
            klines.forEach(k -> recentLines.add(k.asText()));
            int start = Math.max(0, recentLines.size() - 10);
            for (int i = start; i < recentLines.size(); i++) {
                String[] parts = recentLines.get(i).split(",");
                if (parts.length >= 6) {
                    double mainNet = parseDouble(parts[1]);
                    double superNet = parseDouble(parts[2]);
                    double largeNet = parseDouble(parts[3]);
                    double midNet = parseDouble(parts[4]);
                    double smallNet = parseDouble(parts[5]);
                    totalMain += mainNet;
                    totalSuper += superNet;
                    totalLarge += largeNet;
                    totalMid += midNet;
                    totalSmall += smallNet;
                    sb.append(String.format("  %s  主力:%s  超大单:%s  大单:%s\n",
                            parts[0], formatWan(mainNet), formatWan(superNet), formatWan(largeNet)));
                }
            }
            sb.append(String.format("\n今日累计: 主力净流入 %s  超大单 %s  大单 %s  中单 %s  小单 %s",
                    formatWan(totalMain), formatWan(totalSuper), formatWan(totalLarge),
                    formatWan(totalMid), formatWan(totalSmall)));
            return sb.toString();
        } catch (Exception e) {
            return "资金流向查询失败: " + e.getMessage();
        }
    }

    private String dragonTigerBoard(String stockCode, String tradeDate, int lookBackDays) {
        try {
            String code = AStockUtils.normalizeCode(stockCode);
            LocalDate end = LocalDate.parse(tradeDate, YYYY_MM_DD);
            LocalDate start = end.minusDays(lookBackDays);
            String startStr = start.format(YYYY_MM_DD);
            String filter = String.format("(TRADE_DATE>='%s')(TRADE_DATE<='%s')(SECURITY_CODE=\"%s\")", startStr, tradeDate, code);

            // 1. 上榜记录
            String url1 = DATACENTER_URL + "?reportName=RPT_DAILYBILLBOARD_DETAILSNEW&columns=ALL"
                    + "&filter=" + filter + "&pageNumber=1&pageSize=50&sortColumns=TRADE_DATE&sortTypes=-1&source=WEB&client=WEB";
            log.debug("[AStockSignalRouterTool] 请求龙虎榜记录: code={}, date={}", code, tradeDate);
            String body1 = emRateLimiter.get(url1, Map.of());
            JsonNode root1 = objectMapper.readTree(body1);
            JsonNode data1 = root1.path("result").path("data");

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== %s 龙虎榜数据 ===\n\n", code));

            List<String> dates = new ArrayList<>();
            if (data1.isArray()) {
                for (JsonNode row : data1) {
                    String date = strVal(row.get("TRADE_DATE"), "").substring(0, 10);
                    String reason = strVal(row.get("EXPLANATION"), "");
                    double netBuy = (row.get("BILLBOARD_NET_AMT") != null ? row.get("BILLBOARD_NET_AMT").asDouble() : 0) / 10000;
                    sb.append(String.format("  %s  原因: %s  净买入: %.1f万\n", date, reason, netBuy));
                    dates.add(date);
                }
            }
            if (dates.isEmpty()) {
                sb.append("  近期无龙虎榜上榜记录\n");
                return sb.toString();
            }

            // 2. 最近上榜的买卖席位
            String latestDate = dates.get(0);
            sb.append(String.format("\n最近上榜 %s 买卖席位:\n", latestDate));

            String buyFilter = String.format("(TRADE_DATE='%s')(SECURITY_CODE=\"%s\")", latestDate, code);
            String urlBuy = DATACENTER_URL + "?reportName=RPT_BILLBOARD_DAILYDETAILSBUY&columns=ALL"
                    + "&filter=" + buyFilter + "&pageNumber=1&pageSize=10&sortColumns=BUY&sortTypes=-1&source=WEB&client=WEB";
            log.debug("[AStockSignalRouterTool] 请求龙虎榜买席位: code={}, date={}", code, latestDate);
            String bodyBuy = emRateLimiter.get(urlBuy, Map.of());
            JsonNode buyData = objectMapper.readTree(bodyBuy).path("result").path("data");
            sb.append("  买入TOP5:\n");
            if (buyData.isArray()) {
                int rank = 1;
                for (JsonNode row : buyData) {
                    if (rank > 5) break;
                    String name = strVal(row.get("OPERATEDEPT_NAME"), "");
                    double buy = (row.get("BUY") != null ? row.get("BUY").asDouble() : 0) / 10000;
                    double sell = (row.get("SELL") != null ? row.get("SELL").asDouble() : 0) / 10000;
                    double net = (row.get("NET") != null ? row.get("NET").asDouble() : 0) / 10000;
                    sb.append(String.format("    %d. %s: 买%.0f万 卖%.0f万 净%.0f万\n", rank++, name, buy, sell, net));
                }
            }

            String urlSell = DATACENTER_URL + "?reportName=RPT_BILLBOARD_DAILYDETAILSSELL&columns=ALL"
                    + "&filter=" + buyFilter + "&pageNumber=1&pageSize=10&sortColumns=SELL&sortTypes=-1&source=WEB&client=WEB";
            log.debug("[AStockSignalRouterTool] 请求龙虎榜卖席位: code={}, date={}", code, latestDate);
            String bodySell = emRateLimiter.get(urlSell, Map.of());
            JsonNode sellData = objectMapper.readTree(bodySell).path("result").path("data");
            sb.append("  卖出TOP5:\n");
            if (sellData.isArray()) {
                int rank = 1;
                for (JsonNode row : sellData) {
                    if (rank > 5) break;
                    String name = strVal(row.get("OPERATEDEPT_NAME"), "");
                    double buy = (row.get("BUY") != null ? row.get("BUY").asDouble() : 0) / 10000;
                    double sell = (row.get("SELL") != null ? row.get("SELL").asDouble() : 0) / 10000;
                    double net = (row.get("NET") != null ? row.get("NET").asDouble() : 0) / 10000;
                    sb.append(String.format("    %d. %s: 买%.0f万 卖%.0f万 净%.0f万\n", rank++, name, buy, sell, net));
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "龙虎榜查询失败: " + e.getMessage();
        }
    }

    private String dailyDragonTiger(String tradeDate, double minNetBuy) {
        try {
            String filter = String.format("(TRADE_DATE='%s')", tradeDate);
            if (minNetBuy > 0) {
                filter += String.format("(BILLBOARD_NET_AMT>=%s)", minNetBuy * 10000);
            }
            String url = DATACENTER_URL + "?reportName=RPT_DAILYBILLBOARD_DETAILSNEW&columns=ALL"
                    + "&filter=" + filter + "&pageNumber=1&pageSize=50&sortColumns=BILLBOARD_NET_AMT&sortTypes=-1&source=WEB&client=WEB";
            log.debug("[AStockSignalRouterTool] 请求全市场龙虎榜: date={}", tradeDate);
            String body = emRateLimiter.get(url, Map.of());
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.path("result").path("data");

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== 全市场龙虎榜 %s ===\n\n", tradeDate));
            if (data.isArray()) {
                for (JsonNode row : data) {
                    String code = strVal(row.get("SECURITY_CODE"), "");
                    String name = strVal(row.get("SECURITY_NAME_ABBR"), "");
                    double netBuy = (row.get("BILLBOARD_NET_AMT") != null ? row.get("BILLBOARD_NET_AMT").asDouble() : 0) / 10000;
                    String reason = strVal(row.get("EXPLANATION"), "");
                    sb.append(String.format("  %s %s  净买入:%.0f万  %s\n", code, name, netBuy, reason));
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "全市场龙虎榜查询失败: " + e.getMessage();
        }
    }

    private String lockupExpiry(String stockCode, String tradeDate, int forwardDays) {
        try {
            String code = AStockUtils.normalizeCode(stockCode);
            String filter = String.format("(SECURITY_CODE=\"%s\")", code);
            String url = DATACENTER_URL + "?reportName=RPT_LIFT_STAGE&columns=ALL"
                    + "&filter=" + filter + "&pageNumber=1&pageSize=50&sortColumns=FREE_DATE&sortTypes=-1&source=WEB&client=WEB";
            log.debug("[AStockSignalRouterTool] 请求解禁日历: code={}", code);
            String body = emRateLimiter.get(url, Map.of());
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.path("result").path("data");

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== %s 限售解禁日历 ===\n\n", code));
            LocalDate today = LocalDate.parse(tradeDate, YYYY_MM_DD);
            LocalDate futureLimit = today.plusDays(forwardDays);
            List<String> history = new ArrayList<>();
            List<String> upcoming = new ArrayList<>();

            if (data.isArray()) {
                for (JsonNode row : data) {
                    String freeDate = strVal(row.get("FREE_DATE"), "").substring(0, 10);
                    double freeRatio = row.get("FREE_RATIO") != null ? row.get("FREE_RATIO").asDouble() : 0;
                    double freeNum = (row.get("FREE_NUM") != null ? row.get("FREE_NUM").asDouble() : 0) / 10000;
                    String holders = strVal(row.get("HOLDERS_NAME"), "");
                    String line = String.format("  %s  解禁比例:%.2f%%  数量:%.0f万股  持有者:%s", freeDate, freeRatio, freeNum, holders);
                    LocalDate fd = LocalDate.parse(freeDate, YYYY_MM_DD);
                    if (fd.isBefore(today)) {
                        history.add(line);
                    } else if (!fd.isAfter(futureLimit)) {
                        upcoming.add(line);
                    }
                }
            }
            sb.append("历史解禁:\n");
            history.stream().limit(5).forEach(sb::append);
            if (history.isEmpty()) sb.append("  无\n");
            sb.append(String.format("\n未来%d天待解禁:\n", forwardDays));
            upcoming.forEach(sb::append);
            if (upcoming.isEmpty()) sb.append("  无\n");
            return sb.toString();
        } catch (Exception e) {
            return "解禁日历查询失败: " + e.getMessage();
        }
    }

    private String industryRanking(int topN) {
        try {
            String url = "https://push2.eastmoney.com/api/qt/clist/get"
                    + "?pn=1&pz=" + topN + "&po=1&np=1&ut=bd1d9ddb04089700cf9c27f6f7426281"
                    + "&fltt=2&invt=2&fid=f3&fs=m:90+t:2&fields=f12,f14,f3,f104,f105,f128";
            log.debug("[AStockSignalRouterTool] 请求行业排名: topN={}", topN);
            String body = emRateLimiter.get(url, Map.of("Referer", "https://quote.eastmoney.com/"));
            JsonNode root = objectMapper.readTree(body);
            JsonNode diff = root.path("data").path("diff");

            StringBuilder sb = new StringBuilder();
            sb.append("=== 行业板块排名 ===\n\n");
            sb.append("涨幅TOP:\n");
            if (diff.isArray()) {
                int rank = 1;
                for (JsonNode item : diff) {
                    String name = strVal(item.get("f14"), "");
                    double changePct = item.get("f3") != null ? item.get("f3").asDouble() : 0;
                    int upCount = item.get("f104") != null ? item.get("f104").asInt() : 0;
                    int downCount = item.get("f105") != null ? item.get("f105").asInt() : 0;
                    String lead = strVal(item.get("f128"), "");
                    sb.append(String.format("  %d. %s  %+.2f%%  上涨:%d 下跌:%d  龙头:%s\n",
                            rank++, name, changePct, upCount, downCount, lead));
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "行业排名查询失败: " + e.getMessage();
        }
    }

    private double parseDouble(String s) {
        try {
            return (s == null || s.isEmpty()) ? 0 : Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0;
        }
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

    private String getOptStr(JsonNode p, String key, String defaultVal) {
        if (p == null || !p.has(key) || p.get(key).isNull()) return defaultVal;
        String val = p.get(key).asText();
        return val.isEmpty() ? defaultVal : val;
    }

    private int getOptInt(JsonNode p, String key, int defaultVal) {
        if (p == null || !p.has(key) || p.get(key).isNull()) return defaultVal;
        return p.get(key).asInt(defaultVal);
    }

    private double getOptDouble(JsonNode p, String key, double defaultVal) {
        if (p == null || !p.has(key) || p.get(key).isNull()) return defaultVal;
        return p.get(key).asDouble(defaultVal);
    }
}
