package com.itlk.myclaudecode.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itlk.myclaudecode.tool.annotation.ToolBehavior;
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
            金融计算器。执行各类金融和数学计算。

            operation 可选值与适用场景：

            [利息与收益]
            - compoundInterest: 复利终值。参数: principal, annualRate, years
            - simpleInterest: 单利终值。参数: principal, annualRate, years
            - annualizedReturn: 年化收益率换算。参数: totalReturnPercent, days
            - dcaReturn: 定投收益（纯定投，无初始资金）。参数: monthlyAmount, annualRate, months
            - compoundDca: 复利+定投综合（有初始资金+定投）。参数: initialCapital, periodicAmount, annualRate, years, frequency
            - ruleOf72: 72法则（多久翻倍）。参数: annualRate
            - cagr: 复合年增长率。参数: beginValue, endValue, years
            - totalReturn: 总收益率（含分红）。参数: buyPrice, sellPrice, dividends
            - inflationAdjusted: 通胀调整购买力。参数: amount, inflationRate, years

            [估值指标]
            - peRatio: 市盈率PE。参数: stockPrice, earningsPerShare
            - pbRatio: 市净率PB。参数: stockPrice, bookValuePerShare
            - dividendYield: 股息率。参数: annualDividend, stockPrice

            [贷款]
            - loanPayment: 等额本息月供。参数: principal, annualRate, months

            [投资决策]
            - npv: 净现值。参数: discountRate, cashFlows
            - irr: 内部收益率。参数: cashFlows

            [债券]
            - bondPrice: 债券定价。参数: faceValue, couponRate, marketRate, periods
            - bondYtm: 债券到期收益率。参数: faceValue, marketPrice, couponRate, periods

            [退休规划]
            - retirementTarget: 退休所需本金。参数: annualExpense, safeWithdrawalRate(可选)
            - withdrawalPlan: 定额提取计划。参数: principal, annualWithdrawal, annualRate

            [风险指标]
            - sharpeRatio: 夏普比率。参数: portfolioReturn, riskFreeRate, volatility
            - maxDrawdown: 最大回撤。参数: navSeries

            [通用计算]
            - calculate: 数学表达式计算。参数: expression

            params 为 JSON 字符串，格式参考各 operation 的参数说明。
            """)
    public String financial_calculator(
            @ToolParam(description = "操作类型，可选值：calculate, compoundInterest, simpleInterest, annualizedReturn, dcaReturn, compoundDca, ruleOf72, cagr, totalReturn, inflationAdjusted, peRatio, pbRatio, dividendYield, loanPayment, npv, irr, bondPrice, bondYtm, retirementTarget, withdrawalPlan, sharpeRatio, maxDrawdown") String operation,
            @ToolParam(description = "JSON格式参数，如 {\"principal\":10000, \"annualRate\":0.05, \"years\":10}") String params) {
        log.info("[FinancialCalcRouterTool] operation={}, params={}", operation, params);
        try {
            if (operation == null || operation.isBlank()) {
                return "操作类型不能为空。可用操作：calculate, compoundInterest, simpleInterest, annualizedReturn, dcaReturn, compoundDca, ruleOf72, cagr, totalReturn, inflationAdjusted, peRatio, pbRatio, dividendYield, loanPayment, npv, irr, bondPrice, bondYtm, retirementTarget, withdrawalPlan, sharpeRatio, maxDrawdown";
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
                default -> "未知操作: " + operation + "。可用操作：calculate, compoundInterest, simpleInterest, annualizedReturn, dcaReturn, compoundDca, ruleOf72, cagr, totalReturn, inflationAdjusted, peRatio, pbRatio, dividendYield, loanPayment, npv, irr, bondPrice, bondYtm, retirementTarget, withdrawalPlan, sharpeRatio, maxDrawdown";
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
