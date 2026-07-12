import { Document, Packer, Paragraph, TextRun, HeadingLevel } from 'docx'
import { saveAs } from 'file-saver'
import { marked } from 'marked'
import type { AnalysisRecord, WorkflowEvent } from '@/api/analysis'

type ExportFormat = 'pdf' | 'word' | 'md'

// 代理名称映射
const agentNameMap: Record<string, string> = {
  MarketAnalyst: '技术面分析师',
  FundamentalsAnalyst: '基本面分析师',
  NewsAnalyst: '新闻事件分析师',
  BullResearcher: '看多研究员',
  BearResearcher: '看空研究员',
  ResearchManager: '研究主管',
  Trader: '交易员',
  AggressiveAnalyst: '激进分析师',
  ConservativeAnalyst: '保守分析师',
  NeutralAnalyst: '中立分析师',
  RiskJudge: '风险裁决官',
}

// 阶段名称映射
const phaseNameMap: Record<string, string> = {
  Layer1_DataCollection: '数据采集',
  BullBearDebate: '多空辩论',
  Trader: '交易决策',
  RiskDebate: '风险评估',
}

/**
 * 从 WorkflowEvent 或 JSON 字段获取代理内容
 */
function getAgentContent(
  events: WorkflowEvent[],
  agentName: string,
  jsonFallback?: string | null,
): string {
  // 优先从 events 获取
  const event = events.find(
    (e) =>
      e.agentName === agentName &&
      (e.type === 'AGENT_COMPLETE' || e.type === 'DEBATE_COMPLETE'),
  )
  if (event?.content) return event.content

  // 回退到 JSON 字段
  if (jsonFallback) {
    try {
      const parsed = JSON.parse(jsonFallback)
      return parsed[agentName] || ''
    } catch {
      return ''
    }
  }
  return ''
}

/**
 * 组装 Markdown 报告
 */
export function buildMarkdownReport(
  record: AnalysisRecord,
  events: WorkflowEvent[],
): string {
  const lines: string[] = []
  const stockName = record.resolvedStockName || record.originalQuery
  const stockCode = record.resolvedStockCode || ''

  // 标题
  lines.push(`# ${stockName}${stockCode ? ` (${stockCode})` : ''} 深度分析报告`)
  lines.push('')

  // 元信息
  const createdAt = record.createdAt
    ? new Date(record.createdAt).toLocaleString('zh-CN')
    : '-'
  lines.push(`**分析时间**: ${createdAt}`)
  lines.push(`**分析状态**: ${getStatusLabel(record.workflowStatus)}`)
  lines.push('')
  lines.push('---')
  lines.push('')

  // 投资决策
  lines.push('## 投资决策')
  lines.push('')
  if (record.action) lines.push(`- **操作建议**: ${record.action}`)
  if (record.confidence != null)
    lines.push(`- **置信度**: ${Math.round(record.confidence * 100)}%`)
  if (record.targetPrice != null)
    lines.push(`- **目标价**: ¥${record.targetPrice}`)
  lines.push('')
  if (record.summary) {
    lines.push(record.summary)
    lines.push('')
  }
  lines.push('---')
  lines.push('')

  // 交易方案
  if (record.tradingProposal) {
    lines.push('## 交易方案')
    lines.push('')
    lines.push(record.tradingProposal)
    lines.push('')
    lines.push('---')
    lines.push('')
  }

  // 投资计划
  if (record.investmentPlan) {
    lines.push('## 投资计划')
    lines.push('')
    lines.push(record.investmentPlan)
    lines.push('')
    lines.push('---')
    lines.push('')
  }

  // 各阶段代理内容
  const agentGroups: { phase: string; agents: string[] }[] = [
    {
      phase: 'Layer1_DataCollection',
      agents: ['MarketAnalyst', 'FundamentalsAnalyst', 'NewsAnalyst'],
    },
    {
      phase: 'BullBearDebate',
      agents: ['BullResearcher', 'BearResearcher', 'ResearchManager'],
    },
    { phase: 'Trader', agents: ['Trader'] },
    {
      phase: 'RiskDebate',
      agents: ['AggressiveAnalyst', 'ConservativeAnalyst', 'NeutralAnalyst', 'RiskJudge'],
    },
  ]

  // JSON 回退字段映射
  const jsonFallbackMap: Record<string, string | null> = {
    Layer1_DataCollection: record.analystReportsJson,
    BullBearDebate: record.bullBearDebateJson,
    Trader: null,
    RiskDebate: record.riskDebateJson,
  }

  for (const group of agentGroups) {
    const hasContent = group.agents.some((agent) => {
      const fallback = jsonFallbackMap[group.phase]
      return getAgentContent(events, agent, fallback)
    })
    if (!hasContent) continue

    lines.push(`## ${phaseNameMap[group.phase] || group.phase}`)
    lines.push('')

    for (const agent of group.agents) {
      const fallback = jsonFallbackMap[group.phase]
      const content = getAgentContent(events, agent, fallback)
      if (!content) continue

      lines.push(`### ${agentNameMap[agent] || agent}`)
      lines.push('')
      lines.push(content)
      lines.push('')
    }

    lines.push('---')
    lines.push('')
  }

  return lines.join('\n')
}

/**
 * 获取状态标签
 */
function getStatusLabel(status: string): string {
  const map: Record<string, string> = {
    PENDING: '等待中',
    RUNNING: '分析中',
    COMPLETED: '已完成',
    CANCELLED: '已停止',
    FAILED: '失败',
  }
  return map[status] || status
}

/**
 * 生成文件名
 */
export function generateFilename(record: AnalysisRecord, ext: string): string {
  const stockName = record.resolvedStockName || '分析报告'
  const date = new Date(record.createdAt)
  const dateStr = `${date.getFullYear()}${String(date.getMonth() + 1).padStart(2, '0')}${String(date.getDate()).padStart(2, '0')}`
  return `${stockName}_深度分析_${dateStr}.${ext}`
}

/**
 * 导出为 Markdown 文件
 */
export function exportAsMarkdown(markdown: string, filename: string): void {
  const blob = new Blob([markdown], { type: 'text/markdown;charset=utf-8' })
  saveAs(blob, filename)
}

/**
 * 导出为 Word 文档
 */
export async function exportAsWord(markdown: string, filename: string): Promise<void> {
  const tokens = marked.lexer(markdown)
  const children: Paragraph[] = []

  for (const token of tokens) {
    switch (token.type) {
      case 'heading': {
        const headingLevels = [
          HeadingLevel.HEADING_1,
          HeadingLevel.HEADING_2,
          HeadingLevel.HEADING_3,
          HeadingLevel.HEADING_4,
          HeadingLevel.HEADING_5,
          HeadingLevel.HEADING_6,
        ]
        children.push(
          new Paragraph({
            heading: headingLevels[Math.min(token.depth - 1, 5)],
            children: [new TextRun(token.text)],
          }),
        )
        break
      }
      case 'paragraph': {
        children.push(
          new Paragraph({
            children: [new TextRun(token.text)],
          }),
        )
        break
      }
      case 'list': {
        for (const item of token.items) {
          children.push(
            new Paragraph({
              text: item.text,
              bullet: { level: 0 },
            }),
          )
        }
        break
      }
      case 'code': {
        children.push(
          new Paragraph({
            children: [
              new TextRun({
                text: token.text,
                font: 'Courier New',
                size: 20,
              }),
            ],
          }),
        )
        break
      }
      case 'hr': {
        children.push(new Paragraph({ text: '' }))
        break
      }
      default: {
        // 处理其他类型的 token
        const text = (token as any).text || (token as any).raw || ''
        if (text.trim()) {
          children.push(
            new Paragraph({
              children: [new TextRun(text)],
            }),
          )
        }
      }
    }
  }

  const doc = new Document({
    sections: [{ children }],
  })

  const blob = await Packer.toBlob(doc)
  saveAs(blob, filename)
}

/**
 * 导出为 PDF（通过打印窗口）
 */
export function exportAsPdf(markdown: string, filename: string): void {
  const htmlContent = marked.parse(markdown)

  const printWindow = window.open('', '_blank')
  if (!printWindow) {
    alert('请允许弹出窗口以导出 PDF')
    return
  }

  printWindow.document.write(`
    <!DOCTYPE html>
    <html>
    <head>
      <title>${filename}</title>
      <style>
        body {
          font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
          line-height: 1.6;
          max-width: 800px;
          margin: 0 auto;
          padding: 40px 20px;
          color: #333;
        }
        h1 { font-size: 24px; border-bottom: 2px solid #eee; padding-bottom: 10px; }
        h2 { font-size: 20px; margin-top: 30px; color: #1a1a1a; }
        h3 { font-size: 16px; margin-top: 20px; color: #444; }
        p { margin: 10px 0; }
        ul, ol { padding-left: 20px; }
        li { margin: 5px 0; }
        hr { border: none; border-top: 1px solid #eee; margin: 20px 0; }
        code { background: #f5f5f5; padding: 2px 6px; border-radius: 3px; font-size: 14px; }
        pre { background: #f5f5f5; padding: 16px; border-radius: 6px; overflow-x: auto; }
        pre code { background: none; padding: 0; }
        strong { color: #1a1a1a; }
        @media print {
          body { padding: 0; }
          @page { margin: 2cm; }
        }
      </style>
    </head>
    <body>
      ${htmlContent}
      <script>
        window.onload = function() {
          setTimeout(function() { window.print(); }, 200);
        };
      </script>
    </body>
    </html>
  `)
  printWindow.document.close()
}

/**
 * 主导出函数
 */
export async function exportAnalysisReport(options: {
  record: AnalysisRecord
  events: WorkflowEvent[]
  format: ExportFormat
}): Promise<void> {
  const { record, events, format } = options
  const markdown = buildMarkdownReport(record, events)

  const extMap: Record<ExportFormat, string> = {
    pdf: 'pdf',
    word: 'docx',
    md: 'md',
  }

  const filename = generateFilename(record, extMap[format])

  switch (format) {
    case 'md':
      exportAsMarkdown(markdown, filename)
      break
    case 'word':
      await exportAsWord(markdown, filename)
      break
    case 'pdf':
      exportAsPdf(markdown, filename)
      break
  }
}
