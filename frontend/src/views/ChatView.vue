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
import { useNotificationStore } from '@/stores/notification'
import AnalysisSidePanel from '@/components/analysis/AnalysisSidePanel.vue'
import YjbQrLogin from '@/components/yangjibao/YjbQrLogin.vue'
import YjbHoldingsCard from '@/components/yangjibao/YjbHoldingsCard.vue'
import SettingsDialog from '@/components/SettingsDialog.vue'
import NotificationPanel from '@/components/NotificationPanel.vue'

const router = useRouter()
const authStore = useAuthStore()
const chatStore = useChatStore()
const yjbStore = useYangjibaoStore()
const analysisStore = useAnalysisStore()
const notificationStore = useNotificationStore()
const { schedule, cancel: cancelRaf } = useRafThrottle()

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

// 工具名 → 用户友好的状态描述
// 支持两种 key：精确匹配 "toolName:operation"，通用匹配 "toolName"
const toolLabelMap: Record<string, string> = {
  // market_data 路由 - 按 operation 区分
  'market_data:aShareQuote':   '正在获取A股行情',
  'market_data:hkStockQuote':  '正在获取港股行情',
  'market_data:usStockQuote':  '正在获取美股行情',
  'market_data:fundNav':       '正在查询基金净值',
  'market_data:searchStock':   '正在搜索股票',
  market_data:                 '正在获取行情数据',
  // A股数据路由 - 按 operation 区分
  'a_stock_quote:tencentQuote':  '正在获取实时行情',
  'a_stock_quote:baiduKline':    '正在获取K线数据',
  a_stock_quote:                 '正在获取行情数据',
  'a_stock_report:stockReport':      '正在查询个股研报',
  'a_stock_report:industryReport':   '正在查询行业研报',
  'a_stock_report:downloadReportPdf':'正在下载研报PDF',
  'a_stock_report:thsEpsForecast':   '正在查询盈利预测',
  'a_stock_report:iwencaiSearch':    '正在智能搜索',
  'a_stock_report:iwencaiQuery':     '正在智能查询',
  a_stock_report:                    '正在查询研报',
  'a_stock_signal:conceptBlocks':    '正在查询概念板块',
  'a_stock_signal:fundFlowMinute':   '正在查询分钟资金流向',
  'a_stock_signal:dragonTigerBoard': '正在查询龙虎榜',
  'a_stock_signal:dailyDragonTiger': '正在查询龙虎榜',
  'a_stock_signal:lockupExpiry':     '正在查询解禁信息',
  'a_stock_signal:industryRanking':  '正在查询行业排名',
  a_stock_signal:                    '正在查询板块与资金',
  'a_stock_capital:marginTrading':   '正在查询融资融券',
  'a_stock_capital:blockTrade':      '正在查询大宗交易',
  'a_stock_capital:holderNumChange': '正在查询股东户数',
  'a_stock_capital:dividendHistory': '正在查询分红历史',
  'a_stock_capital:fundFlow120d':    '正在查询资金流向',
  'a_stock_capital:northboundFlow':  '正在查询北向资金',
  a_stock_capital:                   '正在查询资金数据',
  'a_stock_news:stockNews':          '正在查询个股新闻',
  'a_stock_news:globalNews':         '正在查询全球资讯',
  'a_stock_news:cninfoAnnouncements':'正在查询公司公告',
  'a_stock_news:irmQA':              '正在查询互动问答',
  'a_stock_news:sinaFinancialReport':'正在查询财务报告',
  a_stock_news:                      '正在查询资讯',
  'a_stock_limit_up:ztPool':         '正在查询涨停数据',
  'a_stock_limit_up:zbPool':         '正在查询炸板数据',
  'a_stock_limit_up:dtPool':         '正在查询跌停数据',
  'a_stock_limit_up:yztPool':        '正在查询预涨停数据',
  'a_stock_limit_up:thsLimitUpPool': '正在查询涨停数据',
  'a_stock_limit_up:sentimentOverview':'正在分析市场情绪',
  a_stock_limit_up:                  '正在查询涨跌停数据',
  'a_stock_option:optionCodes':      '正在查询期权合约',
  'a_stock_option:optionTQuote':     '正在查询期权报价',
  'a_stock_option:optionGreeks':     '正在查询期权指标',
  a_stock_option:                    '正在查询期权数据',
  'a_stock_sentiment:thsHotList':    '正在查询热门榜单',
  'a_stock_sentiment:emHotRank':     '正在查询热度排名',
  'a_stock_sentiment:emConceptHit':  '正在查询热门概念',
  a_stock_sentiment:                 '正在查询市场热点',
  // 金融计算路由
  financial_calculator:              '正在计算',
  // 基础工具
  fetchWebpage:       '正在抓取网页内容',
  fetchArticleContent:'正在抓取文章内容',
  executeQuery:       '正在查询数据库',
  getDatabaseSchema:  '正在获取数据库结构',
  readFile:           '正在读取文件',
  writeFile:          '正在写入文件',
  appendFile:         '正在追加文件',
  listFiles:          '正在列出文件',
  // 养基宝
  getMyHoldings:      '正在查询基金持仓',
  getMyAccountSummary:'正在查询账户信息',
  // 深度分析
  getAnalysisReport:  '正在查询分析报告',
  // MCP
  bailian_web_search: '正在搜索网络信息',
}

function getStatusLabel(status: StatusEvent): string {
  if (status.type === 'THINKING') return '思考中'
  if (status.type === 'TOOL_CALL' && status.toolName) {
    // 优先精确匹配 toolName:operation，再通用匹配 toolName
    if (status.operation) {
      const exact = toolLabelMap[`${status.toolName}:${status.operation}`]
      if (exact) return exact
    }
    return toolLabelMap[status.toolName] || '正在获取数据'
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
  // 新建会话前，中断当前正在进行的流式输出
  if (abortController) {
    abortController.abort()
    abortController = null
    chatStore.isGenerating = false
    statusText.value = 'READY'
    currentStatus.value = null
  }
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
  notificationStore.closePanel()
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

  // 捕获当前会话 ID，防止回调在会话切换后更新错误的消息
  const convId = chatStore.currentConvId!

  abortController = streamChat(convId, text, authStore.token, {
    onStatus(event: StatusEvent) {
      // 会话已切换，忽略回调
      if (chatStore.currentConvId !== convId) return
      // TOOL_RESULT 没有有用信息，跳过，保持显示 TOOL_CALL 标签
      if (event.type !== 'TOOL_RESULT') {
        currentStatus.value = event
      }
    },
    onChunk(fullText: string) {
      // 会话已切换，忽略回调
      if (chatStore.currentConvId !== convId) return
      schedule(() => {
        chatStore.updateLastAiMessage(fullText)
        scrollToBottomIfNear()
      })
    },
    onDone(fullText: string) {
      cancelRaf()
      // 会话已切换，只清理状态，不更新消息
      if (chatStore.currentConvId !== convId) {
        chatStore.isGenerating = false
        abortController = null
        return
      }
      chatStore.updateLastAiMessage(fullText || '(empty)')
      chatStore.isGenerating = false
      statusText.value = 'READY'
      currentStatus.value = null
      abortController = null
      scrollToBottom()
      chatStore.loadConversations()
      chatStore.generateTitle(convId)
    },
    onError(err: Error) {
      cancelRaf()
      // 会话已切换，只清理状态，不更新消息
      if (chatStore.currentConvId !== convId) {
        chatStore.isGenerating = false
        abortController = null
        return
      }
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
  if (authStore.token) {
    notificationStore.init(authStore.token)
  }
})

onUnmounted(() => {
  document.removeEventListener('click', onDocumentClick)
  notificationStore.reset()
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
          <div class="notify-wrapper">
            <button class="notify-btn" title="通知" @click.stop="notificationStore.togglePanel()">
              <Bell :size="18" />
              <span v-if="notificationStore.unreadCount > 0" class="notify-badge">
                {{ notificationStore.unreadCount > 99 ? '99+' : notificationStore.unreadCount }}
              </span>
            </button>
            <Transition name="notify-fade">
              <NotificationPanel v-if="notificationStore.panelOpen" />
            </Transition>
          </div>
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

.notify-wrapper {
  position: relative;
}

.notify-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 18px;
  height: 18px;
  border-radius: 9px;
  background: #ef4444;
  color: white;
  font-size: 10px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 4px;
  line-height: 1;
  pointer-events: none;
}

.notify-fade-enter-active,
.notify-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.notify-fade-enter-from,
.notify-fade-leave-to {
  opacity: 0;
  transform: translateY(-8px);
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
