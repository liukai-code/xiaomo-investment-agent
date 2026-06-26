<script setup lang="ts">
import { ref, nextTick, onMounted, computed } from 'vue'
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

let abortController: AbortController | null = null

const currentTitle = computed(() => {
  const conv = chatStore.getCurrentConversation()
  return conv ? conv.title : 'Terminal'
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

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}

async function handleSend() {
  const text = inputText.value.trim()
  if (!text || chatStore.isGenerating) return

  // 自动创建会话
  if (!chatStore.currentConvId) {
    const conv = await chatStore.createConversation()
    if (!conv) return
  }

  chatStore.addUserMessage(text)
  inputText.value = ''
  if (inputEl.value) inputEl.value.style.height = 'auto'

  chatStore.isGenerating = true
  statusText.value = 'GENERATING...'

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
})
</script>

<template>
  <div class="app">
    <!-- 侧边栏 -->
    <div class="sidebar">
      <div class="sidebar-header">
        <span class="logo">&gt; {{ authStore.username }}</span>
        <div class="header-actions">
          <button @click="themeStore.toggle()" :title="themeStore.isLight ? '深色模式' : '浅色模式'">
            {{ themeStore.isLight ? '☾' : '☀' }}
          </button>
          <button @click="handleCreateConversation()" title="新建会话" style="font-size: 20px; line-height: 1">
            +
          </button>
          <button id="logoutBtn" @click="handleLogout()" title="退出登录">⏻</button>
        </div>
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
          <div class="conv-title">{{ conv.title }}</div>
          <div class="conv-time">{{ formatTime(conv.updatedAt) }}</div>
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
          <div class="logo">&gt; _</div>
          <div class="sub">AI Terminal v1.0</div>
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

      <div class="chat-input">
        <textarea
          ref="inputEl"
          v-model="inputText"
          rows="1"
          placeholder="输入消息..."
          @input="handleInput"
          @keydown="handleKeydown"
        ></textarea>
        <button :disabled="chatStore.isGenerating" @click="handleSend()">SEND</button>
      </div>
    </div>
  </div>
</template>
