<script setup lang="ts">
import { TrendingUp, TrendingDown, Minus } from 'lucide-vue-next'
import type { IndexData } from '@/types/yangjibao'

defineProps<{
  data: IndexData[]
}>()

function formatValue(v: number): string {
  return v.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function formatDir(dir: number): string {
  const sign = dir > 0 ? '+' : ''
  return `${sign}${dir.toFixed(2)}%`
}
</script>

<template>
  <div class="index-quotes">
    <div class="section-title">指数行情</div>
    <div class="quotes-grid">
      <div v-for="item in data" :key="item.name" class="quote-card">
        <div class="quote-name">{{ item.name }}</div>
        <div class="quote-value">{{ formatValue(item.v) }}</div>
        <div class="quote-dir" :class="{ up: item.dir > 0, down: item.dir < 0 }">
          <TrendingUp v-if="item.dir > 0" :size="14" />
          <TrendingDown v-else-if="item.dir < 0" :size="14" />
          <Minus v-else :size="14" />
          <span>{{ formatDir(item.dir) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.index-quotes {
  margin-bottom: 20px;
}

.section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-dim);
  margin-bottom: 10px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.quotes-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
}

.quote-card {
  background: var(--surface-2);
  border-radius: 10px;
  padding: 12px;
}

.quote-name {
  font-size: 12px;
  color: var(--text-dim);
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.quote-value {
  font-size: 16px;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 4px;
}

.quote-dir {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-dim);
}

.quote-dir.up {
  color: var(--red);
}

.quote-dir.down {
  color: var(--green);
}
</style>
