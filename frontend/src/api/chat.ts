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
        if (data) lastText = sanitizeContent(data)
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
  return { content: sanitizeContent(content), incomplete }
}

function sanitizeContent(text: string): string {
  return text.replace(/\n*\[GUARD_SIGNAL\][\s\S]*?\[\/GUARD_SIGNAL\]\n*/g, '').trim()
}
