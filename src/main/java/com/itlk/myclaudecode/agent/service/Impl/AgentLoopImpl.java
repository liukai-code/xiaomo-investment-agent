package com.itlk.myclaudecode.agent.service.Impl;

import com.itlk.myclaudecode.agent.service.AgentLoop;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
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

    public AgentLoopImpl(ChatModel chatModel,
                         @Value("${system-default-prompt}") String systemPrompt) {
        this.chatModel = chatModel;
        this.systemPrompt = systemPrompt;
    }

    @Override
    public String chat(String message) {
        if (history.isEmpty()) {
            history.add(new SystemMessage(systemPrompt));
        }
        Message userMessage = new UserMessage(message);
        history.add(userMessage);
        ChatResponse response = chatModel.call(new Prompt(history));
        AssistantMessage assistantMessage = response.getResult().getOutput();
        history.add(assistantMessage);
        return assistantMessage.getText();
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

        return chatModel.stream(new Prompt(history, options))
                .filter(chatResponse -> chatResponse.getResult() != null
                        && chatResponse.getResult().getOutput() != null)
                .mapNotNull(chatResponse -> chatResponse.getResult().getOutput().getText())
                .filter(text -> text != null && !text.isEmpty())
                .doOnNext(accumulated::append)
                .doOnComplete(() -> {
                    AssistantMessage assistantMessage = new AssistantMessage(accumulated.toString());
                    history.add(assistantMessage);
                });
    }

}
