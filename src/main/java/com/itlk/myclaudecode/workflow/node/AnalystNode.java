package com.itlk.myclaudecode.workflow.node;

import com.itlk.myclaudecode.agent.config.ToolGuardProperties;
import com.itlk.myclaudecode.agent.service.impl.MaxToolCallManager;
import com.itlk.myclaudecode.tool.guard.FetchSessionTracker;
import com.itlk.myclaudecode.tool.guard.InfoGainTracker;
import com.itlk.myclaudecode.tool.guard.RepetitionDetector;
import com.itlk.myclaudecode.tool.guard.SearchSessionTracker;
import com.itlk.myclaudecode.workflow.engine.WorkflowNode;
import com.itlk.myclaudecode.workflow.event.WorkflowEvent;
import com.itlk.myclaudecode.workflow.state.AgentReport;
import com.itlk.myclaudecode.workflow.state.WorkflowState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class AnalystNode implements WorkflowNode {

    private final ChatModel chatModel;
    private final String roleName;
    private final String systemPrompt;
    private final List<ToolCallback> toolCallbacks;
    private final ToolGuardProperties guardProperties;

    public AnalystNode(ChatModel chatModel, String roleName,
                       String systemPrompt, List<ToolCallback> toolCallbacks,
                       ToolGuardProperties guardProperties) {
        this.chatModel = chatModel;
        this.roleName = roleName;
        this.systemPrompt = systemPrompt;
        this.toolCallbacks = toolCallbacks;
        this.guardProperties = guardProperties;
    }

    @Override
    public String name() {
        return roleName;
    }

    @Override
    public Flux<WorkflowEvent> execute(WorkflowState state, Sinks.Many<WorkflowEvent> sink) {
        log.info("[{}] 开始执行数据采集", roleName);

        ChatClient agentClient = ChatClient.builder(chatModel)
                .defaultToolCallbacks(toolCallbacks.toArray(new ToolCallback[0]))
                .defaultSystem(systemPrompt)
                .build();

        String userPrompt = "请对以下标的进行深度分析：\n\n" + state.getOriginalQuery()
                + "\n\n请使用可用工具获取数据，产出一份结构化的分析报告。报告需要包含具体的数据和分析结论。";

        sink.tryEmitNext(WorkflowEvent.agentStart(roleName));

        StringBuilder report = new StringBuilder();

        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .thinking(AnthropicApi.ThinkingType.DISABLED, null)
                .temperature(guardProperties != null ? 0.4 : 0.4)
                .maxTokens(guardProperties != null ? 8192 : 8192)
                .toolContext(Map.of(
                        MaxToolCallManager.TOOL_CALL_COUNTER_KEY, new AtomicInteger(0),
                        MaxToolCallManager.INFO_GAIN_TRACKER_KEY,
                                new InfoGainTracker(3, 0.8),
                        MaxToolCallManager.REPETITION_DETECTOR_KEY,
                                new RepetitionDetector(3),
                        MaxToolCallManager.FETCH_SESSION_TRACKER_KEY,
                                new FetchSessionTracker(3, 2),
                        MaxToolCallManager.SEARCH_SESSION_TRACKER_KEY,
                                new SearchSessionTracker(1)
                ))
                .build();

        return agentClient.prompt()
                .user(userPrompt)
                .options(options)
                .stream()
                .content()
                .map(chunk -> {
                    report.append(chunk);
                    return WorkflowEvent.agentChunk(roleName, chunk);
                })
                .doOnComplete(() -> {
                    String fullReport = report.toString();
                    state.getAnalystReports().put(roleName,
                            new AgentReport(roleName, fullReport, Instant.now()));
                    sink.tryEmitNext(WorkflowEvent.agentComplete(roleName, fullReport));
                    log.info("[{}] 数据采集完成，报告长度: {}", roleName, fullReport.length());
                })
                .doOnError(e -> log.error("[{}] 数据采集错误: {}", roleName, e.getMessage()));
    }
}
