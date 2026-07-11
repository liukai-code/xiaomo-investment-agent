<script setup lang="ts">
import { useNotificationStore } from '@/stores/notification'
import { Bell, Check, CheckCheck, X, Trash2 } from 'lucide-vue-next'

const notificationStore = useNotificationStore()

function formatTime(dateStr: string): string {
  const date = new Date(dateStr)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMin = Math.floor(diffMs / 60000)
  const diffHour = Math.floor(diffMs / 3600000)
  const diffDay = Math.floor(diffMs / 86400000)

  if (diffMin < 1) return '刚刚'
  if (diffMin < 60) return `${diffMin}分钟前`
  if (diffHour < 24) return `${diffHour}小时前`
  if (diffDay < 30) return `${diffDay}天前`
  return date.toLocaleDateString('zh-CN')
}

function truncate(text: string, maxLen: number): string {
  return text.length > maxLen ? text.slice(0, maxLen) + '...' : text
}
</script>

<template>
  <div class="notification-panel" @click.stop>
    <div class="notification-header">
      <span class="notification-title">通知</span>
      <div class="notification-actions">
        <button
          v-if="notificationStore.unreadCount > 0"
          class="mark-all-btn"
          @click="notificationStore.markAllAsRead()"
          title="全部标为已读"
        >
          <CheckCheck :size="14" />
          <span>全部已读</span>
        </button>
        <button class="close-btn" @click="notificationStore.closePanel()">
          <X :size="16" />
        </button>
      </div>
    </div>

    <div class="notification-list">
      <div
        v-if="notificationStore.notifications.length === 0"
        class="notification-empty"
      >
        <Bell :size="32" />
        <span>暂无通知</span>
      </div>

      <div
        v-for="item in notificationStore.notifications"
        :key="item.id"
        class="notification-item"
        :class="{ unread: !notificationStore.readIds.has(item.id) }"
        @click="notificationStore.markAsRead(item.id)"
      >
        <div class="notification-dot-wrapper">
          <span
            v-if="!notificationStore.readIds.has(item.id)"
            class="notification-dot"
          ></span>
        </div>
        <div class="notification-content">
          <div class="notification-item-title">{{ item.title }}</div>
          <div class="notification-item-body">{{ truncate(item.content, 80) }}</div>
          <div class="notification-item-time">{{ formatTime(item.createdAt) }}</div>
        </div>
        <button
          class="notification-delete-btn"
          title="删除"
          @click.stop="notificationStore.deleteNotification(item.id)"
        >
          <Trash2 :size="14" />
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.notification-panel {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  width: 360px;
  max-height: 480px;
  background: var(--surface-1);
  border: 1px solid var(--border);
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  z-index: 1000;
}

.notification-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border);
}

.notification-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text);
}

.notification-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.mark-all-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--primary);
  background: none;
  border: none;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
  transition: background 0.15s;
}

.mark-all-btn:hover {
  background: var(--surface-2);
}

.close-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  border: none;
  background: none;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.15s;
}

.close-btn:hover {
  background: var(--surface-2);
  color: var(--text);
}

.notification-list {
  flex: 1;
  overflow-y: auto;
  max-height: 400px;
}

.notification-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 16px;
  color: var(--text-muted);
  gap: 12px;
}

.notification-empty svg {
  opacity: 0.4;
}

.notification-item {
  display: flex;
  gap: 10px;
  padding: 12px 16px;
  cursor: pointer;
  transition: background 0.15s;
  border-bottom: 1px solid var(--border);
}

.notification-item:last-child {
  border-bottom: none;
}

.notification-item:hover {
  background: var(--surface-2);
}

.notification-item.unread {
  background: rgba(59, 130, 246, 0.04);
}

.notification-dot-wrapper {
  width: 8px;
  flex-shrink: 0;
  display: flex;
  justify-content: center;
  padding-top: 6px;
}

.notification-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--primary);
}

.notification-content {
  flex: 1;
  min-width: 0;
}

.notification-item-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.notification-item-body {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.5;
  margin-bottom: 4px;
  word-break: break-all;
}

.notification-item-time {
  font-size: 11px;
  color: var(--text-muted);
}

.notification-delete-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  border: none;
  background: none;
  color: var(--text-muted);
  cursor: pointer;
  opacity: 0;
  transition: all 0.15s;
  flex-shrink: 0;
  align-self: center;
}

.notification-item:hover .notification-delete-btn {
  opacity: 1;
}

.notification-delete-btn:hover {
  background: rgba(239, 68, 68, 0.1);
  color: #F87171;
}
</style>
