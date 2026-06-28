<script setup lang="ts">
import { computed } from 'vue'
import type { TableBlock } from '@/types/blocks'
import { parseBlocks } from '@/composables/useMarkdownBlocks'

const props = defineProps<{
  content: string
}>()

interface KpiItem {
  label: string
  value: string
  trend: 'up' | 'down' | 'neutral'
}

const kpiItems = computed<KpiItem[]>(() => {
  const blocks = parseBlocks(props.content)
  const table = blocks.find((b) => b.type === 'table') as TableBlock | undefined
  if (!table) return []

  return table.rows.map((row) => {
    const label = row[0] || ''
    const value = row[1] || ''
    const trendRaw = (row[2] || '').toLowerCase().trim()
    let trend: 'up' | 'down' | 'neutral' = 'neutral'
    if (trendRaw === 'up') trend = 'up'
    else if (trendRaw === 'down') trend = 'down'
    return { label, value, trend }
  })
})

// Content without the table (everything before and after the table)
const nonTableContent = computed(() => {
  const lines = props.content.split('\n')
  const result: string[] = []
  let inTable = false
  for (const line of lines) {
    if (line.startsWith('|') && line.includes('|')) {
      inTable = true
      continue
    }
    if (inTable && line.trim() === '') {
      inTable = false
      continue
    }
    if (!inTable) {
      result.push(line)
    }
  }
  return result.join('\n').trim()
})
</script>

<template>
  <div class="kpi-card">
    <div v-if="kpiItems.length > 0" class="kpi-grid">
      <div
        v-for="(item, i) in kpiItems"
        :key="i"
        class="kpi-item"
        :class="`kpi-${item.trend}`"
      >
        <div class="kpi-label">{{ item.label }}</div>
        <div class="kpi-value">{{ item.value }}</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.kpi-card {
  width: 100%;
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

@media (max-width: 480px) {
  .kpi-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
