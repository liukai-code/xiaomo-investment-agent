<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import type { WorkflowEvent } from '@/api/chat'
import MarkdownRenderer from '@/components/blocks/MarkdownRenderer.vue'

interface AgentState {
  name: string
  status: 'pending' | 'running' | 'done'
  content: string
  round?: number
}

const props = defineProps<{
  events: WorkflowEvent[]
  isRunning: boolean
}>()

const expandedPhases = ref<Set<string>>(new Set(['Layer1_DataCollection']))

const phaseNames: Record<string, string> = {
  Layer1_DataCollection: '数据采集',
  BullBearDebate: '多空辩论',
  Trader: '交易决策',
  RiskDebate: '风险评估',
}

const agentLabels: Record<string, string> = {
  MarketAnalyst: '技术分析师',
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

// 解析事件流，构建各 Agent 的状态
const agentStates = computed(() => {
  const states = new Map<string, AgentState>()

  for (const event of props.events) {
    const agent = event.agentName
    if (!agent) continue

    if (event.type === 'AGENT_START' || event.type === 'DEBATE_START') {
      const existing = states.get(agent)
      if (existing) {
        existing.status = 'running'
        existing.content = ''
        if (event.type === 'DEBATE_START' && event.content) {
          const round = parseInt(event.content.replace('round:', ''))
          existing.round = round
        }
      } else {
        states.set(agent, {
          name: agent,
          status: 'running',
          content: '',
          round: event.type === 'DEBATE_START' ? parseInt((event.content || '').replace('round:', '')) : undefined,
        })
      }
    } else if (event.type === 'AGENT_CHUNK' || event.type === 'DEBATE_CHUNK') {
      const existing = states.get(agent)
      if (existing) {
        // 累积内容：取最新的完整内容
        existing.content = event.content || ''
      }
    } else if (event.type === 'AGENT_COMPLETE' || event.type === 'DEBATE_COMPLETE') {
      const existing = states.get(agent)
      if (existing) {
        existing.status = 'done'
        existing.content = event.content || ''
      } else {
        states.set(agent, {
          name: agent,
          status: 'done',
          content: event.content || '',
        })
      }
    }
  }

  return states
})

// 当前阶段
const currentPhase = computed(() => {
  for (let i = props.events.length - 1; i >= 0; i--) {
    if (props.events[i].type === 'PHASE_START') {
      return props.events[i].phase
    }
  }
  return null
})

// 阶段完成状态
const phaseStatus = computed(() => {
  const completed = new Set<string>()
  for (const event of props.events) {
    if (event.type === 'PHASE_COMPLETE' && event.phase) {
      completed.add(event.phase)
    }
  }
  return completed
})

// 最终决策
const finalDecision = computed(() => {
  for (let i = props.events.length - 1; i >= 0; i--) {
    if (props.events[i].type === 'FINAL_DECISION') {
      return props.events[i]
    }
  }
  return null
})

// 解析 RiskJudge 的 JSON 输出为结构化数据
function parseJudgeJson(content: string): { action: string; confidence: number; targetPrice: number; summary: string } | null {
  try {
    // 匹配 ```json``` 代码块内的 JSON 或裸 JSON
    const codeBlockMatch = content.match(/```json\s*([\s\S]*?)\s*```/)
    const jsonStr = codeBlockMatch ? codeBlockMatch[1] : content.match(/\{[\s\S]*?}/)?.[0]
    if (jsonStr) {
      const obj = JSON.parse(jsonStr)
      return {
        action: obj.action || 'HOLD',
        confidence: typeof obj.confidence === 'number' ? obj.confidence : 0.5,
        targetPrice: typeof obj.target_price === 'number' ? obj.target_price : 0,
        summary: obj.summary || '',
      }
    }
  } catch {}
  return null
}

// 按阶段分组的 Agent
const phaseAgents: Record<string, string[]> = {
  Layer1_DataCollection: ['MarketAnalyst', 'FundamentalsAnalyst', 'NewsAnalyst', 'SocialAnalyst'],
  BullBearDebate: ['BullResearcher', 'BearResearcher', 'ResearchManager'],
  Trader: ['Trader'],
  RiskDebate: ['AggressiveAnalyst', 'ConservativeAnalyst', 'NeutralAnalyst', 'RiskJudge'],
}

function togglePhase(phase: string) {
  if (expandedPhases.value.has(phase)) {
    expandedPhases.value.delete(phase)
  } else {
    expandedPhases.value.add(phase)
  }
}

function getPhaseIcon(phase: string): string {
  if (phaseStatus.value.has(phase)) return '✓'
  if (currentPhase.value === phase) return '◌'
  return '○'
}

function getPhaseIconClass(phase: string): string {
  if (phaseStatus.value.has(phase)) return 'icon-done'
  if (currentPhase.value === phase) return 'icon-running'
  return 'icon-pending'
}

// 将 RiskJudge 的 JSON 输出格式化为可读文本
function formatAgentContent(agentName: string, content: string): string {
  if (agentName !== 'RiskJudge') return content
  const parsed = parseJudgeJson(content)
  if (!parsed) return content
  return `**裁决：${parsed.action}** | 置信度：${(parsed.confidence * 100).toFixed(0)}%${parsed.targetPrice > 0 ? ' | 目标价：' + parsed.targetPrice : ''}\n\n${parsed.summary}`
}
</script>

<template>
  <div class="workflow-panel">
    <div class="workflow-header">
      <span class="workflow-title">深度分析工作流</span>
      <span v-if="isRunning" class="workflow-spinner"></span>
      <span v-else-if="phaseStatus.size === 4" class="workflow-done">✓ 完成</span>
    </div>

    <!-- 4 阶段进度条 -->
    <div class="phase-progress">
      <div
        v-for="(label, phase) in phaseNames"
        :key="phase"
        class="phase-step"
        :class="{
          active: currentPhase === phase,
          done: phaseStatus.has(phase),
        }"
      >
        <span class="phase-dot" :class="getPhaseIconClass(phase)">{{ getPhaseIcon(phase) }}</span>
        <span class="phase-label">{{ label }}</span>
      </div>
    </div>

    <!-- 各阶段详情 -->
    <div v-for="(label, phase) in phaseNames" :key="phase" class="phase-section">
      <div class="phase-header" @click="togglePhase(phase)">
        <span :class="getPhaseIconClass(phase)">{{ getPhaseIcon(phase) }}</span>
        <span class="phase-name">{{ label }}</span>
        <span class="phase-toggle">{{ expandedPhases.has(phase) ? '▼' : '▶' }}</span>
      </div>

      <div v-if="expandedPhases.has(phase)" class="phase-content">
        <div
          v-for="agentName in phaseAgents[phase]"
          :key="agentName"
          class="agent-card"
          :class="{
            running: agentStates.get(agentName)?.status === 'running',
            done: agentStates.get(agentName)?.status === 'done',
          }"
        >
          <div class="agent-header">
            <span class="agent-label">{{ agentLabels[agentName] || agentName }}</span>
            <span v-if="agentStates.get(agentName)?.round" class="agent-round">
              第{{ agentStates.get(agentName)?.round }}轮
            </span>
            <span class="agent-status">
              {{ agentStates.get(agentName)?.status === 'running' ? '分析中...' :
                 agentStates.get(agentName)?.status === 'done' ? '✓' : '等待中' }}
            </span>
          </div>
          <div
            v-if="agentStates.get(agentName)?.content"
            class="agent-content"
          >
            <MarkdownRenderer
              :text="formatAgentContent(agentName, agentStates.get(agentName)?.content || '')"
              :is-streaming="agentStates.get(agentName)?.status === 'running'"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- 最终决策 -->
    <div v-if="finalDecision" class="final-decision">
      <div class="decision-header">最终裁决</div>
      <div class="decision-content">
        <template v-if="parseJudgeJson(finalDecision.content || '')">
          <div class="decision-card">
            <div class="decision-action" :class="(parseJudgeJson(finalDecision.content || '')?.action || 'HOLD').toLowerCase()">
              {{ parseJudgeJson(finalDecision.content || '')?.action }}
            </div>
            <div class="decision-meta">
              <span class="decision-label">置信度</span>
              <span class="decision-value">{{ ((parseJudgeJson(finalDecision.content || '')?.confidence || 0) * 100).toFixed(0) }}%</span>
              <template v-if="(parseJudgeJson(finalDecision.content || '')?.targetPrice || 0) > 0">
                <span class="decision-label" style="margin-left:16px">目标价</span>
                <span class="decision-value">{{ parseJudgeJson(finalDecision.content || '')?.targetPrice }}</span>
              </template>
            </div>
            <div class="decision-summary">{{ parseJudgeJson(finalDecision.content || '')?.summary }}</div>
          </div>
        </template>
        <template v-else>
          <MarkdownRenderer :text="finalDecision.content || ''" :is-streaming="false" />
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.workflow-panel {
  border: 1px solid var(--border, #e0e0e0);
  border-radius: 12px;
  overflow: hidden;
  background: var(--bg-secondary, #fafafa);
  margin: 8px 0;
}

.workflow-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: var(--bg-tertiary, #f0f0f0);
  font-weight: 600;
  font-size: 14px;
}

.workflow-spinner {
  display: inline-block;
  width: 16px;
  height: 16px;
  border: 2px solid var(--accent, #4f46e5);
  border-top-color: transparent;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.workflow-done {
  color: #22c55e;
  font-size: 13px;
}

.phase-progress {
  display: flex;
  justify-content: space-around;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border, #e0e0e0);
}

.phase-step {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--text-dim, #999);
  transition: color 0.2s;
}

.phase-step.active {
  color: var(--accent, #4f46e5);
}

.phase-step.done {
  color: #22c55e;
}

.phase-dot {
  font-size: 16px;
}

.icon-done { color: #22c55e; }
.icon-running { color: var(--accent, #4f46e5); animation: pulse 1.5s ease-in-out infinite; }
.icon-pending { color: var(--text-dim, #999); }

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.phase-section {
  border-bottom: 1px solid var(--border, #e0e0e0);
}

.phase-section:last-of-type {
  border-bottom: none;
}

.phase-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  user-select: none;
}

.phase-header:hover {
  background: var(--bg-hover, #f5f5f5);
}

.phase-name {
  flex: 1;
}

.phase-toggle {
  font-size: 10px;
  color: var(--text-dim, #999);
}

.phase-content {
  padding: 0 16px 12px;
}

.agent-card {
  margin: 8px 0;
  border: 1px solid var(--border, #e0e0e0);
  border-radius: 8px;
  overflow: hidden;
  background: var(--bg-primary, #fff);
}

.agent-card.running {
  border-color: var(--accent, #4f46e5);
  box-shadow: 0 0 0 1px var(--accent, #4f46e5);
}

.agent-card.done {
  border-color: #22c55e33;
}

.agent-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--bg-secondary, #fafafa);
  font-size: 13px;
}

.agent-label {
  font-weight: 500;
  flex: 1;
}

.agent-round {
  font-size: 11px;
  color: var(--accent, #4f46e5);
  background: var(--accent-bg, #eef2ff);
  padding: 1px 6px;
  border-radius: 4px;
}

.agent-status {
  font-size: 12px;
  color: var(--text-dim, #999);
}

.agent-content {
  padding: 8px 12px;
  font-size: 13px;
  max-height: 400px;
  overflow-y: auto;
  word-break: break-word;
  overflow-wrap: break-word;
}

.final-decision {
  margin: 12px 16px;
  border: 2px solid #22c55e;
  border-radius: 8px;
  overflow: hidden;
}

.decision-header {
  padding: 8px 12px;
  background: #22c55e15;
  font-weight: 600;
  font-size: 14px;
  color: #22c55e;
}

.decision-content {
  padding: 12px 16px;
}

.decision-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.decision-action {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 2px;
}

.decision-action.buy { color: #ef4444; }
.decision-action.sell { color: #22c55e; }
.decision-action.hold { color: #f59e0b; }

.decision-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.decision-label {
  color: var(--text-dim, #999);
}

.decision-value {
  font-weight: 600;
}

.decision-summary {
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-secondary, #666);
  word-break: break-word;
}
</style>
