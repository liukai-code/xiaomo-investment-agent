package com.itlk.myclaudecode.yjb.service;

import com.itlk.myclaudecode.yjb.entity.YjbAccountCollect;
import com.itlk.myclaudecode.yjb.entity.YjbHolding;

import java.math.BigDecimal;
import java.util.List;

public interface YjbService {

    void syncHoldings(Long userId, String accountId, BigDecimal holdCost,
                      BigDecimal todayIncome, BigDecimal todayIncomeRate,
                      List<YjbHolding> holdings);

    List<YjbHolding> getHoldings(Long userId);

    List<YjbAccountCollect> getAccountSummary(Long userId);
}
