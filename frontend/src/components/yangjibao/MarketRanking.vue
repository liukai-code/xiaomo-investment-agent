<script setup lang="ts">
import { computed } from 'vue'
import { TrendingUp, TrendingDown, Minus } from 'lucide-vue-next'
import type { MarketRankingItem } from '@/types/yangjibao'

const props = defineProps<{
  data: MarketRankingItem[]
}>()

const sorted = computed(() => {
  return [...props.data].sort((a, b) => b.change_rate - a.change_rate)
})

const topList = computed(() => sorted.value.slice(0, 10))
const bottomList = computed(() => sorted.value.slice(-5).reverse())

function formatRate(rate: number): string {
  return `${rate > 0 ? '+' : ''}${rate.toFixed(2)}%`
}
</script>

<template>
  <div class="market-ranking">
    <div class="section-title">板块排行</div>
    <div v-if="data.length > 0" class="ranking-content">
      <div class="ranking-group">
        <div class="group-label">涨幅前列</div>
        <div v-for="item in topList" :key="item.name" class="ranking-row">
          <span class="rank-name">{{ item.name }}</span>
          <span class="rank-rate" :class="{ up: item.change_rate > 0, down: item.change_rate < 0 }">
            <TrendingUp v-if="item.change_rate > 0" :size="12" />
            <TrendingDown v-else-if="item.change_rate < 0" :size="12" />
            <Minus v-else :size="12" />
            {{ formatRate(item.change_rate) }}
          </span>
        </div>
      </div>
      <div class="ranking-divider"></div>
      <div class="ranking-group">
        <div class="group-label">跌幅前列</div>
        <div v-for="item in bottomList" :key="item.name" class="ranking-row">
          <span class="rank-name">{{ item.name }}</span>
          <span class="rank-rate" :class="{ up: item.change_rate > 0, down: item.change_rate < 0 }">
            <TrendingUp v-if="item.change_rate > 0" :size="12" />
            <TrendingDown v-else-if="item.change_rate < 0" :size="12" />
            <Minus v-else :size="12" />
            {{ formatRate(item.change_rate) }}
          </span>
        </div>
      </div>
    </div>
    <div v-else class="empty">
      暂无板块数据（非交易日或接口超时）
    </div>
  </div>
</template>

<style scoped>
.market-ranking {
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

.ranking-content {
  background: var(--surface-2);
  border-radius: 10px;
  padding: 12px;
}

.ranking-group {
  margin-bottom: 4px;
}

.group-label {
  font-size: 11px;
  color: var(--text-dim);
  margin-bottom: 6px;
  padding-left: 4px;
}

.ranking-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 4px;
  border-radius: 6px;
  font-size: 13px;
}

.ranking-row:hover {
  background: var(--surface-1);
}

.rank-name {
  color: var(--text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 140px;
}

.rank-rate {
  display: flex;
  align-items: center;
  gap: 3px;
  font-size: 12px;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  color: var(--text-dim);
}

.rank-rate.up {
  color: var(--red);
}

.rank-rate.down {
  color: var(--green);
}

.ranking-divider {
  height: 1px;
  background: var(--border);
  margin: 8px 0;
}

.empty {
  padding: 20px;
  text-align: center;
  color: var(--text-dim);
  font-size: 13px;
}
</style>
