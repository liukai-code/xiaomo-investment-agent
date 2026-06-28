package com.itlk.myclaudecode.agent.service.impl;

import com.itlk.myclaudecode.agent.config.ToolGuardProperties;
import com.itlk.myclaudecode.agent.service.AgentLoop;
import com.itlk.myclaudecode.conversation.entity.*;
import com.itlk.myclaudecode.conversation.repository.ChatMessageRepository;
import com.itlk.myclaudecode.conversation.service.*;
import com.itlk.myclaudecode.user.entity.User;
import com.itlk.myclaudecode.user.repository.UserRepository;
import com.itlk.myclaudecode.tool.FileListTool;
import com.itlk.myclaudecode.tool.guard.InfoGainTracker;
import com.itlk.myclaudecode.tool.guard.RepetitionDetector;
import com.itlk.myclaudecode.tool.FileReadTool;
import com.itlk.myclaudecode.tool.FileWriteTool;
import com.itlk.myclaudecode.tool.FinancialCalcTool;
import com.itlk.myclaudecode.tool.FinancialDataTool;
import com.itlk.myclaudecode.tool.SqlTool;
import com.itlk.myclaudecode.tool.WebFetchTool;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class AgentLoopImpl implements AgentLoop {

    private static final int MAX_CONTEXT_MESSAGES = 50;

    private final String systemPrompt;
    private final ChatClient chatClient;
    private final ToolGuardProperties toolGuardProperties;

    @Resource
    private UserRepository userRepository;

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

    public AgentLoopImpl(ChatModel chatModel,
                         FileReadTool fileReadTool,
                         FileWriteTool fileWriteTool,
                         FileListTool fileListTool,
                         FinancialCalcTool financialCalcTool,
                         FinancialDataTool financialDataTool,
                         SqlTool sqlTool,
                         WebFetchTool webFetchTool,
                         ToolCallbackProvider toolCallbackProvider,
                         ToolGuardProperties toolGuardProperties,
                         @Value("${system-default-prompt}") String systemPrompt) {
        this.systemPrompt = systemPrompt;
        this.toolGuardProperties = toolGuardProperties;

        ChatClient.Builder builder = ChatClient.builder(chatModel)
                .defaultTools(fileReadTool, fileWriteTool, fileListTool, financialCalcTool, financialDataTool, sqlTool, webFetchTool);

        if (toolCallbackProvider != null) {
            builder.defaultToolCallbacks(toolCallbackProvider);
            log.info("已注册 MCP 工具: {}", (Object) toolCallbackProvider.getToolCallbacks());
        }

        this.chatClient = builder.build();
    }

    @Override
    @Transactional
    public String chat(Long userId, Long conversationId, String message) {
        Conversation conversation = getOrCreateConversation(userId, conversationId);
        maxToolCallManager.reset();

        chatMessageService.saveMessage(conversation, MessageRole.USER, message, null, null);

        List<Message> context = buildContext(conversation.getId(), userId);

        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .thinking(AnthropicApi.ThinkingType.DISABLED, null)
                .temperature(0.7)
                .toolContext(Map.of(
                        MaxToolCallManager.TOOL_CALL_COUNTER_KEY, new AtomicInteger(0),
                        MaxToolCallManager.INFO_GAIN_TRACKER_KEY, new InfoGainTracker(toolGuardProperties.infoGainWindow(), toolGuardProperties.infoGainThreshold()),
                        MaxToolCallManager.REPETITION_DETECTOR_KEY, new RepetitionDetector(toolGuardProperties.repetitionThreshold())))
                .build();

        String response = chatClient.prompt()
                .messages(context.toArray(new Message[0]))
                .options(options)
                .call()
                .content();

        chatMessageService.saveMessage(conversation, MessageRole.ASSISTANT, response, null, null);
        return response;
    }

    @Override
    @Transactional
    public Flux<String> chatStream(Long userId, Long conversationId, String message) {
        Conversation conversation = getOrCreateConversation(userId, conversationId);
        maxToolCallManager.reset();

        chatMessageService.saveMessage(conversation, MessageRole.USER, message, null, null);

        List<Message> context = buildContext(conversation.getId(), userId);
        Long convId = conversation.getId();

        StringBuilder accumulated = new StringBuilder();

        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .thinking(AnthropicApi.ThinkingType.DISABLED, null)
                .temperature(0.7)
                .toolContext(Map.of(
                        MaxToolCallManager.TOOL_CALL_COUNTER_KEY, new AtomicInteger(0),
                        MaxToolCallManager.INFO_GAIN_TRACKER_KEY, new InfoGainTracker(toolGuardProperties.infoGainWindow(), toolGuardProperties.infoGainThreshold()),
                        MaxToolCallManager.REPETITION_DETECTOR_KEY, new RepetitionDetector(toolGuardProperties.repetitionThreshold())))
                .build();

        return chatClient.prompt()
                .messages(context.toArray(new Message[0]))
                .options(options)
                .stream()
                .content()
                .map(delta -> {
                    accumulated.append(delta);
                    return accumulated.toString();
                })
                .doOnComplete(() -> {
                    String fullResponse = accumulated.toString();
                    chatMessageService.saveAssistantMessage(convId, fullResponse);
                })
                .timeout(Duration.ofSeconds(300))
                .onErrorResume(e -> {
                    String errorMsg = "服务端响应超时，请重试";
                    log.error("流式请求异常: {}", e.getMessage());
                    String partial = accumulated.toString();
                    if (!partial.isEmpty()) {
                        chatMessageService.saveAssistantMessage(convId, partial);
                    }
                    return Flux.just("\n\n[" + errorMsg + "]");
                });
    }

    @Override
    @Transactional
    public String generateTitle(Long userId, Long conversationId) {
        Conversation conversation = conversationService.getConversation(conversationId);
        conversationService.checkOwnership(conversation, userId);

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
                String cleanContent = stripXmlTags(msg.getContent());
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

        String title = stripXmlTags(rawTitle);
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
            Conversation conversation = conversationService.getConversation(conversationId);
            conversationService.checkOwnership(conversation, userId);
            return conversation;
        }
        return conversationService.createConversation(userId, "新对话");
    }

    private List<Message> buildContext(Long conversationId, Long userId) {
        List<Message> context = new ArrayList<>();

        String enrichedPrompt = systemPrompt;
        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            enrichedPrompt += "\n\n[用户信息]\n当前用户：" + user.getUsername() + "（ID: " + userId + "）";
        }
        enrichedPrompt += "\n\n[当前时间]\n" + java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss EEEE"));
        context.add(new SystemMessage(enrichedPrompt));

        List<ChatMessage> recentMessages = cacheService.getCachedRecentMessages(conversationId, MAX_CONTEXT_MESSAGES);
        if (recentMessages == null) {
            recentMessages = chatMessageRepository
                    .findRecentByConversationId(conversationId, MAX_CONTEXT_MESSAGES);
            Collections.reverse(recentMessages);
            cacheService.cacheRecentMessages(conversationId, MAX_CONTEXT_MESSAGES, recentMessages);
        }

        for (ChatMessage msg : recentMessages) {
            switch (msg.getRole()) {
                case USER -> context.add(new UserMessage(msg.getContent()));
                case ASSISTANT -> context.add(new AssistantMessage(msg.getContent()));
            }
        }

        return context;
    }

    private String stripXmlTags(String text) {
        if (text == null) return "";
        return text.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
    }
}
