package com.itlk.myclaudecode.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

@Slf4j
public class FinancialCalcTool {

    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int MAX_ITER = 1000;
    private static final BigDecimal CONVERGENCE = new BigDecimal("1E-10");

    // ==================== 通用计算 ====================

    @Tool(description = "数学表达式计算。当需要计算加减乘除、括号运算时调用，如'50*30'、'(100+200)*3'。支持+-*/和括号。")
    public String calculate(
            @ToolParam(description = "数学表达式，如'50*30'、'(100+200)*3'、'7890+50*365'") String expression) {
        log.info("[FinancialCalcTool] calculate 入参: expression={}", expression);
        try {
            if (expression == null || expression.isBlank()) return "表达式不能为空";
            String cleaned = expression.replaceAll("\\s+", "");
            int[] pos = {0};
            double value = parseExpression(cleaned, pos);
            if (pos[0] < cleaned.length()) {
                return "表达式格式错误，无法解析: " + cleaned.substring(pos[0]);
            }
            String output = String.format("计算结果：%s = %s", expression.trim(),
                    BigDecimal.valueOf(value).stripTrailingZeros().toPlainString());
            log.info("[FinancialCalcTool] calculate 出参: {}", output);
            return output;
        } catch (Exception e) {
            log.error("[FinancialCalcTool] calculate 异常: {}", e.getMessage(), e);
            return "计算失败: " + e.getMessage();
        }
    }

    private double parseExpression(String s, int[] pos) {
        double result = parseTerm(s, pos);
        while (pos[0] < s.length()) {
            char c = s.charAt(pos[0]);
            if (c == '+') { pos[0]++; result += parseTerm(s, pos); }
            else if (c == '-') { pos[0]++; result -= parseTerm(s, pos); }
            else break;
        }
        return result;
    }

    private double parseTerm(String s, int[] pos) {
        double result = parseFactor(s, pos);
        while (pos[0] < s.length()) {
            char c = s.charAt(pos[0]);
            if (c == '*') { pos[0]++; result *= parseFactor(s, pos); }
            else if (c == '/') { pos[0]++; result /= parseFactor(s, pos); }
            else break;
        }
        return result;
    }

    private double parseFactor(String s, int[] pos) {
        if (pos[0] < s.length() && s.charAt(pos[0]) == '-') {
            pos[0]++;
            return -parseFactor(s, pos);
        }
        if (pos[0] < s.length() && s.charAt(pos[0]) == '(') {
            pos[0]++;
            double val = parseExpression(s, pos);
            if (pos[0] < s.length() && s.charAt(pos[0]) == ')') pos[0]++;
            return val;
        }
        int start = pos[0];
        while (pos[0] < s.length() && (Character.isDigit(s.charAt(pos[0])) || s.charAt(pos[0]) == '.')) {
            pos[0]++;
        }
        if (start == pos[0]) throw new RuntimeException("期望数字，位置: " + pos[0]);
        return Double.parseDouble(s.substring(start, pos[0]));
    }

    // ==================== A. 基础利息 ====================

    @Tool(description = "复利终值计算。当用户询问复利收益、本金增值、投资终值、利滚利时调用。")
    public String compoundInterest(
            @ToolParam(description = "本金（元）") double principal,
            @ToolParam(description = "年化利率（如0.05表示5%）") double annualRate,
            @ToolParam(description = "投资年限") int years) {
        log.info("[FinancialCalcTool] compoundInterest 入参: principal={}, annualRate={}, years={}", principal, annualRate, years);
        try {
            BigDecimal p = BigDecimal.valueOf(principal);
            BigDecimal r = BigDecimal.valueOf(annualRate);
            BigDecimal one = BigDecimal.ONE;
            BigDecimal factor = one.add(r).pow(years, MC);
            BigDecimal result = p.multiply(factor, MC);
            BigDecimal profit = result.subtract(p, MC);
            String output = String.format(
                    "复利计算结果：\n本金：%.2f元\n年化利率：%.2f%%\n投资年限：%d年\n终值：%.2f元\n收益：%.2f元",
                    principal, annualRate * 100, years,
                    result.setScale(2, RoundingMode.HALF_UP),
                    profit.setScale(2, RoundingMode.HALF_UP));
            log.info("[FinancialCalcTool] compoundInterest 出参: {}", output);
            return output;
        } catch (Exception e) {
            log.error("[FinancialCalcTool] compoundInterest 异常: {}", e.getMessage(), e);
            return "复利计算失败: " + e.getMessage();
        }
    }

    @Tool(description = "单利终值计算。当用户询问单利收益、单利和复利区别时调用。")
    public String simpleInterest(
            @ToolParam(description = "本金（元）") double principal,
            @ToolParam(description = "年化利率（如0.05表示5%）") double annualRate,
            @ToolParam(description = "投资年限") int years) {
        log.info("[FinancialCalcTool] simpleInterest 入参: principal={}, annualRate={}, years={}", principal, annualRate, years);
        try {
            BigDecimal p = BigDecimal.valueOf(principal);
            BigDecimal r = BigDecimal.valueOf(annualRate);
            BigDecimal y = BigDecimal.valueOf(years);
            BigDecimal interest = p.multiply(r, MC).multiply(y, MC);
            BigDecimal result = p.add(interest, MC);
            String output = String.format(
                    "单利计算结果：\n本金：%.2f元\n年化利率：%.2f%%\n投资年限：%d年\n利息：%.2f元\n终值：%.2f元",
                    principal, annualRate * 100, years,
                    interest.setScale(2, RoundingMode.HALF_UP),
                    result.setScale(2, RoundingMode.HALF_UP));
            log.info("[FinancialCalcTool] simpleInterest 出参: {}", output);
            return output;
        } catch (Exception e) {
            log.error("[FinancialCalcTool] simpleInterest 异常: {}", e.getMessage(), e);
            return "单利计算失败: " + e.getMessage();
        }
    }

    @Tool(description = "年化收益率换算。当用户说'几个月赚了X%，年化是多少'时调用。")
    public String annualizedReturn(
            @ToolParam(description = "总收益率百分比（如8表示8%）") double totalReturnPercent,
            @ToolParam(description = "持有天数") int days) {
        log.info("[FinancialCalcTool] annualizedReturn 入参: totalReturnPercent={}, days={}", totalReturnPercent, days);
        try {
            if (days <= 0) return "持有天数必须大于0";
            BigDecimal totalReturn = BigDecimal.valueOf(totalReturnPercent).divide(HUNDRED, MC);
            BigDecimal dayFactor = BigDecimal.valueOf(365.0 / days);
            // annualized = (1 + totalReturn)^(365/days) - 1
            double exponent = 365.0 / days;
            BigDecimal factor = BigDecimal.ONE.add(totalReturn, MC);
            BigDecimal annualized = BigDecimal.valueOf(Math.pow(factor.doubleValue(), exponent)).subtract(BigDecimal.ONE, MC);
            String output = String.format(
                    "年化收益率换算：\n总收益率：%.2f%%\n持有天数：%d天\n年化收益率：%.2f%%",
                    totalReturnPercent, days,
                    annualized.multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP));
            log.info("[FinancialCalcTool] annualizedReturn 出参: {}", output);
            return output;
        } catch (Exception e) {
            log.error("[FinancialCalcTool] annualizedReturn 异常: {}", e.getMessage(), e);
            return "年化收益率计算失败: " + e.getMessage();
        }
    }

    @Tool(description = "定投收益计算。当用户询问'每月定投X元，年化Y%，N年后有多少'时调用。")
    public String dcaReturn(
            @ToolParam(description = "每月定投金额（元）") double monthlyAmount,
            @ToolParam(description = "年化收益率（如0.08表示8%）") double annualRate,
            @ToolParam(description = "定投月数") int months) {
        log.info("[FinancialCalcTool] dcaReturn 入参: monthlyAmount={}, annualRate={}, months={}", monthlyAmount, annualRate, months);
        try {
            BigDecimal pmt = BigDecimal.valueOf(monthlyAmount);
            BigDecimal r = BigDecimal.valueOf(annualRate).divide(BigDecimal.valueOf(12), MC);
            if (r.compareTo(BigDecimal.ZERO) == 0) {
                BigDecimal total = pmt.multiply(BigDecimal.valueOf(months), MC);
                String output = String.format(
                        "定投计算结果（利率为0）：\n每月定投：%.2f元\n定投月数：%d月\n总投入：%.2f元\n终值：%.2f元",
                        monthlyAmount, months, total.setScale(2, RoundingMode.HALF_UP), total.setScale(2, RoundingMode.HALF_UP));
                log.info("[FinancialCalcTool] dcaReturn 出参: {}", output);
                return output;
            }
            // FV = PMT * [((1+r)^n - 1) / r] * (1+r)
            BigDecimal onePlusR = BigDecimal.ONE.add(r, MC);
            BigDecimal factor = onePlusR.pow(months, MC);
            BigDecimal numerator = factor.subtract(BigDecimal.ONE, MC);
            BigDecimal fv = pmt.multiply(numerator, MC).multiply(onePlusR, MC).divide(r, MC);
            BigDecimal totalInvest = pmt.multiply(BigDecimal.valueOf(months), MC);
            BigDecimal profit = fv.subtract(totalInvest, MC);
            String output = String.format(
                    "定投收益计算结果：\n每月定投：%.2f元\n年化收益率：%.2f%%\n定投月数：%d月\n总投入：%.2f元\n终值：%.2f元\n收益：%.2f元",
                    monthlyAmount, annualRate * 100, months,
                    totalInvest.setScale(2, RoundingMode.HALF_UP),
                    fv.setScale(2, RoundingMode.HALF_UP),
                    profit.setScale(2, RoundingMode.HALF_UP));
            log.info("[FinancialCalcTool] dcaReturn 出参: {}", output);
            return output;
        } catch (Exception e) {
            log.error("[FinancialCalcTool] dcaReturn 异常: {}", e.getMessage(), e);
            return "定投收益计算失败: " + e.getMessage();
        }
    }

    @Tool(description = "复利+定投综合计算。当用户同时提到初始资金和定投时调用，支持日定投、周定投、月定投。例如'初始资金X元，每日定投Y元，年化Z%，N年后多少'。")
    public String compoundDca(
            @ToolParam(description = "初始资金（元）") double initialCapital,
            @ToolParam(description = "每期定投金额（元）") double periodicAmount,
            @ToolParam(description = "年化收益率（如0.078表示7.8%）") double annualRate,
            @ToolParam(description = "投资年限") int years,
            @ToolParam(description = "定投频率：daily=每日, weekly=每周, monthly=每月") String frequency) {
        log.info("[FinancialCalcTool] compoundDca 入参: initialCapital={}, periodicAmount={}, annualRate={}, years={}, frequency={}",
                initialCapital, periodicAmount, annualRate, years, frequency);
        try {
            int periodsPerYear;
            switch (frequency.toLowerCase()) {
                case "daily":   periodsPerYear = 365; break;
                case "weekly":  periodsPerYear = 52;  break;
                case "monthly": periodsPerYear = 12;  break;
                default: return "定投频率不支持: " + frequency + "，请使用 daily/weekly/monthly";
            }

            BigDecimal initCap = BigDecimal.valueOf(initialCapital);
            BigDecimal pmt = BigDecimal.valueOf(periodicAmount);
            BigDecimal annualR = BigDecimal.valueOf(annualRate);
            int totalPeriods = years * periodsPerYear;
            BigDecimal periodRate = annualR.divide(BigDecimal.valueOf(periodsPerYear), MC);
            BigDecimal onePlusR = BigDecimal.ONE.add(periodRate, MC);

            // 初始资金复利终值
            BigDecimal fvInitial = initCap.multiply(onePlusR.pow(totalPeriods, MC), MC);

            // 定投终值: FV = PMT * [((1+r)^n - 1) / r] * (1+r)
            BigDecimal fvDca;
            BigDecimal totalDcaInvest;
            if (periodRate.compareTo(BigDecimal.ZERO) == 0) {
                fvDca = pmt.multiply(BigDecimal.valueOf(totalPeriods), MC);
                totalDcaInvest = fvDca;
            } else {
                BigDecimal factor = onePlusR.pow(totalPeriods, MC);
                BigDecimal numerator = factor.subtract(BigDecimal.ONE, MC);
                fvDca = pmt.multiply(numerator, MC).multiply(onePlusR, MC).divide(periodRate, MC);
                totalDcaInvest = pmt.multiply(BigDecimal.valueOf(totalPeriods), MC);
            }

            BigDecimal totalFv = fvInitial.add(fvDca, MC);
            BigDecimal totalInvested = initCap.add(totalDcaInvest, MC);
            BigDecimal totalProfit = totalFv.subtract(totalInvested, MC);

            String freqName;
            switch (frequency.toLowerCase()) {
                case "daily":   freqName = "每日"; break;
                case "weekly":  freqName = "每周"; break;
                default:        freqName = "每月"; break;
            }

            String output = String.format(
                    "复利+定投综合计算：\n" +
                    "初始资金：%.2f元\n" +
                    "%s定投：%.2f元\n" +
                    "年化收益率：%.2f%%\n" +
                    "投资年限：%d年（共%d期）\n\n" +
                    "--- 分项结果 ---\n" +
                    "初始资金复利终值：%.2f元（收益%.2f元）\n" +
                    "定投终值：%.2f元（定投本金%.2f元，收益%.2f元）\n\n" +
                    "--- 合计 ---\n" +
                    "总投入：%.2f元\n" +
                    "终值：%.2f元\n" +
                    "总收益：%.2f元",
                    initialCapital, freqName, periodicAmount,
                    annualRate * 100, years, totalPeriods,
                    fvInitial.setScale(2, RoundingMode.HALF_UP),
                    fvInitial.subtract(initCap, MC).setScale(2, RoundingMode.HALF_UP),
                    fvDca.setScale(2, RoundingMode.HALF_UP),
                    totalDcaInvest.setScale(2, RoundingMode.HALF_UP),
                    fvDca.subtract(totalDcaInvest, MC).setScale(2, RoundingMode.HALF_UP),
                    totalInvested.setScale(2, RoundingMode.HALF_UP),
                    totalFv.setScale(2, RoundingMode.HALF_UP),
                    totalProfit.setScale(2, RoundingMode.HALF_UP));
            log.info("[FinancialCalcTool] compoundDca 出参: {}", output);
            return output;
        } catch (Exception e) {
            log.error("[FinancialCalcTool] compoundDca 异常: {}", e.getMessage(), e);
            return "复利定投计算失败: " + e.getMessage();
        }
    }

    @Tool(description = "72法则。当用户问'年化X%多久翻倍'时调用，快速估算资金翻倍所需年数。")
    public String ruleOf72(
            @ToolParam(description = "年化收益率（如0.06表示6%）") double annualRate) {
        log.info("[FinancialCalcTool] ruleOf72 入参: annualRate={}", annualRate);
        try {
            if (annualRate <= 0) return "年化收益率必须大于0";
            BigDecimal rate = BigDecimal.valueOf(annualRate * 100);
            BigDecimal years = BigDecimal.valueOf(72).divide(rate, MC);
            String output = String.format(
                    "72法则：\n年化收益率：%.2f%%\n资金翻倍约需：%.1f年",
                    annualRate * 100, years.setScale(1, RoundingMode.HALF_UP));
            log.info("[FinancialCalcTool] ruleOf72 出参: {}", output);
            return output;
        } catch (Exception e) {
            log.error("[FinancialCalcTool] ruleOf72 异常: {}", e.getMessage(), e);
            return "72法则计算失败: " + e.getMessage();
        }
    }

    // ==================== B. 估值指标 ====================

    @Tool(description = "市盈率计算。当用户询问PE、市盈率、估值是否合理时调用。市盈率=股价/每股收益。")
    public String peRatio(
            @ToolParam(description = "股价（元）") double stockPrice,
            @ToolParam(description = "每股收益（元）") double earningsPerShare) {
        log.info("[FinancialCalcTool] peRatio 入参: stockPrice={}, eps={}", stockPrice, earningsPerShare);
        try {
            if (earningsPerShare == 0) return "每股收益为0，无法计算市盈率";
            BigDecimal price = BigDecimal.valueOf(stockPrice);
            BigDecimal eps = BigDecimal.valueOf(earningsPerShare);
            BigDecimal pe = price.divide(eps, MC);
            String level;
            double peVal = pe.doubleValue();
            if (peVal < 0) level = "（亏损，PE无意义）";
            else if (peVal < 15) level = "（低估区间）";
            else if (peVal < 25) level = "（合理区间）";
            else if (peVal < 40) level = "（偏高）";
            else level = "（高估）";
            String output = String.format(
                    "市盈率计算：\n股价：%.2f元\n每股收益：%.2f元\n市盈率（PE）：%.2f %s",
                    stockPrice, earningsPerShare, pe.setScale(2, RoundingMode.HALF_UP), level);
            log.info("[FinancialCalcTool] peRatio 出参: {}", output);
            return output;
        } catch (Exception e) {
            log.error("[FinancialCalcTool] peRatio 异常: {}", e.getMessage(), e);
            return "市盈率计算失败: " + e.getMessage();
        }
    }

    @Tool(description = "市净率计算。当用户询问PB、市净率、是否破净时调用。市净率=股价/每股净资产。")
    public String pbRatio(
            @ToolParam(description = "股价（元）") double stockPrice,
            @ToolParam(description = "每股净资产（元）") double bookValuePerShare) {
        log.info("[FinancialCalcTool] pbRatio 入参: stockPrice={}, bvps={}", stockPrice, bookValuePerShare);
        try {
            if (bookValuePerShare == 0) return "每股净资产为0，无法计算市净率";
            BigDecimal price = BigDecimal.valueOf(stockPrice);
            BigDecimal bvps = BigDecimal.valueOf(bookValuePerShare);
            BigDecimal pb = price.divide(bvps, MC);
            String level;
            double pbVal = pb.doubleValue();
            if (pbVal < 1) level = "（破净）";
            else if (pbVal < 2) level = "（较低）";
            else if (pbVal < 5) level = "（适中）";
            else level = "（较高）";
            String output = String.format(
                    "市净率计算：\n股价：%.2f元\n每股净资产：%.2f元\n市净率（PB）：%.2f %s",
                    stockPrice, bookValuePerShare, pb.setScale(2, RoundingMode.HALF_UP), level);
            log.info("[FinancialCalcTool] pbRatio 出参: {}", output);
            return output;
        } catch (Exception e) {
            log.error("[FinancialCalcTool] pbRatio 异常: {}", e.getMessage(), e);
            return "市净率计算失败: " + e.getMessage();
        }
    }

    @Tool(description = "股息率计算。当用户询问分红收益、股息率、高股息股票时调用。股息率=年度每股分红/股价。")
    public String dividendYield(
            @ToolParam(description = "年度每股分红（元）") double annualDividend,
            @ToolParam(description = "股价（元）") double stockPrice) {
        log.info("[FinancialCalcTool] dividendYield 入参: annualDividend={}, stockPrice={}", annualDividend, stockPrice);
        try {
            if (stockPrice == 0) return "股价不能为0";
            BigDecimal dividend = BigDecimal.valueOf(annualDividend);
            BigDecimal price = BigDecimal.valueOf(stockPrice);
            BigDecimal yield = dividend.divide(price, MC).multiply(HUNDRED, MC);
            String output = String.format(
                    "股息率计算：\n年度每股分红：%.2f元\n股价：%.2f元\n股息率：%.2f%%",
                    annualDividend, stockPrice, yield.setScale(2, RoundingMode.HALF_UP));
            log.info("[FinancialCalcTool] dividendYield 出参: {}", output);
            return output;
        } catch (Exception e) {
            log.error("[FinancialCalcTool] dividendYield 异常: {}", e.getMessage(), e);
            return "股息率计算失败: " + e.getMessage();
        }
    }

    // ==================== C. 贷款 ====================

    @Tool(description = "等额本息月供计算。当用户询问房贷月供、贷款还款、每月还多少钱时调用。")
    public String loanPayment(
            @ToolParam(description = "贷款本金（元）") double principal,
            @ToolParam(description = "年利率（如0.041表示4.1%）") double annualRate,
            @ToolParam(description = "还款总月数（如30年=360月）") int months) {
        log.info("[FinancialCalcTool] loanPayment 入参: principal={}, annualRate={}, months={}", principal, annualRate, months);
        try {
            BigDecimal p = BigDecimal.valueOf(principal);
            BigDecimal r = BigDecimal.valueOf(annualRate).divide(BigDecimal.valueOf(12), MC);
            BigDecimal n = BigDecimal.valueOf(months);

            if (r.compareTo(BigDecimal.ZERO) == 0) {
                BigDecimal monthly = p.divide(n, MC);
                BigDecimal totalPayment = p;
                String output = String.format(
                        "等额本息月供（利率为0）：\n贷款金额：%.2f元\n还款月数：%d月\n月供：%.2f元\n总还款：%.2f元\n总利息：0.00元",
                        principal, months, monthly.setScale(2, RoundingMode.HALF_UP), totalPayment.setScale(2, RoundingMode.HALF_UP));
                log.info("[FinancialCalcTool] loanPayment 出参: {}", output);
                return output;
            }

            // 月供 = P * r * (1+r)^n / ((1+r)^n - 1)
            BigDecimal onePlusR = BigDecimal.ONE.add(r, MC);
            BigDecimal powFactor = onePlusR.pow(months, MC);
            BigDecimal numerator = p.multiply(r, MC).multiply(powFactor, MC);
            BigDecimal denominator = powFactor.subtract(BigDecimal.ONE, MC);
            BigDecimal monthly = numerator.divide(denominator, MC);
            BigDecimal totalPayment = monthly.multiply(n, MC);
            BigDecimal totalInterest = totalPayment.subtract(p, MC);

            String output = String.format(
                    "等额本息月供计算：\n贷款金额：%.2f元\n年利率：%.2f%%\n还款月数：%d月（%.0f年）\n月供：%.2f元\n总还款：%.2f元\n总利息：%.2f元\n利息占比：%.1f%%",
                    principal, annualRate * 100, months, months / 12.0,
                    monthly.setScale(2, RoundingMode.HALF_UP),
                    totalPayment.setScale(2, RoundingMode.HALF_UP),
                    totalInterest.setScale(2, RoundingMode.HALF_UP),
                    totalInterest.divide(p, MC).multiply(HUNDRED).setScale(1, RoundingMode.HALF_UP));
            log.info("[FinancialCalcTool] loanPayment 出参: {}", output);
            return output;
        } catch (Exception e) {
            log.error("[FinancialCalcTool] loanPayment 异常: {}", e.getMessage(), e);
            return "月供计算失败: " + e.getMessage();
        }
    }

    // ==================== D. 投资决策 ====================

    @Tool(description = "净现值（NPV）计算。当用户评估一个投资项目是否值得、比较不同投资方案时调用。NPV>0表示值得投资。")
    public String npv(
            @ToolParam(description = "折现率（如0.1表示10%）") double discountRate,
            @ToolParam(description = "现金流数组，第一个通常为负数表示初始投入，用逗号分隔，如'-10000,3000,4000,5000,6000'") String cashFlows) {
        log.info("[FinancialCalcTool] npv 入参: discountRate={}, cashFlows={}", discountRate, cashFlows);
        try {
            BigDecimal rate = BigDecimal.valueOf(discountRate);
            String[] parts = cashFlows.split(",");
            BigDecimal npvValue = BigDecimal.ZERO;
            StringBuilder breakdown = new StringBuilder();

            for (int i = 0; i < parts.length; i++) {
                BigDecimal cf = new BigDecimal(parts[i].trim());
                BigDecimal discountFactor = BigDecimal.ONE.add(rate, MC).pow(i, MC);
                BigDecimal pv = cf.divide(discountFactor, MC);
                npvValue = npvValue.add(pv, MC);
                breakdown.append(String.format("  第%d期：%.2f元（现值%.2f元）\n", i, cf.setScale(2, RoundingMode.HALF_UP), pv.setScale(2, RoundingMode.HALF_UP)));
            }

            String recommendation = npvValue.compareTo(BigDecimal.ZERO) > 0 ? "NPV > 0，项目值得投资" : "NPV < 0，项目不值得投资";

            String output = String.format(
                    "净现值（NPV）计算：\n折现率：%.2f%%\n\n现金流明细：\n%s\nNPV = %.2f元\n结论：%s",
                    discountRate * 100, breakdown.toString(),
                    npvValue.setScale(2, RoundingMode.HALF_UP), recommendation);
            log.info("[FinancialCalcTool] npv 出参: {}", output);
            return output;
        } catch (Exception e) {
            log.error("[FinancialCalcTool] npv 异常: {}", e.getMessage(), e);
            return "NPV计算失败: " + e.getMessage();
        }
    }

    @Tool(description = "内部收益率（IRR）计算。当用户问'这个投资的收益率是多少'、比较不同投资方案时调用。IRR是使NPV=0的折现率。")
    public String irr(
            @ToolParam(description = "现金流数组，第一个通常为负数表示初始投入，用逗号分隔，如'-10000,3000,4000,5000,6000'") String cashFlows) {
        log.info("[FinancialCalcTool] irr 入参: cashFlows={}", cashFlows);
        try {
            String[] parts = cashFlows.split(",");
            double[] cf = new double[parts.length];
            for (int i = 0; i < parts.length; i++) {
                cf[i] = Double.parseDouble(parts[i].trim());
            }

            // 牛顿迭代法求IRR
            double rate = 0.1; // 初始猜测10%
            for (int iter = 0; iter < MAX_ITER; iter++) {
                double npvVal = 0;
                double npvDeriv = 0;
                for (int i = 0; i < cf.length; i++) {
                    double factor = Math.pow(1 + rate, i);
                    npvVal += cf[i] / factor;
                    if (i > 0) {
                        npvDeriv -= i * cf[i] / (factor * (1 + rate));
                    }
                }
                if (Math.abs(npvDeriv) < 1e-15) break;
                double newRate = rate - npvVal / npvDeriv;
                if (Math.abs(newRate - rate) < 1e-10) {
                    rate = newRate;
                    break;
                }
                rate = newRate;
            }

            StringBuilder breakdown = new StringBuilder();
            for (int i = 0; i < cf.length; i++) {
                breakdown.append(String.format("  第%d期：%.2f元\n", i, cf[i]));
            }

            String output = String.format(
                    "内部收益率（IRR）计算：\n\n现金流明细：\n%s\nIRR = %.2f%%\n含义：该项目的年化复合收益率约为 %.2f%%",
                    breakdown.toString(), rate * 100, rate * 100);
            log.info("[FinancialCalcTool] irr 出参: {}", output);
            return output;
        } catch (Exception e) {
            log.error("[FinancialCalcTool] irr 异常: {}", e.getMessage(), e);
            return "IRR计算失败: " + e.getMessage();
        }
    }

    // ==================== E. 债券 ====================

    @Tool(description = "债券定价。当用户询问债券价格、债券值多少钱时调用。输入面值、票面利率、市场利率和剩余期数。")
    public String bondPrice(
            @ToolParam(description = "债券面值（元）") double faceValue,
            @ToolParam(description = "票面利率（如0.05表示5%）") double couponRate,
            @ToolParam(description = "市场利率/到期收益率（如0.06表示6%）") double marketRate,
            @ToolParam(description = "剩余付息期数（年）") int periods) {
        log.info("[FinancialCalcTool] bondPrice 入参: faceValue={}, couponRate={}, marketRate={}, periods={}", faceValue, couponRate, marketRate, periods);
        try {
            BigDecimal fv = BigDecimal.valueOf(faceValue);
            BigDecimal cr = BigDecimal.valueOf(couponRate);
            BigDecimal mr = BigDecimal.valueOf(marketRate);
            BigDecimal n = BigDecimal.valueOf(periods);

            BigDecimal coupon = fv.multiply(cr, MC); // 每期票息
            BigDecimal onePlusMr = BigDecimal.ONE.add(mr, MC);

            // 债券价格 = 票息现值 + 面值现值
            // PV(票息) = C * [1 - (1+r)^(-n)] / r
            // PV(面值) = FV / (1+r)^n
            BigDecimal pvCoupons;
            if (mr.compareTo(BigDecimal.ZERO) == 0) {
                pvCoupons = coupon.multiply(n, MC);
            } else {
                BigDecimal discountFactor = onePlusMr.pow(periods, MC);
                pvCoupons = coupon.multiply(BigDecimal.ONE.subtract(BigDecimal.ONE.divide(discountFactor, MC), MC).divide(mr, MC), MC);
            }
            BigDecimal pvFace = fv.divide(onePlusMr.pow(periods, MC), MC);
            BigDecimal price = pvCoupons.add(pvFace, MC);
            BigDecimal premium = price.subtract(fv, MC);

            String status = premium.compareTo(BigDecimal.ZERO) > 0 ? "溢价发行" :
                           premium.compareTo(BigDecimal.ZERO) < 0 ? "折价发行" : "平价发行";

            String output = String.format(
                    "债券定价：\n面值：%.2f元\n票面利率：%.2f%%\n市场利率：%.2f%%\n剩余期数：%d年\n年票息：%.2f元\n\n债券价格：%.2f元\n%s（%s%.2f元）",
                    faceValue, couponRate * 100, marketRate * 100, periods,
                    coupon.setScale(2, RoundingMode.HALF_UP),
                    price.setScale(2, RoundingMode.HALF_UP), status,
                    premium.compareTo(BigDecimal.ZERO) >= 0 ? "溢价" : "折价",
                    premium.abs().setScale(2, RoundingMode.HALF_UP));
            log.info("[FinancialCalcTool] bondPrice 出参: {}", output);
            return output;
        } catch (Exception e) {
            log.error("[FinancialCalcTool] bondPrice 异常: {}", e.getMessage(), e);
            return "债券定价失败: " + e.getMessage();
        }
    }

    @Tool(description = "债券到期收益率（YTM）计算。当用户问'这个债券的实际收益率是多少'时调用。")
    public String bondYtm(
            @ToolParam(description = "债券面值（元）") double faceValue,
            @ToolParam(description = "当前市场价格（元）") double marketPrice,
            @ToolParam(description = "票面利率（如0.05表示5%）") double couponRate,
            @ToolParam(description = "剩余付息期数（年）") int periods) {
        log.info("[FinancialCalcTool] bondYtm 入参: faceValue={}, marketPrice={}, couponRate={}, periods={}", faceValue, marketPrice, couponRate, periods);
        try {
            double coupon = faceValue * couponRate;

            // 牛顿迭代法求YTM
            double ytm = couponRate; // 初始猜测为票面利率
            for (int iter = 0; iter < MAX_ITER; iter++) {
                double npvVal = 0;
                double npvDeriv = 0;
                for (int i = 1; i <= periods; i++) {
                    double factor = Math.pow(1 + ytm, i);
                    npvVal += coupon / factor;
                    npvDeriv -= i * coupon / (factor * (1 + ytm));
                }
                npvVal += faceValue / Math.pow(1 + ytm, periods) - marketPrice;
                npvDeriv -= periods * faceValue / (Math.pow(1 + ytm, periods) * (1 + ytm));

                if (Math.abs(npvDeriv) < 1e-15) break;
                double newYtm = ytm - npvVal / npvDeriv;
                if (Math.abs(newYtm - ytm) < 1e-10) {
                    ytm = newYtm;
                    break;
                }
                ytm = newYtm;
            }

            String output = String.format(
                    "债券到期收益率（YTM）：\n面值：%.2f元\n市场价格：%.2f元\n票面利率：%.2f%%\n剩余期数：%d年\n年票息：%.2f元\n\n到期收益率（YTM）：%.2f%%\n含义：持有到期的年化复合收益率",
                    faceValue, marketPrice, couponRate * 100, periods, coupon,
                    ytm * 100);
            log.info("[FinancialCalcTool] bondYtm 出参: {}", output);
            return output;
        } catch (Exception e) {
            log.error("[FinancialCalcTool] bondYtm 异常: {}", e.getMessage(), e);
            return "YTM计算失败: " + e.getMessage();
        }
    }

    // ==================== F. 退休规划 ====================

    @Tool(description = "退休所需本金估算。当用户问'我需要存多少钱才能退休'、'财务自由需要多少钱'时调用。基于4%安全提取率法则。")
    public String retirementTarget(
            @ToolParam(description = "退休后每年支出（元）") double annualExpense,
            @ToolParam(description = "安全提取率（默认0.04即4%，可不填）", required = false) Double safeWithdrawalRate) {
        log.info("[FinancialCalcTool] retirementTarget 入参: annualExpense={}, safeWithdrawalRate={}", annualExpense, safeWithdrawalRate);
        try {
            double swr = safeWithdrawalRate != null ? safeWithdrawalRate : 0.04;
            BigDecimal expense = BigDecimal.valueOf(annualExpense);
            BigDecimal rate = BigDecimal.valueOf(swr);
            BigDecimal target = expense.divide(rate, MC);

            String output = String.format(
                    "退休本金估算：\n每年支出：%.2f元\n安全提取率：%.2f%%\n\n所需本金：%.2f元（约%.0f万元）\n\n说明：按4%%法则，攒够年支出25倍的本金，每年提取4%%可维持永续生活。",
                    annualExpense, swr * 100,
                    target.setScale(2, RoundingMode.HALF_UP),
                    target.divide(HUNDRED, MC).divide(HUNDRED, MC).setScale(0, RoundingMode.HALF_UP));
            log.info("[FinancialCalcTool] retirementTarget 出参: {}", output);
            return output;
        } catch (Exception e) {
            log.error("[FinancialCalcTool] retirementTarget 异常: {}", e.getMessage(), e);
            return "退休本金估算失败: " + e.getMessage();
        }
    }

    @Tool(description = "定额提取计划。当用户问'有X万元本金，每年取Y万元，能撑多少年'时调用。")
    public String withdrawalPlan(
            @ToolParam(description = "初始本金（元）") double principal,
            @ToolParam(description = "每年提取金额（元）") double annualWithdrawal,
            @ToolParam(description = "预期年化收益率（如0.06表示6%）") double annualRate) {
        log.info("[FinancialCalcTool] withdrawalPlan 入参: principal={}, annualWithdrawal={}, annualRate={}", principal, annualWithdrawal, annualRate);
        try {
            if (annualWithdrawal <= 0) return "每年提取金额必须大于0";

            BigDecimal p = BigDecimal.valueOf(principal);
            BigDecimal w = BigDecimal.valueOf(annualWithdrawal);
            BigDecimal r = BigDecimal.valueOf(annualRate);
            BigDecimal onePlusR = BigDecimal.ONE.add(r, MC);

            int years = 0;
            BigDecimal balance = p;
            StringBuilder yearlyBreakdown = new StringBuilder();

            while (balance.compareTo(w) >= 0 && years < 200) {
                BigDecimal interest = balance.multiply(r, MC);
                BigDecimal newBalance = balance.add(interest, MC).subtract(w, MC);
                years++;
                if (years <= 10 || newBalance.compareTo(BigDecimal.ZERO) <= 0) {
                    yearlyBreakdown.append(String.format("  第%d年：余额%.0f → 利息%.0f → 取出%.0f → 剩余%.0f\n",
                            years, balance.setScale(0, RoundingMode.HALF_UP),
                            interest.setScale(0, RoundingMode.HALF_UP),
                            w.setScale(0, RoundingMode.HALF_UP),
                            newBalance.setScale(0, RoundingMode.HALF_UP)));
                }
                balance = newBalance;
            }

            if (balance.compareTo(BigDecimal.ZERO) > 0 && years >= 200) {
                String output = String.format(
                        "定额提取计划：\n初始本金：%.0f元\n每年提取：%.0f元\n预期收益率：%.2f%%\n\n结果：本金可永续使用（提取金额低于收益）\n第200年末余额：%.0f元",
                        principal, annualWithdrawal, annualRate * 100, balance.setScale(0, RoundingMode.HALF_UP));
                log.info("[FinancialCalcTool] withdrawalPlan 出参: {}", output);
                return output;
            }

            String output = String.format(
                    "定额提取计划：\n初始本金：%.0f元\n每年提取：%.0f元\n预期收益率：%.2f%%\n\n本金可持续：%d年\n\n逐年明细：\n%s",
                    principal, annualWithdrawal, annualRate * 100, years, yearlyBreakdown.toString());
            log.info("[FinancialCalcTool] withdrawalPlan 出参: {}", output);
            return output;
        } catch (Exception e) {
            log.error("[FinancialCalcTool] withdrawalPlan 异常: {}", e.getMessage(), e);
            return "提取计划计算失败: " + e.getMessage();
        }
    }

    // ==================== G. 风险指标 ====================

    @Tool(description = "夏普比率计算。当用户问'这个投资的风险收益比如何'、'夏普比率多少'时调用。夏普比率=（组合收益-无风险收益）/波动率。")
    public String sharpeRatio(
            @ToolParam(description = "投资组合年化收益率（如0.12表示12%）") double portfolioReturn,
            @ToolParam(description = "无风险收益率（如0.03表示3%）") double riskFreeRate,
            @ToolParam(description = "投资组合年化波动率/标准差（如0.15表示15%）") double volatility) {
        log.info("[FinancialCalcTool] sharpeRatio 入参: portfolioReturn={}, riskFreeRate={}, volatility={}", portfolioReturn, riskFreeRate, volatility);
        try {
            if (volatility == 0) return "波动率不能为0";
            BigDecimal excessReturn = BigDecimal.valueOf(portfolioReturn).subtract(BigDecimal.valueOf(riskFreeRate), MC);
            BigDecimal vol = BigDecimal.valueOf(volatility);
            BigDecimal sharpe = excessReturn.divide(vol, MC);

            String level;
            double s = sharpe.doubleValue();
            if (s < 0) level = "（风险调整后收益为负，不推荐）";
            else if (s < 1) level = "（一般）";
            else if (s < 2) level = "（良好）";
            else if (s < 3) level = "（优秀）";
            else level = "（极佳）";

            String output = String.format(
                    "夏普比率：\n组合收益率：%.2f%%\n无风险收益率：%.2f%%\n波动率：%.2f%%\n\n夏普比率：%.2f %s\n含义：每承担1%%的波动风险，获得%.2f%%的超额收益",
                    portfolioReturn * 100, riskFreeRate * 100, volatility * 100,
                    sharpe.setScale(2, RoundingMode.HALF_UP), level,
                    sharpe.setScale(2, RoundingMode.HALF_UP));
            log.info("[FinancialCalcTool] sharpeRatio 出参: {}", output);
            return output;
        } catch (Exception e) {
            log.error("[FinancialCalcTool] sharpeRatio 异常: {}", e.getMessage(), e);
            return "夏普比率计算失败: " + e.getMessage();
        }
    }

    @Tool(description = "最大回撤计算。当用户问'这个投资最大跌了多少'、'最大回撤是多少'时调用。输入净值或价格序列。")
    public String maxDrawdown(
            @ToolParam(description = "净值或价格序列，用逗号分隔，如'1.0,1.2,0.9,1.1,0.8,1.0'") String navSeries) {
        log.info("[FinancialCalcTool] maxDrawdown 入参: navSeries={}", navSeries);
        try {
            String[] parts = navSeries.split(",");
            double[] nav = new double[parts.length];
            for (int i = 0; i < parts.length; i++) {
                nav[i] = Double.parseDouble(parts[i].trim());
            }

            double peak = nav[0];
            double maxDrawdownVal = 0;
            int peakIdx = 0;
            int troughIdx = 0;
            int drawdownPeakIdx = 0;
            int drawdownTroughIdx = 0;

            for (int i = 1; i < nav.length; i++) {
                if (nav[i] > peak) {
                    peak = nav[i];
                    peakIdx = i;
                }
                double drawdown = (peak - nav[i]) / peak;
                if (drawdown > maxDrawdownVal) {
                    maxDrawdownVal = drawdown;
                    drawdownPeakIdx = peakIdx;
                    drawdownTroughIdx = i;
                }
            }

            String output = String.format(
                    "最大回撤：\n序列长度：%d个数据点\n起始净值：%.4f\n最高净值：%.4f（第%d个点）\n最低净值：%.4f（第%d个点）\n\n最大回撤：%.2f%%\n含义：从最高点到最低点下跌了%.2f%%",
                    nav.length, nav[0], nav[drawdownPeakIdx], drawdownPeakIdx + 1,
                    nav[drawdownTroughIdx], drawdownTroughIdx + 1,
                    maxDrawdownVal * 100, maxDrawdownVal * 100);
            log.info("[FinancialCalcTool] maxDrawdown 出参: {}", output);
            return output;
        } catch (Exception e) {
            log.error("[FinancialCalcTool] maxDrawdown 异常: {}", e.getMessage(), e);
            return "最大回撤计算失败: " + e.getMessage();
        }
    }

    // ==================== H. 补充实用方法 ====================

    @Tool(description = "复合年增长率（CAGR）计算。当用户问'这笔投资平均每年赚多少'、'年化复合增长率'时调用。")
    public String cagr(
            @ToolParam(description = "期初价值（元）") double beginValue,
            @ToolParam(description = "期末价值（元）") double endValue,
            @ToolParam(description = "投资年数") int years) {
        log.info("[FinancialCalcTool] cagr 入参: beginValue={}, endValue={}, years={}", beginValue, endValue, years);
        try {
            if (beginValue <= 0) return "期初价值必须大于0";
            if (years <= 0) return "投资年数必须大于0";
            if (endValue < 0) return "期末价值不能为负";

            BigDecimal bv = BigDecimal.valueOf(beginValue);
            BigDecimal ev = BigDecimal.valueOf(endValue);
            double ratio = endValue / beginValue;
            double cagrVal = Math.pow(ratio, 1.0 / years) - 1;

            String output = String.format(
                    "复合年增长率（CAGR）：\n期初价值：%.2f元\n期末价值：%.2f元\n投资年数：%d年\n总收益：%.2f%%\n\nCAGR = %.2f%%\n含义：平均每年复合增长%.2f%%",
                    beginValue, endValue, years,
                    (endValue / beginValue - 1) * 100,
                    cagrVal * 100, cagrVal * 100);
            log.info("[FinancialCalcTool] cagr 出参: {}", output);
            return output;
        } catch (Exception e) {
            log.error("[FinancialCalcTool] cagr 异常: {}", e.getMessage(), e);
            return "CAGR计算失败: " + e.getMessage();
        }
    }

    @Tool(description = "总收益率计算（含分红）。当用户问'我这笔投资总共赚了多少'时调用，考虑买入价、卖出价和期间分红。")
    public String totalReturn(
            @ToolParam(description = "买入价（元）") double buyPrice,
            @ToolParam(description = "卖出价（元）") double sellPrice,
            @ToolParam(description = "期间累计分红（元），没有分红填0") double dividends) {
        log.info("[FinancialCalcTool] totalReturn 入参: buyPrice={}, sellPrice={}, dividends={}", buyPrice, sellPrice, dividends);
        try {
            if (buyPrice <= 0) return "买入价必须大于0";
            BigDecimal bp = BigDecimal.valueOf(buyPrice);
            BigDecimal sp = BigDecimal.valueOf(sellPrice);
            BigDecimal div = BigDecimal.valueOf(dividends);
            BigDecimal totalGain = sp.add(div, MC).subtract(bp, MC);
            BigDecimal returnRate = totalGain.divide(bp, MC).multiply(HUNDRED, MC);

            String output = String.format(
                    "总收益率：\n买入价：%.2f元\n卖出价：%.2f元\n累计分红：%.2f元\n\n总收益：%.2f元\n总收益率：%.2f%%",
                    buyPrice, sellPrice, dividends,
                    totalGain.setScale(2, RoundingMode.HALF_UP),
                    returnRate.setScale(2, RoundingMode.HALF_UP));
            log.info("[FinancialCalcTool] totalReturn 出参: {}", output);
            return output;
        } catch (Exception e) {
            log.error("[FinancialCalcTool] totalReturn 异常: {}", e.getMessage(), e);
            return "总收益率计算失败: " + e.getMessage();
        }
    }

    @Tool(description = "通胀调整后实际购买力。当用户问'N年后的X万元相当于现在多少钱'、'考虑通胀后实际收益是多少'时调用。")
    public String inflationAdjusted(
            @ToolParam(description = "当前金额（元）") double amount,
            @ToolParam(description = "年通胀率（如0.03表示3%）") double inflationRate,
            @ToolParam(description = "年数") int years) {
        log.info("[FinancialCalcTool] inflationAdjusted 入参: amount={}, inflationRate={}, years={}", amount, inflationRate, years);
        try {
            BigDecimal a = BigDecimal.valueOf(amount);
            BigDecimal r = BigDecimal.valueOf(inflationRate);
            BigDecimal oneMinusR = BigDecimal.ONE.subtract(r, MC);
            BigDecimal adjusted = a.multiply(oneMinusR.pow(years, MC), MC);
            BigDecimal lostPurchasingPower = a.subtract(adjusted, MC);

            String output = String.format(
                    "通胀调整：\n当前金额：%.2f元\n年通胀率：%.2f%%\n年数：%d年\n\n%d年后的实际购买力：%.2f元\n购买力缩水：%.2f元（%.1f%%）\n含义：%.0f年后的%.2f元只能买到今天%.2f元的东西",
                    amount, inflationRate * 100, years,
                    years, adjusted.setScale(2, RoundingMode.HALF_UP),
                    lostPurchasingPower.setScale(2, RoundingMode.HALF_UP),
                    lostPurchasingPower.divide(a, MC).multiply(HUNDRED).setScale(1, RoundingMode.HALF_UP),
                    (double) years, amount, adjusted.setScale(2, RoundingMode.HALF_UP));
            log.info("[FinancialCalcTool] inflationAdjusted 出参: {}", output);
            return output;
        } catch (Exception e) {
            log.error("[FinancialCalcTool] inflationAdjusted 异常: {}", e.getMessage(), e);
            return "通胀调整计算失败: " + e.getMessage();
        }
    }
}
