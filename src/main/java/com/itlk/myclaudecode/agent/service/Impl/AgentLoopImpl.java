package com.itlk.myclaudecode.agent.service.Impl;

import com.itlk.myclaudecode.agent.service.AgentLoop;
import com.itlk.myclaudecode.tool.FileListTool;
import com.itlk.myclaudecode.tool.FileReadTool;
import com.itlk.myclaudecode.tool.FileWriteTool;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Service
public class AgentLoopImpl implements AgentLoop {

    private final ChatModel chatModel;
    private final List<Message> history = new ArrayList<>();
    private final String systemPrompt;
    private final ChatClient chatClient;

    public AgentLoopImpl(ChatModel chatModel,
                         FileReadTool fileReadTool,
                         FileWriteTool fileWriteTool,
                         FileListTool fileListTool,
                         @Value("${system-default-prompt}") String systemPrompt) {
        this.chatModel = chatModel;
        this.systemPrompt = systemPrompt;

        // 创建 ChatClient 并注册 Tool
        this.chatClient = ChatClient.builder(chatModel)
                .defaultTools(fileReadTool, fileWriteTool, fileListTool)
                .build();
    }

    @Override
    public String chat(String message) {
        if (history.isEmpty()) {
            history.add(new SystemMessage(systemPrompt));
        }
        Message userMessage = new UserMessage(message);
        history.add(userMessage);

        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .thinking(AnthropicApi.ThinkingType.DISABLED, null)
                .build();

        String response = chatClient.prompt()
                .messages(history.toArray(new Message[0]))
                .options(options)
                .call()
                .content();

        history.add(new AssistantMessage(response));
        return response;
    }

    @Override
    public Flux<String> chatStream(String message) {
        if (history.isEmpty()) {
            history.add(new SystemMessage(systemPrompt));
        }
        Message userMessage = new UserMessage(message);
        history.add(userMessage);

        StringBuilder accumulated = new StringBuilder();

        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .thinking(AnthropicApi.ThinkingType.DISABLED, null)
                .build();

        return chatClient.prompt()
                .messages(history.toArray(new Message[0]))
                .options(options)
                .stream()
                .content()
                .doOnNext(accumulated::append)
                .doOnComplete(() -> {
                    AssistantMessage assistantMessage = new AssistantMessage(accumulated.toString());
                    history.add(assistantMessage);
                });
    }

}
