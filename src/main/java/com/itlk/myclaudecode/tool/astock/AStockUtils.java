package com.itlk.myclaudecode.tool.astock;

public class AStockUtils {

    private AStockUtils() {
    }

    /**
     * 标准化股票代码：去除前后缀，保留纯6位数字
     * 支持输入：600519, sh600519, 600519.SH, SH600519 等
     */
    public static String normalizeCode(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("股票代码不能为空");
        }
        String code = input.trim().toLowerCase();
        // 去除后缀 .sh .sz .bj
        code = code.replaceAll("\\.(sh|sz|bj)$", "");
        // 去除前缀 sh sz bj
        code = code.replaceAll("^(sh|sz|bj)", "");
        if (!code.matches("^\\d{6}$")) {
            throw new IllegalArgumentException("无效的股票代码: " + input + "，需要6位数字");
        }
        return code;
    }

    /**
     * 根据代码判断市场前缀（东财 secid 格式）
     * 沪市: 1.600519  深市: 0.000858  北交所: 0.830799
     */
    public static String toEastmoneySecId(String code) {
        String normalized = normalizeCode(code);
        if (normalized.startsWith("6") || normalized.startsWith("9")) {
            return "1." + normalized;
        }
        return "0." + normalized;
    }

    /**
     * 根据代码判断市场前缀（腾讯/新浪格式）
     * 沪市: sh600519  深市: sz000858
     */
    public static String toMarketPrefix(String code) {
        String normalized = normalizeCode(code);
        if (normalized.startsWith("6") || normalized.startsWith("9")) {
            return "sh" + normalized;
        }
        return "sz" + normalized;
    }

    /**
     * 格式化金额：元 → 亿元
     */
    public static String formatYi(double amount) {
        return String.format("%.2f亿", amount / 1e8);
    }

    /**
     * 格式化金额：元 → 万元
     */
    public static String formatWan(double amount) {
        return String.format("%.2f万", amount / 1e4);
    }
}
