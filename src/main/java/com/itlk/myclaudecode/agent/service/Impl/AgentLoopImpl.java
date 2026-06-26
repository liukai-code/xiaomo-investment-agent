package com.itlk.myclaudecode.agent.service.Impl;

import com.itlk.myclaudecode.agent.Entity.*;
import com.itlk.myclaudecode.agent.repository.ChatMessageRepository;
import com.itlk.myclaudecode.agent.repository.ConversationRepository;
import com.itlk.myclaudecode.agent.repository.UserRepository;
import com.itlk.myclaudecode.agent.service.AgentLoop;
import com.itlk.myclaudecode.agent.service.ChatHistoryCacheService;
import com.itlk.myclaudecode.tool.FileListTool;
import com.itlk.myclaudecode.tool.FileReadTool;
import com.itlk.myclaudecode.tool.FileWriteTool;
import com.itlk.myclaudecode.tool.FinancialDataTool;
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
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class AgentLoopImpl implements AgentLoop {

    private static final int MAX_CONTEXT_MESSAGES = 50;

    private final ChatModel chatModel;
    private final String systemPrompt;
    private final ChatClient chatClient;

    @Resource
    private ConversationRepository conversationRepository;

    @Resource
    private ChatMessageRepository chatMessageRepository;

    @Resource
    private UserRepository userRepository;

    @Resource
    private ChatHistoryCacheService cacheService;

    public AgentLoopImpl(ChatModel chatModel,
                         FileReadTool fileReadTool,
                         FileWriteTool fileWriteTool,
                         FileListTool fileListTool,
                         FinancialDataTool financialDataTool,
                         ToolCallbackProvider toolCallbackProvider,
                         @Value("${system-default-prompt}") String systemPrompt) {
        this.chatModel = chatModel;
        this.systemPrompt = systemPrompt;

        ChatClient.Builder builder = ChatClient.builder(chatModel)
                .defaultTools(fileReadTool, fileWriteTool, fileListTool, financialDataTool);

        if (toolCallbackProvider != null) {
            builder.defaultToolCallbacks(toolCallbackProvider);
            log.info("已注册 MCP 工具: {}", (Object) toolCallbackProvider.getToolCallbacks());
        }

        this.chatClient = builder.build();
    }

    @Override
    @Transactional
    public Conversation createConversation(Long userId, String title) {
        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setTitle(title);
        Conversation saved = conversationRepository.save(conversation);
        cacheService.evictConversationList(userId);
        return saved;
    }

    @Override
    public List<Conversation> listConversations(Long userId) {
        List<Conversation> cached = cacheService.getCachedConversations(userId);
        if (cached != null) {
            return cached;
        }
        List<Conversation> conversations = conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        cacheService.cacheConversations(userId, conversations);
        return conversations;
    }

    @Override
    public List<ChatMessage> getHistory(Long userId, Long conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("会话不存在: " + conversationId));
        checkOwnership(conversation, userId);

        List<ChatMessage> cached = cacheService.getCachedMessages(conversationId);
        if (cached != null) {
            return cached;
        }
        List<ChatMessage> messages = chatMessageRepository.findByConversationIdOrderByIdAsc(conversationId);
        cacheService.cacheMessages(conversationId, messages);
        return messages;
    }

    @Override
    @Transactional
    public String chat(Long userId, Long conversationId, String message) {
        Conversation conversation = getOrCreateConversation(userId, conversationId);

        saveMessage(conversation, MessageRole.USER, message, null, null);

        List<Message> context = buildContext(conversation.getId(), userId);

        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .thinking(AnthropicApi.ThinkingType.DISABLED, null)
                .build();

        String response = chatClient.prompt()
                .messages(context.toArray(new Message[0]))
                .options(options)
                .call()
                .content();

        saveMessage(conversation, MessageRole.ASSISTANT, response, null, null);

        return response;
    }

    @Override
    @Transactional
    public Flux<String> chatStream(Long userId, Long conversationId, String message) {
        Conversation conversation = getOrCreateConversation(userId, conversationId);

        saveMessage(conversation, MessageRole.USER, message, null, null);

        List<Message> context = buildContext(conversation.getId(), userId);
        Long convId = conversation.getId();

        StringBuilder accumulated = new StringBuilder();

        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .thinking(AnthropicApi.ThinkingType.DISABLED, null)
                .build();

        return chatClient.prompt()
                .messages(context.toArray(new Message[0]))
                .options(options)
                .stream()
                .content()
                .doOnNext(accumulated::append)
                .doOnComplete(() -> {
                    String fullResponse = accumulated.toString();
                    saveAssistantMessage(convId, fullResponse);
                })
                .timeout(Duration.ofSeconds(120))
                .onErrorResume(e -> {
                    log.error("流式请求异常: {}", e.getMessage());
                    String partial = accumulated.toString();
                    if (!partial.isEmpty()) {
                        saveAssistantMessage(convId, partial);
                    }
                    return Flux.just("\n\n[服务端响应超时，请重试]");
                });
    }

    private Conversation getOrCreateConversation(Long userId, Long conversationId) {
        if (conversationId != null) {
            Conversation conversation = conversationRepository.findById(conversationId)
                    .orElseThrow(() -> new RuntimeException("会话不存在: " + conversationId));
            checkOwnership(conversation, userId);
            return conversation;
        }
        return createConversation(userId, "新对话");
    }

    private void checkOwnership(Conversation conversation, Long userId) {
        if (conversation.getUserId() != null && !conversation.getUserId().equals(userId)) {
            throw new RuntimeException("无权访问该会话");
        }
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

    private void saveMessage(Conversation conversation, MessageRole role, String content,
                             String toolName, String toolCallId) {
        ChatMessage msg = new ChatMessage();
        msg.setConversation(conversation);
        msg.setRole(role);
        msg.setContent(content);
        msg.setToolName(toolName);
        msg.setToolCallId(toolCallId);
        chatMessageRepository.save(msg);
        cacheService.evictMessageCache(conversation.getId());
    }

    @Transactional
    public void saveAssistantMessage(Long conversationId, String content) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("会话不存在: " + conversationId));
        saveMessage(conversation, MessageRole.ASSISTANT, content, null, null);
    }

    @Override
    @Transactional
    public String generateTitle(Long userId, Long conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("会话不存在: " + conversationId));
        checkOwnership(conversation, userId);

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
                "4）严禁超过15个字。\n\n");
        for (ChatMessage msg : messages) {
            if (msg.getRole() == MessageRole.USER) {
                prompt.append("用户：").append(msg.getContent()).append("\n");
            } else if (msg.getRole() == MessageRole.ASSISTANT) {
                prompt.append("助手：").append(msg.getContent(), 0, Math.min(100, msg.getContent().length())).append("\n");
            }
        }

        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .thinking(AnthropicApi.ThinkingType.DISABLED, null)
                .maxTokens(50)
                .build();

        String title = chatClient.prompt()
                .user(prompt.toString())
                .options(options)
                .call()
                .content()
                .trim();

        conversation.setTitle(title);
        conversationRepository.save(conversation);
        cacheService.evictConversationList(userId);

        return title;
    }
}
