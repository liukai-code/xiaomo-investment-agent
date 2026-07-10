# 深度分析独立模块设计

**日期**: 2026-07-09
**状态**: 待实施

## 背景

当前深度分析工作流嵌在对话流中，存在三个核心痛点：
1. **分析阻塞对话** — 5 层工作流运行数分钟，期间用户无法正常使用对话功能
2. **信息密度杂乱** — WorkflowPanel 嵌在对话流里，和普通消息混在一起
3. **结果不便回看** — 分析结果散落在对话历史中，不方便对比和回顾

## 目标

- 将深度分析抽离为独立前端模块，与对话解耦
- 支持分析完成后在对话中就报告提问
- MVP 阶段保持简单，后续可迭代

## 方案

**独立页面 + 左右分栏 + 对话工具桥接**

### 页面结构

路由：`/analysis`，侧边栏新增"深度分析"入口（lucide `Brain` 图标）。

```
┌─────────────────────────────────────────────────────┐
│  顶部：标的输入框 + [开始分析] 按钮                    │
├──────────────┬──────────────────────────────────────┤
│              │                                      │
│  左侧面板     │  右侧详情区                           │
│  (280px)     │                                      │
│              │  状态：运行中/已完成                     │
│  分析记录列表  │  ┌─────────────────────────────┐     │
│  按时间倒序    │  │  4阶段进度条                  │     │
│              │  │  数据采集→辩论→交易→风险       │     │
│  每条显示：    │  ├─────────────────────────────┤     │
│  - 标的名称    │  │  Agent 实时内容流             │     │
│  - 操作建议    │  │  (复用 WorkflowPanel 逻辑)    │     │
│  - 置信度      │  ├─────────────────────────────┤     │
│  - 日期时间    │  │  最终裁决卡片                 │     │
│  - 状态标签    │  └─────────────────────────────┘     │
│              │                                      │
├──────────────┴──────────────────────────────────────┤
│  底部：分析完成后显示"去对话中提问"快捷入口             │
└─────────────────────────────────────────────────────┘
```

- 左侧列表项选中态：高亮边框 + 背景色
- 运行中的分析：脉动动画
- 右侧无选中项时：空态引导

### 交互流程

#### 发起分析

1. 用户在顶部输入标的名称/代码，点击"开始分析"
2. 前端调用 `POST /api/analysis/start` 创建分析记录，拿到 `analysisId`
3. 前端通过 SSE `GET /api/analysis/{analysisId}/stream` 建立连接
4. 左侧列表立即新增一条"运行中"记录并自动选中
5. 右侧详情区实时渲染工作流进度和 Agent 内容

#### 查看历史

1. 页面加载时调用 `GET /api/analysis/list` 获取历史分析列表
2. 点击左侧列表项，右侧加载该分析的完整结果
3. 如果该分析正在运行中，右侧切换为实时 SSE 渲染模式

#### 在对话中引用分析

后端新增 AI Tool `get_analysis_report`：
- 参数：`stockCode`（股票代码或名称），可选 `analysisId`
- 返回：结构化分析报告摘要（各阶段报告 + 最终裁决）
- AI 通过 system prompt 引导，在用户提及"深度分析"时调用此 Tool

示例对话：
```
用户：丰光精密的深度分析结论是什么？
AI：[调用 get_analysis_report(stockCode="丰光精密")]
AI：根据最近一次深度分析（2026-07-09），结论如下：
    - 操作建议：BUY
    - 置信度：72%
    - 目标价：45.80
    - 主要逻辑：...
```

### 后端接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/analysis/start` | POST | 创建分析，返回 analysisId |
| `/api/analysis/{id}/stream` | GET (SSE) | 实时工作流事件流 |
| `/api/analysis/list` | GET | 历史分析列表 |
| `/api/analysis/{id}` | GET | 单条分析详情 |
| `/api/analysis/{id}` | DELETE | 删除分析记录 |

**`/api/analysis/start` 请求体**：
```json
{
  "query": "深度分析丰光精密"
}
```
返回：`{ "analysisId": 123, "stockCode": "688398", "stockName": "丰光精密" }`

**并发分析**：同一用户同时只允许一个分析运行中。如果已有运行中的分析，`/start` 返回 409 冲突。

### 数据库

复用现有 `workflow_analyses` 表，不需要新建表。

### 现有对话入口处理

- 保留 `/agent/chat/deep-analysis` 接口不动（向后兼容）
- `ChatView.vue` 中的 `isDeepAnalysisRequest()` 关键词检测逻辑**移除**，深度分析统一走独立页面
- 已有的对话中深度分析历史消息保持原样，不影响查看

## 改动范围

### 前端（纯新增）

| 文件 | 说明 |
|------|------|
| `views/AnalysisView.vue` | 主页面，左右分栏布局 |
| `components/analysis/AnalysisList.vue` | 左侧列表 |
| `components/analysis/AnalysisDetail.vue` | 右侧详情 |
| `components/analysis/AnalysisInput.vue` | 顶部输入区 |
| `api/analysis.ts` | API 调用封装 |
| `router/index.ts` | 添加路由 |
| 侧边栏组件 | 添加"深度分析"入口 |

复用现有 `WorkflowPanel.vue` 的核心逻辑（阶段进度、Agent 状态机、内容渲染），在 AnalysisDetail 中重新组织。

### 后端（新增 3-4 个文件）

| 文件 | 说明 |
|------|------|
| `AnalysisController.java` | REST 接口（list/detail/delete/start） |
| `AnalysisService.java` | 分析记录 CRUD |
| `GetAnalysisReportTool.java` | 对话 AI 可调用的 Tool |
| `AgentLoopImpl.java` | 注册新 Tool |

核心工作流引擎（`DeepAnalysisWorkflow`、`WorkflowEngine` 等）**零改动**。

### 不改动的部分

- WorkflowEngine 执行逻辑
- WorkflowGraph DAG 定义
- AgentRole 角色配置
- WorkflowEvent 事件协议
- 现有的对话接口 `/agent/chat/deep-analysis`（保留兼容）
