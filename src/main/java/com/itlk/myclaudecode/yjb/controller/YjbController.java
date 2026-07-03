package com.itlk.myclaudecode.yjb.controller;

import com.itlk.myclaudecode.common.entity.Result;
import com.itlk.myclaudecode.yjb.entity.YjbHolding;
import com.itlk.myclaudecode.yjb.service.YjbService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/yjb")
public class YjbController {

    @Resource
    private YjbService yjbService;

    @PostMapping("/sync")
    @SuppressWarnings("unchecked")
    public Result<Void> syncHoldings(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error("未登录");
        }

        String accountId = String.valueOf(body.get("accountId"));
        if (accountId == null || accountId.isBlank()) {
            return Result.error("accountId 不能为空");
        }

        Map<String, Object> collectData = (Map<String, Object>) body.get("accountCollect");
        BigDecimal holdCost = collectData != null ? toBigDecimal(collectData.get("holdCost")) : BigDecimal.ZERO;
        BigDecimal todayIncome = collectData != null ? toBigDecimal(collectData.get("todayIncome")) : BigDecimal.ZERO;
        BigDecimal todayIncomeRate = collectData != null ? toBigDecimal(collectData.get("todayIncomeRate")) : BigDecimal.ZERO;

        List<Map<String, Object>> holdingsRaw = (List<Map<String, Object>>) body.get("holdings");
        List<YjbHolding> holdings = new java.util.ArrayList<>();
        if (holdingsRaw != null) {
            for (Map<String, Object> item : holdingsRaw) {
                YjbHolding h = new YjbHolding();
                h.setFundId(str(item.get("fund_id")));
                h.setCode(str(item.get("code")));
                h.setShortName(str(item.get("short_name")));
                h.setMoney(toBigDecimal(item.get("money")));
                h.setHoldEarn(toBigDecimal(item.get("hold_earn")));
                h.setHoldShare(toBigDecimal(item.get("hold_share")));
                h.setHoldCost(toBigDecimal(item.get("hold_cost")));
                h.setCostMoney(toBigDecimal(item.get("cost_money")));
                h.setHoldDay(str(item.get("hold_day")));
                h.setCategory(str(item.get("category")));
                h.setMarketType(str(item.get("market_type")));
                holdings.add(h);
            }
        }

        log.info("[YJB] 同步持仓数据: userId={}, accountId={}, 基金数={}", userId, accountId, holdings.size());
        yjbService.syncHoldings(userId, accountId, holdCost, todayIncome, todayIncomeRate, holdings);
        return Result.success();
    }

    private static BigDecimal toBigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof Number n) return new BigDecimal(n.toString());
        try {
            return new BigDecimal(val.toString());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private static String str(Object val) {
        return val != null ? val.toString() : null;
    }
}
