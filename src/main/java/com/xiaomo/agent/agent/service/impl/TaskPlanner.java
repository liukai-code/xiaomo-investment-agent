package com.xiaomo.agent.agent.service.impl;

import com.xiaomo.agent.agent.config.PlanningProperties;
import com.xiaomo.agent.agent.intent.AnalysisDepth;
import com.xiaomo.agent.agent.intent.ExecutionMode;
import com.xiaomo.agent.agent.intent.IntentResult;
import com.xiaomo.agent.agent.intent.IntentType;
import com.xiaomo.agent.agent.intent.RequestFeatures;
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
import java.util.regex.Pattern;

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
     * 判断执行模式（纯规则，不走 LLM）。
     * DEEP 深度分析始终触发 PLANNING；其余按结构特征判断。
     */
    public ExecutionMode determineExecutionMode(String message, IntentType intent, AnalysisDepth depth) {
        if (!planningProperties.enabled()) return ExecutionMode.DIRECT;
        if (message == null || message.isBlank()) return ExecutionMode.DIRECT;
        if (intent == null) return ExecutionMode.DIRECT;

        // 深度分析始终触发 LLM 规划
        if (depth == AnalysisDepth.DEEP) return ExecutionMode.PLANNING;

        RequestFeatures features = extractFeatures(message);

        // 存在前后依赖 → PLANNING（最强信号）
        if (features.hasDependentSteps()) return ExecutionMode.PLANNING;

        // 多标的 × 多维度 → PLANNING
        if (features.targetCount() >= 2 && features.dimensionCount() >= 2) return ExecutionMode.PLANNING;

        // 多维度 + 综合决策需求 → PLANNING
        if (features.dimensionCount() >= 2 && features.hasSynthesisRequirement()) return ExecutionMode.PLANNING;

        // 3 个以上子目标 → PLANNING
        if (features.subGoalCount() >= 3) return ExecutionMode.PLANNING;

        // 预估 2 次以上工具调用 → PARALLEL
        if (features.estimatedToolCalls() >= 2) return ExecutionMode.PARALLEL;

        return ExecutionMode.DIRECT;
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

    // ===== 特征提取 =====

    /** 信息维度关键词 */
    private static final String[] DIMENSION_KEYWORDS = {
            "估值", "基本面", "资金面", "技术面", "情绪面", "行情", "财务",
            "盈利", "营收", "利润", "ROE", "PE", "PB", "K线",
            "资金流", "北向", "龙虎榜", "融资", "研报",
            "角度", "维度", "方面"
    };

    /** 依赖步骤关键词 — 需要前后对应不同动作才有效 */
    private static final String[] DEPENDENCY_KEYWORDS = {
            "先", "然后", "再", "之后",
            "从中选择", "筛选出", "找出", "根据结果",
            "其中", "分别分析"
    };

    /** 综合决策关键词 */
    private static final String[] SYNTHESIS_KEYWORDS = {
            "是否值得", "是否适合", "能不能买", "给出建议",
            "投资价值", "风险收益", "综合判断", "形成结论",
            "推荐", "买入区间", "目标价"
    };

    /** 对比关键词 */
    private static final String[] COMPARISON_KEYWORDS = {
            "对比", "比较", " vs ", "哪个好", "哪个强", "优劣"
    };

    /** 标的识别模式：股票名称/代码之间用"和"、"与"、"、"、顿号等连接 */
    private static final Pattern MULTI_TARGET_PATTERN = Pattern.compile(
            "[\\u4e00-\\u9fa5]{2,}[和、与,][\\u4e00-\\u9fa5]{2,}");

    /** 子目标分隔模式：逗号、句号、分号分隔的不同动作 */
    private static final Pattern SUB_GOAL_SEPARATOR = Pattern.compile("[，。；]");

    /**
     * 从用户请求中提取结构化特征。
     */
    RequestFeatures extractFeatures(String msg) {
        int dimensionCount = countMatches(msg, DIMENSION_KEYWORDS);
        boolean hasDependent = hasDependentSteps(msg);
        boolean hasSynthesis = containsAny(msg, SYNTHESIS_KEYWORDS);
        boolean hasComparison = containsAny(msg, COMPARISON_KEYWORDS);
        int targetCount = estimateTargetCount(msg);
        int subGoalCount = estimateSubGoalCount(msg);
        int estimatedToolCalls = estimateToolCalls(msg, dimensionCount, targetCount, hasComparison);

        return new RequestFeatures(
                targetCount, dimensionCount, subGoalCount,
                estimatedToolCalls, hasDependent, hasSynthesis, hasComparison
        );
    }

    /**
     * 判断是否存在前后依赖步骤。
     * 不能只看"先"字出现，需要确认前后确实对应不同动作。
     */
    private boolean hasDependentSteps(String msg) {
        // 快速检查：必须包含依赖关键词
        if (!containsAny(msg, DEPENDENCY_KEYWORDS)) return false;

        // "先...再/然后/之后..." 模式 — 至少两个不同动作
        if (msg.contains("先") && (msg.contains("再") || msg.contains("然后") || msg.contains("之后"))) {
            return true;
        }
        // "从中选择/筛选出/找出" — 需要先有结果才能筛选
        if (msg.contains("从中") || msg.contains("筛选出")) return true;
        // "根据结果" — 明确依赖前序步骤
        if (msg.contains("根据结果")) return true;
        // "分别分析" — 多标的需要分别处理
        if (msg.contains("分别分析") || msg.contains("分别研究")) return true;

        return false;
    }

    /**
     * 估算标的数量。
     */
    private int estimateTargetCount(String msg) {
        // 检查多标的连接模式
        if (MULTI_TARGET_PATTERN.matcher(msg).find()) return 2;
        // "X和Y的PE" 模式
        if (msg.matches(".*[\\u4e00-\\u9fa5]{2,}[和、与][\\u4e00-\\u9fa5]{2,}.*")) return 2;
        return 1;
    }

    /**
     * 估算子目标数量（按分隔符和动作词粗略估计）。
     */
    private int estimateSubGoalCount(String msg) {
        String[] separators = {"，", "。", "；"};
        int segments = 1;
        for (String sep : separators) {
            int idx = 0;
            while ((idx = msg.indexOf(sep, idx)) >= 0) {
                segments++;
                idx += sep.length();
            }
        }
        // 至少 1 个子目标
        return Math.max(1, segments);
    }

    /**
     * 估算工具调用次数。
     */
    private int estimateToolCalls(String msg, int dimensionCount, int targetCount, boolean hasComparison) {
        int calls = 0;
        // 每个维度至少一次工具调用
        calls += Math.max(0, dimensionCount);
        // 对比场景需要额外查询
        if (hasComparison) calls = Math.max(calls, 2);
        // 多标的倍增（但不是简单乘法，有些工具可以一次查多标的）
        if (targetCount >= 2) calls = Math.max(calls, 2);
        // 至少 1 次
        return Math.max(1, calls);
    }

    private int countMatches(String msg, String[] keywords) {
        int count = 0;
        for (String kw : keywords) {
            if (msg.contains(kw)) count++;
        }
        return count;
    }

    private boolean containsAny(String msg, String[] keywords) {
        for (String kw : keywords) {
            if (msg.contains(kw)) return true;
        }
        return false;
    }
}
