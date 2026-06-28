<script setup lang="ts">
import { computed } from 'vue'
import { renderInline } from '@/utils/markdown'
import type { MessageBlock, KpiData, TableData } from '@/utils/validateJsonBlocks'

const props = defineProps<{
  block: MessageBlock
}>()

const kpiItems = computed<KpiData[]>(() => {
  if (props.block.type !== 'kpi' || !Array.isArray(props.block.data)) return []
  return props.block.data as KpiData[]
})

const tableData = computed<TableData | null>(() => {
  if (props.block.type !== 'table' || !props.block.data || Array.isArray(props.block.data)) return null
  return props.block.data as TableData
})

const renderedContent = computed(() => {
  if (!props.block.content) return ''
  return renderInline(props.block.content)
})
</script>

<template>
  <!-- Title -->
  <div v-if="block.type === 'title'" class="block-title">
    {{ block.content }}
  </div>

  <!-- Text -->
  <div v-else-if="block.type === 'text'" class="block-text" v-html="renderedContent"></div>

  <!-- KPI -->
  <div v-else-if="block.type === 'kpi'" class="block-kpi">
    <div v-if="block.content" class="kpi-header">{{ block.content }}</div>
    <div class="kpi-grid">
      <div
        v-for="(item, i) in kpiItems"
        :key="i"
        class="kpi-item"
        :class="`kpi-${item.trend || 'neutral'}`"
      >
        <div class="kpi-label">{{ item.label }}</div>
        <div class="kpi-value">{{ item.value }}</div>
      </div>
    </div>
  </div>

  <!-- Table -->
  <div v-else-if="block.type === 'table'" class="block-table">
    <div v-if="block.content" class="table-header">{{ block.content }}</div>
    <table v-if="tableData">
      <thead>
        <tr>
          <th v-for="(h, i) in tableData.headers" :key="i">{{ h }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(row, ri) in tableData.rows" :key="ri">
          <td v-for="(cell, ci) in row" :key="ci">{{ cell }}</td>
        </tr>
      </tbody>
    </table>
  </div>

  <!-- Card -->
  <div v-else-if="block.type === 'card'" class="block-card">
    <div v-for="(line, i) in block.content.split('\\n')" :key="i" class="card-line">
      <span v-if="line.trim()" v-html="renderInline(line)"></span>
      <br v-else />
    </div>
  </div>

  <!-- Warning -->
  <div v-else-if="block.type === 'warning'" class="block-warning">
    <span class="warning-icon">⚠</span>
    <span>{{ block.content }}</span>
  </div>

  <!-- Fallback -->
  <div v-else class="block-text">{{ block.content }}</div>
</template>

<style scoped>
/* Spacing between blocks */
:host {
  display: block;
  margin-bottom: 8px;
}

:host:last-child {
  margin-bottom: 0;
}

.block-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--text);
  margin-bottom: 4px;
}

.block-text {
  font-size: 14px;
  line-height: 1.7;
  color: var(--text);
}

.block-kpi {
  width: 100%;
}

.kpi-header {
  font-size: 13px;
  color: var(--text-dim);
  margin-bottom: 10px;
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.kpi-item {
  background: var(--surface-2);
  border-radius: 10px;
  padding: 12px;
  text-align: center;
}

.kpi-label {
  font-size: 12px;
  color: var(--text-dim);
  margin-bottom: 4px;
}

.kpi-value {
  font-size: 18px;
  font-weight: 700;
  color: var(--text);
}

.kpi-up .kpi-value {
  color: var(--green);
}

.kpi-down .kpi-value {
  color: var(--red);
}

.block-table {
  width: 100%;
  overflow-x: auto;
}

.table-header {
  font-size: 13px;
  color: var(--text-dim);
  margin-bottom: 8px;
}

.block-table table {
  width: 100%;
  border-collapse: collapse;
}

.block-table th,
.block-table td {
  border: 1px solid var(--border);
  padding: 8px 12px;
  text-align: left;
  font-size: 13px;
}

.block-table th {
  background: var(--surface-2);
  font-weight: 600;
  color: var(--text);
}

.block-table td {
  color: var(--text);
}

.block-card {
  background: var(--surface-2);
  border-radius: 10px;
  padding: 14px 16px;
  font-size: 14px;
  line-height: 1.7;
  color: var(--text);
}

.card-line {
  margin-bottom: 2px;
}

.block-warning {
  background: rgba(239, 68, 68, 0.08);
  border: 1px solid rgba(239, 68, 68, 0.2);
  border-radius: 10px;
  padding: 10px 14px;
  font-size: 13px;
  color: var(--red);
  display: flex;
  align-items: center;
  gap: 8px;
}

.warning-icon {
  font-size: 16px;
  flex-shrink: 0;
}

@media (max-width: 480px) {
  .kpi-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
