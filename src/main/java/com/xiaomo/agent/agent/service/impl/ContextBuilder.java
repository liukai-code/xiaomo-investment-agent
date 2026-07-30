package com.xiaomo.agent.agent.service.impl;

import com.xiaomo.agent.agent.intent.IntentClassifier;
import com.xiaomo.agent.agent.intent.IntentResult;
import com.xiaomo.agent.agent.intent.IntentType;
import com.xiaomo.agent.common.config.HttpClientService;
import com.xiaomo.agent.common.util.DebugFileLogger;
import com.xiaomo.agent.conversation.entity.ChatMessage;
import com.xiaomo.agent.conversation.entity.MessageRole;
import com.xiaomo.agent.conversation.repository.ChatMessageRepository;
import com.xiaomo.agent.conversation.service.ChatHistoryCacheService;
import com.xiaomo.agent.memory.service.MemoryService;
import com.xiaomo.agent.user.entity.User;
import com.xiaomo.agent.user.repository.UserRepository;
import com.xiaomo.agent.workflow.util.StockResolver;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 构建发送给 AI 模型的消息上下文列表（system prompt + 历史消息 + 标的锁提醒），
 * 以及标的解析逻辑。
 */
@Component
@Slf4j
public class ContextBuilder {

    private static final int DEFAULT_CONTEXT_MESSAGES = 50;

    @Value("${system-default-prompt}")
    private String systemPrompt;

    @Resource
    private UserRepository userRepository;

    @Resource
    private ChatHistoryCacheService cacheService;

    @Resource
    private ChatMessageRepository chatMessageRepository;

    @Resource
    private HttpClientService httpClientService;

    @Resource
    private MemoryService memoryService;

    public record ResolvedTarget(String code, String name) {}

    /** 将内部 ResolvedTarget 转换为 IntentResult.ResolvedTarget */
    public static IntentResult.ResolvedTarget toResolvedTarget(ResolvedTarget t) {
        return t == null ? null : new IntentResult.ResolvedTarget(t.code(), t.name());
    }

    public List<Message> buildContext(Long conversationId, Long userId,
                                      IntentResult.ResolvedTarget target, IntentType intent,
                                      int contextWindow,
                                      PlanContext planContext) {
        List<Message> context = new ArrayList<>();

        String enrichedPrompt = systemPrompt;
        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            enrichedPrompt += "\n\n[用户信息]\n当前用户：" + user.getAccountId() + "（邮箱: " + user.getEmail() + "）";
        }

        // 注入用户画像记忆和对话摘要（用户关闭记忆时跳过）
        boolean memoryEnabled = user != null && (user.getMemoryEnabled() == null || user.getMemoryEnabled());
        if (memoryEnabled) {
            String memoryPrompt = memoryService.buildMemoryPrompt(userId, conversationId);
            if (memoryPrompt != null && !memoryPrompt.isEmpty()) {
                enrichedPrompt += memoryPrompt;
            }
        }

        enrichedPrompt += "\n\n[当前时间]\n" + java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss EEEE"));

        if (target != null) {
            String stockLabel = target.name() != null
                    ? target.name() + "（" + target.code() + "）"
                    : target.code();
            DebugFileLogger.logBuildContext("TARGET_LOCKED", stockLabel);
            enrichedPrompt += "\n\n[当前分析标的]\n"
                    + "标的已锁定为：" + stockLabel + "\n"
                    + "⚠️ 你必须严格遵守以下约束：\n"
                    + "1. 当前用户请求分析的标的是 " + stockLabel + "，所有工具调用和数据获取必须围绕该标的\n"
                    + "2. 禁止分析、引用、对比任何其他标的\n"
                    + "3. 如果工具返回包含其他股票的数据，必须忽略，只关注 " + stockLabel + " 的数据\n"
                    + "4. 输出报告的标题、数据、结论必须与 " + stockLabel + " 完全一致\n"
                    + "5. 禁止出现用户问A你分析B的情况\n"
                    + "6. 分析范围仅限该标的本身（行情、基本面、技术面、新闻、估值），禁止生成投资组合建议、资产配置方案、多标的推荐列表\n"
                    + "7. 用户问的是单个标的，回答也必须是单个标的的分析，不要扩展到投资组合层面";
        } else {
            DebugFileLogger.logBuildContext("NO_TARGET", "标的解析失败或未触发，未设置标的锁");
        }

        // 意图提示：在 system prompt 中追加任务类型约束
        if (intent != null) {
            enrichedPrompt += switch (intent) {
                case MARKET_NEWS -> "\n\n[当前任务类型]\n用户请求的是市场新闻资讯分析，"
                        + "请使用新闻搜索工具获取最新资讯，然后基于资讯内容回答用户。"
                        + "如果用户提到了某个具体标的的新闻，可以查询该标的的个股新闻，"
                        + "但不要展开成完整的个股分析报告。";
                case SECTOR_ANALYSIS -> "\n\n[当前任务类型]\n用户请求的是板块/行业层面的分析，"
                        + "请使用行业排名、行业研报、概念板块等工具获取板块数据。"
                        + "不需要锁定个股标的，分析整个板块/行业的趋势和机会。";
                case TRADING_SENTIMENT -> "\n\n[当前任务类型]\n用户请求的是打板/情绪面分析，"
                        + "请使用涨停池、龙虎榜、情绪速算等工具获取市场情绪数据。";
                case HOLDINGS_QUERY -> "\n\n[当前任务类型]\n用户请求查询自己的持仓信息，"
                        + "请使用持仓查询工具获取数据。";
                case FINANCIAL_CALC -> "\n\n[当前任务类型]\n用户请求金融计算，"
                        + "请使用金融计算器工具完成计算。";
                default -> "";
            };
        }

        // 注入执行计划
        if (planContext != null) {
            enrichedPrompt += planContext.planPrompt();
        }

        context.add(new SystemMessage(enrichedPrompt));

        List<ChatMessage> recentMessages = cacheService.getCachedRecentMessages(conversationId, contextWindow);
        if (recentMessages == null) {
            recentMessages = chatMessageRepository
                    .findRecentByConversationId(conversationId, contextWindow);
            Collections.reverse(recentMessages);
            cacheService.cacheRecentMessages(conversationId, contextWindow, recentMessages);
        }

        for (ChatMessage msg : recentMessages) {
            switch (msg.getRole()) {
                case USER -> context.add(new UserMessage(msg.getContent()));
                case ASSISTANT -> context.add(new AssistantMessage(msg.getContent()));
            }
        }

        // 标的锁定期：在上下文末尾追加强提醒，防止模型在多轮工具调用后"忘记"分析目标
        if (target != null) {
            String stockLabel = target.name() != null
                    ? target.name() + "（" + target.code() + "）"
                    : target.code();
            context.add(new UserMessage(
                    "⚠️ 重要提醒：你当前正在分析的标的是 " + stockLabel + "。"
                    + "请基于以上工具返回的数据生成分析报告，报告标题和所有内容必须严格对应 " + stockLabel + "。"
                    + "禁止出现任何其他股票的名称或代码。如果之前的对话中提到了其他标的，必须完全忽略。"
                    + "分析范围仅限该标的本身，禁止生成投资组合建议、资产配置方案或多标的推荐。"));
        }

        return context;
    }

    /**
     * 原有的标的解析逻辑，作为 IntentClassifier 禁用时的 fallback。
     */
    public ResolvedTarget resolveStockFromMessage(String message) {
        DebugFileLogger.logResolveStock("START", message, "-");
        if (message == null || message.isBlank()) return null;

        // 1. 快速路径：6位数字代码
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b(\\d{6})\\b").matcher(message);
        if (m.find()) {
            log.info("[ChatStream] 从消息中提取到数字代码: {}", m.group(1));
            DebugFileLogger.logResolveStock("CODE_EXTRACT", message, m.group(1));
            return new ResolvedTarget(m.group(1), null);
        }

        // 2. 意图检测：含分析类关键词才尝试解析
        String[] kws = {"深入分析", "深度分析", "全面分析", "详细分析", "深度研究", "深度调研",
                "深度剖析", "深入研究", "全面研究", "详细研究", "个股分析", "个股研究",
                "帮我分析", "帮我看看", "帮我研究", "分析一下", "研究一下",
                "分析", "研究", "调研", "估值", "行情", "股价", "怎么样", "如何",
                "值得入手吗", "值得买吗", "可以买吗", "可以入手吗", "能买吗",
                "值得投资吗", "值得持有吗", "现在能买吗", "现在可以买吗", "目前怎么样",
                "可以买", "看好", "看好吗", "有前途吗", "前景如何", "还能涨吗", "还能买吗",
                "研报", "现在", "目前"};
        boolean match = false;
        for (String kw : kws) {
            if (message.contains(kw)) { match = true; break; }
        }
        if (!match) {
            DebugFileLogger.logResolveStock("NO_INTENT_KEYWORD", message, "null");
            return null;
        }

        // 2.5 板块/行业级查询检测：包含板块关键词时，不触发标的守卫
        String[] sectorKeywords = {"板块", "行业", "概念", "赛道", "题材", "领域"};
        for (String sk : sectorKeywords) {
            if (message.contains(sk)) {
                log.info("[ChatStream] 检测到板块/行业关键词「{}」，跳过标的守卫", sk);
                DebugFileLogger.logResolveStock("SECTOR_QUERY", message, "null");
                return null;
            }
        }

        // 3. 剥离意图关键词 + 时间词，避免 StockResolver 把时间词当作股票名称
        String[] timeKeywords = {"今天", "今日", "明天", "明日", "昨天", "昨日",
                "上周", "本周", "下周", "上个月", "这个月", "下个月",
                "最近", "近期", "前段时间", "这段时间"};
        String cleaned = message;
        for (String kw : kws) {
            cleaned = cleaned.replace(kw, "");
        }
        for (String kw : timeKeywords) {
            cleaned = cleaned.replace(kw, "");
        }
        cleaned = cleaned.replaceAll("[，。？！、\\s]+", "").trim();
        if (cleaned.isEmpty()) {
            DebugFileLogger.logResolveStock("CLEANED_EMPTY", message, "null");
            return null;
        }

        log.info("[ChatStream] 关键词剥离后: \"{}\" → \"{}\"", message, cleaned);
        DebugFileLogger.logResolveStock("KEYWORD_STRIPPED", message, cleaned);

        // 4. 复用 StockResolver 解析股票名称
        try {
            var r = StockResolver.resolve(cleaned, httpClientService);
            log.info("[ChatStream] 标的解析成功: {}({})", r.name(), r.code());
            DebugFileLogger.logResolveStock("RESOLVED", cleaned, r.code() + "(" + r.name() + ")");
            return new ResolvedTarget(r.code(), r.name());
        } catch (IllegalArgumentException e) {
            log.warn("[ChatStream] 标的解析失败: {}", e.getMessage());
            DebugFileLogger.logResolveStock("RESOLVE_FAILED", cleaned, e.getMessage());
            return null;
        }
    }

    public String stripXmlTags(String text) {
        if (text == null) return "";
        return text.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
    }
}
