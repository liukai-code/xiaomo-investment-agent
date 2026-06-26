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
      let fullText = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const result = processEvents(buffer)
        buffer = result.incomplete
        if (result.content) {
          fullText += result.content
          callbacks.onChunk(fullText)
        }
      }

      // 处理剩余 buffer
      if (buffer.trim()) {
        const lines = buffer.split(/\n/)
        for (const line of lines) {
          if (line.startsWith('data:')) {
            fullText += line.substring(5)
          }
        }
      }

      callbacks.onDone(fullText)
    })
    .catch((err) => {
      if (err.name !== 'AbortError') {
        callbacks.onError(err)
      }
    })

  return controller
}

function processEvents(raw: string): { content: string; incomplete: string } {
  const events = raw.split(/\n\n/)
  const incomplete = events.pop() || ''
  let content = ''
  for (const event of events) {
    if (!event.trim()) continue
    const lines = event.split(/\n/)
    for (const line of lines) {
      if (line.startsWith('data:')) {
        if (content) content += '\n'
        content += line.substring(5)
      }
    }
  }
  return { content, incomplete }
}
