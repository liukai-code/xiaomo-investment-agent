package com.xiaomo.agent.agent.intent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * LLM 意图分类器 —— 作为规则分类器的兜底。
 * <p>
 * 仅在规则分类器 fallback 到 GENERAL_CHAT（置信度 0.5）时调用，
 * 用一次轻量 LLM 请求判断用户真实意图。
 */
@Component
@Slf4j
public class LlmIntentClassifier {

    private static final String SYSTEM_PROMPT = """
            你是一个意图分类器。根据用户消息，判断其业务意图，只返回以下枚举值之一（不要返回其他内容）：

            STOCK_ANALYSIS      — 个股分析（分析某只股票、查某股票的行情/估值/研报/财报等）
            MARKET_NEWS         — 市场新闻资讯（今日新闻、最新消息、政策影响等）
            SECTOR_ANALYSIS     — 板块/行业分析（半导体板块、新能源行业、AI概念等）
            TRADING_SENTIMENT   — 打板/情绪/排名（涨停、跌停、龙虎榜、热榜、涨幅排名、涨的最好的股票等）
            HOLDINGS_QUERY      — 持仓查询（我的基金、我的持仓、养基宝等）
            FINANCIAL_CALC      — 金融计算（计算收益率、NPV、IRR、贷款月供等）
            DB_QUERY            — 数据库查询（SQL、查询数据库等）
            GENERAL_CHAT        — 通用对话（问候、概念解释、学习入门、闲聊等）

            注意：
            - "昨天涨的最好的前十只股票" → TRADING_SENTIMENT（排名类）
            - "今天涨幅最大的股票" → TRADING_SENTIMENT（排名类）
            - "半导体行业怎么样" → SECTOR_ANALYSIS
            - "茅台怎么样" → STOCK_ANALYSIS
            - "今天有什么新闻" → MARKET_NEWS
            - 只返回枚举值，不要解释。
            """;

    private static final Map<String, IntentType> INTENT_MAP = Map.of(
            "STOCK_ANALYSIS", IntentType.STOCK_ANALYSIS,
            "MARKET_NEWS", IntentType.MARKET_NEWS,
            "SECTOR_ANALYSIS", IntentType.SECTOR_ANALYSIS,
            "TRADING_SENTIMENT", IntentType.TRADING_SENTIMENT,
            "HOLDINGS_QUERY", IntentType.HOLDINGS_QUERY,
            "FINANCIAL_CALC", IntentType.FINANCIAL_CALC,
            "DB_QUERY", IntentType.DB_QUERY,
            "GENERAL_CHAT", IntentType.GENERAL_CHAT
    );

    private final ChatClient chatClient;

    public LlmIntentClassifier(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    /**
     * 调用 LLM 对用户消息进行意图分类。
     *
     * @param message 用户原始消息
     * @return 分类结果，解析失败时返回 GENERAL_CHAT
     */
    public IntentType classify(String message) {
        try {
            AnthropicChatOptions options = AnthropicChatOptions.builder()
                    .thinking(AnthropicApi.ThinkingType.DISABLED, null)
                    .temperature(0.1)
                    .maxTokens(20)
                    .build();

            String result = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(message)
                    .options(options)
                    .call()
                    .content();

            String trimmed = result == null ? "" : result.strip();
            log.info("[LlmIntentClassifier] LLM 返回: '{}', 输入: {}", trimmed, message);

            // 尝试精确匹配
            IntentType intent = INTENT_MAP.get(trimmed);
            if (intent != null) {
                return intent;
            }

            // 模糊匹配（LLM 可能返回多余文本）
            for (Map.Entry<String, IntentType> entry : INTENT_MAP.entrySet()) {
                if (trimmed.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }

            log.warn("[LlmIntentClassifier] 无法解析 LLM 返回: '{}', 降级为 GENERAL_CHAT", trimmed);
            return IntentType.GENERAL_CHAT;

        } catch (Exception e) {
            log.error("[LlmIntentClassifier] LLM 调用异常, 降级为 GENERAL_CHAT: {}", e.getMessage());
            return IntentType.GENERAL_CHAT;
        }
    }
}
