package com.itlk.myclaudecode.yjb.controller;

import com.itlk.myclaudecode.common.entity.Result;
import com.itlk.myclaudecode.yjb.entity.YjbAccountCollect;
import com.itlk.myclaudecode.yjb.entity.YjbHolding;
import com.itlk.myclaudecode.yjb.service.YjbApiClient;
import com.itlk.myclaudecode.yjb.service.YjbService;
import com.itlk.myclaudecode.yjb.service.YjbTokenStore;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/yjb")
public class YjbController {

    @Resource
    private YjbService yjbService;

    @Resource
    private YjbApiClient yjbApiClient;

    @Resource
    private YjbTokenStore yjbTokenStore;

    // ==================== QR 登录代理 ====================

    @GetMapping("/qr-code")
    public Result<?> getQrCode() {
        try {
            YjbApiClient.QrCodeResponse qr = yjbApiClient.getQrCode();
            Map<String, String> data = new HashMap<>();
            data.put("id", qr.id);
            data.put("url", qr.url);
            return Result.success(data);
        } catch (Exception e) {
            log.error("[YJB] 获取二维码失败", e);
            return Result.error("获取二维码失败: " + e.getMessage());
        }
    }

    @GetMapping("/qr-state/{qrId}")
    public Result<?> getQrState(@PathVariable String qrId) {
        try {
            YjbApiClient.QrCodeStateResponse state = yjbApiClient.getQrCodeState(qrId);
            Map<String, Object> data = new HashMap<>();
            data.put("state", state.state);
            data.put("token", state.token);
            return Result.success(data);
        } catch (Exception e) {
            log.error("[YJB] 查询扫码状态失败", e);
            return Result.error("查询扫码状态失败: " + e.getMessage());
        }
    }

    // ==================== Token 管理 ====================

    @PostMapping("/token")
    public Result<Void> saveToken(@RequestBody Map<String, String> body, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error("未登录");
        }
        String token = body.get("token");
        if (token == null || token.isBlank()) {
            return Result.error("token 不能为空");
        }
        yjbTokenStore.saveToken(userId, token);
        log.info("[YJB] 保存 token: userId={}", userId);
        return Result.success();
    }

    @GetMapping("/status")
    public Result<?> getStatus(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error("未登录");
        }
        boolean loggedIn = yjbTokenStore.hasToken(userId);
        Map<String, Boolean> data = Map.of("loggedIn", loggedIn);
        return Result.success(data);
    }

    // ==================== 数据同步 ====================

    @PostMapping("/sync")
    public Result<?> syncHoldings(@RequestParam(required = false) String accountId,
                                  HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error("未登录");
        }

        String yjbToken = yjbTokenStore.getToken(userId);
        if (yjbToken == null) {
            return Result.error("养基宝未登录，请先扫码");
        }

        try {
            // 获取账户列表
            List<YjbApiClient.UserAccountResponse> accounts = yjbApiClient.getUserAccounts(yjbToken);
            if (accounts.isEmpty()) {
                return Result.error("养基宝无账户数据");
            }

            // 确定要同步的账户
            String syncAccountId = accountId;
            if (syncAccountId == null || syncAccountId.isBlank()) {
                syncAccountId = accounts.get(0).id;
            }

            // 拉取数据
            YjbApiClient.AccountCollectResponse collect = yjbApiClient.getAccountCollect(yjbToken, syncAccountId);
            List<YjbApiClient.FundHoldResponse> fundHolds = yjbApiClient.getFundHoldings(yjbToken, syncAccountId);

            // 转为实体
            BigDecimal holdCost = collect.holdCost != null ? collect.holdCost : BigDecimal.ZERO;
            BigDecimal todayIncome = collect.todayIncome != null ? collect.todayIncome : BigDecimal.ZERO;
            BigDecimal todayIncomeRate = collect.todayIncomeRate != null ? collect.todayIncomeRate : BigDecimal.ZERO;

            List<YjbHolding> holdings = fundHolds.stream().map(item -> {
                YjbHolding h = new YjbHolding();
                h.setFundId(item.fundId);
                h.setCode(item.code);
                h.setShortName(item.shortName);
                h.setMoney(item.money != null ? item.money : BigDecimal.ZERO);
                h.setHoldEarn(item.holdEarn != null ? item.holdEarn : BigDecimal.ZERO);
                h.setHoldShare(item.holdShare != null ? item.holdShare : BigDecimal.ZERO);
                h.setHoldCost(item.holdCost != null ? item.holdCost : BigDecimal.ZERO);
                h.setCostMoney(item.costMoney != null ? item.costMoney : BigDecimal.ZERO);
                h.setHoldDay(item.holdDay);
                h.setCategory(item.category);
                h.setMarketType(item.marketType);
                return h;
            }).toList();

            log.info("[YJB] 同步持仓数据: userId={}, accountId={}, 基金数={}", userId, syncAccountId, holdings.size());
            yjbService.syncHoldings(userId, syncAccountId, holdCost, todayIncome, todayIncomeRate, holdings);

            // 返回数据给前端
            Map<String, Object> result = new HashMap<>();
            result.put("accounts", accounts);
            result.put("accountCollect", Map.of(
                    "hold_cost", holdCost,
                    "today_income", todayIncome,
                    "today_income_rate", todayIncomeRate
            ));
            result.put("holdings", holdings);
            result.put("selectedAccountId", syncAccountId);
            return Result.success(result);

        } catch (Exception e) {
            log.error("[YJB] 同步持仓失败: userId={}", userId, e);
            // token 可能过期，清除
            if (e.getMessage() != null && e.getMessage().contains("code=")) {
                yjbTokenStore.removeToken(userId);
                return Result.error("养基宝登录已过期，请重新扫码");
            }
            return Result.error("同步失败: " + e.getMessage());
        }
    }

    // ==================== 读取已同步数据 ====================

    @GetMapping("/holdings")
    public Result<?> getHoldings(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error("未登录");
        }
        List<YjbHolding> holdings = yjbService.getHoldings(userId);
        List<YjbAccountCollect> collectList = yjbService.getAccountSummary(userId);
        Map<String, Object> data = new HashMap<>();
        data.put("holdings", holdings);
        data.put("accountCollect", collectList.isEmpty() ? null : collectList.get(0));
        return Result.success(data);
    }
}
