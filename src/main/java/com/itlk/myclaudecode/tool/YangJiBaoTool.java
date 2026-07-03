package com.itlk.myclaudecode.tool;

import com.itlk.myclaudecode.tool.annotation.ToolBehavior;
import com.itlk.myclaudecode.yjb.entity.YjbAccountCollect;
import com.itlk.myclaudecode.yjb.entity.YjbHolding;
import com.itlk.myclaudecode.yjb.service.YjbService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
@Slf4j
public class YangJiBaoTool {

    private final YjbService yjbService;

    public YangJiBaoTool(YjbService yjbService) {
        this.yjbService = yjbService;
    }

    // 由 ToolCallbackContextWrapper 在工具执行前线程设置
    private static final ThreadLocal<Long> CURRENT_USER_ID = new ThreadLocal<>();

    public static void setCurrentUserId(Long userId) {
        CURRENT_USER_ID.set(userId);
    }

    public static void clearCurrentUserId() {
        CURRENT_USER_ID.remove();
    }

    @ToolBehavior(deterministic = false, cacheable = false)
    @Tool(description = "查询当前用户在养基宝的基金持仓列表。返回所有基金的名称、代码、持有市值、盈亏金额、收益率等信息。当用户提到'我的基金'、'我的持仓'、'帮我分析持仓'、'看看我的基金'时调用此工具。")
    public String getMyHoldings() {
        Long userId = CURRENT_USER_ID.get();
        log.info("[YangJiBaoTool] getMyHoldings called, userId={}", userId);
        if (userId == null) {
            return "错误：无法获取用户身份，请确认已登录";
        }

        log.info("[YangJiBaoTool] getMyHoldings: userId={}", userId);
        List<YjbHolding> holdings = yjbService.getHoldings(userId);
        if (holdings.isEmpty()) {
            return "暂无持仓数据。请先打开养基宝面板同步持仓数据。";
        }

        BigDecimal totalMoney = BigDecimal.ZERO;
        BigDecimal totalEarn = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        for (YjbHolding h : holdings) {
            totalMoney = totalMoney.add(nullToZero(h.getMoney()));
            totalEarn = totalEarn.add(nullToZero(h.getHoldEarn()));
            totalCost = totalCost.add(nullToZero(h.getCostMoney()));
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== 我的基金持仓（共 ").append(holdings.size()).append(" 只） ===\n\n");
        sb.append("| 基金名称 | 代码 | 持有市值 | 盈亏 | 收益率 | 类型 |\n");
        sb.append("|----------|------|----------|------|--------|------|\n");

        for (YjbHolding h : holdings) {
            BigDecimal money = nullToZero(h.getMoney());
            BigDecimal earn = nullToZero(h.getHoldEarn());
            BigDecimal cost = nullToZero(h.getCostMoney());
            BigDecimal rate = cost.compareTo(BigDecimal.ZERO) > 0
                    ? earn.divide(cost, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"))
                    : BigDecimal.ZERO;

            sb.append("| ").append(str(h.getShortName()))
              .append(" | ").append(str(h.getCode()))
              .append(" | ").append(formatMoney(money))
              .append(" | ").append(formatMoney(earn))
              .append(" | ").append(rate.setScale(2, RoundingMode.HALF_UP)).append("%")
              .append(" | ").append(str(h.getCategory()))
              .append(" |\n");
        }

        sb.append("\n**汇总：** 总市值 ").append(formatMoney(totalMoney))
          .append("，总盈亏 ").append(formatMoney(totalEarn));
        if (totalCost.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal totalRate = totalEarn.divide(totalCost, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
            sb.append("，整体收益率 ").append(totalRate.setScale(2, RoundingMode.HALF_UP)).append("%");
        }
        sb.append("\n\n数据同步时间：").append(holdings.get(0).getSyncedAt());

        return sb.toString();
    }

    @ToolBehavior(deterministic = false, cacheable = false)
    @Tool(description = "查询当前用户养基宝账户汇总信息，包括总持有成本、今日收益、今日收益率。可配合 getMyHoldings 一起使用来全面分析持仓。")
    public String getMyAccountSummary() {
        Long userId = CURRENT_USER_ID.get();
        if (userId == null) {
            return "错误：无法获取用户身份，请确认已登录";
        }

        log.info("[YangJiBaoTool] getMyAccountSummary: userId={}", userId);
        List<YjbAccountCollect> summaries = yjbService.getAccountSummary(userId);
        if (summaries.isEmpty()) {
            return "暂无账户数据。请先打开养基宝面板同步持仓数据。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== 养基宝账户汇总 ===\n\n");

        for (YjbAccountCollect collect : summaries) {
            sb.append("- **持有成本：** ").append(formatMoney(nullToZero(collect.getHoldCost()))).append("\n");
            sb.append("- **今日收益：** ").append(formatMoney(nullToZero(collect.getTodayIncome()))).append("\n");
            sb.append("- **今日收益率：** ").append(nullToZero(collect.getTodayIncomeRate()).setScale(4, RoundingMode.HALF_UP)).append("%\n");
            sb.append("- 同步时间：").append(collect.getSyncedAt()).append("\n");
        }

        return sb.toString();
    }

    private static BigDecimal nullToZero(BigDecimal val) {
        return val != null ? val : BigDecimal.ZERO;
    }

    private static String str(String val) {
        return val != null ? val : "-";
    }

    private static String formatMoney(BigDecimal val) {
        if (val == null) return "0.00";
        return val.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
