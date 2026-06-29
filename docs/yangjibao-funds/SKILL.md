---
name: yangjibao-funds
description: 通过养基宝 API 批量查询用户个人基金持仓数据（净值、估值、涨跌、板块行情）。当用户说"查我的基金"、"查持仓"、"基金净值"、"养基宝"、"基金涨跌"、"我的基金怎么样了"时触发。此 skill 直接调用养基宝后端接口，数据比通用金融数据源快 1-2 天且包含盘中估值。
agent_created: true
---

# 养基宝基金查询（完整版）

通过养基宝 API 实现完整的基金持仓管理：微信扫码登录 → 获取个人持仓 → 净值估算 → 数据分析。

## 触发的两套数据源

养基宝有两套 API，按需求选择：

### 数据源 A: 公网行情（无需登录）
- **Base URL**: `https://app-api.yangjibao.com`
- **用途**: 基金净值、指数行情、板块排行、历史净值
- **认证**: 无需 Token（部分接口需 User-Agent 伪装）
- **脚本**: `scripts/yjb_market.py`

### 数据源 B: 个人持仓（需微信扫码登录）
- **Base URL**: `http://browser-plug-api.yangjibao.com`
- **用途**: 个人持仓数据、账户汇总、收益分析
- **认证**: 微信扫码 → 获取 Token → 签名验证
- **脚本**: `scripts/yjb_login.py`（登录）+ `scripts/yjb_fetch.py`（拉取数据）

---

## 一、个人持仓数据（数据源 B）

### 1.1 认证机制

所有 authenticated API 需要 3 个自定义 Header：

| Header | 说明 |
|--------|------|
| `Request-Time` | Unix 时间戳 |
| `Request-Sign` | MD5 签名 |
| `Authorization` | 登录后的 Token |

**签名算法**:
```
sign = MD5("" + path + token + timestamp + SECRET)
SECRET = "YxmKSrQR4uoJ5lOoWIhcbd7SlUEh9OOc"
```
其中 `path` 为 URL 路径（不含 query string）。

**注意**: 不带签名 header 的请求会返回 404（而非 401），这是服务器设计行为。

### 1.2 QR 码登录流程

执行 `scripts/yjb_login.py` 或内联执行以下步骤：

1. **获取 QR 码**:
   ```
   GET http://browser-plug-api.yangjibao.com/qr_code
   ```
   返回 `{id: "loginQrIdxxx", url: "http://weixin.qq.com/q/xxx"}`

2. **展示 QR 码给用户扫描**:
   使用 qrcode 库生成 ASCII 二维码，或提供在线图片链接：
   `https://api.qrserver.com/v1/create-qr-code/?size=300x300&data={qr_url}`

3. **轮询登录状态**:
   ```
   GET http://browser-plug-api.yangjibao.com/qr_code_state/{qr_id}
   ```
   `state=1` 等待扫码，`state=2` 已扫码（含 token）

4. **保存 Token** 到 `~/.yjb_token.json`:
   ```json
   {"token": "xxx", "timestamp": 1234567890}
   ```

### 1.3 核心 API 接口（需认证）

| 路径 | 说明 | 返回关键字段 |
|------|------|------|
| `/user_account` | 账户列表 | `list[{id, title, count}]` |
| `/account_collect` | 账户汇总 | `account_data[{hold_cost, today_income, today_income_rate}]` |
| `/fund_hold?account_id={id}` | 账户持仓 | `[{fund_id, code, short_name, money, hold_earn, hold_share, hold_cost}]` |
| `/index_data` | 指数行情 | `[{name, v(price), dir(change%)}]` |
| `/income_data?collect=true&date_type=day` | 收益数据 | `{income, income_rate, ranking}` |
| `/income_line_data?collect=true&date_type=day` | 收益走势 | `{line_list[{time, rate}]}` |
| `/notice` | 系统公告 | `{notice_list}` |
| `/search_fund?keyword=xxx` | 搜索基金 | `[{fund_id, short_name, code}]` |

### 1.4 持仓数据字段说明

| 字段 | 含义 |
|------|------|
| `fund_id` | 基金内部 ID |
| `code` | 基金交易代码（6位） |
| `short_name` | 基金名称 |
| `money` | 当前市值（元） |
| `hold_earn` | 持仓盈亏（元） |
| `hold_share` | 持有份额 |
| `hold_cost` | 持仓成本单价 |
| `cost_money` | 投入本金 |
| `hold_day` | 首次买入日期 |
| `category` | 基金类型 |
| `market_type` | 交易市场 |

### 1.5 使用流程

**首次使用**:
1. 运行 `scripts/yjb_login.py` 生成二维码
2. 微信扫码登录
3. Token 自动保存，后续无需重复登录

**日常查询**:
- 直接执行 `scripts/yjb_fetch.py` 获取持仓汇总
- 或使用内联 Python 代码调用 API

**Token 过期时**:
- 重新运行 `scripts/yjb_login.py` 扫码登录

### 1.6 数据分析与呈现

获取持仓数据后，按以下格式汇总展示：

1. **账户汇总**：总资产、今日收益、收益率
2. **指数行情**：A股/港股/美股 12 个主要指数
3. **持仓明细表**：基金名称、代码、市值、盈亏（🔴🟢标识）、份额、成本
4. **汇总统计**：总市值、总盈亏

---

## 二、公网行情数据（数据源 A）

### 2.1 批量基金净值（无需登录）

```
POST https://app-api.yangjibao.com/market/v1/fund/batch
Content-Type: application/json
User-Agent: YJB/2.0.4
Body: {"funds": [{"fund_id": 1359, "data_source": "1"}, ...]}
```

基金 ID 映射见 `references/fund-mapping.md`。

### 2.2 返回字段

**nv_info**: `dwjz`(净值), `rzzl`(日涨幅), `jzrq`(净值日), `vgszzl`(估值涨幅), `true_valuation_date`(真实估值日)
**其他**: `short_name`, `code`, `category`, `year_increase_rate`, `sector_info.name/ratio`, `market_type`, `heavy_position_info`

### 2.3 历史净值 & 指数

```
GET https://app-api.yangjibao.com/market/v1/fund-nav/fund-history-nav?fund_id=X&page=1&per_page=30
GET https://app-api.yangjibao.com/market/v1/quote/index-data
GET https://app-api.yangjibao.com/market/v1/market-ranking/list
GET https://app-api.yangjibao.com/market/v1/fund/relation-gz-data?id=X&type=4
GET https://wx.yangjibao.com/wxapi/day_info
```

---

## 三、净值日期规则

1. **A股基金**: 净值日=最近交易日，当日 19:00-22:00 发布
2. **QDII 基金**: 净值滞后 2-3 个交易日，以 `true_valuation_date` 为准
3. **估值**: `vgsz`/`vgszzl` 为当日盘中估值，A股收盘后更新
4. **建议**: 20:00 后查询可获取完整当日净值

---

## 四、已知问题与排错

| 问题 | 原因 | 解决 |
|------|------|------|
| API 返回 404 | 缺少签名 Header | 确保 Request-Time、Request-Sign、Authorization 三个 Header 齐全 |
| Token 401 未授权 | Token 过期 | 重新运行 `scripts/yjb_login.py` 扫码 |
| 签名错误 | 签名 path 含 query string | 签名时 path 去掉 `?` 及之后部分 |
