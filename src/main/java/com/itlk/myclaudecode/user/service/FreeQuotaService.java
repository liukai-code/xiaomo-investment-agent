package com.itlk.myclaudecode.user.service;

import com.itlk.myclaudecode.user.entity.User;
import com.itlk.myclaudecode.user.repository.UserRepository;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class FreeQuotaService {

    @Resource
    private UserRepository userRepository;

    /**
     * 查询用户剩余免费额度。无免费额度返回 0。
     */
    public long getRemainingQuota(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("[FreeQuota] 用户不存在, userId={}", userId);
            return 0;
        }
        Long quota = user.getFreeTokenQuota();
        Long used = user.getFreeTokenUsed();
        log.info("[FreeQuota] userId={}, quota={}, used={}", userId, quota, used);
        if (quota == null || quota <= 0) {
            return 0;
        }
        long usedVal = used != null ? used : 0L;
        return Math.max(0, quota - usedVal);
    }

    /**
     * 扣减免费额度。在每次对话完成后调用。
     */
    @Transactional
    public void deduct(Long userId, long consumed) {
        try {
            userRepository.findById(userId).ifPresent(user -> {
                long used = (user.getFreeTokenUsed() != null ? user.getFreeTokenUsed() : 0L) + consumed;
                user.setFreeTokenUsed(used);
                userRepository.save(user);
                log.info("[FreeQuota] 扣减成功: userId={}, consumed={}, used={}", userId, consumed, used);
            });
        } catch (Exception e) {
            log.warn("扣减免费额度失败, userId={}, consumed={}: {}", userId, consumed, e.getMessage());
        }
    }
}
