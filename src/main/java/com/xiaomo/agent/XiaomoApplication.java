package com.xiaomo.agent;

import com.xiaomo.agent.agent.config.PlanningProperties;
import com.xiaomo.agent.agent.config.ToolGuardProperties;
import com.xiaomo.agent.workflow.config.RiskOverrideProperties;
import com.xiaomo.agent.workflow.config.WorkflowProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties({ToolGuardProperties.class, WorkflowProperties.class, RiskOverrideProperties.class, PlanningProperties.class})
public class XiaomoApplication {

    public static void main(String[] args) {
        SpringApplication.run(XiaomoApplication.class, args);
    }

}
