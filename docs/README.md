# docs 目录索引

> 项目文档统一存放目录，按用途分类。

## 目录结构

```
docs/
├── APIDocs/            # API 接口文档
├── Deploy/             # 部署相关文档
├── DevelopmentProcess/ # 开发过程中的设计文档、技术方案、bugfix 记录
├── Guides/             # 使用指南、工具说明、功能特性
├── Planning/           # 项目规划、开发计划、改进方案
├── References/         # 参考资料（简历亮点、外部参考等）
├── pic/                # 文档引用的图片资源
├── a-stock-data/       # A股数据工具集（外部参考项目）
├── yangjibao-funds/    # 养基宝基金数据（外部参考项目）
├── superpowers/        # Claude Code 规划与设计文档
└── 门户网站示例/        # 门户网站前端代码示例
```

## 文档分类速查

### API 文档
- [agent-api.md](APIDocs/agent-api.md) — Agent API 接口说明
- [金融工具调用说明.md](APIDocs/金融工具调用说明.md) — 金融工具调用文档

### 部署
- [阿里云Docker部署指南.md](Deploy/阿里云Docker部署指南.md) — 阿里云 Docker 部署流程

### 设计与开发过程
- [agent-architecture-refactor-design.md](DevelopmentProcess/2026-06-24-agent-architecture-refactor-design.md)
- [message-persistence-design.md](DevelopmentProcess/2026-06-24-message-persistence-design.md)
- [tool-system-design.md](DevelopmentProcess/2026-06-24-tool-system-design.md)
- [user-auth-design.md](DevelopmentProcess/2026-06-25-user-auth-design.md)
- [sql-tool-design.md](DevelopmentProcess/2026-06-27-sql-tool-design.md)
- [chat-ui-redesign.md](DevelopmentProcess/chat-ui-redesign.md)
- [tool-call-guard-system.md](DevelopmentProcess/tool-call-guard-system.md)
- [tool-call-loop-bugfix.md](DevelopmentProcess/tool-call-loop-bugfix.md)

### 使用指南与功能特性
- [project-features.md](Guides/project-features.md) — 项目功能与技术亮点总览
- [tool-reference.md](Guides/tool-reference.md) — 工具参考手册
- [MultiAgentWorkflow.md](Guides/MultiAgentWorkflow.md) — 多Agent工作流
- [analyst-tools-mapping.md](Guides/analyst-tools-mapping.md) — 分析师工具映射
- [market-tools-summary.md](Guides/market-tools-summary.md) — 市场工具汇总
- [drift-issues-summary.md](Guides/drift-issues-summary.md) — 幻觉问题汇总
- [stock-drift-prevention.md](Guides/stock-drift-prevention.md) — 股票数据幻觉防护
- [工具开关管理机制.md](Guides/工具开关管理机制.md) — 工具开关配置说明
- [金融Agent高级特性升级指南.md](Guides/金融Agent高级特性升级指南.md) — 高级特性升级方案
- [养基宝持仓查询性能优化.md](Guides/养基宝持仓查询性能优化.md) — 养基宝性能优化记录

### 项目规划
- [金融投资导学Agent开发计划书.md](Planning/金融投资导学Agent开发计划书.md)
- [金融Agent-Tool扩充方案.md](Planning/金融Agent-Tool扩充方案.md)
- [项目全景诊断与改进清单.md](Planning/项目全景诊断与改进清单.md)
- [项目结构改进路线图.md](Planning/项目结构改进路线图.md)
- [tool-call-guard-v2-plan.md](Planning/tool-call-guard-v2-plan.md)

### 参考资料
- [简历项目亮点总结.md](References/简历项目亮点总结.md) — 简历项目亮点梳理
