package com.itlk.myclaudecode.agent.service.Impl;

import com.itlk.myclaudecode.agent.congfig.AnthropicConfig;
import com.itlk.myclaudecode.agent.service.AgentLoop;
import jakarta.annotation.Resource;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgentLoopImpl implements AgentLoop {

    @Resource
    private AnthropicConfig anthropicConfig;

    private AnthropicChatModel chatModel;

    private List<Message> history = new ArrayList<>();

    private AnthropicChatModel getChatModel() {
        if (chatModel == null) {
            AnthropicApi api = AnthropicApi.builder()
                    .baseUrl(anthropicConfig.getBaseurl())
                    .apiKey(anthropicConfig.getApikey())
                    .build();
            chatModel = AnthropicChatModel.builder()
                    .anthropicApi(api)
                    .defaultOptions(AnthropicChatOptions.builder()
                            .model(anthropicConfig.getModel())
                            .maxTokens(4096)
                            .build())
                    .build();
        }
        return chatModel;
    }

    @Override
    public String chat(String message) {

        Message userMessage = new UserMessage(message);
        history.add(userMessage);
        ChatResponse response = getChatModel().call(new Prompt(history));
        AssistantMessage assistantMessage = response.getResult().getOutput();
        history.add(assistantMessage);
        return assistantMessage.getText();
    }

    @Override
    public Flux<String> chatStream(String message) {
        Message userMessage = new UserMessage(message);
        history.add(userMessage);

        StringBuilder accumulated = new StringBuilder();

        return getChatModel().stream(new Prompt(history))
                .mapNotNull(chatResponse -> chatResponse.getResult().getOutput().getText())
                .doOnNext(accumulated::append)
                .doOnComplete(() -> {
                    AssistantMessage assistantMessage = new AssistantMessage(accumulated.toString());
                    history.add(assistantMessage);
                });
    }

}
