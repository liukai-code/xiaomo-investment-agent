# Chat UI Redesign + 消息协议层

> 2026-06-28 | 涉及前后端联动改造

## 一、改动背景

### 原有问题

1. **视觉风格过时**：赛博朋克/CRT 终端美学（扫描线、绿色 accent、等宽字体、固定宽度容器）
2. **布局不可控**：LLM 输出自由格式 Markdown → 前端纯文本渲染 → AI 消息结构不一致
3. **缺乏消息协议**：没有"消息类型"概念，所有内容一视同仁渲染为 Markdown 气泡

### 改动目标

- 视觉：升级为 ChatGPT 结构 + Bloomberg 金融感 + Notion 干净层级
- 架构：AI 输出 JSON blocks 数组 → 前端按 block 类型渲染为 UI 组件树

---

## 二、整体架构

### 架构图

```
AI 输出 JSON → validateJsonBlocks 校验+清理 → useMessageProtocol 解析
                                                      ↓
                                          MarkdownRenderer 分发
                                                      ↓
                              ┌─────────────────────────────────────────┐
                              │  isJson = true  │  isJson = false       │
                              │  → MessageBlock │  → Markdown 块渲染     │
                              │  × N 个 blocks  │  (legacy fallback)    │
                              └─────────────────────────────────────────┘
```

### 核心设计

AI 不再输出自由文本，而是输出 **JSON blocks 数组**。每个 block 是一个独立的 UI 组件：

```json
{"blocks":[
  {"type":"title","content":"贵州茅台实时行情"},
  {"type":"kpi","content":"600519.SH","data":[
    {"label":"当前价格","value":"1856.00","trend":"neutral"},
    {"label":"涨跌","value":"+12.50","trend":"up"}
  ]},
  {"type":"text","content":"数据来源：腾讯行情，仅供参考。"},
  {"type":"warning","content":"投资有风险，入市需谨慎"}
]}
```

---

## 三、Block 类型规范

- **title** — 标题，大号加粗文字，content 为标题文字
- **text** — 文本段落，普通文字（支持内联 Markdown），content 为正文
- **kpi** — 数据指标，3 列网格卡片涨绿跌红，content 为描述，data 为 `[{label, value, trend}]`
- **table** — 数据表格，带表头的表格，content 为描述，data 为 `{headers, rows}`
- **card** — 信息卡片，圆角背景卡片，content 为正文（支持换行）
- **warning** — 风险提示，红色边框警告条，content 为提示文字

### trend 取值

- `up` — 涨/正面（绿色 `#22c55e`）
- `down` — 跌/负面（红色 `#ef4444`）
- `neutral` — 中性/无变化（默认文字色）

---

## 四、JSON 校验与容错

`validateJsonBlocks.ts` 处理 AI 输出的各种异常情况：

- **输出被代码块包裹** — 自动剥离代码块
- **JSON 前有自然语言** — 截取第一个 `{` 到最后一个 `}`
- **尾部多余逗号** — 正则修复
- **JSON 解析失败** — 尝试正则提取部分完整 blocks
- **完全无法解析** — fallback 为 text block
- **旧消息（无 JSON 结构）** — 检测为 legacy，走 Markdown 块渲染

---

## 五、文件清单

### 后端

- `src/main/resources/application.yml` — System Prompt 替换为 JSON blocks 协议

### 前端 — 新增

- `frontend/src/utils/validateJsonBlocks.ts` — JSON 校验、清理、fallback
- `frontend/src/components/blocks/MessageBlock.vue` — 单个 block 渲染组件（6 种类型）

### 前端 — 修改

- `frontend/src/composables/useMessageProtocol.ts` — 重写：调用 validateJsonBlocks 解析
- `frontend/src/components/blocks/MarkdownRenderer.vue` — 分发：JSON 走 MessageBlock，legacy 走 Markdown

### 前端 — 删除

- `frontend/src/components/blocks/KpiBlock.vue` — 被 MessageBlock 替代
- `frontend/src/components/blocks/CardBlock.vue` — 被 MessageBlock 替代

---

## 六、渲染流程

### JSON 消息（新协议）

```
SSE 流式到达 → onChunk(累积全文) → chatStore.updateLastAiMessage
  → MarkdownRenderer 重新渲染
  → useMessageProtocol 调用 parseAndValidate
    → 检测到 "blocks" 字段 → isJson = true
    → JSON.parse + validateBlocks
  → 遍历 blocks 数组，每个 block 渲染一个 MessageBlock
    → title → 大号标题
    → kpi → 3 列网格 + 涨跌颜色
    → table → 带表头的表格
    → card → 圆角卡片
    → warning → 红色警告条
    → text → 普通文字
```

### Legacy 消息（旧 Markdown）

```
SSE 流式到达 → MarkdownRenderer
  → useMessageProtocol 检测无 "blocks" 字段 → isJson = false
  → 走原有 Markdown 块渲染流水线
    → HeadingBlock / ParagraphBlock / CodeBlock / ...
```

---

## 七、UI 设计

### 配色

蓝色金融系（从赛博朋克绿切换）：

- `--accent` — 暗色 `#3b82f6`，亮色 `#2563eb`，蓝色主调
- `--green` — 暗色 `#22c55e`，亮色 `#16a34a`，涨或正面
- `--red` — 暗色 `#ef4444`，亮色 `#dc2626`，跌或负面
- `--bg` — 暗色 `#1a1a2e`，亮色 `#f8fafc`，背景
- `--surface` — 暗色 `#16213e`，亮色 `#ffffff`，卡片表面

### 布局

- 全屏响应式（删除固定 1080px 容器）
- 侧边栏 260px（圆角卡片式会话项）
- 用户消息右对齐蓝色气泡
- AI 消息左侧，每个 block 独立渲染
- 输入框 ChatGPT 风格圆角容器

---

## 八、验证方式

1. 后端重启：`mvn spring-boot:run`（加载新 System Prompt）
2. 前端启动：`cd frontend && npm run dev`
3. 测试场景：
   - "茅台今天多少钱" → title + kpi + text + warning → 标题 + KPI 网格 + 说明 + 风险提示
   - "什么是复利" → title + card + text → 标题 + 卡片 + 文字
   - "查询数据库用户" → title + table + text → 标题 + 表格 + 说明
   - 旧消息（纯 Markdown）→ legacy fallback → Markdown 块渲染
4. 容错测试：AI 输出被代码块包裹、JSON 前有文字等异常情况
5. 主题切换：暗色或亮色模式下所有组件样式正常
