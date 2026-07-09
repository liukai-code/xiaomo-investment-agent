<script setup lang="ts">
import { computed } from 'vue'
import { Activity, CheckCircle2, AlertCircle, TrendingUp, TrendingDown, Minus, MessageSquare } from 'lucide-vue-next'
import MarkdownRenderer from '@/components/blocks/MarkdownRenderer.vue'
import type { AnalysisRecord, WorkflowEvent } from '@/api/analysis'

const props = defineProps<{
  detail: AnalysisRecord | null
  events: WorkflowEvent[]
  isRunning: boolean
}>()

const emit = defineEmits<{
  goToChat: []
}>()

// --- 从 WorkflowPanel 复用的逻辑 ---

const phaseNames: Record<string, string> = {
  Layer1_DataCollection: '数据采集',
  BullBearDebate: '多空辩论',
  Trader: '交易决策',
  RiskDebate: '风险评估',
}

const phaseAgents: Record<string, string[]> = {
  Layer1_DataCollection: ['MarketAnalyst', 'FundamentalsAnalyst', 'NewsAnalyst', 'SocialAnalyst'],
  BullBearDebate: ['BullResearcher', 'BearResearcher', 'ResearchManager'],
  Trader: ['Trader'],
  RiskDebate: ['AggressiveAnalyst', 'ConservativeAnalyst', 'NeutralAnalyst', 'RiskJudge'],
}

const agentLabels: Record<string, string> = {
  MarketAnalyst: '技术面分析师',
  FundamentalsAnalyst: '基本面分析师',
  NewsAnalyst: '新闻分析师',
  SocialAnalyst: '舆情分析师',
  BullResearcher: '看多研究员',
  BearResearcher: '看空研究员',
  ResearchManager: '研究主管',
  Trader: '交易员',
  AggressiveAnalyst: '激进分析师',
  ConservativeAnalyst: '保守分析师',
  NeutralAnalyst: '中立分析师',
  RiskJudge: '风险裁决官',
}

// Agent 状态计算
const agentStates = computed(() => {
  const states = new Map<string, { status: string; content: string; round: number }>()
  for (const event of props.events) {
    const name = event.agentName
    if (!name) continue
    if (event.type === 'AGENT_START' || event.type === 'DEBATE_START') {
      states.set(name, { status: 'running', content: '', round: 0 })
    } else if (event.type === 'AGENT_CHUNK' || event.type === 'DEBATE_CHUNK') {
      const existing = states.get(name)
      if (existing) existing.content += event.content || ''
    } else if (event.type === 'AGENT_COMPLETE' || event.type === 'DEBATE_COMPLETE') {
      const existing = states.get(name)
      if (existing) {
        existing.status = 'done'
        if (event.content) existing.content = event.content
      }
    }
  }
  return states
})

// 已完成阶段
const completedPhases = computed(() => {
  return props.events.filter((e) => e.type === 'PHASE_COMPLETE').map((e) => e.phase)
})

// 最终裁决
const finalDecision = computed(() => {
  const event = props.events.find((e) => e.type === 'FINAL_DECISION')
  if (!event?.content) return null
  try {
    return JSON.parse(event.content)
  } catch {
    return { summary: event.content }
  }
})

// 当前阶段
const currentPhase = computed(() => {
  for (let i = props.events.length - 1; i >= 0; i--) {
    if (props.events[i].type === 'PHASE_START') return props.events[i].phase
  }
  return null
})

function getPhaseStatus(phase: string) {
  if (completedPhases.value.includes(phase)) return 'done'
  if (currentPhase.value === phase) return 'running'
  return 'pending'
}
</script>

<template>
  <div class="analysis-detail">
    <!-- 空态 -->
    <div v-if="!detail && !isRunning" class="detail-empty">
      <Activity :size="48" />
      <p>选择左侧分析记录查看详情</p>
      <p class="sub">或在顶部输入标的开始新的分析</p>
    </div>

    <!-- 有内容时 -->
    <template v-else>
      <!-- 头部信息 -->
      <div class="detail-header">
        <div class="header-stock">
          <h2>{{ detail?.resolvedStockName || detail?.originalQuery || '分析中...' }}</h2>
          <span class="stock-code">{{ detail?.resolvedStockCode }}</span>
        </div>
        <div class="header-status">
          <span v-if="isRunning" class="status running">
            <Activity :size="14" class="spin" /> 运行中
          </span>
          <span v-else-if="detail?.workflowStatus === 'COMPLETED'" class="status completed">
            <CheckCircle2 :size="14" /> 已完成
          </span>
          <span v-else-if="detail?.workflowStatus === 'FAILED'" class="status failed">
            <AlertCircle :size="14" /> 失败
          </span>
        </div>
      </div>

      <!-- 阶段进度条 -->
      <div class="phase-progress">
        <div
          v-for="(label, phase) in phaseNames"
          :key="phase"
          class="phase-step"
          :class="'phase-' + getPhaseStatus(phase)"
        >
          <div class="step-dot" />
          <span class="step-label">{{ label }}</span>
        </div>
      </div>

      <!-- 最终裁决卡片 -->
      <div v-if="finalDecision && !isRunning" class="decision-card">
        <div class="decision-header">
          <span class="decision-label">投资决策</span>
          <span class="decision-action" :class="'action-' + (finalDecision.action || '').toLowerCase()">
            {{ finalDecision.action || 'N/A' }}
          </span>
        </div>
        <div class="decision-metrics">
          <div class="metric" v-if="finalDecision.confidence">
            <span class="metric-label">置信度</span>
            <span class="metric-value">{{ Math.round(finalDecision.confidence * 100) }}%</span>
          </div>
          <div class="metric" v-if="finalDecision.targetPrice">
            <span class="metric-label">目标价</span>
            <span class="metric-value">¥{{ finalDecision.targetPrice }}</span>
          </div>
        </div>
        <div v-if="finalDecision.summary" class="decision-summary">
          <MarkdownRenderer :text="finalDecision.summary" :is-streaming="false" />
        </div>
        <button class="chat-btn" @click="emit('goToChat')">
          <MessageSquare :size="14" /> 在对话中提问
        </button>
      </div>

      <!-- Agent 内容区 -->
      <div class="phases-content">
        <div v-for="(label, phase) in phaseNames" :key="phase" class="phase-section">
          <div class="phase-header">
            <span class="phase-title">{{ label }}</span>
            <span class="phase-status-icon">
              <CheckCircle2 v-if="getPhaseStatus(phase) === 'done'" :size="14" />
              <Activity v-else-if="getPhaseStatus(phase) === 'running'" :size="14" class="spin" />
            </span>
          </div>
          <div class="agent-cards">
            <div
              v-for="agent in phaseAgents[phase]"
              :key="agent"
              class="agent-card"
              :class="{ 'agent-done': agentStates.get(agent)?.status === 'done' }"
            >
              <div class="agent-header">
                <span class="agent-name">{{ agentLabels[agent] || agent }}</span>
                <span class="agent-status">
                  <CheckCircle2 v-if="agentStates.get(agent)?.status === 'done'" :size="12" />
                  <Activity v-else-if="agentStates.get(agent)?.status === 'running'" :size="12" class="spin" />
                </span>
              </div>
              <div v-if="agentStates.get(agent)?.content" class="agent-content">
                <MarkdownRenderer :text="agentStates.get(agent)!.content" :is-streaming="false" />
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.analysis-detail {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: var(--bg, #f8fafc);
}
.detail-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--text-dim, #94a3b8);
  gap: 8px;
}
.detail-empty .sub { font-size: 13px; }

.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.header-stock h2 { font-size: 20px; font-weight: 600; margin: 0; }
.stock-code { font-size: 13px; color: var(--text-dim, #94a3b8); margin-left: 8px; }
.status {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  padding: 4px 10px;
  border-radius: 6px;
}
.status.running { background: var(--accent-dim, #2563eb18); color: var(--accent, #2563eb); }
.status.completed { background: #dcfce7; color: var(--green, #16a34a); }
.status.failed { background: #fef2f2; color: var(--danger, #dc2626); }

.phase-progress {
  display: flex;
  gap: 0;
  margin-bottom: 20px;
  background: var(--surface, #ffffff);
  border-radius: 8px;
  padding: 12px 16px;
  border: 1px solid var(--border, #e2e8f0);
}
.phase-step {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text-dim, #94a3b8);
}
.phase-step:not(:last-child)::after {
  content: '';
  flex: 1;
  height: 2px;
  background: var(--border, #e2e8f0);
  margin: 0 8px;
}
.phase-done .step-dot { background: var(--green, #16a34a); }
.phase-running .step-dot { background: var(--accent, #2563eb); animation: pulse 1.5s infinite; }
.phase-pending .step-dot { background: var(--border, #e2e8f0); }
.step-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }

.decision-card {
  background: var(--surface, #ffffff);
  border: 1px solid var(--border, #e2e8f0);
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 20px;
}
.decision-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.decision-label { font-size: 14px; font-weight: 600; }
.decision-action {
  font-size: 14px;
  font-weight: 700;
  padding: 4px 12px;
  border-radius: 6px;
}
.action-buy { background: #dcfce7; color: var(--green, #16a34a); }
.action-sell { background: #fef2f2; color: var(--danger, #dc2626); }
.action-hold { background: var(--surface-2, #f1f5f9); color: var(--text-dim, #94a3b8); }
.decision-metrics { display: flex; gap: 24px; margin-bottom: 12px; }
.metric-label { font-size: 12px; color: var(--text-dim, #94a3b8); display: block; }
.metric-value { font-size: 18px; font-weight: 600; }
.decision-summary { font-size: 14px; line-height: 1.6; }
.chat-btn {
  margin-top: 12px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border: 1px solid var(--accent, #2563eb);
  background: none;
  color: var(--accent, #2563eb);
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
}
.chat-btn:hover { background: var(--accent-dim, #2563eb18); }

.phases-content { display: flex; flex-direction: column; gap: 16px; }
.phase-section {
  background: var(--surface, #ffffff);
  border: 1px solid var(--border, #e2e8f0);
  border-radius: 8px;
  overflow: hidden;
}
.phase-header {
  padding: 10px 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--surface-2, #f1f5f9);
  border-bottom: 1px solid var(--border, #e2e8f0);
}
.phase-title { font-size: 14px; font-weight: 600; }
.phase-status-icon { color: var(--green, #16a34a); }
.agent-cards { padding: 8px; }
.agent-card {
  padding: 10px 12px;
  border-radius: 6px;
  margin-bottom: 4px;
}
.agent-card.agent-done { background: var(--surface-2, #f1f5f9); }
.agent-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 4px; }
.agent-name { font-size: 13px; font-weight: 500; }
.agent-status { color: var(--green, #16a34a); }
.agent-content { font-size: 13px; line-height: 1.6; max-height: 400px; overflow-y: auto; }

.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
@keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.4; } }
</style>
