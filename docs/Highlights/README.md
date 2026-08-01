# 小墨技术亮点 -- 保姆级深度解读

> 本目录收录小墨项目各核心模块的技术亮点文档，面向初次接触项目的开发者。
> 每篇文档从**问题出发**，逐步拆解设计思路与实现细节，附带架构图、代码走读和实战案例。

## 阅读指南

建议按编号顺序阅读，后续文档会引用前面的概念：

| # | 文档 | 一句话说明 | 关键词 |
|---|------|-----------|--------|
| 1 | [多智能体深度分析工作流](01-MultiAgentWorkflow.md) | 5 层 DAG 工作流，从数据采集到风险裁决的多 Agent 协作引擎 | Reactor, DAG, 多空辩论, 风险裁决 |
| 2 | [工具调用防护 + 幻觉防护](02-ToolGuardSystem.md) | 6 个协作组件防止 Agent 陷入无限调用循环，阻止 AI 编造数据 | GuardSignal, 重复检测, 信息增益, 输出消毒 |
| 3 | [意图分类 + 工具过滤](03-IntentClassificationAndToolFiltering.md) | 用户消息进入系统的第一道关卡，4 级优先匹配 9 种意图 | 意图分类, 工具白名单, 执行模式 |
| 4 | [A 股数据工具集 + Router 架构](04-AStockDataAndRouterTool.md) | 8 个 Router Tool 封装 44 个 @Tool 方法，覆盖 7+ 数据源 | Router 模式, 东财限流, 59 路由操作 |
| 5 | [两层记忆系统](05-MemorySystem.md) | 用户画像 + 对话摘要，让 Agent 记住你是谁、聊过什么 | UserProfile, AI 提取, 10:1 压缩 |
| 6 | [SSE 流式架构 + 通知系统](06-SSEStreamingAndNotification.md) | 后端 Flux 驱动、前端 ReadableStream 消费的实时推送体系 | SSE, 流式对话, 通知推送 |
| 7 | [用户配置系统](07-UserConfigSystem.md) | 用户自定义 API Key、模型、工具开关、多渠道管理 | ChatModel, 工具开关, API 渠道 |
| 8 | [自主任务规划](08-AutonomousTaskPlanning.md) | LLM 为自己制定结构化执行计划，按步骤调用工具并汇总 | TaskPlanner, PlanContext, Scratchpad |
| 9 | [认证与安全架构](09-AuthAndSecurity.md) | Redis 双 Key 单设备登录、滑动过期、AES-GCM 加密、异步注册事件 | 单设备登录, BCrypt, AES-GCM, Spring Event |
| 10 | [会话数据架构与用量统计](10-ConversationAndUsageStats.md) | 三级 Redis 缓存 + 逐请求 token 计量 + 软重置统计 | 三级缓存, 用量追踪, 软重置, 日聚合 |
| 11 | [流式 Markdown 渲染器](11-StreamingMarkdownRenderer.md) | 自研状态机解析器、djb2 块级稳定 Key、不完整输入优雅降级 | 状态机, djb2 哈希, 流式渲染, Canvas 动画 |
| 12 | [养基宝基金集成](12-YangJiBaoIntegration.md) | QR 码登录、MD5 请求签名、CompletableFuture 并行数据同步 | QR 登录, MD5 签名, 并行同步, 双 API |

## 文档模板

每篇文档遵循 **WHY → WHAT → HOW** 结构：

1. **核心内容** -- 本文涵盖的关键点
2. **为什么需要这个设计** -- 问题场景 + 设计目标
3. **整体架构** -- Mermaid 架构图 + 核心组件表
4. **代码走读** -- 请求入口 → 核心逻辑 → 关键决策
5. **配置与调参** -- application.yml 配置项说明
6. **实战案例** -- 正常流程 + 异常边界
7. **与其他模块的关系** -- 依赖关系图
8. **常见问题排查** -- FAQ 表格
9. **源码索引** -- 关键文件路径速查
10. **延伸阅读** -- 相关文档链接

## 与其他文档的关系

本目录是**深度解读**，与已有文档的定位不同：

| 本目录 | 已有文档 | 区别 |
|--------|---------|------|
| 保姆级，从 WHY 讲起 | 设计文档（DevelopmentProcess/） | 设计文档记录决策过程，本目录面向新人上手 |
| 带 Mermaid 图 + 代码走读 | 使用指南（Guides/） | 使用指南偏操作手册，本目录偏原理讲解 |
| 聚焦单个模块 | 功能总览（project-features.md） | 总览是全景地图，本目录是每个景点的导游 |
