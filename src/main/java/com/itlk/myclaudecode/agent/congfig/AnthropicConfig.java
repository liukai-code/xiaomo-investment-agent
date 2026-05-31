package com.itlk.myclaudecode.agent.congfig;


import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Data
@Component
public class AnthropicConfig {

    @Value("${spring.ai.anthropic.base-url}")
    private String baseurl;

    @Value("${spring.ai.anthropic.api-key}")
    private String apikey;

    @Value("${spring.ai.anthropic.model-id}")
    private String model;

    @Value("${system-default-prompt}")
    private String systemPrompt;

}
