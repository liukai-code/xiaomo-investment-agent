package com.itlk.myclaudecode.yjb.service.impl;

import com.itlk.myclaudecode.yjb.entity.YjbAccountCollect;
import com.itlk.myclaudecode.yjb.entity.YjbHolding;
import com.itlk.myclaudecode.yjb.repository.YjbAccountCollectRepository;
import com.itlk.myclaudecode.yjb.repository.YjbHoldingRepository;
import com.itlk.myclaudecode.yjb.service.YjbService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class YjbServiceImpl implements YjbService {

    @Resource
    private YjbHoldingRepository holdingRepository;

    @Resource
    private YjbAccountCollectRepository accountCollectRepository;

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
    }

    @Override
    public List<YjbHolding> getHoldings(Long userId) {
        return holdingRepository.findByUserIdOrderByMoneyDesc(userId);
    }

    @Override
    public List<YjbAccountCollect> getAccountSummary(Long userId) {
        return accountCollectRepository.findByUserIdOrderBySyncedAtDesc(userId);
    }
}
