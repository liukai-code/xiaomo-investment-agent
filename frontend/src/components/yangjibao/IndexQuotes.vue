<script setup lang="ts">
import { computed } from 'vue'
import { TrendingUp, TrendingDown, Minus } from 'lucide-vue-next'
import type { IndexData } from '@/types/yangjibao'

const props = defineProps<{
  data: IndexData[]
}>()

// Pad to even count (2-col grid needs integer rows) then duplicate for seamless loop
const loopData = computed(() => {
  const list = [...props.data]
  if (list.length % 2 !== 0) list.push(null as any)
  return [...list, ...list]
})

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
    <div class="section-title">大盘指数</div>
    <div class="quotes-viewport">
      <div class="quotes-track">
        <div v-for="(item, i) in loopData" :key="`${item?.name ?? 'pad'}-${i}`" class="quote-card">
          <template v-if="item">
            <div class="quote-name">{{ item.name }}</div>
            <div class="quote-value">{{ formatValue(item.v) }}</div>
            <div class="quote-dir" :class="{ up: item.dir > 0, down: item.dir < 0 }">
              <TrendingUp v-if="item.dir > 0" :size="14" />
              <TrendingDown v-else-if="item.dir < 0" :size="14" />
              <Minus v-else :size="14" />
              <span>{{ formatDir(item.dir) }}</span>
            </div>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.index-quotes {
  margin-bottom: 16px;
}

.section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-dim);
  margin-bottom: 10px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.quotes-viewport {
  height: 148px;
  overflow: hidden;
  border-radius: 10px;
}

.quotes-track {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
  animation: marquee 20s linear infinite;
}

.quotes-track:hover {
  animation-play-state: paused;
}

@keyframes marquee {
  0% { transform: translateY(0); }
  100% { transform: translateY(-50%); }
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
