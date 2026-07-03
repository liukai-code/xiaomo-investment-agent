package com.itlk.myclaudecode.yjb.service.impl;

import com.itlk.myclaudecode.yjb.entity.YjbAccountCollect;
import com.itlk.myclaudecode.yjb.entity.YjbHolding;
import com.itlk.myclaudecode.yjb.repository.YjbAccountCollectRepository;
import com.itlk.myclaudecode.yjb.repository.YjbHoldingRepository;
import com.itlk.myclaudecode.yjb.service.YjbService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class YjbServiceImpl implements YjbService {

    private static final String HOLDINGS_KEY = "cache:yjb:holdings:";
    private static final String COLLECT_KEY = "cache:yjb:collect:";
    private static final long CACHE_TTL_MINUTES = 30;

    @Resource
    private YjbHoldingRepository holdingRepository;

    @Resource
    private YjbAccountCollectRepository accountCollectRepository;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    @Transactional
    public void syncHoldings(Long userId, String accountId, BigDecimal holdCost,
                             BigDecimal todayIncome, BigDecimal todayIncomeRate,
                             List<YjbHolding> holdings) {
        LocalDateTime now = LocalDateTime.now();

        // 删除旧数据
        holdingRepository.deleteByUserIdAndAccountId(userId, accountId);
        accountCollectRepository.deleteByUserIdAndAccountId(userId, accountId);

        // 插入账户汇总
        YjbAccountCollect collect = new YjbAccountCollect();
        collect.setUserId(userId);
        collect.setAccountId(accountId);
        collect.setHoldCost(holdCost);
        collect.setTodayIncome(todayIncome);
        collect.setTodayIncomeRate(todayIncomeRate);
        collect.setSyncedAt(now);
        accountCollectRepository.save(collect);

        // 插入持仓明细
        for (YjbHolding holding : holdings) {
            holding.setUserId(userId);
            holding.setAccountId(accountId);
            holding.setSyncedAt(now);
        }
        holdingRepository.saveAll(holdings);

        // 同步后清除缓存
        evictCache(userId);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<YjbHolding> getHoldings(Long userId) {
        String key = HOLDINGS_KEY + userId;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof YjbHolding) {
            log.debug("[YJB] 持仓缓存命中: userId={}", userId);
            return (List<YjbHolding>) (List<?>) list;
        }
        List<YjbHolding> holdings = holdingRepository.findByUserIdOrderByMoneyDesc(userId);
        redisTemplate.opsForValue().set(key, holdings, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        log.debug("[YJB] 持仓已缓存: userId={}, count={}", userId, holdings.size());
        return holdings;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<YjbAccountCollect> getAccountSummary(Long userId) {
        String key = COLLECT_KEY + userId;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof YjbAccountCollect) {
            log.debug("[YJB] 账户汇总缓存命中: userId={}", userId);
            return (List<YjbAccountCollect>) (List<?>) list;
        }
        List<YjbAccountCollect> collectList = accountCollectRepository.findByUserIdOrderBySyncedAtDesc(userId);
        redisTemplate.opsForValue().set(key, collectList, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        log.debug("[YJB] 账户汇总已缓存: userId={}, count={}", userId, collectList.size());
        return collectList;
    }

    private void evictCache(Long userId) {
        redisTemplate.delete(HOLDINGS_KEY + userId);
        redisTemplate.delete(COLLECT_KEY + userId);
        log.debug("[YJB] 缓存已清除: userId={}", userId);
    }
}
