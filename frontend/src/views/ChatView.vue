<script setup lang="ts">
import { ref, nextTick, onMounted, onUnmounted, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import { streamChat, type StatusEvent } from '@/api/chat'
import MarkdownRenderer from '@/components/blocks/MarkdownRenderer.vue'
import { useRafThrottle } from '@/composables/useMarkdownBlocks'
import { Settings, LogOut, MoreHorizontal, User, PanelLeftClose, PanelLeftOpen, Bell, Square, Loader2, Brain } from 'lucide-vue-next'
import { useYangjibaoStore } from '@/stores/yangjibao'
import { useAnalysisStore } from '@/stores/analysis'
import AnalysisSidePanel from '@/components/analysis/AnalysisSidePanel.vue'
import YjbQrLogin from '@/components/yangjibao/YjbQrLogin.vue'
import YjbHoldingsCard from '@/components/yangjibao/YjbHoldingsCard.vue'
import SettingsDialog from '@/components/SettingsDialog.vue'

const router = useRouter()
const authStore = useAuthStore()
const chatStore = useChatStore()
const yjbStore = useYangjibaoStore()
const analysisStore = useAnalysisStore()

const messagesEl = ref<HTMLDivElement>()
const inputEl = ref<HTMLTextAreaElement>()
const inputText = ref('')
const statusText = ref('READY')
const activeMenuConvId = ref<number | null>(null)
const deleteConfirmConvId = ref<number | null>(null)
const showUserMenu = ref(false)
const sidebarCollapsed = ref(false)
const showSettings = ref(false)

// 侧面板互斥切换
function toggleAnalysisPanel() {
  if (!analysisStore.panelVisible) {
    yjbStore.cardVisible = false
  }
  analysisStore.togglePanel()
}

function openHoldingsCard() {
  analysisStore.panelVisible = false
  yjbStore.openCard()
}

let abortController: AbortController | null = null

// 工具调用状态
const currentStatus = ref<StatusEvent | null>(null)

// 工具名 → 中文标签映射
const toolLabelMap: Record<string, string> = {
  // 基础工具
  getAShareQuote: '查询A股行情',
  getHKStockQuote: '查询港股行情',
  getUSStockQuote: '查询美股行情',
  getFundNav: '查询基金净值',
  searchStockByName: '搜索股票',
  fetchWebpage: '抓取网页',
  fetchArticleContent: '抓取文章',
  executeQuery: '执行数据库查询',
  getDatabaseSchema: '获取数据库结构',
  read_file: '读取文件',
  write_file: '写入文件',
  append_file: '追加文件',
  list_files: '列出文件',
  // 金融计算
  compoundInterest: '复利计算',
  loanPayment: '贷款计算',
  npv: '净现值计算',
  irr: '内部收益率计算',
  sharpeRatio: '夏普比率计算',
  // A股数据工具
  tencentQuote: '查询腾讯行情',
  baiduKline: '查询百度K线',
  stockReport: '查询个股研报',
  industryReport: '查询行业研报',
  downloadReportPdf: '下载研报PDF',
  thsEpsForecast: '查询同花顺EPS预测',
  iwencaiSearch: '问财搜索',
  iwencaiQuery: '问财查询',
  conceptBlocks: '查询概念板块',
  fundFlowMinute: '查询分钟资金流',
  dragonTigerBoard: '查询龙虎榜',
  dailyDragonTiger: '查询日龙虎榜',
  lockupExpiry: '查询解禁信息',
  industryRanking: '查询行业排名',
  marginTrading: '查询融资融券',
  blockTrade: '查询大宗交易',
  holderNumChange: '查询股东户数变化',
  dividendHistory: '查询分红历史',
  fundFlow120d: '查询120日资金流',
  northboundFlow: '查询北向资金',
  stockNews: '查询个股新闻',
  globalNews: '查询全球资讯',
  cninfoAnnouncements: '查询巨潮公告',
  irmQA: '查询互动易问答',
  sinaFinancialReport: '查询新浪财报',
  ztPool: '查询涨停池',
  zbPool: '查询炸板池',
  dtPool: '查询跌停池',
  yztPool: '查询预涨停池',
  thsLimitUpPool: '查询同花顺涨停池',
  sentimentOverview: '查询情绪概览',
  optionCodes: '查询期权代码',
  optionTQuote: '查询期权T型报价',
  optionGreeks: '查询期权希腊字母',
  thsHotList: '查询同花顺热榜',
  emHotRank: '查询东财热度排名',
  emConceptHit: '查询东财概念命中',
  // MCP
  bailian_web_search: '百炼搜索',
}

function getStatusLabel(status: StatusEvent): string {
  if (status.type === 'THINKING') return '思考中'
  if (status.type === 'TOOL_CALL' && status.toolName) {
    return toolLabelMap[status.toolName] || `调用 ${status.toolName}`
  }
  return '处理中'
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

function onSettingsSaved() {
  // 配置保存成功后的处理
  console.log('配置已保存');
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

  // 检查用户配置
  try {
    const { getConfig } = await import('@/api/config')
    const config = await getConfig()
    if (!config || !config.apiKey) {
      alert('请先配置API Key才能使用AI对话功能')
      showSettings.value = true
      return
    }
  } catch (error) {
    console.error('检查配置失败:', error)
  }

  if (!chatStore.currentConvId) {
    const conv = await chatStore.createConversation()
    if (!conv) return
  }

  chatStore.addUserMessage(text)
  inputText.value = ''
  if (inputEl.value) inputEl.value.style.height = 'auto'

  chatStore.isGenerating = true
  statusText.value = '生成中...'
  currentStatus.value = { type: 'THINKING' }

  chatStore.addStreamingAiMessage()
  scrollToBottom()

  const { schedule, cancel: cancelRaf } = useRafThrottle()

  abortController = streamChat(chatStore.currentConvId!, text, authStore.token, {
    onStatus(event: StatusEvent) {
      // TOOL_RESULT 没有有用信息，跳过，保持显示 TOOL_CALL 标签
      if (event.type !== 'TOOL_RESULT') {
        currentStatus.value = event
      }
    },
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
      currentStatus.value = null
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
      currentStatus.value = null
      abortController = null
    },
  })
}

function handleStop() {
  // 先取快照，再 abort
  const convId = chatStore.currentConvId
  const lastMsg = chatStore.messages[chatStore.messages.length - 1]

  if (abortController) {
    abortController.abort()
    abortController = null
  }

  // 用独立请求保存部分内容（不走共享 axios 实例，避免被 abortController 连带取消）
  if (lastMsg && lastMsg.role === 'ASSISTANT' && convId) {
    const content = lastMsg.content
    if (content) {
      const finalContent = content + '\n\n> *已手动终止*'
      lastMsg.content = finalContent
      const token = authStore.token
      fetch(`/agent/conversation/${convId}/message`, {
        method: 'POST',
        headers: {
          'Content-Type': 'text/plain',
          'Authorization': `Bearer ${token}`,
        },
        body: finalContent,
      }).catch(() => {})
    }
  }

  chatStore.isGenerating = false
  statusText.value = 'READY'
  currentStatus.value = null
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
            <Transition name="menu-fade">
              <div v-if="activeMenuConvId === conv.id" class="conv-menu" @click.stop>
                <div class="conv-menu-item conv-menu-item--danger" @click="handleDeleteConversation(conv.id)">删除</div>
              </div>
            </Transition>
          </div>
        </div>
      </div>

      <div class="sidebar-footer">
        <div class="footer-item" title="设置" @click="showSettings = true">
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
          <button
            class="analysis-toggle-btn"
            :class="{ active: analysisStore.panelVisible }"
            @click="toggleAnalysisPanel"
            title="个股深度分析"
          >
            <Brain :size="16" />
            <span>个股深度分析</span>
          </button>
          <div class="yjb-trigger-wrapper">
            <button class="yjb-connect-btn" @click.stop="openHoldingsCard()">
              <span v-if="yjbStore.yjbLoggedIn" class="yjb-status-dot connected"></span>
              <span v-else class="yjb-status-dot"></span>
              {{ yjbStore.yjbLoggedIn ? '已连接养基宝' : '连接养基宝' }}
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
            <Transition name="user-fade">
              <div v-if="showUserMenu" class="user-dropdown" @click.stop>
                <div class="user-dropdown-header">
                  <div class="user-dropdown-name">{{ authStore.accountId }}</div>
                  <div class="user-dropdown-id">{{ authStore.email }}</div>
                </div>
              </div>
            </Transition>
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
              <button v-if="chatStore.isGenerating" class="stop-btn" @click="handleStop()" title="停止">
                <Square :size="18" />
              </button>
              <button v-else class="send-btn" @click="handleSend()" title="发送">
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
                    <!-- 工具调用状态指示器：只要正在生成且有状态事件就显示 -->
                    <div
                      v-if="currentStatus && chatStore.isGenerating && msg === chatStore.messages[chatStore.messages.length - 1]"
                      class="status-indicator"
                    >
                      <Loader2 :size="14" class="status-spinner" />
                      <span>{{ getStatusLabel(currentStatus) }}...</span>
                    </div>
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
                  placeholder="有问题，尽管问"
                  @input="handleInput"
                  @keydown="handleKeydown"
                ></textarea>
                <button v-if="chatStore.isGenerating" class="stop-btn" @click="handleStop()" title="停止">
                  <Square :size="18" />
                </button>
                <button v-else class="send-btn" @click="handleSend()" title="发送">
                  <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <line x1="22" y1="2" x2="11" y2="13"></line>
                    <polygon points="22 2 15 22 11 13 2 9 22 2"></polygon>
                  </svg>
                </button>
              </div>
            </div>
          </template>
        </div>

        <!-- 右侧深度分析面板 -->
        <Transition name="analysis-slide">
          <AnalysisSidePanel v-if="analysisStore.panelVisible" />
        </Transition>

        <!-- 右侧养基宝卡片 -->
        <Transition name="holdings-slide">
          <div v-if="yjbStore.cardVisible && yjbStore.yjbLoggedIn" class="holdings-side-card">
            <YjbHoldingsCard />
          </div>
        </Transition>
      </div>
    </div>
  </div>

  <!-- 删除确认弹窗 -->
  <Transition name="modal-fade">
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
  </Transition>

  <!-- 设置弹窗 -->
  <SettingsDialog
    :visible="showSettings"
    @close="showSettings = false"
    @saved="onSettingsSaved"
  />
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

@keyframes breathe {
  0%, 100% { opacity: 1; box-shadow: 0 0 4px var(--green); }
  50% { opacity: 0.4; box-shadow: 0 0 1px var(--green); }
}

.yjb-status-dot.connected {
  background: var(--green);
  box-shadow: 0 0 4px var(--green);
  animation: breathe 2s ease-in-out infinite;
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
  scrollbar-gutter: stable;
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

.analysis-slide-enter-active,
.analysis-slide-leave-active {
  transition: width 0.3s ease, padding 0.3s ease, opacity 0.25s ease;
}

.analysis-slide-enter-from,
.analysis-slide-leave-to {
  width: 0;
  padding: 0;
  opacity: 0;
}

.analysis-toggle-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  background: var(--surface-2);
  color: var(--text);
  border: 1px solid var(--border);
  border-radius: 20px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s;
  height: 36px;
}

.analysis-toggle-btn:hover {
  background: var(--border);
}

.analysis-toggle-btn.active {
  background: var(--accent-dim);
  color: var(--accent);
  border-color: var(--accent);
}

.status-indicator {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 0;
  color: var(--text-dim);
  font-size: 13px;
}

.status-spinner {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
