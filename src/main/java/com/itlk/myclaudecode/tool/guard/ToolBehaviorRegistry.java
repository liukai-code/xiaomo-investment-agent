package com.itlk.myclaudecode.tool.guard;

import com.itlk.myclaudecode.tool.annotation.ToolBehavior;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class ToolBehaviorRegistry {

    private final ApplicationContext applicationContext;
    private final Map<String, ToolBehaviorData> behaviorMap = new HashMap<>();

    private static final ToolBehaviorData DEFAULT = new ToolBehaviorData(true, true);

    public ToolBehaviorRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    public void init() {
        String[] beanNames = applicationContext.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            Object bean;
            try {
                bean = applicationContext.getBean(beanName);
            } catch (Exception e) {
                continue;
            }
            for (Method method : bean.getClass().getMethods()) {
                ToolBehavior behavior = method.getAnnotation(ToolBehavior.class);
                if (behavior == null) continue;

                Tool tool = method.getAnnotation(Tool.class);
                String toolName = (tool != null && !tool.name().isEmpty()) ? tool.name() : method.getName();

                behaviorMap.put(toolName, new ToolBehaviorData(behavior.deterministic(), behavior.cacheable()));
                log.info("[ToolBehaviorRegistry] 注册工具行为: {} → deterministic={}, cacheable={}",
                        toolName, behavior.deterministic(), behavior.cacheable());
            }
        }
        log.info("[ToolBehaviorRegistry] 共注册 {} 个工具行为", behaviorMap.size());
    }

    public ToolBehaviorData getBehavior(String toolName) {
        return behaviorMap.getOrDefault(toolName, DEFAULT);
    }

    public record ToolBehaviorData(boolean deterministic, boolean cacheable) {}
}
