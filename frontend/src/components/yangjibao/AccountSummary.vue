<script setup lang="ts">
import { computed } from 'vue'
import type { AccountCollect, UserAccount, FundHoldItem } from '@/types/yangjibao'

const props = defineProps<{
  data: AccountCollect | null
  accounts: UserAccount[]
  selectedAccountId: string
  fundHoldings: FundHoldItem[]
}>()

const emit = defineEmits<{
  'switch-account': [accountId: string]
}>()

const totalAssets = computed(() => props.fundHoldings.reduce((sum, f) => sum + f.money, 0))
const totalCost = computed(() => props.data?.hold_cost ?? 0)
const todayIncome = computed(() => props.data?.today_income ?? 0)
const todayRate = computed(() => props.data?.today_income_rate ?? 0)

function formatMoney(v: number): string {
  return (v ?? 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function switchAccount(e: Event) {
  const target = e.target as HTMLSelectElement
  emit('switch-account', target.value)
}
</script>

<template>
  <div class="account-summary">
    <div class="summary-header">
      <div class="section-title">账户汇总</div>
      <select v-if="accounts.length > 1" :value="selectedAccountId" class="account-select" @change="switchAccount">
        <option v-for="acct in accounts" :key="acct.id" :value="acct.id">
          {{ acct.title }}
        </option>
      </select>
    </div>

    <div v-if="data" class="summary-cards">
      <div class="summary-card">
        <div class="card-label">账户资产</div>
        <div class="card-value">{{ formatMoney(totalAssets) }}</div>
      </div>
      <div class="summary-card">
        <div class="card-label">持有成本</div>
        <div class="card-value">{{ formatMoney(totalCost) }}</div>
      </div>
      <div class="summary-card">
        <div class="card-label">今日收益</div>
        <div class="card-value" :class="{ up: todayIncome > 0, down: todayIncome < 0 }">
          {{ todayIncome > 0 ? '+' : '' }}{{ formatMoney(todayIncome) }}
        </div>
      </div>
      <div class="summary-card">
        <div class="card-label">今日收益率</div>
        <div class="card-value" :class="{ up: todayRate > 0, down: todayRate < 0 }">
          {{ todayRate > 0 ? '+' : '' }}{{ todayRate.toFixed(2) }}%
        </div>
      </div>
    </div>
    <div v-else class="summary-empty">暂无数据</div>
  </div>
</template>

<style scoped>
.account-summary {
  margin-bottom: 20px;
}

.summary-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-dim);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.account-select {
  font-size: 12px;
  padding: 4px 8px;
  border-radius: 6px;
  border: 1px solid var(--border);
  background: var(--surface-2);
  color: var(--text);
  cursor: pointer;
}

.summary-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}

.summary-card {
  background: var(--surface-2);
  border-radius: 10px;
  padding: 12px;
}

.card-label {
  font-size: 12px;
  color: var(--text-dim);
  margin-bottom: 6px;
}

.card-value {
  font-size: 16px;
  font-weight: 600;
  color: var(--text);
}

.card-value.up {
  color: var(--red);
}

.card-value.down {
  color: var(--green);
}

.summary-empty {
  padding: 20px;
  text-align: center;
  color: var(--text-dim);
  font-size: 14px;
}
</style>
