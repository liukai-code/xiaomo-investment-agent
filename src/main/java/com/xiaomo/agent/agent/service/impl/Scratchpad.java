package com.xiaomo.agent.agent.service.impl;

import java.util.ArrayList;
import java.util.List;

/**
 * 多步工具调用的中间结果暂存器。
 * 在计划执行模式下，累积每步工具调用的结果摘要，供 LLM 汇总时参考。
 */
public class Scratchpad {

    private final List<StepResult> results = new ArrayList<>();
    private final int maxLength;

    public Scratchpad(int maxLength) {
        this.maxLength = maxLength;
    }

    public void record(int stepId, String toolName, String rawResult) {
        String summary = truncate(rawResult, maxLength);
        results.add(new StepResult(stepId, toolName, summary));
    }

    public boolean isEmpty() {
        return results.isEmpty();
    }

    public int size() {
        return results.size();
    }

    public String format() {
        if (results.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("## 已完成步骤的结果摘要\n");
        for (StepResult r : results) {
            sb.append("[步骤").append(r.stepId).append("] ").append(r.toolName).append(": ").append(r.summary).append("\n");
        }
        return sb.toString();
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "(无数据)";
        String cleaned = text.replaceAll("\\s+", " ").trim();
        if (cleaned.length() <= maxLen) return cleaned;
        return cleaned.substring(0, maxLen) + "...";
    }

    public record StepResult(int stepId, String toolName, String summary) {}
}
