# 分析师工具映射表

> 最后更新: 2026-07-04 | Layer 1 四位数据采集分析师的工具配置

---

## 目录

1. [工具总览](#工具总览)
2. [MARKET_ANALYST — 市场技术分析师](#market_analyst--市场技术分析师)
3. [FUNDAMENTALS_ANALYST — 基本面分析师](#fundamentals_analyst--基本面分析师)
4. [NEWS_ANALYST — 新闻分析师](#news_analyst--新闻分析师)
5. [SOCIAL_ANALYST — 舆情分析师](#social_analyst--舆情分析师)
6. [工具优先级规则](#工具优先级规则)

---

## 工具总览

四位分析师共使用 9 个工具，覆盖 8 个 AStock Router Tool 中的 7 个：

| 工具 | 数据层 | Market | Fundamentals | News | Social |
|------|--------|:------:|:------------:|:----:|:------:|
| `a_stock_quote` | 行情层 | ✅ 主力 | ✅ 主力 | - | - |
| `a_stock_report` | 研报层 | - | ✅ 主力 | ✅ 辅助 | - |
| `a_stock_signal` | 信号层 | ✅ 辅助 | - | ✅ 辅助 | ✅ 辅助 |
| `a_stock_capital` | 资金面 | - | ✅ 辅助 | - | - |
| `a_stock_news` | 新闻层 | - | ✅ 辅助 | ✅ 主力 | ✅ 辅助 |
| `a_stock_limit_up` | 打板层 | ✅ 辅助 | - | - | ✅ 辅助 |
| `a_stock_sentiment` | 舆情层 | - | - | - | ✅ 主力 |
| `market_data` | 基础行情 | ✅ 后备 | ✅ 后备 | - | - |
| `bailian_web_search` | MCP搜索 | - | - | ✅ 后备 | ✅ 后备 |

- **主力**: 优先使用，核心数据来源
- **辅助**: 工作流中使用，补充特定维度数据
- **后备**: 仅在 AStock 工具不覆盖时使用（港股/美股/非A股信息）

---

## MARKET_ANALYST — 市场技术分析师

**职责**: 技术面分析 — K线形态、均线系统、趋势判断、支撑阻力位

### 工具列表

| 优先级 | 工具 | Operation | 用途 | 参数 |
|:------:|------|-----------|------|------|
| 1 | `a_stock_quote` | `tencentQuote` | 批量实时行情（含PE/PB/市值/换手率/量比/振幅） | `codes`: 代码列表 |
| 1 | `a_stock_quote` | `baiduKline` | K线 + MA5/10/20 均线数据 | `code`, `period`(kline/week/month) |
| 2 | `a_stock_signal` | `conceptBlocks` | 个股板块归属（概念/行业/地域） | `stockCode` |
| 2 | `a_stock_signal` | `industryRanking` | 行业板块排名（涨跌幅/领涨股/主力净流入） | `top` |
| 2 | `a_stock_signal` | `fundFlowMinute` | 分钟级资金流 | `stockCode` |
| 3 | `a_stock_limit_up` | `sentimentOverview` | 打板情绪速算（炸板率/涨停跌停比/连板梯队） | 无 |
| 4 | `market_data` | `hkStockQuote` | 港股行情 | `stockCode` |
| 4 | `market_data` | `usStockQuote` | 美股行情 | `stockCode` |
| 4 | `market_data` | `fundNav` | 基金净值 | `fundCode` |
| 4 | `market_data` | `searchStock` | 按名称搜索股票代码 | `name` |

### 工作流程

```
tencentQuote (行情) → baiduKline (K线) → conceptBlocks (板块)
    → industryRanking (行业) → sentimentOverview (情绪) → 输出报告
```

---

## FUNDAMENTALS_ANALYST — 基本面分析师

**职责**: 财务数据和估值分析 — PE/PB、财报、研报评级、股东结构、分红

### 工具列表

| 优先级 | 工具 | Operation | 用途 | 参数 |
|:------:|------|-----------|------|------|
| 1 | `a_stock_quote` | `tencentQuote` | 完整行情（含PE(TTM)/PB/总市值/流通市值） | `codes` |
| 2 | `a_stock_report` | `stockReport` | 个股研报（评级/目标价/机构观点） | `stockCode`, `pageSize` |
| 2 | `a_stock_report` | `thsEpsForecast` | 一致预期EPS（机构预测汇总） | `stockCode` |
| 2 | `a_stock_report` | `industryReport` | 行业研报 | `industryName`, `pageSize` |
| 2 | `a_stock_report` | `iwencaiSearch` | NL语义搜索研报 | `query` |
| 3 | `a_stock_news` | `cninfoAnnouncements` | 巨潮公告全文 | `stockCode`, `pageSize` |
| 3 | `a_stock_news` | `irmQA` | 互动易问答（公司官方回复） | `stockCode`, `pageSize` |
| 3 | `a_stock_news` | `sinaFinancialReport` | 财报三表（利润表/资产负债表/现金流量表） | `stockCode`, `reportType` |
| 4 | `a_stock_capital` | `holderNumChange` | 股东户数变化（筹码集中度） | `stockCode`, `startDate`, `endDate` |
| 4 | `a_stock_capital` | `dividendHistory` | 分红送转历史 | `stockCode` |
| 4 | `a_stock_capital` | `marginTrading` | 融资融券明细 | `stockCode`, `startDate`, `endDate` |
| 5 | `market_data` | - | 基础行情（港股/美股/基金） | - |
| - | `financial_calculator` | - | 金融计算（PE/PB/股息率等） | - |
| - | `executeQuery` | - | SQL查询数据库（历史数据） | - |

### 工作流程

```
tencentQuote (行情) → stockReport (研报) → thsEpsForecast (预期EPS)
    → sinaFinancialReport (财报) → cninfoAnnouncements (公告)
    → holderNumChange (股东) → dividendHistory (分红)
    → financial_calculator (估值计算) → 输出报告
```

---

## NEWS_ANALYST — 新闻分析师

**职责**: 搜集和分析影响股价的新闻事件 — 个股新闻、公告、研报、资金动向

### 工具列表

| 优先级 | 工具 | Operation | 用途 | 参数 |
|:------:|------|-----------|------|------|
| 1 | `a_stock_news` | `stockNews` | 个股新闻 | `stockCode`, `pageSize` |
| 1 | `a_stock_news` | `globalNews` | 全球财经资讯7x24 | `pageSize` |
| 1 | `a_stock_news` | `cninfoAnnouncements` | 巨潮公告全文 | `stockCode`, `pageSize` |
| 1 | `a_stock_news` | `irmQA` | 互动易问答（公司回应投资者） | `stockCode`, `pageSize` |
| 1 | `a_stock_news` | `sinaFinancialReport` | 财报三表 | `stockCode`, `reportType` |
| 2 | `a_stock_report` | `stockReport` | 个股研报（机构观点/评级/目标价） | `stockCode`, `pageSize` |
| 2 | `a_stock_report` | `industryReport` | 行业研报 | `industryName`, `pageSize` |
| 2 | `a_stock_report` | `downloadReportPdf` | 研报PDF下载（返回摘要） | `pdfUrl` |
| 3 | `a_stock_signal` | `dragonTigerBoard` | 龙虎榜席位明细 | `stockCode`, `date` |
| 3 | `a_stock_signal` | `dailyDragonTiger` | 全市场龙虎榜 | `date` |
| 3 | `a_stock_signal` | `lockupExpiry` | 限售解禁日历 | `stockCode`, `startDate`, `endDate` |
| 4 | `bailian_web_search` | - | 联网搜索（非A股/工具外信息） | `query` |

### 工作流程

```
stockNews (个股新闻) → cninfoAnnouncements (公告) → stockReport (研报)
    → dragonTigerBoard (龙虎榜) → globalNews (全球资讯)
    → [bailian_web_search 补充] → 输出报告
```

### bailian_web_search 使用条件

- 目标不是A股（港股、美股、宏观政策等）
- a_stock_news 未找到相关信息
- 需要搜索非金融类新闻（如行业政策、公司事件等）

---

## SOCIAL_ANALYST — 舆情分析师

**职责**: 分析市场情绪和投资者关注度 — 人气热度、打板情绪、互动易、板块联动

### 工具列表

| 优先级 | 工具 | Operation | 用途 | 参数 |
|:------:|------|-----------|------|------|
| 1 | `a_stock_sentiment` | `thsHotList` | 同花顺热榜（人气排名+概念标签+涨跌幅） | `period`(hour/day) |
| 1 | `a_stock_sentiment` | `emHotRank` | 东财人气榜（市场关注度排名） | `top` |
| 1 | `a_stock_sentiment` | `emConceptHit` | 个股概念命中（市场归类+热度值） | `stockCode` |
| 2 | `a_stock_limit_up` | `sentimentOverview` | 打板情绪速算（炸板率/涨停跌停比/连板梯队） | 无 |
| 2 | `a_stock_limit_up` | `thsLimitUpPool` | 涨停揭秘（含题材归因） | `date` |
| 3 | `a_stock_news` | `stockNews` | 个股新闻 | `stockCode`, `pageSize` |
| 3 | `a_stock_news` | `globalNews` | 全球财经资讯7x24 | `pageSize` |
| 3 | `a_stock_news` | `irmQA` | 互动易问答（投资者情绪直接来源） | `stockCode`, `pageSize` |
| 4 | `a_stock_signal` | `conceptBlocks` | 个股板块归属（概念/行业/地域） | `stockCode` |
| 4 | `a_stock_signal` | `industryRanking` | 行业板块排名 | `top` |
| 5 | `bailian_web_search` | - | 联网搜索（非A股/社交媒体/论坛） | `query` |

### 工作流程

```
thsHotList (热榜) → emConceptHit (概念热度) → sentimentOverview (打板情绪)
    → irmQA (互动易) → conceptBlocks (板块联动)
    → [bailian_web_search 补充] → 输出报告
```

### bailian_web_search 使用条件

- 目标不是A股（港股、美股等）
- a_stock_sentiment / a_stock_news 未找到相关信息
- 需要搜索社交媒体讨论、论坛帖子等非官方信息

---

## 工具优先级规则

### 通用原则

1. **A股优先**: 所有A股相关数据必须通过 AStock Router Tool 获取，不得使用 MCP 搜索
2. **分层调用**: 按照 工具优先级 从高到低依次调用，避免遗漏核心数据
3. **MCP 后备**: `bailian_web_search` 仅在以下情况使用：
   - 目标不是A股（港股、美股、宏观政策等）
   - AStock 工具未找到相关信息
   - 需要搜索非金融类或非官方信息（社交媒体、论坛等）
4. **market_data 后备**: `market_data` 仅在目标为港股/美股/基金时使用

### 数据源覆盖

| 数据需求 | 首选工具 | 后备工具 |
|----------|----------|----------|
| A股实时行情 | `a_stock_quote` → `tencentQuote` | `market_data` → `aShareQuote` |
| K线/均线 | `a_stock_quote` → `baiduKline` | - |
| 个股新闻 | `a_stock_news` → `stockNews` | `bailian_web_search` |
| 公司公告 | `a_stock_news` → `cninfoAnnouncements` | - |
| 机构研报 | `a_stock_report` → `stockReport` | `bailian_web_search` |
| 财报数据 | `a_stock_news` → `sinaFinancialReport` | `executeQuery` |
| 资金流向 | `a_stock_signal` → `fundFlowMinute` | `a_stock_capital` → `fundFlow120d` |
| 龙虎榜 | `a_stock_signal` → `dragonTigerBoard` | - |
| 市场情绪 | `a_stock_sentiment` → `thsHotList` | `a_stock_limit_up` → `sentimentOverview` |
| 板块归属 | `a_stock_signal` → `conceptBlocks` | - |
| 港股/美股 | `market_data` → `hkStockQuote`/`usStockQuote` | - |
| 非A股/非金融 | `bailian_web_search` | `fetchWebpage` |
