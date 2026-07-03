<script setup lang="ts">
import { ref, nextTick, onMounted, onUnmounted, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import { streamChat, streamDeepAnalysis, type WorkflowEvent } from '@/api/chat'
import MarkdownRenderer from '@/components/blocks/MarkdownRenderer.vue'
import WorkflowPanel from '@/components/workflow/WorkflowPanel.vue'
import { useRafThrottle } from '@/composables/useMarkdownBlocks'
import { Settings, LogOut, MoreHorizontal, User, PanelLeftClose, PanelLeftOpen, Bell } from 'lucide-vue-next'
import { useYangjibaoStore } from '@/stores/yangjibao'
import YjbQrLogin from '@/components/yangjibao/YjbQrLogin.vue'
import YjbHoldingsCard from '@/components/yangjibao/YjbHoldingsCard.vue'

const router = useRouter()
const authStore = useAuthStore()
const chatStore = useChatStore()
const yjbStore = useYangjibaoStore()

const messagesEl = ref<HTMLDivElement>()
const inputEl = ref<HTMLTextAreaElement>()
const inputText = ref('')
const statusText = ref('READY')
const activeMenuConvId = ref<number | null>(null)
const deleteConfirmConvId = ref<number | null>(null)
const showUserMenu = ref(false)
const sidebarCollapsed = ref(false)

let abortController: AbortController | null = null

// 深度分析工作流状态
const workflowEvents = ref<WorkflowEvent[]>([])
const isWorkflowRunning = ref(false)
const isWorkflowMode = ref(false)

function isDeepAnalysisRequest(text: string): boolean {
  const keywords = ['深度分析', '全面分析', '深度研究', '深度调研', '多维度分析']
  return keywords.some((kw) => text.includes(kw))
}

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

function isNearBottom() {
  if (!messagesEl.value) return true
  const { scrollTop, scrollHeight, clientHeight } = messagesEl.value
  return scrollHeight - scrollTop - clientHeight < 80
}

function scrollToBottom() {
  nextTick(() => {
    if (messagesEl.value) {
      messagesEl.value.scrollTop = messagesEl.value.scrollHeight
    }
  })
}

function scrollToBottomIfNear() {
  if (isNearBottom()) {
    scrollToBottom()
  }
}

async function handleCreateConversation() {
  chatStore.currentConvId = null
  chatStore.messages = []
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

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
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
  showUserMenu.value = false
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

  // 判断是否走深度分析工作流
  if (isDeepAnalysisRequest(text)) {
    handleDeepAnalysis(text)
    return
  }

  chatStore.addStreamingAiMessage()
  scrollToBottom()

  const { schedule, cancel: cancelRaf } = useRafThrottle()

  abortController = streamChat(chatStore.currentConvId!, text, authStore.token, {
    onChunk(fullText: string) {
      schedule(() => {
        chatStore.updateLastAiMessage(fullText)
        scrollToBottomIfNear()
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

function handleDeepAnalysis(text: string) {
  // 切换到工作流模式
  isWorkflowMode.value = true
  isWorkflowRunning.value = true
  workflowEvents.value = []
  statusText.value = '深度分析中...'

  // 添加一个占位消息，用于显示工作流面板
  chatStore.addStreamingAiMessage()
  chatStore.updateLastAiMessage('深度分析工作流已启动，请稍候...')
  scrollToBottom()

  abortController = streamDeepAnalysis(chatStore.currentConvId!, text, authStore.token, {
    onEvent(event: WorkflowEvent) {
      workflowEvents.value.push(event)
      scrollToBottomIfNear()
    },
    onDone() {
      isWorkflowRunning.value = false
      chatStore.isGenerating = false
      statusText.value = 'READY'
      abortController = null

      // 将最终结果写入消息
      const summary = buildWorkflowSummary()
      chatStore.updateLastAiMessage(summary)
      scrollToBottom()
      chatStore.loadConversations()
      chatStore.generateTitle(chatStore.currentConvId!)
    },
    onError(err: Error) {
      isWorkflowRunning.value = false
      chatStore.updateLastAiMessage(`深度分析失败: ${err.message}`)
      chatStore.isGenerating = false
      statusText.value = 'READY'
      abortController = null
    },
  })
}

function buildWorkflowSummary(): string {
  // 从工作流事件中提取最终决策
  const finalEvent = workflowEvents.value.find((e) => e.type === 'FINAL_DECISION')
  if (finalEvent?.content) {
    return `## 深度分析完成\n\n${finalEvent.content}\n\n---\n*以上由多智能体工作流自动生成，仅供参考*`
  }

  // 如果没有最终决策，显示各阶段完成状态
  const completedPhases = workflowEvents.value
    .filter((e) => e.type === 'PHASE_COMPLETE')
    .map((e) => e.phase)

  return `## 深度分析完成\n\n已完成阶段：${completedPhases.join(' → ')}\n\n详细分析结果请查看上方工作流面板。\n\n---\n*以上由多智能体工作流自动生成，仅供参考*`
}

onMounted(async () => {
  await chatStore.loadConversations()
  document.addEventListener('click', onDocumentClick)
})

onUnmounted(() => {
  document.removeEventListener('click', onDocumentClick)
})

watch(() => yjbStore.cardVisible, (visible) => {
  if (visible) {
    nextTick(() => scrollToBottom())
  }
})
</script>

<template>
  <div class="chat-bg">
  <div class="app">
    <!-- 侧边栏 -->
    <div class="sidebar" :class="{ collapsed: sidebarCollapsed }">
      <div class="sidebar-header">
        <div class="sidebar-header-top">
          <div class="sidebar-brand">
            <img src="/logo.png" alt="Logo" class="logo-img" />
            <div class="logo-text">
              <div class="logo-title">小墨</div>
              <div class="logo-sub">Financial Agent</div>
            </div>
          </div>
          <button class="sidebar-collapse-btn" @click="toggleSidebar" title="折叠侧边栏">
            <PanelLeftClose :size="18" />
          </button>
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
            <button class="conv-action-btn" @click="toggleMenu(conv.id, $event)"><MoreHorizontal :size="18" /></button>
            <div v-if="activeMenuConvId === conv.id" class="conv-menu" @click.stop>
              <div class="conv-menu-item conv-menu-item--danger" @click="handleDeleteConversation(conv.id)">删除</div>
            </div>
          </div>
        </div>
      </div>

      <div class="sidebar-footer">
        <div class="footer-item" title="设置">
          <Settings :size="18" />
          <span class="footer-label">设置</span>
        </div>
        <div class="footer-item" @click="handleLogout()" title="退出登录">
          <LogOut :size="18" />
          <span class="footer-label">退出登录</span>
        </div>
      </div>
    </div>

    <!-- 聊天区 -->
    <div class="chat-container">
      <div class="chat-header">
        <div class="left">
          <button v-if="sidebarCollapsed" class="sidebar-expand-btn" @click="toggleSidebar" title="展开侧边栏">
            <PanelLeftOpen :size="18" />
          </button>
          <span class="dot"></span>
          <span class="title">{{ currentTitle }}</span>
        </div>
        <div class="header-right">
          <div class="yjb-trigger-wrapper">
            <button class="yjb-connect-btn" @click.stop="yjbStore.openCard()">
              <span v-if="yjbStore.isLoggedIn" class="yjb-status-dot connected"></span>
              <span v-else class="yjb-status-dot"></span>
              {{ yjbStore.isLoggedIn ? '已连接养基宝' : '连接养基宝' }}
            </button>
            <YjbQrLogin
              :visible="yjbStore.qrModalVisible"
              @success="yjbStore.onQrLoginSuccess"
              @close="yjbStore.qrModalVisible = false"
            />
          </div>
          <button class="notify-btn" title="通知">
            <Bell :size="18" />
          </button>
          <div class="user-menu-wrapper">
            <button class="user-avatar-btn" @click.stop="showUserMenu = !showUserMenu">
              <User :size="20" />
            </button>
            <div v-if="showUserMenu" class="user-dropdown" @click.stop>
              <div class="user-dropdown-header">
                <div class="user-dropdown-name">{{ authStore.accountId }}</div>
                <div class="user-dropdown-id">{{ authStore.email }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 主内容区：聊天 + 右侧卡片 -->
      <div class="chat-main-area">
        <!-- 聊天区域 -->
        <div class="chat-content">
          <!-- 欢迎页 -->
          <div v-if="chatStore.messages.length === 0" class="welcome-page">
            <div class="welcome-title">你好，我是小墨</div>
            <div class="welcome-sub">你的 AI 金融投资助手，随时为你解答</div>
            <div class="chat-input welcome-input">
              <textarea
                ref="inputEl"
                v-model="inputText"
                rows="1"
                placeholder="有问题，尽管问"
                @input="handleInput"
                @keydown="handleKeydown"
              ></textarea>
              <button class="send-btn" :disabled="chatStore.isGenerating" @click="handleSend()" title="发送">
                <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <line x1="22" y1="2" x2="11" y2="13"></line>
                  <polygon points="22 2 15 22 11 13 2 9 22 2"></polygon>
                </svg>
              </button>
            </div>
          </div>

          <!-- 对话页 -->
          <template v-else>
            <div ref="messagesEl" class="chat-messages">
              <div
                v-for="msg in chatStore.messages"
                :key="msg.id"
                class="message"
                :class="msg.role === 'USER' ? 'user' : 'ai'"
              >
                <div class="msg-header">
                  <img v-if="msg.role !== 'USER'" src="/logo.png" alt="AI" class="msg-avatar" />
                  <div class="label">{{ msg.role === 'USER' ? 'YOU' : '小墨' }}</div>
                </div>
                <div class="bubble">
                  <template v-if="msg.role === 'USER'">{{ msg.content }}</template>
                  <template v-else>
                    <template v-if="isWorkflowMode && msg === chatStore.messages[chatStore.messages.length - 1] && workflowEvents.length > 0">
                      <WorkflowPanel :events="workflowEvents" :is-running="isWorkflowRunning" />
                    </template>
                    <template v-else>
                      <MarkdownRenderer
                        :text="msg.content"
                        :is-streaming="chatStore.isGenerating && msg === chatStore.messages[chatStore.messages.length - 1]"
                      />
                    </template>
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
                  placeholder="有问题，尽管问"
                  @input="handleInput"
                  @keydown="handleKeydown"
                ></textarea>
                <button class="send-btn" :disabled="chatStore.isGenerating" @click="handleSend()" title="发送">
                  <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <line x1="22" y1="2" x2="11" y2="13"></line>
                    <polygon points="22 2 15 22 11 13 2 9 22 2"></polygon>
                  </svg>
                </button>
              </div>
            </div>
          </template>
        </div>

        <!-- 右侧养基宝卡片 -->
        <Transition name="holdings-slide">
          <div v-if="yjbStore.cardVisible && yjbStore.isLoggedIn" class="holdings-side-card">
            <YjbHoldingsCard />
          </div>
        </Transition>
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

<style scoped>
.yjb-trigger-wrapper {
  position: relative;
}

.yjb-connect-btn {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 7px 16px;
  background: var(--surface-2);
  color: var(--accent);
  border: 1px solid var(--border);
  border-radius: 20px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s;
  height: 36px;
  box-sizing: border-box;
}

.yjb-connect-btn:hover {
  background: var(--border);
}

.yjb-status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--text-dim);
  flex-shrink: 0;
}

.yjb-status-dot.connected {
  background: var(--green);
  box-shadow: 0 0 4px var(--green);
}

.notify-btn {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--surface-2);
  border: 1px solid var(--border);
  color: var(--text);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}

.notify-btn:hover {
  background: var(--border);
}

.chat-main-area {
  flex: 1;
  display: flex;
  overflow: hidden;
  min-width: 0;
}

.chat-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}

.holdings-side-card {
  width: 560px;
  flex-shrink: 0;
  overflow: hidden;
  overflow-y: auto;
  padding: 20px;
  background: var(--bg);
}

.holdings-slide-enter-active,
.holdings-slide-leave-active {
  transition: width 0.3s ease, padding 0.3s ease, opacity 0.25s ease;
}

.holdings-slide-enter-from,
.holdings-slide-leave-to {
  width: 0;
  padding: 20px 0;
  opacity: 0;
}
</style>
