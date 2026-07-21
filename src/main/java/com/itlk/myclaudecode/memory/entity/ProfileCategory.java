package com.itlk.myclaudecode.memory.entity;

/**
 * 用户画像记忆类别
 */
public enum ProfileCategory {

    INVESTMENT_STYLE("投资风格"),
    RISK_PREFERENCE("风险偏好"),
    FOCUS_SECTOR("关注板块"),
    HOLDING_HABIT("持仓习惯"),
    EXPERIENCE_LEVEL("投资经验"),
    GENERAL("其他偏好");

    private final String displayName;

    ProfileCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
