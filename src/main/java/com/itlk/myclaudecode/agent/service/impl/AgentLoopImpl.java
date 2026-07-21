package com.itlk.myclaudecode.agent.service.impl;

import com.itlk.myclaudecode.agent.config.ToolGuardProperties;
import com.itlk.myclaudecode.agent.intent.IntentClassifier;
import com.itlk.myclaudecode.agent.intent.IntentResult;
import com.itlk.myclaudecode.agent.service.AgentLoop;
import com.itlk.myclaudecode.agent.service.ChatStreamEvent;
import com.itlk.myclaudecode.common.config.HttpClientService;
import com.itlk.myclaudecode.common.util.DebugFileLogger;
import com.itlk.myclaudecode.conversation.entity.*;
import com.itlk.myclaudecode.conversation.repository.ChatMessageRepository;
import com.itlk.myclaudecode.conversation.service.*;
import com.itlk.myclaudecode.memory.service.MemoryExtractionService;
import com.itlk.myclaudecode.memory.service.MemoryService;
import com.itlk.myclaudecode.tool.FileListTool;
import com.itlk.myclaudecode.tool.FileReadTool;
import com.itlk.myclaudecode.tool.FileWriteTool;
import com.itlk.myclaudecode.tool.FinancialCalcRouterTool;
import com.itlk.myclaudecode.tool.FinancialDataRouterTool;
import com.itlk.myclaudecode.tool.SqlTool;
import com.itlk.myclaudecode.tool.WebFetchTool;
import com.itlk.myclaudecode.tool.YangJiBaoTool;
import com.itlk.myclaudecode.tool.GetAnalysisReportTool;
import com.itlk.myclaudecode.tool.astock.*;
import com.itlk.myclaudecode.user.config.UserConfigDTO;
import com.itlk.myclaudecode.user.config.UserConfigService;
import com.itlk.myclaudecode.user.entity.User;
import com.itlk.myclaudecode.user.repository.UserRepository;
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
import com.itlk.myclaudecode.tool.config.ToolCallbackContextWrapper;
import com.itlk.myclaudecode.tool.config.ToolConfigService;
import com.itlk.myclaudecode.tool.config.ToolEnabledCheckWrapper;
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
    private com.itlk.myclaudecode.user.service.FreeQuotaService freeQuotaService;

    @Resource
    private UserRepository userRepository;

    @Resource
    private MemoryService memoryService;

    @Resource
    private MemoryExtractionService memoryExtractionService;

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

        // 读取用户偏好
        User user = userRepository.findById(userId).orElse(null);
        double temperature = user != null && user.getTemperature() != null ? user.getTemperature() : 0.7;
        int maxTokens = user != null && user.getMaxTokens() != null ? user.getMaxTokens() : 4096;
        int contextWindow = user != null && user.getContextWindow() != null ? user.getContextWindow() : 50;
        boolean memoryEnabled = user == null || user.getMemoryEnabled() == null || user.getMemoryEnabled();

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
        if (intentResult.confidence() == 0 && intentResult.suggestedTools() == null) {
            target = ContextBuilder.toResolvedTarget(contextBuilder.resolveStockFromMessage(message));
        }

        List<Message> context = contextBuilder.buildContext(conversation.getId(), userId, target, intentResult.intent(), contextWindow);
        Long convId = conversation.getId();

        // 构建工具上下文
        Map<String, Object> toolCtx = toolContextBuilder.build(userId, convId, target, null);

        // 过滤工具
        Set<String> intentToolWhitelist = intentResult.suggestedTools();
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

        // 读取用户偏好
        User user = userRepository.findById(userId).orElse(null);
        double temperature = user != null && user.getTemperature() != null ? user.getTemperature() : 0.7;
        int maxTokens = user != null && user.getMaxTokens() != null ? user.getMaxTokens() : 4096;
        int contextWindow = user != null && user.getContextWindow() != null ? user.getContextWindow() : 50;
        boolean memoryEnabled = user == null || user.getMemoryEnabled() == null || user.getMemoryEnabled();

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
        if (intentResult.confidence() == 0 && intentResult.suggestedTools() == null) {
            target = ContextBuilder.toResolvedTarget(contextBuilder.resolveStockFromMessage(message));
        }

        List<Message> context = contextBuilder.buildContext(conversation.getId(), userId, target, intentResult.intent(), contextWindow);
        Long convId = conversation.getId();

        // 创建状态事件 Sink
        Sinks.Many<ChatStreamEvent> statusSink = Sinks.many().multicast().onBackpressureBuffer();

        // 构建工具上下文
        Map<String, Object> toolCtx = toolContextBuilder.build(userId, convId, target, statusSink);
        toolCtx.put("memoryEnabled", memoryEnabled);

        // 过滤工具
        Set<String> intentToolWhitelist = intentResult.suggestedTools();
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
}
