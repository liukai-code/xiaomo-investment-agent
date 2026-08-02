package com.xiaomo.agent.agent.service.impl;

import com.xiaomo.agent.agent.config.PlanningProperties;
import com.xiaomo.agent.agent.config.ToolGuardProperties;
import com.xiaomo.agent.agent.intent.AnalysisDepth;
import com.xiaomo.agent.agent.intent.ExecutionMode;
import com.xiaomo.agent.agent.intent.IntentClassifier;
import com.xiaomo.agent.agent.intent.IntentResult;
import com.xiaomo.agent.agent.intent.IntentType;
import com.xiaomo.agent.agent.intent.ToolPolicyMode;
import com.xiaomo.agent.agent.service.AgentLoop;
import com.xiaomo.agent.agent.service.ChatStreamEvent;
import com.xiaomo.agent.common.config.HttpClientService;
import com.xiaomo.agent.common.util.DebugFileLogger;
import com.xiaomo.agent.conversation.entity.*;
import com.xiaomo.agent.conversation.repository.ChatMessageRepository;
import com.xiaomo.agent.conversation.service.*;
import com.xiaomo.agent.memory.service.MemoryExtractionService;
import com.xiaomo.agent.memory.service.MemoryService;
import com.xiaomo.agent.tool.FileListTool;
import com.xiaomo.agent.tool.FileReadTool;
import com.xiaomo.agent.tool.FileWriteTool;
import com.xiaomo.agent.tool.FinancialCalcRouterTool;
import com.xiaomo.agent.tool.FinancialDataRouterTool;
import com.xiaomo.agent.tool.SqlTool;
import com.xiaomo.agent.tool.WebFetchTool;
import com.xiaomo.agent.tool.YangJiBaoTool;
import com.xiaomo.agent.tool.GetAnalysisReportTool;
import com.xiaomo.agent.tool.astock.*;
import com.xiaomo.agent.user.config.UserConfigDTO;
import com.xiaomo.agent.user.config.UserConfigService;
import com.xiaomo.agent.user.dto.UserPreferences;
import com.xiaomo.agent.user.service.UserPreferencesCacheService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import com.xiaomo.agent.tool.config.ToolCallbackContextWrapper;
import com.xiaomo.agent.tool.config.ToolConfigService;
import com.xiaomo.agent.tool.config.ToolEnabledCheckWrapper;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AgentLoopImpl implements AgentLoop {

    private final String systemPrompt;
    private ChatClient chatClient;
    private final ToolGuardProperties toolGuardProperties;
    private final ToolConfigService toolConfigService;
    private final UserConfigService userConfigService;
    private final IntentClassifier intentClassifier;
    private List<ToolCallback> allWrappedCallbacks;
    /** MCP 工具名集合，意图过滤时始终保留 */
    private Set<String> mcpToolNames = Set.of();

    @Resource
    private ConversationService conversationService;

    @Resource
    private ChatMessageService chatMessageService;

    @Resource
    private ChatHistoryCacheService cacheService;

    @Resource
    private ChatMessageRepository chatMessageRepository;

    @Resource
    private MaxToolCallManager maxToolCallManager;

    @Resource
    private UsageRecordService usageRecordService;

    @Resource
    private ToolContextBuilder toolContextBuilder;

    @Resource
    private ToolFilter toolFilter;

    @Resource
    private ContextBuilder contextBuilder;

    @Resource
    private StreamHandler streamHandler;

    @Resource
    private com.xiaomo.agent.user.service.FreeQuotaService freeQuotaService;

    @Resource
    private UserPreferencesCacheService userPreferencesCacheService;

    @Resource
    private MemoryService memoryService;

    @Resource
    private MemoryExtractionService memoryExtractionService;

    @Resource
    private TaskPlanner taskPlanner;

    @Resource
    private PlanningProperties planningProperties;

    /**
     * 根据用户配置解析对应的 ChatClient。
     * 用户有自定义 API Key 配置时，使用用户级 ChatModel；否则使用全局默认。
     */
    private ChatClient resolveChatClient(Long userId) {
        try {
            ChatModel userChatModel = userConfigService.getUserChatModel(userId);
            if (userChatModel != null) {
                log.info("[resolveChatClient] 使用用户自定义配置, userId={}", userId);
                return ChatClient.builder(userChatModel).build();
            }
        } catch (Exception e) {
            log.warn("[resolveChatClient] 创建用户级 ChatClient 失败, 回退全局配置, userId={}: {}", userId, e.getMessage());
        }
        return this.chatClient;
    }

    public AgentLoopImpl(ChatModel chatModel,
                         FileReadTool fileReadTool,
                         FileWriteTool fileWriteTool,
                         FileListTool fileListTool,
                         FinancialCalcRouterTool financialCalcRouterTool,
                         FinancialDataRouterTool financialDataRouterTool,
                         SqlTool sqlTool,
                         WebFetchTool webFetchTool,
                         YangJiBaoTool yangJiBaoTool,
                         GetAnalysisReportTool getAnalysisReportTool,
                         AStockQuoteRouterTool aStockQuoteRouterTool,
                         AStockReportRouterTool aStockReportRouterTool,
                         AStockSignalRouterTool aStockSignalRouterTool,
                         AStockCapitalRouterTool aStockCapitalRouterTool,
                         AStockNewsRouterTool aStockNewsRouterTool,
                         AStockLimitUpRouterTool aStockLimitUpRouterTool,
                         AStockOptionRouterTool aStockOptionRouterTool,
                         AStockSentimentRouterTool aStockSentimentRouterTool,
                         ToolCallbackProvider toolCallbackProvider,
                         ToolGuardProperties toolGuardProperties,
                         ToolConfigService toolConfigService,
                         HttpClientService httpClientService,
                         UserConfigService userConfigService,
                         IntentClassifier intentClassifier,
                         @Value("${system-default-prompt}") String systemPrompt) {
        this.systemPrompt = systemPrompt;
        this.toolGuardProperties = toolGuardProperties;
        this.toolConfigService = toolConfigService;
        this.userConfigService = userConfigService;
        this.intentClassifier = intentClassifier;

        // 将工具对象转为 ToolCallback，再用拦截器包装
        try {
            ToolCallbackProvider provider = MethodToolCallbackProvider.builder()
                    .toolObjects(fileReadTool, fileWriteTool, fileListTool,
                            financialCalcRouterTool, financialDataRouterTool, sqlTool, webFetchTool,
                            yangJiBaoTool, getAnalysisReportTool,
                            aStockQuoteRouterTool, aStockReportRouterTool, aStockSignalRouterTool,
                            aStockCapitalRouterTool, aStockNewsRouterTool, aStockLimitUpRouterTool,
                            aStockOptionRouterTool, aStockSentimentRouterTool)
                    .build();
            ToolCallback[] originalCallbacks = provider.getToolCallbacks();
            List<ToolCallback> wrappedCallbacks = new ArrayList<>();
            List<String> toolNames = new ArrayList<>();

            for (ToolCallback cb : originalCallbacks) {
                wrappedCallbacks.add(new ToolEnabledCheckWrapper(cb, toolConfigService));
                toolNames.add(cb.getToolDefinition().name());
            }

            // 注册 MCP 工具（同样包装），并记录 MCP 工具名（意图过滤时始终保留）
            if (toolCallbackProvider != null) {
                Set<String> mcpNames = new java.util.HashSet<>();
                for (ToolCallback mcp : toolCallbackProvider.getToolCallbacks()) {
                    wrappedCallbacks.add(new ToolEnabledCheckWrapper(mcp, toolConfigService));
                    toolNames.add(mcp.getToolDefinition().name());
                    mcpNames.add(mcp.getToolDefinition().name());
                }
                this.mcpToolNames = Set.copyOf(mcpNames);
                log.info("已注册 MCP 工具: {}", (Object) toolCallbackProvider.getToolCallbacks());
            }

            this.allWrappedCallbacks = wrappedCallbacks;
            this.chatClient = ChatClient.builder(chatModel).build();

            toolConfigService.initDefaults(toolNames);
            log.info("工具拦截器已包装，共 {} 个工具: {}", toolNames.size(), toolNames);
        } catch (Exception e) {
            log.error("工具初始化失败，回退到直接注册", e);
            this.allWrappedCallbacks = List.of();
            ChatClient.Builder builder = ChatClient.builder(chatModel)
                    .defaultTools(fileReadTool, fileWriteTool, fileListTool,
                            financialCalcRouterTool, financialDataRouterTool, sqlTool, webFetchTool,
                            yangJiBaoTool, getAnalysisReportTool,
                            aStockQuoteRouterTool, aStockReportRouterTool, aStockSignalRouterTool,
                            aStockCapitalRouterTool, aStockNewsRouterTool, aStockLimitUpRouterTool,
                            aStockOptionRouterTool, aStockSentimentRouterTool);
            if (toolCallbackProvider != null) {
                builder.defaultToolCallbacks(toolCallbackProvider);
            }
            this.chatClient = builder.build();
        }
    }

    @Override
    @Transactional
    public String chat(Long userId, Long conversationId, String message) {
        log.info("[Chat] 收到请求: userId={}, conversationId={}", userId, conversationId);
        // 检查用户配置：有自有 API Key 则直接放行，否则检查免费额度
        UserConfigDTO userConfig = userConfigService.getConfig(userId);
        boolean hasOwnApiKey = userConfig != null && userConfig.getApiKey() != null && !userConfig.getApiKey().isEmpty();
        if (!hasOwnApiKey) {
            long remaining = freeQuotaService.getRemainingQuota(userId);
            log.info("[Chat] 免费额度检查: userId={}, remaining={}, hasOwnApiKey={}", userId, remaining, hasOwnApiKey);
            if (remaining <= 0) {
                log.warn("[Chat] 免费额度不足, userId={}", userId);
                return "免费体验额度已用完，请在设置中配置自己的 API Key 继续使用";
            }
        }

        Conversation conversation = getOrCreateConversation(userId, conversationId);
        maxToolCallManager.reset();

        chatMessageService.saveMessage(conversation, MessageRole.USER, message, null, null);

        // 读取用户偏好（Redis 缓存）
        UserPreferences prefs = userPreferencesCacheService.getPreferences(userId);
        double temperature = prefs != null ? prefs.temperature() : 0.7;
        int maxTokens = prefs != null ? prefs.maxTokens() : 4096;
        int contextWindow = prefs != null ? prefs.contextWindow() : 50;
        boolean memoryEnabled = prefs == null || prefs.memoryEnabled();

        // 用户主动记忆检测（"记住XXX"），记忆关闭时仍保留主动记忆
        MemoryService.DetectResult detectResult = memoryService.detectExplicitMemory(message);
        if (detectResult.detected()) {
            memoryService.addUserMemory(userId, detectResult.content(),
                    detectResult.category(), conversation.getId());
        }

        // 意图分类
        IntentResult intentResult = intentClassifier.classify(message);
        IntentResult.ResolvedTarget target = intentResult.target();
        log.info("[Chat] 意图分类: intent={}, confidence={}, target={}",
                intentResult.intent(), intentResult.confidence(),
                target != null ? target.code() : "null");
        DebugFileLogger.logBuildContext("CHAT_SYNC",
                "message=\"" + message + "\" | intent=" + intentResult.intent()
                        + " | target=" + (target != null ? target.code() + "(" + target.name() + ")" : "null"));

        // 如果意图分类器未启用，回退到原有逻辑
        if (intentResult.confidence() == 0 && intentResult.policy().mode() == ToolPolicyMode.PLANNER_MANAGED) {
            target = ContextBuilder.toResolvedTarget(contextBuilder.resolveStockFromMessage(message));
        }

        // 自主任务规划
        PlanContext planContext = maybePlan(message, intentResult.intent(), intentResult.depth(),
                intentResult.executionMode(), target, allWrappedCallbacks);
        Scratchpad scratchpad = planContext != null ? new Scratchpad(planningProperties.scratchpadMaxLength()) : null;

        List<Message> context = contextBuilder.buildContext(conversation.getId(), userId, target, intentResult.intent(), contextWindow, planContext);
        Long convId = conversation.getId();

        // 构建工具上下文
        Map<String, Object> toolCtx = toolContextBuilder.build(userId, convId, target, null, planContext, scratchpad);

        // 过滤工具
        Set<String> intentToolWhitelist = intentResult.policy().toWhitelist();
        List<ToolCallback> enabledTools = toolFilter.filter(allWrappedCallbacks, mcpToolNames, intentToolWhitelist, target);

        // 构建选项
        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .thinking(AnthropicApi.ThinkingType.DISABLED, null)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .toolContext(toolCtx)
                .build();

        try {
            ChatClient activeChatClient = resolveChatClient(userId);
            ChatResponse chatResponse = activeChatClient.prompt()
                    .messages(context.toArray(new Message[0]))
                    .toolCallbacks(enabledTools.toArray(new ToolCallback[0]))
                    .options(options)
                    .call()
                    .chatResponse();

            String response = chatResponse.getResult().getOutput().getText();
            String sanitized = streamHandler.sanitizeOutput(response);
            chatMessageService.saveMessage(conversation, MessageRole.ASSISTANT, sanitized, null, null);

            // 异步触发记忆提取（画像 + 对话摘要压缩），记忆关闭时跳过
            if (memoryEnabled) {
                memoryExtractionService.extractMemoriesAsync(userId, conversation.getId(), null);
            }

            // Record token usage
            try {
                Usage usage = chatResponse.getMetadata() != null ? chatResponse.getMetadata().getUsage() : null;
                AtomicInteger toolCounter = (AtomicInteger) toolCtx.get(MaxToolCallManager.TOOL_CALL_COUNTER_KEY);
                int toolCalls = toolCounter != null ? toolCounter.get() : 0;
                Long inputTokens = usage != null && usage.getPromptTokens() != null ? usage.getPromptTokens().longValue() : UsageRecordService.estimateInputTokens(context);
                Long outputTokens = usage != null && usage.getCompletionTokens() != null ? usage.getCompletionTokens().longValue() : null;
                usageRecordService.record(userId, conversation.getId(), inputTokens, outputTokens, toolCalls);
                log.info("[Chat] usage recorded: input={}, output={}, tools={}, hasOwnApiKey={}", inputTokens, outputTokens, toolCalls, hasOwnApiKey);
                // 免费额度扣减
                if (!hasOwnApiKey) {
                    long consumed = (inputTokens != null ? inputTokens : 0L) + (outputTokens != null ? outputTokens : 0L);
                    freeQuotaService.deduct(userId, consumed);
                }
            } catch (Exception e) {
                log.warn("记录token用量失败: {}", e.getMessage());
            }

            return sanitized;
        } finally {
            // userId 已通过 ToolContext 传递，无需清理 ConcurrentHashMap
        }
    }

    @Override
    @Transactional
    public Flux<ServerSentEvent<String>> chatStream(Long userId, Long conversationId, String message) {
        log.info("[ChatStream] 收到请求: userId={}, conversationId={}", userId, conversationId);
        // 检查用户配置：有自有 API Key 则直接放行，否则检查免费额度
        UserConfigDTO userConfig = userConfigService.getConfig(userId);
        boolean hasOwnApiKey = userConfig != null && userConfig.getApiKey() != null && !userConfig.getApiKey().isEmpty();
        if (!hasOwnApiKey) {
            long remaining = freeQuotaService.getRemainingQuota(userId);
            log.info("[ChatStream] 免费额度检查: userId={}, remaining={}, hasOwnApiKey={}", userId, remaining, hasOwnApiKey);
            if (remaining <= 0) {
                log.warn("[ChatStream] 免费额度不足, userId={}, remaining={}", userId, remaining);
                return Flux.just(ServerSentEvent.<String>builder()
                        .event("content")
                        .data("免费体验额度已用完，请在设置中配置自己的 API Key 继续使用")
                        .build());
            }
        }

        Conversation conversation = getOrCreateConversation(userId, conversationId);
        maxToolCallManager.reset();

        chatMessageService.saveMessage(conversation, MessageRole.USER, message, null, null);

        // 读取用户偏好（Redis 缓存）
        UserPreferences prefs = userPreferencesCacheService.getPreferences(userId);
        double temperature = prefs != null ? prefs.temperature() : 0.7;
        int maxTokens = prefs != null ? prefs.maxTokens() : 4096;
        int contextWindow = prefs != null ? prefs.contextWindow() : 50;
        boolean memoryEnabled = prefs == null || prefs.memoryEnabled();

        // 用户主动记忆检测（"记住XXX"），记忆关闭时仍保留主动记忆
        MemoryService.DetectResult detectResult = memoryService.detectExplicitMemory(message);
        if (detectResult.detected()) {
            memoryService.addUserMemory(userId, detectResult.content(),
                    detectResult.category(), conversation.getId());
        }

        // 意图分类
        IntentResult intentResult = intentClassifier.classify(message);
        IntentResult.ResolvedTarget target = intentResult.target();
        log.info("[ChatStream] 意图分类: intent={}, confidence={}, target={}",
                intentResult.intent(), intentResult.confidence(),
                target != null ? target.code() : "null");
        DebugFileLogger.logBuildContext("CHAT_STREAM",
                "message=\"" + message + "\" | intent=" + intentResult.intent()
                        + " | target=" + (target != null ? target.code() + "(" + target.name() + ")" : "null"));

        // 如果意图分类器未启用，回退到原有逻辑
        if (intentResult.confidence() == 0 && intentResult.policy().mode() == ToolPolicyMode.PLANNER_MANAGED) {
            target = ContextBuilder.toResolvedTarget(contextBuilder.resolveStockFromMessage(message));
        }

        // 自主任务规划
        PlanContext planContext = maybePlan(message, intentResult.intent(), intentResult.depth(),
                intentResult.executionMode(), target, allWrappedCallbacks);
        Scratchpad scratchpad = planContext != null ? new Scratchpad(planningProperties.scratchpadMaxLength()) : null;

        List<Message> context = contextBuilder.buildContext(conversation.getId(), userId, target, intentResult.intent(), contextWindow, planContext);
        Long convId = conversation.getId();

        // 创建状态事件 Sink
        Sinks.Many<ChatStreamEvent> statusSink = Sinks.many().multicast().onBackpressureBuffer();

        // 发射执行计划事件（前端可视化）
        if (planContext != null) {
            List<ChatStreamEvent.PlanStepDto> planStepDtos = planContext.steps().stream()
                    .map(s -> new ChatStreamEvent.PlanStepDto(s.id(), s.action(), s.tool()))
                    .toList();
            statusSink.tryEmitNext(ChatStreamEvent.plan(planContext.goal(), planStepDtos));
        }

        // 构建工具上下文
        Map<String, Object> toolCtx = toolContextBuilder.build(userId, convId, target, statusSink, planContext, scratchpad);
        toolCtx.put("memoryEnabled", memoryEnabled);

        // 过滤工具
        Set<String> intentToolWhitelist = intentResult.policy().toWhitelist();
        List<ToolCallback> enabledTools = toolFilter.filter(allWrappedCallbacks, mcpToolNames, intentToolWhitelist, target);

        // 构建选项
        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .thinking(AnthropicApi.ThinkingType.DISABLED, null)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .toolContext(toolCtx)
                .build();

        // 委托给 StreamHandler 组装 SSE 流
        ChatClient activeChatClient = resolveChatClient(userId);
        boolean useFreeQuota = !hasOwnApiKey;
        return streamHandler.buildStream(activeChatClient, context, enabledTools, options, convId, userId, toolCtx, statusSink, useFreeQuota);
    }

    @Override
    @Transactional
    public String generateTitle(Long userId, Long conversationId) {
        Conversation conversation = conversationService.getConversationForUser(conversationId, userId);

        if (!"新对话".equals(conversation.getTitle())) {
            return conversation.getTitle();
        }

        List<ChatMessage> messages = chatMessageRepository
                .findRecentByConversationId(conversationId, 4);
        Collections.reverse(messages);

        StringBuilder prompt = new StringBuilder(
                "根据以下对话内容，用不超过15个汉字生成一个简短标题。" +
                "规则：1）直接输出标题，不要解释；2）不要加引号；" +
                "3）如果用户没有提出明确问题，用用户的原始消息作为标题（截取前15字）；" +
                "4）严禁超过15个字；" +
                "5）禁止输出任何XML标签、尖括号<>、或工具调用格式；6）只输出纯文本标题。\n\n");
        for (ChatMessage msg : messages) {
            if (msg.getRole() == MessageRole.USER) {
                prompt.append("用户：").append(msg.getContent()).append("\n");
            } else if (msg.getRole() == MessageRole.ASSISTANT) {
                String cleanContent = contextBuilder.stripXmlTags(msg.getContent());
                if (!cleanContent.isEmpty()) {
                    prompt.append("助手：")
                          .append(cleanContent, 0, Math.min(100, cleanContent.length()))
                          .append("\n");
                }
            }
        }

        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .thinking(AnthropicApi.ThinkingType.DISABLED, null)
                .maxTokens(50)
                .build();

        String rawTitle = chatClient.prompt()
                .user(prompt.toString())
                .options(options)
                .call()
                .content();

        String title = contextBuilder.stripXmlTags(rawTitle);
        if (title.length() > 15) {
            title = title.substring(0, 15);
        }
        if (title.isEmpty()) {
            for (ChatMessage msg : messages) {
                if (msg.getRole() == MessageRole.USER && msg.getContent() != null) {
                    title = msg.getContent().length() > 15
                            ? msg.getContent().substring(0, 15)
                            : msg.getContent();
                    break;
                }
            }
        }
        if (title.isEmpty()) {
            title = "新对话";
        }

        conversation.setTitle(title.trim());
        cacheService.evictConversationList(userId);

        return title.trim();
    }

    private Conversation getOrCreateConversation(Long userId, Long conversationId) {
        if (conversationId != null) {
            return conversationService.getConversationForUser(conversationId, userId);
        }
        return conversationService.createConversation(userId, "新对话");
    }

    /**
     * 根据执行模式决定是否生成执行计划。
     * PLANNING 模式调用 TaskPlanner 生成计划；DIRECT / PARALLEL 模式直接返回 null。
     */
    private PlanContext maybePlan(String message, IntentType intent, AnalysisDepth depth,
                                  ExecutionMode executionMode,
                                  IntentResult.ResolvedTarget target,
                                  List<ToolCallback> allWrappedCallbacks) {
        if (executionMode != ExecutionMode.PLANNING) {
            log.info("[Plan] 执行模式={}, 跳过规划", executionMode);
            return null;
        }
        Set<String> availableTools = allWrappedCallbacks.stream()
                .map(cb -> cb.getToolDefinition().name())
                .collect(Collectors.toSet());
        PlanContext planContext = taskPlanner.plan(message, intent, target, availableTools);
        if (planContext != null) {
            log.info("[Plan] 任务规划已启用: goal={}, steps={}", planContext.goal(), planContext.steps().size());
        }
        return planContext;
    }
}
