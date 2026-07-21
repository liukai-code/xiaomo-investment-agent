package com.itlk.myclaudecode.memory.service.impl;

import com.itlk.myclaudecode.memory.entity.*;
import com.itlk.myclaudecode.memory.repository.ConversationSummaryRepository;
import com.itlk.myclaudecode.memory.repository.UserProfileRepository;
import com.itlk.myclaudecode.memory.service.MemoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class MemoryServiceImpl implements MemoryService {

    private static final String PROFILE_CACHE_PREFIX = "memory:profile:";
    private static final String SUMMARY_CACHE_PREFIX = "memory:summary:";
    private static final long PROFILE_CACHE_TTL_MINUTES = 30;
    private static final long SUMMARY_CACHE_TTL_MINUTES = 10;

    /** 画像记忆 token 预算 */
    private static final int PROFILE_TOKEN_BUDGET = 500;
    /** 对话摘要 token 预算 */
    private static final int SUMMARY_TOKEN_BUDGET = 800;
    /** 每用户最大画像记忆数 */
    private static final int MAX_PROFILES_PER_USER = 50;

    /** 用户主动记忆的正则模式 */
    private static final Pattern EXPLICIT_MEMORY_PATTERN = Pattern.compile(
            "(?:记住|记一下|帮我记|请记住|请记下|记着|别忘了|帮我记住)[:：]?\\s*(.+)",
            Pattern.DOTALL);

    @Resource
    private UserProfileRepository userProfileRepository;

    @Resource
    private ConversationSummaryRepository summaryRepository;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    // ====== 用户画像记忆 ======

    @Override
    public List<UserProfile> getActiveProfiles(Long userId) {
        String key = PROFILE_CACHE_PREFIX + userId;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached instanceof List<?> list && !list.isEmpty()
                && list.get(0) instanceof UserProfile) {
            return (List<UserProfile>) (List<?>) list;
        }

        List<UserProfile> profiles = userProfileRepository
                .findByUserIdAndActiveTrueOrderByImportanceDesc(userId);
        redisTemplate.opsForValue().set(key, profiles, PROFILE_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        return profiles;
    }

    @Override
    public List<UserProfile> getProfilesByCategory(Long userId, ProfileCategory category) {
        return userProfileRepository.findByUserIdAndCategoryAndActiveTrue(userId, category);
    }

    @Override
    public UserProfile addUserMemory(Long userId, String content, ProfileCategory category,
                                      Long conversationId) {
        // 检查数量上限
        long activeCount = userProfileRepository.countActiveByUserId(userId);
        if (activeCount >= MAX_PROFILES_PER_USER) {
            log.warn("[Memory] 用户 {} 画像记忆已达上限 {} 条", userId, MAX_PROFILES_PER_USER);
            // 尝试找到最低重要性的记忆进行替换
            List<UserProfile> lowest = userProfileRepository.findTopByUserId(userId, MAX_PROFILES_PER_USER);
            if (!lowest.isEmpty()) {
                UserProfile toReplace = lowest.get(lowest.size() - 1);
                toReplace.setActive(false);
                userProfileRepository.save(toReplace);
            }
        }

        UserProfile profile = new UserProfile();
        profile.setUserId(userId);
        profile.setCategory(category);
        profile.setContent(content);
        profile.setImportance(5); // 用户主动添加的默认最高重要性
        profile.setSourceType(MemorySourceType.USER_EXPLICIT);
        profile.setConversationId(conversationId);
        profile.setActive(true);

        UserProfile saved = userProfileRepository.save(profile);
        evictProfileCache(userId);
        log.info("[Memory] 用户 {} 主动添加画像记忆: [{}] {}", userId, category, content);
        return saved;
    }

    @Override
    public UserProfile updateProfile(Long userId, Long profileId, String content, Integer importance) {
        UserProfile profile = userProfileRepository.findById(profileId)
                .orElseThrow(() -> new IllegalArgumentException("记忆不存在: " + profileId));
        if (!profile.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权修改他人的记忆");
        }
        if (content != null) profile.setContent(content);
        if (importance != null) profile.setImportance(importance);

        UserProfile saved = userProfileRepository.save(profile);
        evictProfileCache(userId);
        return saved;
    }

    @Override
    public void deleteProfile(Long userId, Long profileId) {
        UserProfile profile = userProfileRepository.findById(profileId)
                .orElseThrow(() -> new IllegalArgumentException("记忆不存在: " + profileId));
        if (!profile.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权删除他人的记忆");
        }
        userProfileRepository.delete(profile);
        evictProfileCache(userId);
    }

    // ====== 对话摘要 ======

    @Override
    public ConversationSummary getLatestSummary(Long conversationId) {
        String key = SUMMARY_CACHE_PREFIX + conversationId;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached instanceof ConversationSummary summary) {
            return summary;
        }

        ConversationSummary summary = summaryRepository.findLatestByConversationId(conversationId);
        if (summary != null) {
            redisTemplate.opsForValue().set(key, summary, SUMMARY_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        }
        return summary;
    }

    @Override
    public ConversationSummary saveSummary(Long userId, Long conversationId, String summary,
                                           Long fromMessageId, Long toMessageId, int compressedCount) {
        ConversationSummary entity = new ConversationSummary();
        entity.setUserId(userId);
        entity.setConversationId(conversationId);
        entity.setSummary(summary);
        entity.setFromMessageId(fromMessageId);
        entity.setToMessageId(toMessageId);
        entity.setCompressedCount(compressedCount);

        ConversationSummary saved = summaryRepository.save(entity);
        evictSummaryCache(conversationId);
        log.info("[Memory] 保存对话摘要: convId={}, compressedCount={}", conversationId, compressedCount);
        return saved;
    }

    // ====== System Prompt 注入 ======

    @Override
    public String buildMemoryPrompt(Long userId, Long conversationId) {
        StringBuilder sb = new StringBuilder();

        // 1. 用户画像记忆
        List<UserProfile> profiles = getActiveProfiles(userId);
        if (!profiles.isEmpty()) {
            sb.append("\n\n[用户画像记忆]\n");
            sb.append("以下是该用户的历史偏好信息，请在回答时参考，但不要直接提及你记住了这些：\n");
            int tokenBudget = 0;
            for (UserProfile p : profiles) {
                String line = "- [" + p.getCategory().getDisplayName() + "] " + p.getContent();
                int lineTokens = estimateTokens(line);
                if (tokenBudget + lineTokens > PROFILE_TOKEN_BUDGET) break;
                sb.append(line).append("\n");
                tokenBudget += lineTokens;
            }
        }

        // 2. 对话摘要
        ConversationSummary summary = getLatestSummary(conversationId);
        if (summary != null) {
            sb.append("\n\n[对话历史摘要]\n");
            sb.append("以下是本会话早期被压缩的对话摘要，用于保持上下文连续性：\n");
            String summaryText = summary.getSummary();
            if (estimateTokens(summaryText) > SUMMARY_TOKEN_BUDGET) {
                summaryText = truncateToTokenLimit(summaryText, SUMMARY_TOKEN_BUDGET);
            }
            sb.append(summaryText);
        }

        return sb.toString();
    }

    // ====== 用户主动触发检测 ======

    @Override
    public DetectResult detectExplicitMemory(String message) {
        if (message == null || message.isBlank()) {
            return new DetectResult(false, null, null);
        }
        Matcher matcher = EXPLICIT_MEMORY_PATTERN.matcher(message.trim());
        if (!matcher.find()) {
            return new DetectResult(false, null, null);
        }

        String content = matcher.group(1).trim();
        if (content.isEmpty()) {
            return new DetectResult(false, null, null);
        }

        // 根据内容推断类别
        ProfileCategory category = inferCategory(content);
        return new DetectResult(true, content, category);
    }

    // ====== 内部方法 ======

    private ProfileCategory inferCategory(String content) {
        String lower = content.toLowerCase();
        if (containsAny(lower, "风格", "价值投资", "趋势", "打板", "量化", "短线", "中线", "长线")) {
            return ProfileCategory.INVESTMENT_STYLE;
        }
        if (containsAny(lower, "风险", "保守", "稳健", "激进", "高风险", "低风险")) {
            return ProfileCategory.RISK_PREFERENCE;
        }
        if (containsAny(lower, "板块", "行业", "半导体", "新能源", "消费", "医药", "科技", "赛道")) {
            return ProfileCategory.FOCUS_SECTOR;
        }
        if (containsAny(lower, "持仓", "仓位", "仓位管理", "止盈", "止损")) {
            return ProfileCategory.HOLDING_HABIT;
        }
        if (containsAny(lower, "经验", "新手", "进阶", "资深", "入门")) {
            return ProfileCategory.EXPERIENCE_LEVEL;
        }
        return ProfileCategory.GENERAL;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    private int estimateTokens(String text) {
        if (text == null) return 0;
        int chineseChars = 0;
        int otherChars = 0;
        for (char c : text.toCharArray()) {
            if (c >= '一' && c <= '鿿') {
                chineseChars++;
            } else {
                otherChars++;
            }
        }
        return (int) (chineseChars * 1.5 + otherChars * 0.3);
    }

    private String truncateToTokenLimit(String text, int tokenLimit) {
        int currentTokens = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            currentTokens += (c >= '一' && c <= '鿿') ? 2 : 1;
            if (currentTokens > tokenLimit) {
                return text.substring(0, i) + "...";
            }
        }
        return text;
    }

    private void evictProfileCache(Long userId) {
        redisTemplate.delete(PROFILE_CACHE_PREFIX + userId);
    }

    private void evictSummaryCache(Long conversationId) {
        redisTemplate.delete(SUMMARY_CACHE_PREFIX + conversationId);
    }
}
