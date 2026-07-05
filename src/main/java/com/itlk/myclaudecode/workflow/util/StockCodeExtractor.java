package com.itlk.myclaudecode.workflow.util;

import com.itlk.myclaudecode.tool.astock.AStockUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从自然语言查询中提取股票代码
 */
public class StockCodeExtractor {

    private static final Pattern CODE_PATTERN = Pattern.compile("\\b(\\d{6})\\b");

    private StockCodeExtractor() {
    }

    /**
     * 从查询文本中提取所有6位股票代码
     * 支持纯数字代码（如 600519）和带前缀的代码（如 sh600519）
     */
    public static Set<String> extract(String query) {
        Set<String> codes = new HashSet<>();
        if (query == null || query.isBlank()) {
            return codes;
        }

        // 先尝试提取带前缀的代码（sh600519, sz000858 等）
        String lower = query.toLowerCase();
        Pattern prefixPattern = Pattern.compile("(sh|sz|bj)(\\d{6})");
        Matcher prefixMatcher = prefixPattern.matcher(lower);
        while (prefixMatcher.find()) {
            String code = prefixMatcher.group(2);
            try {
                codes.add(AStockUtils.normalizeCode(code));
            } catch (IllegalArgumentException ignored) {
            }
        }

        // 再提取纯数字代码
        Matcher matcher = CODE_PATTERN.matcher(query);
        while (matcher.find()) {
            String code = matcher.group(1);
            try {
                codes.add(AStockUtils.normalizeCode(code));
            } catch (IllegalArgumentException ignored) {
            }
        }

        return codes;
    }
}
