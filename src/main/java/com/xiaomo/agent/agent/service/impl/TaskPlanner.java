package com.xiaomo.agent.agent.service.impl;

import com.xiaomo.agent.agent.config.PlanningProperties;
import com.xiaomo.agent.agent.intent.AnalysisDepth;
import com.xiaomo.agent.agent.intent.IntentResult;
import com.xiaomo.agent.agent.intent.IntentType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 对话场景下的任务规划器。
 * 判断用户请求是否需要多步规划，需要时调用 LLM 生成结构化执行计划。
 */
@Component
@Slf4j
public class TaskPlanner {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TOOL_CATALOG = """
            可用工具列表：
            - a_stock_quote: 实时行情报价（股价、涨跌幅、PE、市值等）
            - a_stock_report: 研报与财务数据（个股研报、行业研报、财务指标、EPS预测）
            - a_stock_signal: 市场信号（概念板块、资金流、龙虎榜、解禁、行业排名）
            - a_stock_capital: 资金面数据（融资融券、大宗交易、股东户数、分红、北向资金）
            - a_stock_news: 新闻资讯（个股新闻、全球资讯、公告、互动问答）
            - a_stock_limit_up: 打板数据（涨停池、炸板池、跌停池、连板梯队）
            - a_stock_option: ETF期权数据（期权代码、T型报价、希腊字母）
            - a_stock_sentiment: 舆情数据（同花顺热榜、东财人气榜、概念命中）
            - market_data: 基础行情（A股/港股/美股报价、基金净值、股票搜索）
            - financial_calculator: 金融计算器（复利、贷款、NPV、IRR、夏普比率等22种）
            - fetchWebpage: 抓取网页内容
            - fetchArticleContent: 抓取文章正文
            """;

    private static final String PLAN_PROMPT_TEMPLATE = """
            你是一个任务规划器。根据用户的问题，生成一个结构化的执行计划。

            %s

            用户问题：%s

            %s

            要求：
            1. 计划不超过 %d 步
            2. 每步选择一个最合适的工具
            3. 步骤之间如果有依赖关系，后续步骤可以在 args_hint 中说明"基于步骤X的结果"
            4. 可以并行执行的步骤不要互相依赖
            5. 最后一步通常是"综合以上数据，生成分析/回答"

            请严格按以下 JSON 格式输出，不要输出任何其他内容：
            {"goal":"任务目标","steps":[{"id":1,"action":"做什么","tool":"工具名","args_hint":"参数提示"}]}
            """;

    @Resource
    private PlanningProperties planningProperties;

    private ChatClient chatClient;

    public TaskPlanner(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 判断任务是否需要多步规划（纯规则，不走 LLM）。
     * DEEP 深度分析始终触发规划；普通请求按业务意图和复杂度判断。
     */
    public boolean needsPlanning(String message, IntentType intent, AnalysisDepth depth) {
        if (!planningProperties.enabled()) return false;
        if (message == null || message.isBlank()) return false;
        if (intent == null) return false;

        // 深度分析始终触发 LLM 规划
        if (depth == AnalysisDepth.DEEP) return true;

        return switch (intent) {
            case GENERAL_CHAT, FINANCIAL_CALC, DB_QUERY, HOLDINGS_QUERY -> false;
            case STOCK_ANALYSIS -> hasMultiDimensionKeywords(message);
            case MARKET_NEWS -> hasMultiTopicKeywords(message);
            case SECTOR_ANALYSIS -> hasComparisonKeywords(message);
            case TRADING_SENTIMENT -> false;
        };
    }

    /**
     * 调用 LLM 生成执行计划。
     */
    public PlanContext plan(String message, IntentType intent,
                            IntentResult.ResolvedTarget target,
                            Set<String> availableToolNames) {
        String targetHint = "";
        if (target != null) {
            String label = target.name() != null ? target.name() + "（" + target.code() + "）" : target.code();
            targetHint = "当前分析标的：" + label + "\n所有工具调用必须围绕该标的。";
        }

        String prompt = String.format(PLAN_PROMPT_TEMPLATE,
                TOOL_CATALOG, message, targetHint,
                planningProperties.maxSteps());

        try {
            AnthropicChatOptions options = AnthropicChatOptions.builder()
                    .thinking(AnthropicApi.ThinkingType.DISABLED, null)
                    .maxTokens(planningProperties.planMaxTokens())
                    .temperature(0.3)
                    .build();

            ChatResponse response = chatClient.prompt()
                    .user(prompt)
                    .options(options)
                    .call()
                    .chatResponse();

            String raw = response.getResult().getOutput().getText();
            return parsePlan(raw, message);
        } catch (Exception e) {
            log.warn("[TaskPlanner] 规划 LLM 调用失败，跳过规划: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析 LLM 返回的 JSON 计划。
     */
    private PlanContext parsePlan(String raw, String originalMessage) {
        try {
            // 提取 JSON（LLM 可能包裹在 ```json ``` 中）
            String json = raw;
            int jsonStart = raw.indexOf('{');
            int jsonEnd = raw.lastIndexOf('}');
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                json = raw.substring(jsonStart, jsonEnd + 1);
            }

            JsonNode root = MAPPER.readTree(json);
            String goal = root.has("goal") ? root.get("goal").asText() : originalMessage;

            List<PlanContext.PlanStep> steps = new ArrayList<>();
            JsonNode stepsNode = root.get("steps");
            if (stepsNode != null && stepsNode.isArray()) {
                for (JsonNode stepNode : stepsNode) {
                    int id = stepNode.has("id") ? stepNode.get("id").asInt() : steps.size() + 1;
                    String action = stepNode.has("action") ? stepNode.get("action").asText() : "";
                    String tool = stepNode.has("tool") ? stepNode.get("tool").asText() : "";
                    String argsHint = stepNode.has("args_hint") ? stepNode.get("args_hint").asText() : "";
                    steps.add(new PlanContext.PlanStep(id, action, tool, argsHint));
                }
            }

            if (steps.isEmpty()) {
                log.warn("[TaskPlanner] LLM 返回的计划无有效步骤，跳过规划");
                return null;
            }

            // 限制步骤数
            if (steps.size() > planningProperties.maxSteps()) {
                steps = steps.subList(0, planningProperties.maxSteps());
            }

            String planPrompt = formatPlanPrompt(goal, steps);
            log.info("[TaskPlanner] 生成执行计划: goal={}, steps={}", goal, steps.size());
            return new PlanContext(goal, steps, planPrompt);
        } catch (Exception e) {
            log.warn("[TaskPlanner] 解析计划 JSON 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 将计划格式化为注入 system prompt 的文本。
     */
    private String formatPlanPrompt(String goal, List<PlanContext.PlanStep> steps) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n[执行计划]\n目标：").append(goal).append("\n\n");
        for (PlanContext.PlanStep step : steps) {
            sb.append("步骤 ").append(step.id()).append(": ").append(step.action());
            if (step.tool() != null && !step.tool().isBlank()) {
                sb.append(" → ").append(step.tool());
            }
            if (step.argsHint() != null && !step.argsHint().isBlank()) {
                sb.append("（").append(step.argsHint()).append("）");
            }
            sb.append("\n");
        }
        sb.append("\n请按步骤顺序执行工具调用，每步完成后记录关键数据，最后汇总生成完整报告。");
        return sb.toString();
    }

    // ===== 复杂度判断规则 =====

    /**
     * 个股分析是否包含多维度关键词（估值、基本面、资金面等）
     */
    private boolean hasMultiDimensionKeywords(String msg) {
        String[] dimensions = {"估值", "基本面", "资金面", "技术面", "情绪面", "行情", "财务",
                "盈利", "营收", "利润", "ROE", "PE", "PB", "K线",
                "资金流", "北向", "龙虎榜", "融资", "研报"};
        int matchCount = 0;
        for (String kw : dimensions) {
            if (msg.contains(kw)) matchCount++;
        }
        // 匹配 2 个以上维度关键词，或包含"角度"/"维度"/"方面"等规划类词
        return matchCount >= 2 || msg.contains("角度") || msg.contains("维度") || msg.contains("方面");
    }

    /**
     * 市场新闻是否包含多个主题
     */
    private boolean hasMultiTopicKeywords(String msg) {
        String[] topics = {"板块", "行业", "大盘", "指数", "央行", "政策", "美联储", "利率", "通胀"};
        int matchCount = 0;
        for (String kw : topics) {
            if (msg.contains(kw)) matchCount++;
        }
        return matchCount >= 2;
    }

    /**
     * 板块分析是否包含对比词
     */
    private boolean hasComparisonKeywords(String msg) {
        return msg.contains("对比") || msg.contains("比较") || msg.contains(" vs ")
                || msg.contains("哪个好") || msg.contains("哪个强") || msg.contains("优劣");
    }
}
