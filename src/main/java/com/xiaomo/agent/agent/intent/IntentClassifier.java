package com.xiaomo.agent.agent.intent;

/**
 * 意图分类器接口。
 * 对用户消息进行意图分类，返回分类结果（含意图类型、置信度、标的、工具白名单）。
 */
public interface IntentClassifier {

    /**
     * 对用户消息进行意图分类
     *
     * @param message 用户原始消息
     * @return 分类结果
     */
    IntentResult classify(String message);
}
