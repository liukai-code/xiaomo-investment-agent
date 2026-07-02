# 养基宝 API 接口文档

养基宝基金查询系统的完整 API 接口参考。

## 概述

养基宝提供两套 API 数据源：

| 数据源 | Base URL | 用途 | 认证 |
|--------|----------|------|------|
| A - 公网行情 | `https://app-api.yangjibao.com` | 基金净值、指数行情、板块排行 | 无需登录 |
| B - 个人持仓 | `http://browser-plug-api.yangjibao.com` | 个人持仓、账户汇总、收益分析 | 微信扫码登录 |

---

## 一、认证机制（数据源 B）

### 1.1 请求 Header

所有数据源 B 的接口需要 3 个自定义 Header：

| Header | 说明 | 示例 |
|--------|------|------|
| `Request-Time` | Unix 时间戳（秒） | `1719900000` |
| `Request-Sign` | MD5 签名 | `a1b2c3d4e5f6...` |
| `Authorization` | 登录 Token | `eyJhbGciOiJIUzI1...` |

### 1.2 签名算法

```
sign = MD5("" + path + token + timestamp + SECRET)
```

- `path`: URL 路径（不含 query string），如 `/fund_hold`
- `token`: 登录获取的 Token
- `timestamp`: 与 `Request-Time` 一致
- `SECRET`: `YxmKSrQR4uoJ5lOoWIhcbd7SlUEh9OOc`

**注意**: 签名时 path 不含 `?` 及之后的参数。缺少签名 Header 会返回 404（非 401）。

### 1.3 Token 获取流程

```
1. GET /qr_code                    → 获取二维码 ID 和 URL
2. 展示二维码给用户微信扫码
3. GET /qr_code_state/{qr_id}      → 轮询状态（state=1 等待，state=2 已扫码）
4. 保存返回的 Token 到本地
```

---

## 二、数据源 A - 公网行情接口

### 2.1 批量基金净值

批量查询多只基金的实时净值和估值。

**请求**
```
POST https://app-api.yangjibao.com/market/v1/fund/batch
Content-Type: application/json
User-Agent: YJB/2.0.4
```

**请求体**
```json
{
  "funds": [
    {"fund_id": 1359, "data_source": "1"},
    {"fund_id": 20864, "data_source": "1"}
  ]
}
```

**响应字段**

| 字段 | 说明 |
|------|------|
| `short_name` | 基金名称 |
| `code` | 基金代码 |
| `nv_info.dwjz` | 单位净值 |
| `nv_info.rzzl` | 日涨幅（%） |
| `nv_info.jzrq` | 净值日期 |
| `nv_info.vgszzl` | 估值涨幅（%） |
| `nv_info.true_valuation_date` | 真实估值日期 |
| `category` | 基金类型 |
| `year_increase_rate` | 年涨幅 |
| `market_type` | 交易市场 |

---

### 2.2 历史净值

查询单只基金的历史净值数据，支持分页。

**请求**
```
GET https://app-api.yangjibao.com/market/v1/fund-nav/fund-history-nav
    ?fund_id=1359
    &page=1
    &per_page=30
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| `fund_id` | int | 是 | 基金 ID |
| `page` | int | 否 | 页码，默认 1 |
| `per_page` | int | 否 | 每页条数，默认 30 |

---

### 2.3 指数行情

获取主要指数的实时行情数据。

**请求**
```
GET https://app-api.yangjibao.com/market/v1/quote/index-data
```

**响应**: 返回 A 股、港股、美股等主要指数的涨跌数据。

---

### 2.4 市场板块排行

获取市场板块涨跌排行。

**请求**
```
GET https://app-api.yangjibao.com/market/v1/market-ranking/list
```

---

### 2.5 基金关联估值

查询基金的关联估值数据。

**请求**
```
GET https://app-api.yangjibao.com/market/v1/fund/relation-gz-data
    ?id=1359
    &type=4
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| `id` | int | 是 | 基金 ID |
| `type` | int | 是 | 数据类型 |

---

### 2.6 当日市场信息

获取当日市场概况信息。

**请求**
```
GET https://wx.yangjibao.com/wxapi/day_info
```

---

## 三、数据源 B - 个人持仓接口

### 3.1 获取登录二维码

获取微信登录二维码。

**请求**
```
GET http://browser-plug-api.yangjibao.com/qr_code
```

**响应**
```json
{
  "id": "loginQrId_xxx",
  "url": "http://weixin.qq.com/q/xxx"
}
```

---

### 3.2 轮询扫码状态

轮询用户是否已完成扫码。

**请求**
```
GET http://browser-plug-api.yangjibao.com/qr_code_state/{qr_id}
```

**响应**

| state | 说明 |
|:-----:|------|
| 1 | 等待扫码 |
| 2 | 已扫码，返回 Token |

---

### 3.3 账户列表

获取用户的基金账户列表。

**请求**
```
GET http://browser-plug-api.yangjibao.com/user_account
```

**响应**
```json
{
  "list": [
    {"id": "xxx", "title": "账户名称", "count": 5}
  ]
}
```

---

### 3.4 账户汇总

获取账户的汇总统计数据。

**请求**
```
GET http://browser-plug-api.yangjibao.com/account_collect
```

**响应**
```json
{
  "account_data": [
    {
      "hold_cost": 10000.00,
      "today_income": 123.45,
      "today_income_rate": 1.23
    }
  ]
}
```

| 字段 | 说明 |
|------|------|
| `hold_cost` | 持仓成本（元） |
| `today_income` | 今日收益（元） |
| `today_income_rate` | 今日收益率（%） |

---

### 3.5 账户持仓明细

获取指定账户的基金持仓明细。

**请求**
```
GET http://browser-plug-api.yangjibao.com/fund_hold?account_id={id}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| `account_id` | string | 是 | 账户 ID |

**响应**
```json
[
  {
    "fund_id": 1359,
    "code": "270042",
    "short_name": "广发纳斯达克100ETF联接(QDII)A",
    "money": 5000.00,
    "hold_earn": 500.00,
    "hold_share": 1000.00,
    "hold_cost": 4.50,
    "cost_money": 4500.00,
    "hold_day": "2024-01-15",
    "category": "QDII",
    "market_type": "美股"
  }
]
```

**字段说明**

| 字段 | 说明 |
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

---

### 3.6 指数行情

获取主要指数行情（数据源 B 版本）。

**请求**
```
GET http://browser-plug-api.yangjibao.com/index_data
```

**响应**
```json
[
  {"name": "上证指数", "v": 3200.50, "dir": 1.25}
]
```

| 字段 | 说明 |
|------|------|
| `name` | 指数名称 |
| `v` | 当前价格 |
| `dir` | 涨跌幅（%） |

---

### 3.7 收益数据

获取收益统计数据和排名。

**请求**
```
GET http://browser-plug-api.yangjibao.com/income_data
    ?collect=true
    &date_type=day
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| `collect` | bool | 否 | 是否汇总 |
| `date_type` | string | 否 | 时间维度：`day`/`week`/`month` |

**响应**
```json
{
  "income": 123.45,
  "income_rate": 1.23,
  "ranking": 156
}
```

---

### 3.8 收益走势

获取收益走势图表数据。

**请求**
```
GET http://browser-plug-api.yangjibao.com/income_line_data
    ?collect=true
    &date_type=day
```

**响应**
```json
{
  "line_list": [
    {"time": "2024-01-15", "rate": 1.23},
    {"time": "2024-01-16", "rate": 1.45}
  ]
}
```

---

### 3.9 搜索基金

按关键词搜索基金。

**请求**
```
GET http://browser-plug-api.yangjibao.com/search_fund?keyword=纳斯达克
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| `keyword` | string | 是 | 搜索关键词 |

**响应**
```json
[
  {"fund_id": 1359, "short_name": "广发纳斯达克100ETF联接(QDII)A", "code": "270042"}
]
```

---

### 3.10 系统公告

获取系统公告信息。

**请求**
```
GET http://browser-plug-api.yangjibao.com/notice
```

**响应**
```json
{
  "notice_list": [...]
}
```

---

## 四、净值日期规则

| 基金类型 | 净值发布规则 |
|----------|-------------|
| A 股基金 | 净值日 = 最近交易日，当日 19:00-22:00 发布 |
| QDII 基金 | 净值滞后 2-3 个交易日，以 `true_valuation_date` 为准 |
| 估值 | `vgsz`/`vgszzl` 为盘中估值，A 股收盘后更新 |

**建议**: 20:00 后查询可获取完整当日净值。

---

## 五、错误处理

| 状态码 | 原因 | 解决方案 |
|:------:|------|----------|
| 404 | 缺少签名 Header | 确保 Request-Time、Request-Sign、Authorization 齐全 |
| 401 | Token 过期 | 重新扫码登录获取新 Token |
| 签名错误 | path 含 query string | 签名时 path 去掉 `?` 及之后部分 |

---

## 附录：基金 ID 映射

| fund_id | 代码 | 基金名称 |
|---------|------|----------|
| 1359 | 270042 | 广发纳斯达克100ETF联接(QDII)A |
| 20864 | 018043 | 天弘纳斯达克100指数(QDII)A |
| 19672 | 016452 | 南方纳斯达克100指数(QDII)A |
| 16480 | 012920 | 易方达全球成长精选混合(QDII)A |
| 14016 | 012349 | 天弘恒生科技ETF联接(QDII)C |
| 24108 | 021074 | 华夏中证沪深港黄金产业股票ETF联接A |
| 16202 | 012733 | 易方达人工智能ETF联接A |
| 29256 | 025857 | 华夏中证电网设备主题ETF联接C |
| 5736 | 007509 | 华商润丰灵活配置混合C |

---

## 附录：请求示例

### 批量查询净值（cURL）

```bash
curl -X POST https://app-api.yangjibao.com/market/v1/fund/batch \
  -H "Content-Type: application/json" \
  -H "User-Agent: YJB/2.0.4" \
  -d '{"funds": [{"fund_id": 1359, "data_source": "1"}]}'
```

### 查询持仓（Python）

```python
import hashlib
import time
import requests

token = "your_token"
secret = "YxmKSrQR4uoJ5lOoWIhcbd7SlUEh9OOc"
path = "/fund_hold"
timestamp = str(int(time.time()))

sign = hashlib.md5(f"{path}{token}{timestamp}{secret}".encode()).hexdigest()

headers = {
    "Request-Time": timestamp,
    "Request-Sign": sign,
    "Authorization": token
}

resp = requests.get(
    f"http://browser-plug-api.yangjibao.com{path}",
    headers=headers,
    params={"account_id": "your_account_id"}
)
print(resp.json())
```
