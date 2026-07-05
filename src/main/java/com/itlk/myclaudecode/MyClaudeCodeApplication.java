package com.itlk.myclaudecode;

import com.itlk.myclaudecode.agent.config.ToolGuardProperties;
import com.itlk.myclaudecode.workflow.config.RiskOverrideProperties;
import com.itlk.myclaudecode.workflow.config.WorkflowProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({ToolGuardProperties.class, WorkflowProperties.class, RiskOverrideProperties.class})
public class MyClaudeCodeApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyClaudeCodeApplication.class, args);
    }

}
