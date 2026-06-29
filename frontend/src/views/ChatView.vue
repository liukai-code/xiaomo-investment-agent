<script setup lang="ts">
import { ref, nextTick, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import { useThemeStore } from '@/stores/theme'
import { streamChat } from '@/api/chat'
import MarkdownRenderer from '@/components/blocks/MarkdownRenderer.vue'
import { useRafThrottle } from '@/composables/useMarkdownBlocks'

const router = useRouter()
const authStore = useAuthStore()
const chatStore = useChatStore()
const themeStore = useThemeStore()

const messagesEl = ref<HTMLDivElement>()
const inputEl = ref<HTMLTextAreaElement>()
const inputText = ref('')
const statusText = ref('READY')
const activeMenuConvId = ref<number | null>(null)
const deleteConfirmConvId = ref<number | null>(null)

let abortController: AbortController | null = null

const currentTitle = computed(() => {
  const conv = chatStore.getCurrentConversation()
  return conv ? conv.title : 'Financial Agent'
})

function formatTime(ts: string) {
  if (!ts) return ''
  const d = new Date(ts)
  const now = new Date()
  if (d.toDateString() === now.toDateString()) {
    return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  return d.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}

function scrollToBottom() {
  nextTick(() => {
    if (messagesEl.value) {
      messagesEl.value.scrollTop = messagesEl.value.scrollHeight
    }
  })
}

async function handleCreateConversation() {
  await chatStore.createConversation()
}

async function handleSwitchConversation(id: number) {
  if (abortController) {
    abortController.abort()
    abortController = null
    chatStore.isGenerating = false
    statusText.value = 'READY'
  }
  await chatStore.switchConversation(id)
}

async function handleLogout() {
  await authStore.logout()
  router.push('/login')
}

function handleInput() {
  if (!inputEl.value) return
  inputEl.value.style.height = 'auto'
  inputEl.value.style.height = Math.min(inputEl.value.scrollHeight, 120) + 'px'
}

function toggleMenu(convId: number, e: Event) {
  e.stopPropagation()
  activeMenuConvId.value = activeMenuConvId.value === convId ? null : convId
}

function closeMenu() {
  activeMenuConvId.value = null
}

async function handleDeleteConversation(convId: number) {
  closeMenu()
  deleteConfirmConvId.value = convId
}

async function confirmDelete() {
  if (deleteConfirmConvId.value !== null) {
    await chatStore.deleteConversation(deleteConfirmConvId.value)
    deleteConfirmConvId.value = null
  }
}

function cancelDelete() {
  deleteConfirmConvId.value = null
}

function onDocumentClick() {
  closeMenu()
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}

async function handleSend() {
  const text = inputText.value.trim()
  if (!text || chatStore.isGenerating) return

  if (!chatStore.currentConvId) {
    const conv = await chatStore.createConversation()
    if (!conv) return
  }

  chatStore.addUserMessage(text)
  inputText.value = ''
  if (inputEl.value) inputEl.value.style.height = 'auto'

  chatStore.isGenerating = true
  statusText.value = '生成中...'

  chatStore.addStreamingAiMessage()
  scrollToBottom()

  const { schedule, cancel: cancelRaf } = useRafThrottle()

  abortController = streamChat(chatStore.currentConvId!, text, authStore.token, {
    onChunk(fullText: string) {
      schedule(() => {
        chatStore.updateLastAiMessage(fullText)
        scrollToBottom()
      })
    },
    onDone(fullText: string) {
      cancelRaf()
      chatStore.updateLastAiMessage(fullText || '(empty)')
      chatStore.isGenerating = false
      statusText.value = 'READY'
      abortController = null
      scrollToBottom()
      chatStore.loadConversations()
      chatStore.generateTitle(chatStore.currentConvId!)
    },
    onError(err: Error) {
      cancelRaf()
      chatStore.updateLastAiMessage(`ERROR: ${err.message}`)
      chatStore.isGenerating = false
      statusText.value = 'READY'
      abortController = null
    },
  })
}

onMounted(async () => {
  await chatStore.loadConversations()
  document.addEventListener('click', onDocumentClick)
})

onUnmounted(() => {
  document.removeEventListener('click', onDocumentClick)
})
</script>

<template>
  <div class="chat-bg">
  <div class="app">
    <!-- 侧边栏 -->
    <div class="sidebar">
      <div class="sidebar-header">
        <div class="sidebar-brand">
          <div class="logo">
            <span class="logo-dot"></span>
            Financial Agent
          </div>
        </div>
        <button class="new-chat-btn" @click="handleCreateConversation()">
          + 新对话
        </button>
      </div>

      <div class="conversation-list">
        <div v-if="chatStore.conversations.length === 0" class="empty-state">暂无会话</div>
        <div
          v-for="conv in chatStore.conversations"
          :key="conv.id"
          class="conv-item"
          :class="{ active: conv.id === chatStore.currentConvId }"
          @click="handleSwitchConversation(conv.id)"
        >
          <div class="conv-content">
            <div class="conv-title">{{ conv.title }}</div>
            <div class="conv-time">{{ formatTime(conv.updatedAt) }}</div>
          </div>
          <div class="conv-actions">
            <button class="conv-action-btn" @click="toggleMenu(conv.id, $event)">···</button>
            <div v-if="activeMenuConvId === conv.id" class="conv-menu" @click.stop>
              <div class="conv-menu-item conv-menu-item--danger" @click="handleDeleteConversation(conv.id)">删除</div>
            </div>
          </div>
        </div>
      </div>

      <div class="sidebar-footer">
        <span class="user-name">{{ authStore.username }}</span>
        <div class="footer-actions">
          <button @click="themeStore.toggle()" :title="themeStore.isLight ? '深色模式' : '浅色模式'">
            {{ themeStore.isLight ? '☾' : '☀' }}
          </button>
          <button id="logoutBtn" @click="handleLogout()" title="退出登录">⏻</button>
        </div>
      </div>
    </div>

    <!-- 聊天区 -->
    <div class="chat-container">
      <div class="chat-header">
        <div class="left">
          <span class="dot"></span>
          <span class="title">{{ currentTitle }}</span>
        </div>
        <span class="status" :style="{ color: chatStore.isGenerating ? 'var(--accent)' : 'var(--text-dim)' }">
          {{ statusText }}
        </span>
      </div>

      <div ref="messagesEl" class="chat-messages">
        <!-- 欢迎页 -->
        <div v-if="chatStore.messages.length === 0" class="welcome">
          <div class="logo">Financial Agent</div>
          <div class="sub">AI 金融投资助手</div>
          <div class="hint">
            <kbd>Enter</kbd> 发送 &nbsp; <kbd>Shift+Enter</kbd> 换行
          </div>
        </div>

        <!-- 消息列表 -->
        <div
          v-for="msg in chatStore.messages"
          :key="msg.id"
          class="message"
          :class="msg.role === 'USER' ? 'user' : 'ai'"
        >
          <div class="label">{{ msg.role === 'USER' ? 'YOU' : 'AI' }}</div>
          <div class="bubble">
            <template v-if="msg.role === 'USER'">{{ msg.content }}</template>
            <template v-else>
              <MarkdownRenderer
                :text="msg.content"
                :is-streaming="chatStore.isGenerating && msg === chatStore.messages[chatStore.messages.length - 1]"
              />
            </template>
          </div>
        </div>
      </div>

      <div class="chat-input-area">
        <div class="chat-input">
          <textarea
            ref="inputEl"
            v-model="inputText"
            rows="1"
            placeholder="输入你的问题..."
            @input="handleInput"
            @keydown="handleKeydown"
          ></textarea>
          <button :disabled="chatStore.isGenerating" @click="handleSend()">发送</button>
        </div>
      </div>
    </div>
  </div>

  <!-- 删除确认弹窗 -->
  <div v-if="deleteConfirmConvId !== null" class="modal-overlay" @click.self="cancelDelete">
    <div class="modal-box">
      <div class="modal-title">确认删除</div>
      <div class="modal-body">删除后将无法恢复，确定要删除该会话吗？</div>
      <div class="modal-actions">
        <button class="modal-btn modal-btn--cancel" @click="cancelDelete">取消</button>
        <button class="modal-btn modal-btn--danger" @click="confirmDelete">删除</button>
      </div>
    </div>
  </div>
  </div>
</template>
