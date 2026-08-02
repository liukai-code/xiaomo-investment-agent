package com.xiaomo.agent.memory.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaomo.agent.conversation.entity.ChatMessage;
import com.xiaomo.agent.conversation.entity.MessageRole;
import com.xiaomo.agent.conversation.repository.ChatMessageRepository;
import com.xiaomo.agent.memory.entity.*;
import com.xiaomo.agent.memory.repository.ConversationSummaryRepository;
import com.xiaomo.agent.memory.repository.MemoryExtractionTaskRepository;
import com.xiaomo.agent.memory.repository.UserProfileRepository;
import com.xiaomo.agent.memory.service.MemoryExtractionService;
import com.xiaomo.agent.memory.service.MemoryService;
import com.xiaomo.agent.user.dto.UserPreferences;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class MemoryExtractionServiceImpl implements MemoryExtractionService {

    /** 每隔多少轮对话触发一次画像提取 */
    private static final int PROFILE_EXTRACTION_INTERVAL = 5;

    /** 消息压缩阈值：未压缩消息超过此数量时触发 */
    private static final int COMPRESSION_THRESHOLD = 20;

    /** 每批压缩的消息数 */
    private static final int BATCH_SIZE = 15;

    /** 压缩后保留最近 N 条消息在上下文中 */
    private static final int KEEP_RECENT = 5;

    @Resource
    private ChatMessageRepository chatMessageRepository;

    @Resource
    private UserProfileRepository userProfileRepository;

    @Resource
    private ConversationSummaryRepository summaryRepository;

    @Resource
    private MemoryExtractionTaskRepository extractionTaskRepository;

    @Resource
    private com.xiaomo.agent.user.service.UserPreferencesCacheService userPreferencesCacheService;

    @Resource
    private MemoryService memoryService;

    @Resource
    private ChatModel chatModel;

    private ChatClient chatClient;

    @jakarta.annotation.PostConstruct
    void init() {
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    @Resource
    private ObjectMapper objectMapper;

    @Override
    @Async
    public void extractMemoriesAsync(Long userId, Long conversationId, Long triggerMessageId) {
        try {
            // 1. 检查是否已有进行中的任务（防重复）
            if (extractionTaskRepository.existsByConversationIdAndStatus(
                    conversationId, ExtractionTaskStatus.PROCESSING)) {
                log.debug("[Memory] 已有进行中的提取任务, convId={}", conversationId);
                return;
            }

            // 创建任务记录
            MemoryExtractionTask task = new MemoryExtractionTask();
            task.setUserId(userId);
            task.setConversationId(conversationId);
            task.setTriggerMessageId(triggerMessageId != null ? triggerMessageId : 0L);
            task.setStatus(ExtractionTaskStatus.PROCESSING);
            extractionTaskRepository.save(task);

            try {
                // 2. 检查消息数量，超过阈值才触发摘要压缩（用户关闭压缩时跳过）
                long messageCount = chatMessageRepository.countByConversationId(conversationId);
                ConversationSummary existing = summaryRepository.findLatestByConversationId(conversationId);
                int compressedSoFar = existing != null ? existing.getCompressedCount() : 0;
                int uncompactedCount = (int) (messageCount - compressedSoFar);

                UserPreferences prefs = userPreferencesCacheService.getPreferences(userId);
                boolean compressionEnabled = prefs == null || prefs.compressionEnabled();
                if (compressionEnabled && uncompactedCount >= COMPRESSION_THRESHOLD) {
                    compressConversation(userId, conversationId, existing);
                }

                // 3. 每隔 N 轮提取一次画像记忆
                if (shouldExtractProfile(conversationId)) {
                    extractProfileMemories(userId, conversationId);
                }

                task.setStatus(ExtractionTaskStatus.COMPLETED);
                task.setCompletedAt(java.time.LocalDateTime.now());
                extractionTaskRepository.save(task);

            } catch (Exception e) {
                task.setStatus(ExtractionTaskStatus.FAILED);
                task.setErrorMessage(e.getMessage());
                task.setRetryCount(task.getRetryCount() + 1);
                extractionTaskRepository.save(task);
                throw e;
            }

        } catch (Exception e) {
            log.error("[Memory] 异步记忆提取失败, convId={}: {}", conversationId, e.getMessage());
        }
    }

    private boolean shouldExtractProfile(Long conversationId) {
        long totalMessages = chatMessageRepository.countByConversationId(conversationId);
        return totalMessages > 0 && totalMessages % PROFILE_EXTRACTION_INTERVAL == 0;
    }

    private void extractProfileMemories(Long userId, Long conversationId) {
        List<ChatMessage> recentMessages = chatMessageRepository
                .findRecentByConversationId(conversationId, 10);
        Collections.reverse(recentMessages);

        StringBuilder dialog = new StringBuilder();
        for (ChatMessage msg : recentMessages) {
            if (msg.getRole() == MessageRole.USER) {
                dialog.append("用户：").append(msg.getContent()).append("\n");
            } else if (msg.getRole() == MessageRole.ASSISTANT) {
                String content = msg.getContent();
                if (content.length() > 500) content = content.substring(0, 500) + "...";
                dialog.append("助手：").append(content).append("\n");
            }
        }

        // 获取已有画像，让 AI 在提取时考虑去重
        List<UserProfile> existingProfiles = userProfileRepository
                .findByUserIdAndActiveTrueOrderByImportanceDesc(userId);
        StringBuilder existingStr = new StringBuilder();
        for (UserProfile p : existingProfiles) {
            existingStr.append("- [").append(p.getCategory().getDisplayName())
                    .append("] ").append(p.getContent()).append("\n");
        }

        String extractPrompt = """
                你是一个用户画像分析器。从以下金融对话中提取用户的个人投资偏好信息。

                提取维度：
                - INVESTMENT_STYLE: 投资风格（价值投资、趋势交易、打板、量化等）
                - RISK_PREFERENCE: 风险偏好（保守、稳健、激进）
                - FOCUS_SECTOR: 关注板块/行业
                - HOLDING_HABIT: 持仓习惯（短线、中线、长线、仓位管理方式）
                - EXPERIENCE_LEVEL: 投资经验水平
                - GENERAL: 其他值得关注的偏好

                规则：
                1. 只提取用户明确表达或强烈暗示的偏好，不要推测
                2. 每条记忆用简洁的一句话描述
                3. 如果对话中没有明确的偏好信息，输出空数组 []
                4. 与已有记忆重复或高度相似的不要重复提取
                5. 输出纯 JSON 数组格式，不要包含 markdown 代码块标记：[{"category":"类别","content":"记忆内容","importance":3}]
                6. importance: 3=一般观察, 4=明确表达, 5=反复强调

                已有记忆（请勿重复）：
                %s

                对话内容：
                %s
                """.formatted(existingStr.toString(), dialog.toString());

        try {
            ChatClient client = this.chatClient;
            AnthropicChatOptions options = AnthropicChatOptions.builder()
                    .thinking(AnthropicApi.ThinkingType.DISABLED, null)
                    .temperature(0.1)
                    .maxTokens(500)
                    .build();

            String result = client.prompt()
                    .user(extractPrompt)
                    .options(options)
                    .call()
                    .content();

            saveExtractedProfiles(userId, conversationId, result);
            log.info("[Memory] 画像提取完成, userId={}, convId={}", userId, conversationId);

        } catch (Exception e) {
            log.error("[Memory] 画像提取失败, convId={}: {}", conversationId, e.getMessage());
        }
    }

    void saveExtractedProfiles(Long userId, Long conversationId, String jsonResult) {
        try {
            // 清理可能的 markdown 代码块标记
            String cleaned = jsonResult.trim();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
            }

            List<Map<String, Object>> items = objectMapper.readValue(cleaned,
                    new TypeReference<>() {});

            for (Map<String, Object> item : items) {
                String categoryStr = (String) item.get("category");
                String content = (String) item.get("content");
                Integer importance = item.get("importance") != null
                        ? ((Number) item.get("importance")).intValue() : 3;

                if (content == null || content.isBlank()) continue;
                if (categoryStr == null) continue;

                ProfileCategory category;
                try {
                    category = ProfileCategory.valueOf(categoryStr);
                } catch (IllegalArgumentException e) {
                    category = ProfileCategory.GENERAL;
                }

                // 去重：检查是否已有相同内容
                if (!userProfileRepository.existsByUserIdAndContent(userId, content)) {
                    UserProfile profile = new UserProfile();
                    profile.setUserId(userId);
                    profile.setCategory(category);
                    profile.setContent(content);
                    profile.setImportance(importance);
                    profile.setSourceType(MemorySourceType.AI_EXTRACTED);
                    profile.setConversationId(conversationId);
                    profile.setActive(true);
                    userProfileRepository.save(profile);
                    log.info("[Memory] AI 提取画像: [{}] {}", category, content);
                }
            }
        } catch (Exception e) {
            log.warn("[Memory] 解析画像提取结果失败: {}, raw={}", e.getMessage(), jsonResult);
        }
    }

    private void compressConversation(Long userId, Long conversationId,
                                       ConversationSummary existing) {
        // 获取需要压缩的消息（排除已被压缩的部分）
        Long afterMessageId = existing != null ? existing.getToMessageId() : null;
        List<ChatMessage> messagesToCompress;

        if (afterMessageId != null) {
            messagesToCompress = chatMessageRepository
                    .findByConversationIdAndIdGreaterThanOrderByIdAsc(conversationId, afterMessageId);
        } else {
            messagesToCompress = chatMessageRepository
                    .findByConversationIdOrderByIdAsc(conversationId);
        }

        // 只压缩前 BATCH_SIZE 条，保留最近 KEEP_RECENT 条在上下文中
        int batchSize = Math.min(BATCH_SIZE, messagesToCompress.size() - KEEP_RECENT);
        if (batchSize <= 0) return;

        List<ChatMessage> toCompress = messagesToCompress.subList(0, batchSize);

        StringBuilder dialog = new StringBuilder();
        for (ChatMessage msg : toCompress) {
            if (msg.getRole() == MessageRole.USER) {
                dialog.append("用户：").append(msg.getContent()).append("\n");
            } else if (msg.getRole() == MessageRole.ASSISTANT) {
                String content = msg.getContent();
                if (content.length() > 500) content = content.substring(0, 500) + "...";
                dialog.append("助手：").append(content).append("\n");
            }
        }

        String previousSummary = existing != null ? existing.getSummary() : "（无）";

        String compressPrompt = """
                你是对话摘要压缩器。将以下金融对话压缩为简洁摘要。

                要求：
                1. 保留关键决策、结论、用户偏好、重要数据
                2. 压缩比约 10:1（10 条消息压缩为 1 段摘要）
                3. 如果之前有旧摘要，合并新旧内容，去重后输出完整摘要
                4. 用简洁的中文叙述，不要用 bullet point
                5. 保留用户提到的具体股票名称/代码

                之前的摘要：%s

                需要压缩的新对话：
                %s
                """.formatted(previousSummary, dialog.toString());

        try {
            ChatClient client = this.chatClient;
            AnthropicChatOptions options = AnthropicChatOptions.builder()
                    .thinking(AnthropicApi.ThinkingType.DISABLED, null)
                    .temperature(0.2)
                    .maxTokens(800)
                    .build();

            String summary = client.prompt()
                    .user(compressPrompt)
                    .options(options)
                    .call()
                    .content();

            // 保存摘要（合并旧摘要的情况：删除旧记录，保存新的完整摘要）
            if (existing != null) {
                summaryRepository.delete(existing);
            }
            memoryService.saveSummary(userId, conversationId, summary,
                    toCompress.get(0).getId(),
                    toCompress.get(batchSize - 1).getId(),
                    batchSize);

            log.info("[Memory] 对话摘要压缩完成, convId={}, compressedCount={}", conversationId, batchSize);

        } catch (Exception e) {
            log.error("[Memory] 对话摘要压缩失败, convId={}: {}", conversationId, e.getMessage());
        }
    }
}
