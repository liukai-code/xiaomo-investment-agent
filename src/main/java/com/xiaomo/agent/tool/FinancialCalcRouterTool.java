package com.xiaomo.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaomo.agent.tool.annotation.ToolBehavior;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

@Slf4j
public class FinancialCalcRouterTool {

    private final FinancialCalcTool delegate;
    private final ObjectMapper objectMapper;

    public FinancialCalcRouterTool(FinancialCalcTool delegate) {
        this.delegate = delegate;
        this.objectMapper = new ObjectMapper();
    }

    @ToolBehavior(deterministic = true, cacheable = true)
    @Tool(description = """
            金融数值计算器。仅在用户明确要求进行数值计算时调用，概念解释、策略讨论、行情查询等场景禁止调用。

            支持的操作（operation）：
            - calculate: 数学表达式计算。参数: expression
            - compoundInterest / simpleInterest: 复利/单利。参数: principal, annualRate, years
            - annualizedReturn: 年化收益率。参数: totalReturnPercent, days
            - dcaReturn / compoundDca: 定投收益。参数: monthlyAmount, annualRate, months 等
            - ruleOf72: 72法则。参数: annualRate
            - cagr / totalReturn / batchTotalReturn / inflationAdjusted: 收益率类
            - peRatio / pbRatio / dividendYield: 估值指标。参数: stockPrice, earningsPerShare 等
            - loanPayment: 等额本息月供。参数: principal, annualRate, months
            - npv / irr: 净现值/内部收益率。参数: discountRate, cashFlows 等
            - bondPrice / bondYtm: 债券定价/到期收益率
            - retirementTarget / withdrawalPlan: 退休规划
            - sharpeRatio / maxDrawdown: 风险指标

            params 为 JSON 字符串，格式参考各 operation 的参数说明。
            多组买卖对用 batchTotalReturn（trades 数组），不要循环调用 totalReturn。
            """)
    public String financial_calculator(
            @ToolParam(description = "操作类型，可选值：calculate, compoundInterest, simpleInterest, annualizedReturn, dcaReturn, compoundDca, ruleOf72, cagr, totalReturn, batchTotalReturn, inflationAdjusted, peRatio, pbRatio, dividendYield, loanPayment, npv, irr, bondPrice, bondYtm, retirementTarget, withdrawalPlan, sharpeRatio, maxDrawdown") String operation,
            @ToolParam(description = "JSON格式参数，如 {\"principal\":10000, \"annualRate\":0.05, \"years\":10}") String params) {
        log.info("[FinancialCalcRouterTool] operation={}, params={}", operation, params);
        try {
            if (operation == null || operation.isBlank()) {
                return "操作类型不能为空。可用操作：calculate, compoundInterest, simpleInterest, annualizedReturn, dcaReturn, compoundDca, ruleOf72, cagr, totalReturn, batchTotalReturn, inflationAdjusted, peRatio, pbRatio, dividendYield, loanPayment, npv, irr, bondPrice, bondYtm, retirementTarget, withdrawalPlan, sharpeRatio, maxDrawdown";
            }
            JsonNode p = parseParams(params);
            return switch (operation.trim()) {
                case "calculate" -> delegate.calculate(getStr(p, "expression"));
                case "compoundInterest" -> delegate.compoundInterest(getDouble(p, "principal"), getDouble(p, "annualRate"), getInt(p, "years"));
                case "simpleInterest" -> delegate.simpleInterest(getDouble(p, "principal"), getDouble(p, "annualRate"), getInt(p, "years"));
                case "annualizedReturn" -> delegate.annualizedReturn(getDouble(p, "totalReturnPercent"), getInt(p, "days"));
                case "dcaReturn" -> delegate.dcaReturn(getDouble(p, "monthlyAmount"), getDouble(p, "annualRate"), getInt(p, "months"));
                case "compoundDca" -> delegate.compoundDca(getDouble(p, "initialCapital"), getDouble(p, "periodicAmount"), getDouble(p, "annualRate"), getInt(p, "years"), getStr(p, "frequency"));
                case "ruleOf72" -> delegate.ruleOf72(getDouble(p, "annualRate"));
                case "cagr" -> delegate.cagr(getDouble(p, "beginValue"), getDouble(p, "endValue"), getInt(p, "years"));
                case "totalReturn" -> delegate.totalReturn(getDouble(p, "buyPrice"), getDouble(p, "sellPrice"), getDouble(p, "dividends"));
                case "batchTotalReturn" -> {
                    JsonNode trades = p.get("trades");
                    if (trades == null || !trades.isArray() || trades.isEmpty()) {
                        yield "缺少参数 trades（数组），每项需包含 buyPrice, sellPrice, dividends";
                    }
                    yield delegate.batchTotalReturn(trades);
                }
                case "inflationAdjusted" -> delegate.inflationAdjusted(getDouble(p, "amount"), getDouble(p, "inflationRate"), getInt(p, "years"));
                case "peRatio" -> delegate.peRatio(getDouble(p, "stockPrice"), getDouble(p, "earningsPerShare"));
                case "pbRatio" -> delegate.pbRatio(getDouble(p, "stockPrice"), getDouble(p, "bookValuePerShare"));
                case "dividendYield" -> delegate.dividendYield(getDouble(p, "annualDividend"), getDouble(p, "stockPrice"));
                case "loanPayment" -> delegate.loanPayment(getDouble(p, "principal"), getDouble(p, "annualRate"), getInt(p, "months"));
                case "npv" -> delegate.npv(getDouble(p, "discountRate"), getStr(p, "cashFlows"));
                case "irr" -> delegate.irr(getStr(p, "cashFlows"));
                case "bondPrice" -> delegate.bondPrice(getDouble(p, "faceValue"), getDouble(p, "couponRate"), getDouble(p, "marketRate"), getInt(p, "periods"));
                case "bondYtm" -> delegate.bondYtm(getDouble(p, "faceValue"), getDouble(p, "marketPrice"), getDouble(p, "couponRate"), getInt(p, "periods"));
                case "retirementTarget" -> {
                    Double swr = p != null && p.has("safeWithdrawalRate") ? getDouble(p, "safeWithdrawalRate") : null;
                    yield delegate.retirementTarget(getDouble(p, "annualExpense"), swr);
                }
                case "withdrawalPlan" -> delegate.withdrawalPlan(getDouble(p, "principal"), getDouble(p, "annualWithdrawal"), getDouble(p, "annualRate"));
                case "sharpeRatio" -> delegate.sharpeRatio(getDouble(p, "portfolioReturn"), getDouble(p, "riskFreeRate"), getDouble(p, "volatility"));
                case "maxDrawdown" -> delegate.maxDrawdown(getStr(p, "navSeries"));
                default -> "未知操作: " + operation + "。可用操作：calculate, compoundInterest, simpleInterest, annualizedReturn, dcaReturn, compoundDca, ruleOf72, cagr, totalReturn, batchTotalReturn, inflationAdjusted, peRatio, pbRatio, dividendYield, loanPayment, npv, irr, bondPrice, bondYtm, retirementTarget, withdrawalPlan, sharpeRatio, maxDrawdown";
            };
        } catch (Exception e) {
            log.error("[FinancialCalcRouterTool] 异常: operation={}, error={}", operation, e.getMessage(), e);
            return "金融计算失败（operation=" + operation + "）: " + e.getMessage();
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
        if (p == null || !p.has(key)) throw new IllegalArgumentException("缺少参数: " + key);
        return p.get(key).asText();
    }

    private double getDouble(JsonNode p, String key) {
        if (p == null || !p.has(key)) throw new IllegalArgumentException("缺少参数: " + key);
        return p.get(key).asDouble();
    }

    private int getInt(JsonNode p, String key) {
        if (p == null || !p.has(key)) throw new IllegalArgumentException("缺少参数: " + key);
        return p.get(key).asInt();
    }
}
