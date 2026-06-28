package com.itlk.myclaudecode.tool.guard;

import com.itlk.myclaudecode.tool.guard.InfoGainTracker.InfoGainLevel;
import com.itlk.myclaudecode.tool.guard.RepetitionDetector.RepetitionResult;

public record GuardSignal(
        int currentStep,
        int softLimit,
        int hardLimit,
        InfoGainLevel infoGain,
        double infoGainSimilarity,
        RepetitionResult repetition,
        String toolName
) {
    public boolean shouldInject() {
        return currentStep >= softLimit
                || infoGain == InfoGainLevel.LOW
                || repetition != RepetitionResult.NONE;
    }

    public boolean isHardLimit() {
        return currentStep >= hardLimit;
    }

    public String format() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n[GUARD_SIGNAL]\n");
        sb.append("step: ").append(currentStep).append("/").append(hardLimit).append("\n");
        sb.append("soft_limit: ").append(softLimit).append("\n");
        sb.append("info_gain: ").append(infoGain.name().toLowerCase());
        if (infoGain == InfoGainLevel.LOW) {
            sb.append(" (similarity=").append(String.format("%.2f", infoGainSimilarity)).append(")");
        }
        sb.append("\n");
        sb.append("repetition: ").append(repetition.name().toLowerCase());
        if (repetition != RepetitionResult.NONE) {
            sb.append(" (tool: ").append(toolName).append(")");
        }
        sb.append("\n");
        sb.append("suggestion: ").append(suggest()).append("\n");
        sb.append("[/GUARD_SIGNAL]");
        return sb.toString();
    }

    private String suggest() {
        if (isHardLimit()) {
            return "已达到硬上限，必须基于已有结果直接回答用户。";
        }
        if (repetition == RepetitionResult.STUCK_IDENTICAL) {
            return "检测到重复调用相同工具和参数，请换一种方式或基于已有结果回答。";
        }
        if (repetition == RepetitionResult.STUCK_SIMILAR) {
            return "检测到连续相似调用，请评估是否需要调整策略。";
        }
        if (infoGain == InfoGainLevel.LOW) {
            return "最近几轮工具调用未产生新信息，建议基于已有数据尝试回答，或调用不同工具获取新维度信息。";
        }
        return "请评估是否已获得足够信息来回答用户。";
    }
}
