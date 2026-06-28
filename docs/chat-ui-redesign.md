# Chat UI Redesign + 消息协议层

> 2026-06-28 | 涉及前后端联动改造

## 一、改动背景

### 原有问题

1. **视觉风格过时**：赛博朋克/CRT 终端美学（扫描线、绿色 accent、JetBrains Mono 等宽字体、固定 1080px 容器）
2. **布局不可控**：LLM 输出自由格式 Markdown → 前端纯文本渲染 → AI 消息结构不一致
3. **缺乏消息协议**：没有"消息类型"概念，所有内容一视同仁渲染为 Markdown 块

### 改动目标

- 视觉：升级为 ChatGPT 结构 + Bloomberg 金融感 + Notion 干净层级
- 架构：引入消息协议层，AI 输出结构化类型标记 → 前端按类型分发渲染组件

---

## 二、整体架构变更

### Before

```
LLM → 自由 Markdown → 前端 parseBlocks → 统一渲染
```

### After

```
LLM → 类型标记 + Markdown → useMessageProtocol 解析 → 按类型分发渲染
                                            ↓
                              ┌─────────────┼─────────────┐
                              ↓             ↓             ↓
                           KpiBlock     CardBlock    MarkdownRenderer
                          (KPI 网格)   (卡片+标题)    (默认块渲染)
```

---

## 三、消息协议设计

### 协议格式

AI 在回复最开头输出 HTML 注释格式的类型标记，后跟标准 Markdown：

```
<!--type:kpi-->

## 📊 海力士实时股价

| 指标 | 数值 | 趋势 |
|------|------|------|
| 当前价格 | 169.10 | neutral |
| 涨跌 | -25.40 | down |
| 跌幅 | -13.93% | down |
```

### 类型定义

| 类型标记 | 用途 | 渲染组件 | 适用场景 |
|----------|------|----------|----------|
| `<!--type:text-->` | 纯文本回答 | MarkdownRenderer | 概念解释、一般对话、学习建议 |
| `<!--type:card-->` | 带标题卡片 | CardBlock | 投资分析、策略总结、案例解读 |
| `<!--type:kpi-->` | KPI 数据网格 | KpiBlock | 股票行情、基金净值、金融计算结果 |
| `<!--type:table-->` | 数据表格 | MarkdownRenderer | 数据库查询结果、多条目对比 |

### KPI 表格规范

当使用 `<!--type:kpi-->` 时，AI 必须输出三列 Markdown 表格：

```markdown
| 指标 | 数值 | 趋势 |
|------|------|------|
| 名称 | 值 | up/down/neutral |
```

- `up`: 涨/正面（绿色）
- `down`: 跌/负面（红色）
- `neutral`: 中性/无变化（灰色）

### 兼容性

无类型标记的消息默认按 `text` 类型渲染，确保旧消息不受影响。

---

## 四、文件变更清单

### 后端

| 文件 | 变更 |
|------|------|
| `src/main/resources/application.yml` | System Prompt 追加输出协议规则 |

### 前端 — 新增文件

| 文件 | 职责 |
|------|------|
| `frontend/src/composables/useMessageProtocol.ts` | 消息协议解析器：检测 `<!--type:xxx-->` 标记，提取类型、标题、内容 |
| `frontend/src/components/blocks/KpiBlock.vue` | KPI 网格组件：解析 3 列表格 → 网格布局，涨绿跌红 |
| `frontend/src/components/blocks/CardBlock.vue` | 卡片容器组件：标题 + MarkdownRenderer 内容区 |

### 前端 — 修改文件

| 文件 | 变更 |
|------|------|
| `frontend/src/components/blocks/MarkdownRenderer.vue` | 集成协议分发：根据消息类型路由到 KpiBlock/CardBlock/默认渲染 |
| `frontend/src/views/ChatView.vue` | UI 重构：侧边栏、消息布局、输入框、欢迎页 |
| `frontend/src/styles/variables.css` | 全面重写：蓝色系配色、全屏布局、卡片式消息、ChatGPT 风格输入框 |
| `frontend/src/styles/markdown.css` | 适配新配色变量 |

---

## 五、UI 设计变更

### 配色方案

从赛博朋克绿色切换到蓝色金融系：

| 变量 | 暗色值 | 亮色值 | 说明 |
|------|--------|--------|------|
| `--accent` | `#3b82f6` | `#2563eb` | 蓝色主调 |
| `--green` | `#22c55e` | `#16a34a` | 涨/正面 |
| `--red` | `#ef4444` | `#dc2626` | 跌/负面 |
| `--bg` | `#1a1a2e` | `#f8fafc` | 背景 |
| `--surface` | `#16213e` | `#ffffff` | 卡片表面 |

### 布局变更

- **容器**：固定 1080px → 全屏响应式
- **侧边栏**：260px，圆角卡片式会话项，底部用户栏
- **消息**：用户右对齐蓝色气泡，AI 左侧卡片式输出
- **输入框**：ChatGPT 风格圆角容器，focus 蓝色光晕
- **字体**：`Inter` + `Noto Sans SC`（替代 JetBrains Mono 作为主字体）

### 删除

- CRT 扫描线效果 (`body::before`)
- 绿色 accent 配色
- 固定宽度容器限制

---

## 六、渲染流程

### 流式输出时的渲染流程

```
SSE chunk 到达
  → onChunk(累积全文)
  → RAF 节流
  → chatStore.updateLastAiMessage(fullText)
  → Vue 响应式触发 MarkdownRenderer 重新渲染
  → useMessageProtocol 解析类型标记
    → 标记未完整时（如只有 "<!--"），降级为 text 类型
    → 标记完整后，切换到对应组件
  → 对应组件渲染内容
```

### 类型检测时机

- `<!--type:kpi-->` 共 16 个字符
- 流式输出时，前 16 个字符内可能检测不到完整标记
- 检测到前使用默认 `text` 渲染（Markdown 块），检测到后无缝切换
- 用户体验：开头几帧可能以普通文本渲染，随后切换为结构化组件

---

## 七、验证方式

1. 启动后端：`mvn spring-boot:run`（加载新 System Prompt）
2. 启动前端：`cd frontend && npm run dev`
3. 测试场景：

| 测试输入 | 预期类型 | 预期渲染 |
|----------|----------|----------|
| "茅台今天多少钱" | `kpi` | KPI 网格卡片 |
| "什么是复利" | `text` | 默认 Markdown 卡片 |
| "帮我分析一下定投策略" | `card` | 带标题卡片 |
| "查询数据库中的用户" | `table` | 表格卡片 |
| 旧消息（无标记） | `text`（默认） | 正常渲染 |

4. 主题切换：暗色/亮色模式下所有组件样式正常
5. 流式体验：输入发送后实时输出，无卡顿
