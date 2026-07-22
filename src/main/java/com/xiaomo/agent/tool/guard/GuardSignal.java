package com.xiaomo.agent.tool.guard;

import com.xiaomo.agent.tool.guard.InfoGainTracker.InfoGainLevel;
import com.xiaomo.agent.tool.guard.RepetitionDetector.RepetitionResult;

public record GuardSignal(
        int currentStep,
        int softLimit,
        int hardLimit,
        int escalationWarning,
        int escalationFinal,
        InfoGainLevel infoGain,
        double infoGainSimilarity,
        RepetitionResult repetition,
        String toolName,
        boolean isFetchTool,
        int fetchCount,
        int maxFetches,
        boolean isDuplicateUrl,
        int consecutiveNoNewInfo,
        boolean overMaxFetches,
        boolean stuckNoNewInfo
) {

    public enum SignalLevel {
        NONE,       // no signal needed
        ADVISORY,   // soft limit or minor issues - suggest stopping
        WARNING,    // escalation - strongly suggest stopping
        CRITICAL,   // final warning - must stop, returnDirect=true
        FORCE       // hard limit - force stop, returnDirect=true
    }

    public SignalLevel getLevel() {
        if (currentStep >= hardLimit) return SignalLevel.FORCE;
        if (overMaxFetches && isFetchTool) return SignalLevel.FORCE;
        if (currentStep >= escalationFinal) return SignalLevel.CRITICAL;
        if (currentStep >= escalationWarning
                || infoGain == InfoGainLevel.LOW
                || repetition == RepetitionResult.STUCK_IDENTICAL) return SignalLevel.WARNING;
        if (currentStep >= softLimit
                || repetition == RepetitionResult.STUCK_SIMILAR
                || isDuplicateUrl || stuckNoNewInfo) return SignalLevel.ADVISORY;
        return SignalLevel.NONE;
    }

    public boolean shouldInject() {
        return getLevel() != SignalLevel.NONE;
    }

    public boolean isHardLimit() {
        SignalLevel level = getLevel();
        return level == SignalLevel.FORCE || level == SignalLevel.CRITICAL;
    }

    public String format() {
        SignalLevel level = getLevel();
        if (level == SignalLevel.NONE) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n[GUARD: ").append(level.name()).append("]\n");
        sb.append("Action: ").append(suggestAction(level)).append("\n");
        sb.append("Context: step=").append(currentStep).append("/").append(hardLimit);
        if (infoGain == InfoGainLevel.LOW) {
            sb.append(", info=low(").append(String.format("%.2f", infoGainSimilarity)).append(")");
        }
        if (repetition != RepetitionResult.NONE) {
            sb.append(", repeat=").append(repetition.name().toLowerCase());
        }
        if (isFetchTool) {
            sb.append(", fetch=").append(fetchCount).append("/").append(maxFetches);
        }
        sb.append("\n[/GUARD]");
        return sb.toString();
    }

    private String suggestAction(SignalLevel level) {
        switch (level) {
            case FORCE:
                if (overMaxFetches) {
                    return "已达到最大抓取次数(" + maxFetches + "次)。你已经通过工具获取了足够的数据，请立即基于工具返回的行情、研报、资金面等数据生成分析报告。禁止输出与分析标的无关的内容。";
                }
                return "已达到硬上限。请基于已获取的工具数据生成分析报告，禁止输出与分析标的无关的内容。";
            case CRITICAL:
                return "即将达到硬上限，请停止工具调用，基于已获取的数据完成分析报告。";
            case WARNING:
                if (repetition == RepetitionResult.STUCK_IDENTICAL) {
                    return "检测到重复调用相同工具和参数，请换一种方式或基于已有结果回答。";
                }
                if (infoGain == InfoGainLevel.LOW) {
                    return "最近几轮工具调用未产生新信息，建议基于已有数据回答，或调用不同工具获取新维度信息。";
                }
                return "请评估是否已获得足够信息来回答用户。";
            case ADVISORY:
                if (isDuplicateUrl) return "该URL已经被抓取过，请勿重复抓取。";
                if (stuckNoNewInfo) return "连续多轮抓取未获得新信息，建议基于已有内容回答。";
                if (repetition == RepetitionResult.STUCK_SIMILAR) return "检测到连续相似调用，请评估是否需要调整策略。";
                return "请评估是否已获得足够信息。";
            default:
                return "";
        }
    }
}
