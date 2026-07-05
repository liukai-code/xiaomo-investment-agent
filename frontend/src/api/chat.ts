export interface StatusEvent {
  type: 'THINKING' | 'TOOL_CALL' | 'TOOL_RESULT' | 'CONTENT'
  toolName?: string
  content?: string
  step?: number
  totalSteps?: number
}

export interface StreamCallbacks {
  onChunk: (text: string) => void
  onDone: (fullText: string) => void
  onError: (err: Error) => void
  onStatus?: (event: StatusEvent) => void
}

export function streamChat(
  conversationId: number,
  message: string,
  token: string,
  callbacks: StreamCallbacks,
): AbortController {
  const controller = new AbortController()

  const url = `/agent/chat/stream?conversationId=${conversationId}&message=${encodeURIComponent(message)}`

  fetch(url, {
    headers: { Authorization: `Bearer ${token}` },
    signal: controller.signal,
  })
    .then(async (res) => {
      if (!res.ok) throw new Error(`请求失败: ${res.status}`)
      if (!res.body) throw new Error('响应体为空')

      const reader = res.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      let lastText = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const result = processChatEvents(buffer)
        buffer = result.incomplete

        for (const event of result.events) {
          if (event.event === 'status' && event.data && callbacks.onStatus) {
            try {
              const parsed: StatusEvent = JSON.parse(event.data)
              callbacks.onStatus(parsed)
            } catch {
              // ignore parse errors
            }
          } else if (event.event === 'content' && event.data) {
            lastText = event.data
            callbacks.onChunk(lastText)
          } else if (event.event === 'done') {
            // done 事件在循环结束时处理
          }
        }
      }

      // 处理剩余 buffer
      if (buffer.trim()) {
        const remaining = processChatEvents(buffer + '\n\n')
        for (const event of remaining.events) {
          if (event.event === 'status' && event.data && callbacks.onStatus) {
            try {
              const parsed: StatusEvent = JSON.parse(event.data)
              callbacks.onStatus(parsed)
            } catch {
              // ignore
            }
          } else if (event.event === 'content' && event.data) {
            lastText = event.data
          }
        }
      }

      callbacks.onDone(lastText)
    })
    .catch((err) => {
      if (err.name !== 'AbortError') {
        callbacks.onError(err)
      }
    })

  return controller
}

interface ParsedSseEvent {
  event: string
  data: string
}

function processChatEvents(raw: string): { events: ParsedSseEvent[]; incomplete: string } {
  const parts = raw.split(/\n\n/)
  const incomplete = parts.pop() || ''
  const events: ParsedSseEvent[] = []

  for (const part of parts) {
    if (!part.trim()) continue
    let event = 'message'
    const dataLines: string[] = []
    for (const line of part.split(/\n/)) {
      if (line.startsWith('event:')) {
        event = line.substring(6).trim()
      } else if (line.startsWith('data:')) {
        dataLines.push(line.substring(5))
      } else if (dataLines.length > 0) {
        dataLines[dataLines.length - 1] += '\n' + line
      }
    }
    if (dataLines.length > 0) {
      events.push({ event, data: dataLines.join('\n') })
    }
  }

  return { events, incomplete }
}

// ========== 深度分析工作流 ==========

export interface WorkflowEvent {
  type: string
  agentName: string | null
  content: string | null
  phase: string | null
  timestamp: string
}

export interface WorkflowCallbacks {
  onEvent: (event: WorkflowEvent) => void
  onDone: () => void
  onError: (err: Error) => void
}

export function streamDeepAnalysis(
  conversationId: number,
  message: string,
  token: string,
  callbacks: WorkflowCallbacks,
): AbortController {
  const controller = new AbortController()

  const url = `/agent/chat/deep-analysis?conversationId=${conversationId}&message=${encodeURIComponent(message)}`

  fetch(url, {
    headers: { Authorization: `Bearer ${token}` },
    signal: controller.signal,
  })
    .then(async (res) => {
      if (!res.ok) throw new Error(`请求失败: ${res.status}`)
      if (!res.body) throw new Error('响应体为空')

      const reader = res.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const result = processWorkflowEvents(buffer)
        buffer = result.incomplete

        for (const event of result.events) {
          if (event.event === 'done') {
            callbacks.onDone()
          } else if (event.event === 'workflow' && event.data) {
            try {
              const parsed: WorkflowEvent = JSON.parse(event.data)
              callbacks.onEvent(parsed)
            } catch {
              // ignore parse errors
            }
          }
        }
      }

      // 处理剩余 buffer
      if (buffer.trim()) {
        const remaining = processWorkflowEvents(buffer + '\n\n')
        for (const event of remaining.events) {
          if (event.event === 'done') {
            callbacks.onDone()
          } else if (event.event === 'workflow' && event.data) {
            try {
              const parsed: WorkflowEvent = JSON.parse(event.data)
              callbacks.onEvent(parsed)
            } catch {
              // ignore
            }
          }
        }
      }

      callbacks.onDone()
    })
    .catch((err) => {
      if (err.name !== 'AbortError') {
        callbacks.onError(err)
      }
    })

  return controller
}

function processWorkflowEvents(raw: string): { events: ParsedSseEvent[]; incomplete: string } {
  const parts = raw.split(/\n\n/)
  const incomplete = parts.pop() || ''
  const events: ParsedSseEvent[] = []

  for (const part of parts) {
    if (!part.trim()) continue
    let event = 'message'
    const dataLines: string[] = []
    for (const line of part.split(/\n/)) {
      if (line.startsWith('event:')) {
        event = line.substring(6).trim()
      } else if (line.startsWith('data:')) {
        dataLines.push(line.substring(5))
      } else if (dataLines.length > 0) {
        dataLines[dataLines.length - 1] += '\n' + line
      }
    }
    if (dataLines.length > 0) {
      events.push({ event, data: dataLines.join('\n') })
    }
  }

  return { events, incomplete }
}
