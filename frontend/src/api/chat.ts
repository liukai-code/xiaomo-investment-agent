export interface StreamCallbacks {
  onChunk: (text: string) => void
  onDone: (fullText: string) => void
  onError: (err: Error) => void
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
        const result = processEvents(buffer)
        buffer = result.incomplete
        if (result.content) {
          lastText = result.content
          callbacks.onChunk(lastText)
        }
      }

      // 处理剩余 buffer
      if (buffer.trim()) {
        const data = parseEventData(buffer)
        if (data) lastText = data
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

function parseEventData(event: string): string {
  const dataLines: string[] = []
  for (const line of event.split(/\n/)) {
    if (line.startsWith('data:')) {
      dataLines.push(line.substring(5))
    } else if (dataLines.length > 0) {
      // chunk 边界切分导致的续行，拼接到上一个 data 行末尾
      dataLines[dataLines.length - 1] += line
    }
  }
  return dataLines.join('\n')
}

function processEvents(raw: string): { content: string; incomplete: string } {
  const events = raw.split(/\n\n/)
  const incomplete = events.pop() || ''
  let content = ''
  for (const event of events) {
    if (!event.trim()) continue
    const data = parseEventData(event)
    if (data) content = data
  }
  return { content, incomplete }
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

interface ParsedSseEvent {
  event: string
  data: string
}

function processWorkflowEvents(raw: string): { events: ParsedSseEvent[]; incomplete: string } {
  const parts = raw.split(/\n\n/)
  const incomplete = parts.pop() || ''
  const events: ParsedSseEvent[] = []

  for (const part of parts) {
    if (!part.trim()) continue
    let event = 'message'
    let data = ''
    for (const line of part.split(/\n/)) {
      if (line.startsWith('event:')) {
        event = line.substring(6).trim()
      } else if (line.startsWith('data:')) {
        data += line.substring(5)
      } else if (data) {
        data += line
      }
    }
    if (data) {
      events.push({ event, data })
    }
  }

  return { events, incomplete }
}
