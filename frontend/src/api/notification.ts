import request from './request'

export interface Notification {
  id: number
  title: string
  content: string
  createdAt: string
}

export async function getNotifications() {
  const { data } = await request.get('/api/notifications')
  return data as { code: number; data: Notification[] }
}

export async function getUnreadCount() {
  const { data } = await request.get('/api/notifications/unread-count')
  return data as { code: number; data: { count: number } }
}

export async function markAsRead(id: number) {
  const { data } = await request.post(`/api/notifications/${id}/read`)
  return data as { code: number }
}

export function connectNotificationSSE(
  token: string,
  onMessage: (notification: Notification) => void,
  onError?: () => void
): AbortController {
  const controller = new AbortController()

  const connect = () => {
    fetch('/api/notifications/stream', {
      headers: { Authorization: `Bearer ${token}` },
      signal: controller.signal,
    })
      .then(async (res) => {
        if (!res.ok || !res.body) {
          throw new Error(`SSE 连接失败: ${res.status}`)
        }

        const reader = res.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''

        while (true) {
          const { done, value } = await reader.read()
          if (done) break

          buffer += decoder.decode(value, { stream: true })
          const lines = buffer.split('\n')
          buffer = lines.pop() || ''

          for (const line of lines) {
            if (line.startsWith('data:')) {
              try {
                const notification = JSON.parse(line.slice(5).trim())
                onMessage(notification)
              } catch (e) {
                console.error('解析通知失败:', e)
              }
            }
          }
        }
      })
      .catch((err) => {
        if (err.name !== 'AbortError') {
          console.error('通知 SSE 连接断开:', err)
          onError?.()
          if (!controller.signal.aborted) {
            setTimeout(connect, 5000)
          }
        }
      })
  }

  connect()
  return controller
}
