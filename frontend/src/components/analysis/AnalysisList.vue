<script setup lang="ts">
import { Trash2, TrendingUp, TrendingDown, Minus, Loader2 } from 'lucide-vue-next'
import type { AnalysisRecord } from '@/api/analysis'

const props = defineProps<{
  analyses: AnalysisRecord[]
  selectedId: number | null
  loading: boolean
}>()

const emit = defineEmits<{
  select: [id: number]
  delete: [id: number]
}>()

function getStatusLabel(status: string) {
  const map: Record<string, string> = {
    PENDING: '等待中',
    RUNNING: '运行中',
    COMPLETED: '已完成',
    FAILED: '失败',
  }
  return map[status] || status
}

function getActionIcon(action: string | null) {
  if (action === 'BUY') return TrendingUp
  if (action === 'SELL') return TrendingDown
  return Minus
}

function getActionClass(action: string | null) {
  if (action === 'BUY') return 'action-buy'
  if (action === 'SELL') return 'action-sell'
  return 'action-hold'
}

function formatTime(dateStr: string | null) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getMonth() + 1}/${d.getDate()} ${d.getHours()}:${String(d.getMinutes()).padStart(2, '0')}`
}
</script>

<template>
  <div class="analysis-list">
    <div class="list-header">
      <span class="list-title">分析记录</span>
      <span class="list-count">{{ analyses.length }}</span>
    </div>
    <div v-if="loading" class="list-loading">
      <Loader2 :size="20" class="spin" />
    </div>
    <div v-else-if="analyses.length === 0" class="list-empty">暂无分析记录</div>
    <div v-else class="list-items">
      <div
        v-for="item in analyses"
        :key="item.id"
        class="list-item"
        :class="{ active: item.id === selectedId, running: item.workflowStatus === 'RUNNING' }"
        @click="emit('select', item.id)"
      >
        <div class="item-main">
          <div class="item-stock">
            <span class="stock-name">{{ item.resolvedStockName || item.originalQuery }}</span>
            <span class="stock-code">{{ item.resolvedStockCode }}</span>
          </div>
          <div class="item-meta">
            <span class="status-badge" :class="'status-' + item.workflowStatus.toLowerCase()">
              <Loader2 v-if="item.workflowStatus === 'RUNNING'" :size="12" class="spin" />
              {{ getStatusLabel(item.workflowStatus) }}
            </span>
            <span v-if="item.action" class="action-badge" :class="getActionClass(item.action)">
              {{ item.action }}
            </span>
            <span v-if="item.confidence" class="confidence">{{ Math.round(item.confidence * 100) }}%</span>
          </div>
          <div class="item-time">{{ formatTime(item.createdAt) }}</div>
        </div>
        <button
          v-if="item.workflowStatus !== 'RUNNING'"
          class="delete-btn"
          @click.stop="emit('delete', item.id)"
        >
          <Trash2 :size="14" />
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.analysis-list {
  width: 280px;
  min-width: 280px;
  border-right: 1px solid var(--border, #e2e8f0);
  display: flex;
  flex-direction: column;
  background: var(--surface, #ffffff);
  overflow-y: auto;
}
.list-header {
  padding: 12px 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--border, #e2e8f0);
}
.list-title { font-size: 14px; font-weight: 600; color: var(--text, #1e293b); }
.list-count {
  font-size: 12px;
  color: var(--text-dim, #94a3b8);
  background: var(--surface-2, #f1f5f9);
  padding: 2px 8px;
  border-radius: 10px;
}
.list-loading, .list-empty {
  padding: 40px 16px;
  text-align: center;
  color: var(--text-dim, #94a3b8);
  font-size: 13px;
}
.list-items { flex: 1; overflow-y: auto; }
.list-item {
  padding: 12px 16px;
  cursor: pointer;
  border-bottom: 1px solid var(--border, #e2e8f0);
  display: flex;
  align-items: center;
  gap: 8px;
  transition: background 0.15s;
}
.list-item:hover { background: var(--sidebar-hover, #f1f5f9); }
.list-item.active { background: var(--sidebar-active, #dbeafe); border-left: 3px solid var(--accent, #2563eb); }
.list-item.running { border-left: 3px solid var(--accent, #2563eb); }
.item-main { flex: 1; min-width: 0; }
.item-stock { display: flex; align-items: baseline; gap: 6px; }
.stock-name { font-size: 14px; font-weight: 500; color: var(--text, #1e293b); }
.stock-code { font-size: 12px; color: var(--text-dim, #94a3b8); }
.item-meta { display: flex; align-items: center; gap: 6px; margin-top: 4px; }
.status-badge {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 4px;
  display: inline-flex;
  align-items: center;
  gap: 3px;
}
.status-running { background: var(--accent-dim, #2563eb18); color: var(--accent, #2563eb); }
.status-completed { background: #dcfce7; color: var(--green, #16a34a); }
.status-failed { background: #fef2f2; color: var(--danger, #dc2626); }
.status-pending { background: var(--surface-2, #f1f5f9); color: var(--text-dim, #94a3b8); }
.action-badge {
  font-size: 11px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: 4px;
}
.action-buy { background: #dcfce7; color: var(--green, #16a34a); }
.action-sell { background: #fef2f2; color: var(--danger, #dc2626); }
.action-hold { background: var(--surface-2, #f1f5f9); color: var(--text-dim, #94a3b8); }
.confidence { font-size: 11px; color: var(--text-dim, #94a3b8); }
.item-time { font-size: 11px; color: var(--text-dim, #94a3b8); margin-top: 2px; }
.delete-btn {
  opacity: 0;
  background: none;
  border: none;
  color: var(--text-dim, #94a3b8);
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  flex-shrink: 0;
}
.list-item:hover .delete-btn { opacity: 1; }
.delete-btn:hover { color: var(--danger, #dc2626); background: #fef2f2; }
.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
</style>
