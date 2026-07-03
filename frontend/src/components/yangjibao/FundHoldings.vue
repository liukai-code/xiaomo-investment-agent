<script setup lang="ts">
import type { FundHoldItem } from '@/types/yangjibao'

defineProps<{
  data: FundHoldItem[]
}>()

function formatMoney(v: number): string {
  return v.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function formatRate(item: FundHoldItem): string {
  const cost = item.money - item.hold_earn
  if (!cost) return '0.00%'
  const rate = (item.hold_earn / cost) * 100
  return `${rate > 0 ? '+' : ''}${rate.toFixed(2)}%`
}
</script>

<template>
  <div class="fund-holdings">
    <div class="section-title">持仓明细</div>
    <div v-if="data.length > 0" class="holdings-table-wrapper">
      <table class="holdings-table">
        <thead>
          <tr>
            <th>基金名称</th>
            <th>代码</th>
            <th class="num">市值</th>
            <th class="num">盈亏</th>
            <th class="num">收益率</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in data" :key="item.fund_id">
            <td class="name-cell">{{ item.short_name }}</td>
            <td class="code-cell">{{ item.code }}</td>
            <td class="num">{{ formatMoney(item.money) }}</td>
            <td class="num" :class="{ up: item.hold_earn > 0, down: item.hold_earn < 0 }">
              {{ item.hold_earn > 0 ? '+' : '' }}{{ formatMoney(item.hold_earn) }}
            </td>
            <td class="num" :class="{ up: item.hold_earn > 0, down: item.hold_earn < 0 }">
              {{ formatRate(item) }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-else class="empty">暂无持仓</div>
  </div>
</template>

<style scoped>
.fund-holdings {
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

.holdings-table-wrapper {
  overflow-x: auto;
}

.holdings-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.holdings-table th {
  text-align: left;
  padding: 8px 10px;
  font-weight: 600;
  color: var(--text-dim);
  border-bottom: 1px solid var(--border);
  white-space: nowrap;
  font-size: 12px;
}

.holdings-table th.num {
  text-align: right;
}

.holdings-table td {
  padding: 10px;
  border-bottom: 1px solid var(--border);
  color: var(--text);
}

.holdings-table td.num {
  text-align: right;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.holdings-table td.name-cell {
  max-width: 140px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.holdings-table td.code-cell {
  color: var(--text-dim);
  white-space: nowrap;
}

.holdings-table td.up {
  color: var(--red);
}

.holdings-table td.down {
  color: var(--green);
}

.holdings-table tbody tr:hover {
  background: var(--surface-2);
}

.empty {
  padding: 20px;
  text-align: center;
  color: var(--text-dim);
  font-size: 14px;
}
</style>
