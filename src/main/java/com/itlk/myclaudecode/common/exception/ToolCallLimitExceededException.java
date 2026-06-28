package com.itlk.myclaudecode.common.exception;

public class ToolCallLimitExceededException extends RuntimeException {

    public ToolCallLimitExceededException(int maxIterations) {
        super("工具调用次数已达上限（" + maxIterations + " 轮），请尝试简化问题或分步提问");
    }
}
