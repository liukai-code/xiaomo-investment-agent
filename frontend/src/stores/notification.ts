import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  getNotifications,
  getUnreadCount,
  getReadIds,
  markAsRead as apiMarkAsRead,
  hideNotification as apiHideNotification,
  connectNotificationSSE,
  type Notification
} from '@/api/notification'

export const useNotificationStore = defineStore('notification', () => {
  const notifications = ref<Notification[]>([])
  const unreadCount = ref(0)
  const panelOpen = ref(false)
  const readIds = ref<Set<number>>(new Set())
  const error = ref<string | null>(null)
  let sseController: AbortController | null = null

  const unreadNotifications = computed(() =>
    notifications.value.filter(n => !readIds.value.has(n.id))
  )

  async function loadNotifications() {
    try {
      error.value = null
      const res = await getNotifications()
      if (res.code === 1) {
        notifications.value = res.data
      } else {
        error.value = res.msg || '加载失败'
      }
    } catch (e: any) {
      error.value = e?.message || '网络错误'
      console.error('加载通知失败:', e)
    }
  }

  async function loadUnreadCount() {
    try {
      const res = await getUnreadCount()
      if (res.code === 1) {
        unreadCount.value = res.data.count
      }
    } catch (e) {
      console.error('加载未读数失败:', e)
    }
  }

  async function loadReadIds() {
    try {
      const res = await getReadIds()
      if (res.code === 1) {
        readIds.value = new Set(res.data)
      }
    } catch (e) {
      console.error('加载已读状态失败:', e)
    }
  }

  async function markAsRead(id: number) {
    if (readIds.value.has(id)) return
    readIds.value.add(id)
    unreadCount.value = Math.max(0, unreadCount.value - 1)
    try {
      await apiMarkAsRead(id)
    } catch (e) {
      console.error('标记已读失败:', e)
    }
  }

  function markAllAsRead() {
    notifications.value.forEach(n => {
      if (!readIds.value.has(n.id)) {
        readIds.value.add(n.id)
        apiMarkAsRead(n.id).catch(() => {})
      }
    })
    unreadCount.value = 0
  }

  function togglePanel() {
    panelOpen.value = !panelOpen.value
  }

  function closePanel() {
    panelOpen.value = false
  }

  function startSSE(token: string) {
    if (sseController) {
      sseController.abort()
    }
    sseController = connectNotificationSSE(token, (notification) => {
      // 新通知到来
      notifications.value.unshift(notification)
      unreadCount.value++
    })
  }

  function stopSSE() {
    if (sseController) {
      sseController.abort()
      sseController = null
    }
  }

  function init(token: string) {
    loadNotifications()
    loadUnreadCount()
    loadReadIds()
    startSSE(token)
  }

  async function deleteNotification(id: number) {
    const item = notifications.value.find(n => n.id === id)
    notifications.value = notifications.value.filter(n => n.id !== id)
    if (item && !readIds.value.has(id)) {
      unreadCount.value = Math.max(0, unreadCount.value - 1)
    }
    try {
      await apiHideNotification(id)
    } catch (e) {
      console.error('隐藏通知失败:', e)
    }
  }

  function reset() {
    notifications.value = []
    unreadCount.value = 0
    readIds.value = new Set()
    error.value = null
    panelOpen.value = false
    stopSSE()
  }

  return {
    notifications,
    unreadCount,
    panelOpen,
    readIds,
    error,
    unreadNotifications,
    loadNotifications,
    loadUnreadCount,
    markAsRead,
    markAllAsRead,
    deleteNotification,
    togglePanel,
    closePanel,
    init,
    reset
  }
})
