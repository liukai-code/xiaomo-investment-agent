package com.xiaomo.agent.agent.service.impl;

import com.xiaomo.agent.agent.config.ToolGuardProperties;
import com.xiaomo.agent.agent.intent.IntentResult;
import com.xiaomo.agent.agent.service.ChatStreamEvent;
import com.xiaomo.agent.tool.guard.FetchSessionTracker;
import com.xiaomo.agent.tool.guard.InfoGainTracker;
import com.xiaomo.agent.tool.guard.RepetitionDetector;
import com.xiaomo.agent.tool.guard.SearchSessionTracker;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 构建工具调用时传递的上下文 Map（toolCtx），
 * 包括 conversationId、userId、各种 guard tracker 等。
 */
@Component
@Slf4j
public class ToolContextBuilder {

    @Resource
    private ToolGuardProperties toolGuardProperties;

    public Map<String, Object> build(Long userId, Long conversationId,
                                     IntentResult.ResolvedTarget target,
                                     Sinks.Many<ChatStreamEvent> statusSink,
                                     PlanContext planContext,
                                     Scratchpad scratchpad) {
        Map<String, Object> toolCtx = new HashMap<>();
        toolCtx.put("conversationId", conversationId.toString());
        toolCtx.put("userId", userId);
        toolCtx.put(MaxToolCallManager.TOOL_CALL_COUNTER_KEY, new AtomicInteger(0));
        toolCtx.put(MaxToolCallManager.INFO_GAIN_TRACKER_KEY, new InfoGainTracker(toolGuardProperties.infoGainWindow(), toolGuardProperties.infoGainThreshold()));
        toolCtx.put(MaxToolCallManager.REPETITION_DETECTOR_KEY, new RepetitionDetector(toolGuardProperties.repetitionThreshold()));
        toolCtx.put(MaxToolCallManager.FETCH_SESSION_TRACKER_KEY, new FetchSessionTracker(toolGuardProperties.maxFetches(), toolGuardProperties.maxConsecutiveNoNewInfo()));
        toolCtx.put(MaxToolCallManager.SEARCH_SESSION_TRACKER_KEY, new SearchSessionTracker(toolGuardProperties.maxSearchRounds()));
        toolCtx.put(MaxToolCallManager.MAX_FETCHES_KEY, toolGuardProperties.maxFetches());
        toolCtx.put(MaxToolCallManager.DUPLICATE_CACHE_KEY, new LinkedHashMap<String, java.util.List<Message>>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, java.util.List<Message>> eldest) {
                return size() > 50;
            }
        });
        toolCtx.put(MaxToolCallManager.NON_RETRIABLE_CACHE_KEY, new ConcurrentHashMap<String, String>());
        toolCtx.put(MaxToolCallManager.PER_TOOL_CALL_COUNT_KEY, new ConcurrentHashMap<String, AtomicInteger>());

        if (statusSink != null) {
            toolCtx.put(MaxToolCallManager.STATUS_SINK_KEY, statusSink);
        }

        if (planContext != null) {
            toolCtx.put(MaxToolCallManager.PLAN_CONTEXT_KEY, planContext);
        }
        if (scratchpad != null) {
            toolCtx.put(MaxToolCallManager.SCRATCHPAD_KEY, scratchpad);
        }

        if (target != null) {
            toolCtx.put(MaxToolCallManager.ALLOWED_STOCK_CODES_KEY, Set.of(target.code()));
            if (target.name() != null) {
                toolCtx.put(MaxToolCallManager.RESOLVED_STOCK_NAME_KEY, target.name());
            }
            log.info("股票范围守卫已激活: code={}, name={}", target.code(), target.name());
        }

        return toolCtx;
    }
}
