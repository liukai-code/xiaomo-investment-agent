package com.xiaomo.agent.yjb.service;

import com.xiaomo.agent.yjb.entity.YjbAccountCollect;
import com.xiaomo.agent.yjb.entity.YjbHolding;

import java.math.BigDecimal;
import java.util.List;

public interface YjbService {

    void syncHoldings(Long userId, String accountId, BigDecimal holdCost,
                      BigDecimal todayIncome, BigDecimal todayIncomeRate,
                      List<YjbHolding> holdings);

    List<YjbHolding> getHoldings(Long userId);

    List<YjbAccountCollect> getAccountSummary(Long userId);
}
