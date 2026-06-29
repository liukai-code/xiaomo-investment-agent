package com.itlk.myclaudecode.workflow.node;

import com.itlk.myclaudecode.workflow.engine.WorkflowNode;
import com.itlk.myclaudecode.workflow.event.WorkflowEvent;
import com.itlk.myclaudecode.workflow.state.WorkflowState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.List;

@Slf4j
public class TraderNode implements WorkflowNode {

    private final ChatModel chatModel;
    private final String roleName;
    private final String systemPrompt;
    private final List<ToolCallback> toolCallbacks;

    public TraderNode(ChatModel chatModel, String roleName,
                      String systemPrompt, List<ToolCallback> toolCallbacks) {
        this.chatModel = chatModel;
        this.roleName = roleName;
        this.systemPrompt = systemPrompt;
        this.toolCallbacks = toolCallbacks;
    }

    @Override
    public String name() {
        return roleName;
    }

    @Override
    public Flux<WorkflowEvent> execute(WorkflowState state, Sinks.Many<WorkflowEvent> sink) {
        log.info("[{}] 开始制定交易方案", roleName);

        ChatClient client = ChatClient.builder(chatModel)
                .defaultToolCallbacks(toolCallbacks.toArray(new ToolCallback[0]))
                .defaultSystem(systemPrompt)
                .build();

        String prompt = buildTraderPrompt(state);
        sink.tryEmitNext(WorkflowEvent.agentStart(roleName));

        StringBuilder proposal = new StringBuilder();

        return client.prompt()
                .user(prompt)
                .options(AnthropicChatOptions.builder()
                        .thinking(AnthropicApi.ThinkingType.DISABLED, null)
                        .temperature(0.4)
                        .maxTokens(4096)
                        .build())
                .stream()
                .content()
                .map(chunk -> {
                    proposal.append(chunk);
                    return WorkflowEvent.agentChunk(roleName, chunk);
                })
                .doOnComplete(() -> {
                    state.setTradingProposal(proposal.toString());
                    sink.tryEmitNext(WorkflowEvent.agentComplete(roleName, proposal.toString()));
                    log.info("[{}] 交易方案制定完成", roleName);
                });
    }

    private String buildTraderPrompt(WorkflowState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 投资计划\n\n").append(state.getInvestmentPlan()).append("\n\n");

        sb.append("## 分析报告\n\n");
        state.getAnalystReports().forEach((name, report) ->
                sb.append("### ").append(name).append("\n").append(report.reportContent()).append("\n\n"));

        sb.append("\n请基于以上投资计划和分析报告，制定具体的交易方案。\n");
        sb.append("包含：建仓/减仓策略、价格区间、仓位比例、止损止盈设置。");
        return sb.toString();
    }
}
