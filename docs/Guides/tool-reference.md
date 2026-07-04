# AI 工具参考手册

> 共 18 个工具类、24 个 @Tool 方法、117 个操作

---

## 一、文件操作工具

### FileListTool — 列出目录文件

| 操作 | 参数 | 说明 |
|------|------|------|
| `listFiles` | `dirPath`, `recursive`(可选), `pattern`(可选) | 列出目录下的文件和子目录，支持递归和 glob 匹配 |

安全限制：路径必须在项目根目录内。

### FileReadTool — 读取文件

| 操作 | 参数 | 说明 |
|------|------|------|
| `readFile` | `filePath`, `encoding`(可选) | 读取文件内容，最大 10MB |

### FileWriteTool — 写入文件

| 操作 | 参数 | 说明 |
|------|------|------|
| `writeFile` | `filePath`, `content`, `encoding`(可选), `append`(可选) | 写入或追加内容到文件 |

---

## 二、数据库工具

### SqlTool — SQL 查询

| 操作 | 参数 | 说明 |
|------|------|------|
| `getDatabaseSchema` | `tableName`(可选) | 获取表结构（列名、类型、注释） |
| `executeQuery` | `sql`, `maxRows`(可选) | 执行 SELECT 查询，仅只读，禁止 INSERT/UPDATE/DELETE |

安全限制：SQL 注入防护，禁止危险关键词和注释，连接设为只读。

---

## 三、网页抓取工具

### WebFetchTool — 抓取网页内容

| 操作 | 参数 | 说明 |
|------|------|------|
| `fetchWebpage` | `url`, `maxLength`(可选) | 抓取网页并提取可读文本，去除 script/style 标签 |
| `fetchArticleContent` | `url`, `maxLength`(可选) | 仅提取文章正文，去除导航/广告/页脚 |

安全限制：禁止内网地址（localhost/127.0.0.1/192.168.x.x 等）。

---

## 四、金融计算工具

### FinancialCalcRouterTool — 金融计算器

纯数学计算，无外部 API 调用，使用 BigDecimal 保证精度。

| 操作 | 参数 | 说明 |
|------|------|------|
| `calculate` | `expression` | 数学表达式计算（加减乘除、括号） |
| `compoundInterest` | `principal`, `annualRate`, `years` | 复利终值 |
| `simpleInterest` | `principal`, `annualRate`, `years` | 单利终值 |
| `annualizedReturn` | `totalReturnPercent`, `days` | 年化收益率换算 |
| `dcaReturn` | `monthlyAmount`, `annualRate`, `months` | 定投收益（月定投） |
| `compoundDca` | `initialCapital`, `periodicAmount`, `annualRate`, `years`, `frequency` | 复利+定投综合（日/周/月） |
| `ruleOf72` | `annualRate` | 72法则（翻倍时间） |
| `peRatio` | `stockPrice`, `earningsPerShare` | 市盈率 PE |
| `pbRatio` | `stockPrice`, `bookValuePerShare` | 市净率 PB |
| `dividendYield` | `annualDividend`, `stockPrice` | 股息率 |
| `loanPayment` | `principal`, `annualRate`, `months` | 等额本息月供 |
| `npv` | `discountRate`, `cashFlows` | 净现值 |
| `irr` | `cashFlows` | 内部收益率（牛顿迭代法） |
| `bondPrice` | `faceValue`, `couponRate`, `marketRate`, `periods` | 债券定价 |
| `bondYtm` | `faceValue`, `marketPrice`, `couponRate`, `periods` | 债券到期收益率 |
| `retirementTarget` | `annualExpense`, `safeWithdrawalRate` | 退休所需本金（4%法则） |
| `withdrawalPlan` | `principal`, `annualWithdrawal`, `annualRate` | 定额提取计划 |
| `sharpeRatio` | `portfolioReturn`, `riskFreeRate`, `volatility` | 夏普比率 |
| `maxDrawdown` | `navSeries` | 最大回撤 |
| `cagr` | `beginValue`, `endValue`, `years` | 复合年增长率 |
| `totalReturn` | `buyPrice`, `sellPrice`, `dividends` | 总收益率（含分红） |
| `inflationAdjusted` | `amount`, `inflationRate`, `years` | 通胀调整购买力 |

---

## 五、行情数据工具

### FinancialDataRouterTool — 行情数据查询

数据源：腾讯行情、东方财富搜索、新浪基金

| 操作 | 参数 | 说明 |
|------|------|------|
| `aShareQuote` | `stockCodeOrName` | A股实时行情，支持代码或名称自动搜索 |
| `hkStockQuote` | `stockCode` | 港股实时行情（如 00700） |
| `usStockQuote` | `stockCode` | 美股实时行情（如 AAPL） |
| `fundNav` | `fundCode` | 基金净值查询 |
| `searchStock` | `name` | 股票名称模糊搜索（东财主搜 + 新浪兜底） |

### AStockQuoteRouterTool — A股行情深度查询

数据源：腾讯行情、百度K线

| 操作 | 参数 | 说明 |
|------|------|------|
| `tencentQuote` | `stockCodes` | 批量查询实时行情（PE/PB/市值/换手率/涨跌停），逗号分隔代码 |
| `baiduKline` | `stockCode`, `startTime`(可选) | K线数据（含MA5/10/20均线），默认最近180天 |
| `mootdxKline` | — | TODO：mootdx TCP 协议未实现 |
| `mootdxQuotes` | — | TODO |
| `mootdxTransaction` | — | TODO |

特殊：baiduKline 使用 JDK HttpClient 绕过 OkHttp TLS 指纹检测。

---

## 六、研报工具

### AStockReportRouterTool — A股研报查询

数据源：东方财富研报、同花顺、iwencai

| 操作 | 参数 | 说明 |
|------|------|------|
| `stockReport` | `stockCode`, `maxPages`(可选) | 个股研报列表（标题、机构、评级、预测EPS） |
| `industryReport` | `industryCode`, `maxPages`(可选) | 行业研报（"*"=全行业） |
| `downloadReportPdf` | `infoCode` | 生成研报 PDF 下载链接 |
| `thsEpsForecast` | `stockCode` | 同花顺一致预期 EPS |
| `iwencaiSearch` | `query`, `channel`(可选), `size`(可选) | iwencai 语义搜索研报（需 API Key） |
| `iwencaiQuery` | `query`, `page`(可选), `limit`(可选) | iwencai 结构化数据查询（需 API Key） |

---

## 七、信号工具

### AStockSignalRouterTool — A股信号查询

数据源：东方财富 push2/datacenter

| 操作 | 参数 | 说明 |
|------|------|------|
| `conceptBlocks` | `stockCode` | 个股板块/概念归属 |
| `fundFlowMinute` | `stockCode` | 分钟级资金流向 |
| `dragonTigerBoard` | `stockCode`, `tradeDate`(可选), `lookBackDays`(可选) | 个股龙虎榜席位 |
| `dailyDragonTiger` | `tradeDate`(可选), `minNetBuy`(可选) | 全市场龙虎榜 |
| `lockupExpiry` | `stockCode`, `tradeDate`(可选), `forwardDays`(可选) | 限售解禁日历 |
| `industryRanking` | `topN`(可选) | 行业板块排名 |

---

## 八、资金面工具

### AStockCapitalRouterTool — A股资金面查询

数据源：东方财富 datacenter/push2his、同花顺北向

| 操作 | 参数 | 说明 |
|------|------|------|
| `marginTrading` | `stockCode`, `pageSize`(可选) | 融资融券明细 |
| `blockTrade` | `stockCode`, `pageSize`(可选) | 大宗交易记录 |
| `holderNumChange` | `stockCode`, `pageSize`(可选) | 股东户数变化 |
| `dividendHistory` | `stockCode`, `pageSize`(可选) | 分红送转历史 |
| `fundFlow120d` | `stockCode` | 120日资金流向（主力/超大单/大单/中单/小单） |
| `northboundFlow` | `historyDays`(可选) | 北向资金流入（Redis 缓存，30天TTL） |

---

## 九、打板工具

### AStockLimitUpRouterTool — A股打板层查询

数据源：东方财富 push2ex、同花顺涨停揭秘

| 操作 | 参数 | 说明 |
|------|------|------|
| `ztPool` | `date`(可选) | 涨停池（涨停股列表、连板数、封单额） |
| `zbPool` | `date`(可选) | 炸板池（曾涨停后打开的股票） |
| `dtPool` | `date`(可选) | 跌停池 |
| `yztPool` | `date`(可选) | 昨日涨停池 |
| `thsLimitUpPool` | `date`(可选) | 同花顺涨停揭秘（含概念归因） |
| `sentimentOverview` | `date`(可选) | 打板情绪速算（炸板率 + 连板梯队） |

日期格式：YYYYMMDD，默认今天。

---

## 十、舆情互动工具

### AStockSentimentRouterTool — A股舆情互动查询

数据源：同花顺热榜、东方财富人气榜

| 操作 | 参数 | 说明 |
|------|------|------|
| `thsHotList` | `period`(可选) | 同花顺热榜（"hour"=小时榜, "day"=日榜） |
| `emHotRank` | `top`(可选) | 东财人气榜（含股价、涨跌幅） |
| `emConceptHit` | `stockCode` | 个股概念命中（该股属于哪些热门概念） |

---

## 十一、新闻公告工具

### AStockNewsRouterTool — A股新闻公告查询

数据源：东方财富搜索/7x24、巨潮资讯、互动易、新浪财经

| 操作 | 参数 | 说明 |
|------|------|------|
| `stockNews` | `stockCode`, `pageSize`(可选) | 个股新闻（标题、时间、链接） |
| `globalNews` | `pageSize`(可选) | 全球7x24资讯 |
| `cninfoAnnouncements` | `stockCode`, `pageSize`(可选) | 巨潮公告全文（年报、季报、重大事项） |
| `irmQA` | `stockCode`, `pageSize`(可选) | 互动易问答（投资者提问 + 公司回复） |
| `sinaFinancialReport` | `stockCode`, `reportType`(可选), `num`(可选) | 新浪财报三表（lrb=利润表, fzb=资产负债表, llb=现金流量表） |

---

## 十二、期权工具

### AStockOptionRouterTool — ETF期权查询

数据源：新浪期权 API

| 操作 | 参数 | 说明 |
|------|------|------|
| `optionCodes` | `underlying`(可选), `call`(可选) | 期权合约清单（510050=50ETF, 510300=300ETF, 588000=科创50ETF, 510500=500ETF） |
| `optionTQuote` | `contractCode` | T型报价（最新价、买卖价、持仓量、行权价） |
| `optionGreeks` | `contractCode` | 希腊字母（Delta/Gamma/Theta/Vega）+ 隐含波动率(IV) |

特殊：GBK 编码解码，不使用 EastMoneyRateLimiter。

---

## 十三、养基宝工具

### YangJiBaoTool — 养基宝基金持仓

| 操作 | 参数 | 说明 |
|------|------|------|
| `getMyHoldings` | 无 | 查询当前用户基金持仓列表（名称、代码、市值、盈亏） |
| `getMyAccountSummary` | 无 | 账户汇总（总成本、今日收益、收益率） |

用户身份通过 ThreadLocal 自动注入。

---

## 基础设施

### EastMoneyRateLimiter — 东财限流器

所有访问东方财富 API 的工具共用，串行限流 ≥1s + 随机抖动，403 直接抛异常，429/5xx 指数退避重试3次。

### AStockUtils — 股票代码工具

标准化代码（去除 sh/sz/bj 前后缀）、转换为东财 secid 格式（`1.600519`）或腾讯格式（`sh600519`）。

### HttpClientService — HTTP 客户端

OkHttp 封装，支持代理、重试、熔断器。百度K线使用 `getWithJdkClient()` 绕过 TLS 指纹检测。
