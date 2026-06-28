package com.itlk.myclaudecode;

import com.itlk.myclaudecode.agent.config.ToolGuardProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(ToolGuardProperties.class)
public class MyClaudeCodeApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyClaudeCodeApplication.class, args);
    }

}
