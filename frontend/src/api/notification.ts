import request from './request'

export interface Notification {
  id: number
  title: string
  content: string
  createdAt: string
}

export function getNotifications() {
  return request.get<any, { code: number; data: Notification[] }>('/api/notifications')
}

export function getUnreadCount() {
  return request.get<any, { code: number; data: { count: number } }>('/api/notifications/unread-count')
}

export function markAsRead(id: number) {
  return request.post<any, { code: number }>(`/api/notifications/${id}/read`)
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
          // 5 秒后重连
          if (!controller.signal.aborted) {
            setTimeout(connect, 5000)
          }
        }
      })
  }

  connect()
  return controller
}
