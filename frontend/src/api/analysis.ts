import request from './request'
import { processWorkflowEvents } from './chat'
import type { WorkflowEvent } from './chat'

export type { WorkflowEvent }

export interface AnalysisRecord {
  id: number
  userId: number
  conversationId: number | null
  originalQuery: string
  resolvedStockCode: string | null
  resolvedStockName: string | null
  workflowStatus: string
  action: string | null
  confidence: number | null
  targetPrice: number | null
  summary: string | null
  tradingProposal: string | null
  investmentPlan: string | null
  analystReportsJson: string | null
  bullBearDebateJson: string | null
  riskDebateJson: string | null
  startedAt: string | null
  completedAt: string | null
  errorMessage: string | null
  createdAt: string
}

export interface StartAnalysisResponse {
  analysisId: number
  stockCode: string
  stockName: string
}

interface Result<T> {
  code: number
  msg?: string
  data: T
}

export async function startAnalysis(query: string): Promise<StartAnalysisResponse> {
  const res = await request.post<Result<StartAnalysisResponse>>('/api/analysis/start', { query })
  if (res.data.code !== 1) throw new Error(res.data.msg || '发起分析失败')
  return res.data.data
}

export async function getAnalysisList(): Promise<AnalysisRecord[]> {
  const res = await request.get<Result<AnalysisRecord[]>>('/api/analysis/list')
  if (res.data.code !== 1) throw new Error(res.data.msg || '获取列表失败')
  return res.data.data
}

export async function getAnalysisDetail(id: number): Promise<AnalysisRecord> {
  const res = await request.get<Result<AnalysisRecord>>(`/api/analysis/${id}`)
  if (res.data.code !== 1) throw new Error(res.data.msg || '获取详情失败')
  return res.data.data
}

export async function deleteAnalysis(id: number): Promise<void> {
  const res = await request.delete<Result<void>>(`/api/analysis/${id}`)
  if (res.data.code !== 1) throw new Error(res.data.msg || '删除失败')
}

export async function cancelAnalysis(id: number): Promise<void> {
  const res = await request.post<Result<void>>(`/api/analysis/${id}/cancel`)
  if (res.data.code !== 1) throw new Error(res.data.msg || '取消失败')
}

/**
 * SSE 实时分析事件流
 */
export function streamAnalysis(
  analysisId: number,
  token: string,
  callbacks: {
    onEvent: (event: WorkflowEvent) => void
    onDone: () => void
    onError: (error: string) => void
  },
): AbortController {
  const controller = new AbortController()
  const url = `/api/analysis/${analysisId}/stream`

  fetch(url, {
    headers: { Authorization: `Bearer ${token}` },
    signal: controller.signal,
  })
    .then(async (response) => {
      if (!response.ok) {
        callbacks.onError(`HTTP ${response.status}`)
        return
      }
      const reader = response.body!.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      let doneCalled = false

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const result = processWorkflowEvents(buffer)
        buffer = result.incomplete

        for (const event of result.events) {
          if (event.event === 'workflow' && event.data) {
            try {
              callbacks.onEvent(JSON.parse(event.data))
            } catch {
              // ignore parse errors
            }
          } else if (event.event === 'done') {
            doneCalled = true
            callbacks.onDone()
          }
        }
      }

      // 处理剩余 buffer
      if (buffer.trim()) {
        const remaining = processWorkflowEvents(buffer + '\n\n')
        for (const event of remaining.events) {
          if (event.event === 'workflow' && event.data) {
            try {
              callbacks.onEvent(JSON.parse(event.data))
            } catch {
              // ignore
            }
          } else if (event.event === 'done') {
            doneCalled = true
            callbacks.onDone()
          }
        }
      }

      if (!doneCalled) callbacks.onDone()
    })
    .catch((err) => {
      if (err.name !== 'AbortError') callbacks.onError(err.message)
    })

  return controller
}
