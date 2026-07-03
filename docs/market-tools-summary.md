# 行情工具汇总

> 最后更新: 2026-07-03 | 共 9 个工具类, 38 个操作, 覆盖 10 层数据架构

---

## 目录

1. [架构总览](#架构总览)
2. [基础设施层](#基础设施层)
3. [行情层 — AStockQuoteRouterTool](#行情层)
4. [研报层 — AStockReportRouterTool](#研报层)
5. [信号层 — AStockSignalRouterTool](#信号层)
6. [资金面 — AStockCapitalRouterTool](#资金面)
7. [新闻公告 — AStockNewsRouterTool](#新闻公告)
8. [打板层 — AStockLimitUpRouterTool](#打板层)
9. [期权层 — AStockOptionRouterTool](#期权层)
10. [舆情层 — AStockSentimentRouterTool](#舆情层)
11. [基础行情 — FinancialDataTool](#基础行情)
12. [配置说明](#配置说明)

---

## 架构总览

```
┌─────────────────────────────────────────────────────────────┐
│                     AgentLoopImpl                           │
│  chat / chatStream → ChatClient → ToolCallback 拦截链       │
└──────────┬──────────────────────────────────────────────────┘
           │
    ┌──────▼──────┐
    │ ToolConfig  │  ← @Bean 注册所有工具
    └──────┬──────┘
           │
    ┌──────▼──────────────────────────────────────────────┐
    │              8 个 AStock Router Tool                 │
    │                                                     │
    │  Quote ─┐  Report ─┐  Signal ─┐  Capital ─┐       │
    │  News ──┤  LimitUp ─┤  Option ─┤  Sentiment─┘       │
    └───────────────────┬────────────────────────────────┘
                        │
           ┌────────────▼────────────┐
           │  EastMoneyRateLimiter   │  ← synchronized 串行限流
           │  + HttpClientService    │  ← OkHttp 4.12.0 重试/熔断
           └────────────────────────┘
```

**数据源清单 (13个):**

| 序号 | 数据源 | 域名 | 用途 |
|------|--------|------|------|
| 1 | 腾讯财经 | qt.gtimg.cn | 实时行情 |
| 2 | 东方财富 | push2/push2his/datacenter-web 等 | 行情/资金/龙虎榜 |
| 3 | 百度股市通 | finance.pae.baidu.com | K线数据 |
| 4 | 新浪财经 | hq.sinajs.cn / quotes.sina.cn | 期权/财报 |
| 5 | 同花顺 | data.10jqka.com.cn / dq.10jqka.com.cn / basic.10jqka.com.cn | 涨停/热榜/预期 |
| 6 | 天天基金 | fundgz.1234567.com.cn | 养基宝 |
| 7 | 巨潮资讯 | cninfo.com.cn / irm.cninfo.com.cn | 公告/互动易 |
| 8 | 和讯 | data.hexin.cn | 北向资金 |
| 9 | i问财 | openapi.iwencai.com | 语义搜索 (需 API Key) |
| 10 | 东财研报 | reportapi.eastmoney.com | 研报数据 |
| 11 | 东财PDF | pdf.dfcfw.com | 研报 PDF 下载 |
| 12 | 东财人气 | emappdata.eastmoney.com | 人气榜/概念命中 |
| 13 | 东财行情 | stock.finance.sina.com.cn | 期权合约清单 |

---

## 基础设施层

### EastMoneyRateLimiter

所有东财系接口统一经过此限流器, 防止触发风控。

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `astock.eastmoney.min-interval-ms` | 1000 | 最小请求间隔(ms) |
| `astock.eastmoney.jitter-max-ms` | 500 | 随机抖动上限(ms) |
| `astock.eastmoney.connect-timeout-seconds` | 10 | 连接超时(s) |
| `astock.eastmoney.read-timeout-seconds` | 15 | 读取超时(s) |

**限流策略:**
- `synchronized` 串行, 两次请求间隔 >= 1s + [0, 500ms) 随机抖动
- 429/5xx 指数退避重试 3 次 (600ms / 1200ms / 2400ms)
- 403 不重试 (风控信号, 直接抛异常)
- 固定 UA: `Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36`
- 独立 OkHttpClient (连接池 5 / Keep-Alive 2min)

### AStockUtils

工具方法类:

| 方法 | 说明 | 示例 |
|------|------|------|
| `normalizeCode(code)` | 去除 SH/SZ/BJ 前缀和 .SH/.SZ 后缀 | `"SH600519"` → `"600519"` |
| `toEastmoneySecId(code)` | 转东财 secid 格式 | `"600519"` → `"1.600519"` |
| `toMarketPrefix(code)` | 转市场前缀格式 | `"600519"` → `"sh600519"` |
| `formatYi(value)` | 金额格式化 (亿元) | `1234567890` → `"12.35亿"` |
| `formatWan(value)` | 金额格式化 (万元) | `1234567890` → `"123456.79万"` |

---

## 行情层

**工具类:** `AStockQuoteRouterTool`  
**入口方法:** `a_stock_quote(operation, params)`  
**数据源:** 腾讯财经 / 百度股市通

| Operation | 说明 | 参数 | 数据源 |
|-----------|------|------|--------|
| `tencentQuote` | 批量股票/指数/ETF 实时行情 | `codes`: 代码列表 (如 `"600519,000858"`) | qt.gtimg.cn |
| `baiduKline` | K线 + MA5/10/20 均线 | `code`: 股票代码, `period`: kline(日)/week(周)/month(月) | finance.pae.baidu.com |

**tencentQuote 返回字段:** 代码、名称、最新价、涨跌幅、成交量、成交额、PE(TTM)、PB、总市值、流通市值、换手率、振幅、量比、今开、昨收、最高、最低

**baiduKline 返回字段:** 日期、开盘、收盘、最高、最低、成交量、MA5、MA10、MA20

---

## 研报层

**工具类:** `AStockReportRouterTool`  
**入口方法:** `a_stock_report(operation, params)`  
**数据源:** 东财研报 / 同花顺 / i问财

| Operation | 说明 | 参数 | 数据源 |
|-----------|------|------|--------|
| `stockReport` | 个股研报列表 | `stockCode`, `pageSize`(默认10) | reportapi.eastmoney.com |
| `industryReport` | 行业研报 | `industryName`, `pageSize`(默认10) | reportapi.eastmoney.com |
| `downloadReportPdf` | 研报 PDF 下载 (返回摘要) | `pdfUrl` | pdf.dfcfw.com |
| `thsEpsForecast` | 一致预期 EPS | `stockCode` | basic.10jqka.com.cn (Jsoup 解析) |
| `iwencaiSearch` | NL 语义搜索研报 | `query`: 自然语言查询 | openapi.iwencai.com |
| `iwencaiQuery` | NL 结构化查询 | `query`: 同上 | openapi.iwencai.com |

**stockReport 返回字段:** 标题、作者、机构、评级、目标价、研报日期、摘要

**thsEpsForecast 返回字段:** 年份、EPS预测值、一致预期变动、预测机构数

**iwencai 需要配置 `IWENCAI_API_KEY` 环境变量**, 为空时返回提示。

---

## 信号层

**工具类:** `AStockSignalRouterTool`  
**入口方法:** `a_stock_signal(operation, params)`  
**数据源:** 东财行情 / 东财数据中心 / 同花顺

| Operation | 说明 | 参数 | 数据源 |
|-----------|------|------|--------|
| `conceptBlocks` | 个股板块归属 | `stockCode` | push2.eastmoney.com/api/qt/slist |
| `fundFlowMinute` | 分钟级资金流 | `stockCode` | push2.eastmoney.com/api/qt/stock/fflow/kline |
| `dragonTigerBoard` | 龙虎榜席位明细 | `stockCode`, `date`(可选, YYYYMMDD) | datacenter-web.eastmoney.com |
| `dailyDragonTiger` | 全市场龙虎榜 | `date`(可选) | datacenter-web.eastmoney.com |
| `lockupExpiry` | 限售解禁日历 | `stockCode`(可选), `startDate`, `endDate` | datacenter-web.eastmoney.com |
| `industryRanking` | 行业板块排名 | `top`(默认20) | push2.eastmoney.com/api/qt/clist |

**dragonTigerBoard 特殊说明:** 发起 3 次请求 — 交易记录 + 买入席位 + 席位, 结果合并展示。

**conceptBlocks 返回字段:** 板块名称、板块代码、板块类型 (概念/行业/地域)

**fundFlowMinute 返回字段:** 时间、主力净流入、超大单净流入、大单净流入、中单净流入、小单净流入

**industryRanking 返回字段:** 排名、行业名称、涨跌幅、领涨股、主力净流入

---

## 资金面

**工具类:** `AStockCapitalRouterTool`  
**入口方法:** `a_stock_capital(operation, params)`  
**数据源:** 东财数据中心 / 东财历史 / 和讯

| Operation | 说明 | 参数 | 数据源 |
|-----------|------|------|--------|
| `marginTrading` | 融资融券明细 | `stockCode`, `startDate`, `endDate` | datacenter-web.eastmoney.com |
| `blockTrade` | 大宗交易 | `stockCode`, `startDate`, `endDate` | datacenter-web.eastmoney.com |
| `holderNumChange` | 股东户数变化 | `stockCode`, `startDate`, `endDate` | datacenter-web.eastmoney.com |
| `dividendHistory` | 分红送转历史 | `stockCode` | datacenter-web.eastmoney.com |
| `fundFlow120d` | 120 日资金流 | `stockCode` | push2his.eastmoney.com |
| `northboundFlow` | 北向资金 (实时 + 历史) | `date`(可选) | data.hexin.cn |

**northboundFlow 缓存策略:** Redis key `astock:northbound:{date}`, TTL 30 天, 每次调用存当日快照。

**marginTrading 返回字段:** 日期、融资余额、融资买入额、融券余额、融券卖出量、融资融券余额

**blockTrade 返回字段:** 交易日期、买方营业部、卖方营业部、成交价、成交量、成交额、折溢率

**holderNumChange 返回字段:** 日期、股东户数、户均持股、变动幅度、变动户数

---

## 新闻公告

**工具类:** `AStockNewsRouterTool`  
**入口方法:** `a_stock_news(operation, params)`  
**数据源:** 东财搜索 / 东财资讯 / 巨潮资讯 / 新浪

| Operation | 说明 | 参数 | 数据源 |
|-----------|------|------|--------|
| `stockNews` | 个股新闻 | `stockCode`, `pageSize`(默认10) | search-api-web.eastmoney.com |
| `globalNews` | 全球财经资讯 | `pageSize`(默认10) | np-weblist.eastmoney.com |
| `cninfoAnnouncements` | 公告全文 | `stockCode`, `pageSize`(默认10) | cninfo.com.cn |
| `irmQA` | 互动易问答 | `stockCode`, `pageSize`(默认10) | irm.cninfo.com.cn |
| `sinaFinancialReport` | 财报三表 | `stockCode`, `reportType`(balance/income/cashflow) | quotes.sina.cn |

**stockNews 特殊说明:** 东财返回 JSONP 格式 `jQuery_xxx({...})`, 需截取 JSON 部分解析。

**cninfoAnnouncements 特殊说明:** 使用 `ConcurrentHashMap` 懒加载 orgId 映射 (6198 只股), 首次调用从 `szse_stock.json` 加载。

**irmQA 特殊说明:** 两步请求 — 先 `queryKeyboardInfo` 获取 company 信息, 再请求问答列表。

**sinaFinancialReport 返回字段:**
- `balance`: 资产负债表 (流动资产/非流动资产/流动负债/非流动负债)
- `income`: 利润表 (营业总收入/营业总成本/净利润)
- `cashflow`: 现金流量表 (经营活动/投资活动/筹资活动)

---

## 打板层

**工具类:** `AStockLimitUpRouterTool`  
**入口方法:** `a_stock_limit_up(operation, params)`  
**数据源:** 东财盘中 / 同花顺

| Operation | 说明 | 参数 | 数据源 |
|-----------|------|------|--------|
| `ztPool` | 涨停池 (当日) | `date`(可选) | push2ex.eastmoney.com |
| `zbPool` | 炸板池 (当日) | `date`(可选) | push2ex.eastmoney.com |
| `dtPool` | 跌停池 (当日) | `date`(可选) | push2ex.eastmoney.com |
| `yztPool` | 昨涨停池 (昨日涨停今日表现) | `date`(可选) | push2ex.eastmoney.com |
| `thsLimitUpPool` | 涨停揭秘 (含题材归因) | `date`(可选) | data.10jqka.com.cn |
| `sentimentOverview` | 打板情绪速算 | 无需参数 | 组合 ztPool+zbPool+dtPool |

**sentimentOverview 计算逻辑:**
- 炸板率 = 炸板数 / (涨停数 + 炸板数)
- 连板梯队: 统计 2 连板、3 连板...的个股数量
- 涨停/跌停比: 反映市场多空情绪

**ztPool 返回字段:** 代码、名称、涨停时间、封单额、连板天数、首次涨停时间、最后涨停时间、涨停原因

---

## 期权层

**工具类:** `AStockOptionRouterTool`  
**入口方法:** `a_stock_option(operation, params)`  
**数据源:** 新浪财经

| Operation | 说明 | 参数 | 数据源 |
|-----------|------|------|--------|
| `optionCodes` | ETF 期权合约清单 | `underlying`(510050/510300/588000/510500), `call`(true=认购/false=认沽) | stock.finance.sina.com.cn |
| `optionTQuote` | T 型报价 | `contractCode` (如 "10004857") | hq.sinajs.cn |
| `optionGreeks` | 希腊字母 + 隐含波动率 | `contractCode` | hq.sinajs.cn |

**optionTQuote 返回字段:** 名称、最新价、涨跌幅、买一/卖一 (价量)、行权价、持仓量、今开/昨收/最高/最低、成交量/成交额

**optionGreeks 返回字段:** 名称、Delta、Gamma、Theta、Vega、隐含波动率(IV)、行权价、最新价、理论价值

**编码说明:** 新浪接口返回 GBK 编码, 使用 `Charset.forName("GBK")` 解码。

---

## 舆情层

**工具类:** `AStockSentimentRouterTool`  
**入口方法:** `a_stock_sentiment(operation, params)`  
**数据源:** 同花顺 / 东财人气

| Operation | 说明 | 参数 | 数据源 |
|-----------|------|------|--------|
| `thsHotList` | 同花顺热榜 (人气+概念标签) | `period`("hour" 或 "day", 默认 "hour") | dq.10jqka.com.cn |
| `emHotRank` | 东财人气榜 | `top` (前N名, 默认50) | emappdata.eastmoney.com |
| `emConceptHit` | 个股概念命中 (市场归类+热度) | `stockCode` | emappdata.eastmoney.com |

**thsHotList 返回字段:** 排名、代码、名称、人气值、涨跌幅、排名变化、热度标签

**emHotRank 返回字段:** 排名、代码、名称、价格、涨跌幅、排名变化

**emConceptHit 返回字段:** 概念名称、板块代码 (BK)、热度值

---

## 基础行情

**工具类:** `FinancialDataTool` (现有工具, 与上述 8 个 AStock Router Tool 并存)

| 方法 | 说明 | 数据源 |
|------|------|--------|
| `getAShareQuote(codes)` | A 股行情 (简化版) | qt.gtimg.cn |
| `getHKStockQuote(codes)` | 港股行情 | qt.gtimg.cn |
| `getUSStockQuote(codes)` | 美股行情 | qt.gtimg.cn |
| `getFundNav(code)` | 基金净值 | fundgz.1234567.com.cn |
| `searchStockByName(name)` | 按名称搜索股票 | qt.gtimg.cn |

**与 AStockQuoteRouterTool 的区别:** FinancialDataTool 返回简化行情 (名称/价格/涨跌幅), AStockQuoteRouterTool 返回完整行情 (含 PE/PB/市值/换手率等)。

---

## 配置说明

### application.yml

```yaml
astock:
  eastmoney:
    min-interval-ms: 1000        # 东财最小请求间隔
    jitter-max-ms: 500           # 随机抖动上限
    connect-timeout-seconds: 10  # 连接超时
    read-timeout-seconds: 15     # 读取超时
  iwencai:
    api-key: ${IWENCAI_API_KEY:}  # i问财 API Key (环境变量)
  northbound:
    history-days: 30              # 北向资金历史缓存天数
```

### 环境变量

| 变量 | 必需 | 说明 |
|------|------|------|
| `IWENCAI_API_KEY` | 否 | i问财语义搜索 API Key, 为空时 iwencaiSearch/iwencaiQuery 返回提示 |

### 工具开关

所有工具均可通过管理后台动态开关, 无需重启。开关状态存储在 Redis, 默认全部开启。

---

## 注意事项

1. **东财限流:** 所有东财系请求经 `EastMoneyRateLimiter` 串行处理, 连续调用需等待间隔
2. **GBK 编码:** 腾讯/新浪/同花顺部分接口返回 GBK, 代码中已处理
3. **JSONP 解析:** 东财个股新闻返回 JSONP 格式, 需截取 JSON 部分
4. **orgId 缓存:** CNInfo 公告需 orgId, 首次调用加载 6198 只股映射 (ConcurrentHashMap)
5. **北向缓存:** Redis TTL 30 天, 重复查询同日数据不重复请求
6. **i问财:** 需要 API Key, 未配置时返回提示而非报错
7. **期权 Greeks:** 新浪 GBK 编码, 部分字段可能缺失导致解析异常, 已 catch 处理
