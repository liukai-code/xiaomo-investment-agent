<script setup lang="ts">
import { useYangjibaoStore } from '@/stores/yangjibao'
import IndexQuotes from './IndexQuotes.vue'
import AccountSummary from './AccountSummary.vue'
import FundHoldings from './FundHoldings.vue'
import { TrendingUp, RefreshCw, DoorOpen, X, Loader2 } from 'lucide-vue-next'

const yjbStore = useYangjibaoStore()

function handleClose() {
  yjbStore.cardVisible = false
}

function handleLogout() {
  yjbStore.logout()
}
</script>

<template>
  <div class="yjb-holdings-card">
    <!-- Header -->
    <div class="yjb-card-header">
      <div class="yjb-card-title-area">
        <TrendingUp :size="18" class="yjb-card-icon" />
        <span class="yjb-card-title">养基宝持仓</span>
      </div>
      <div class="yjb-card-actions">
        <select
          v-if="yjbStore.accounts.length > 1"
          :value="yjbStore.selectedAccountId"
          class="yjb-account-select"
          @change="yjbStore.switchAccount(($event.target as HTMLSelectElement).value)"
        >
          <option v-for="acct in yjbStore.accounts" :key="acct.id" :value="acct.id">
            {{ acct.title }}
          </option>
        </select>
        <button class="yjb-icon-btn" @click="yjbStore.loadAllData()" title="刷新数据">
          <RefreshCw :size="15" />
        </button>
        <button class="yjb-icon-btn" @click="handleLogout" title="退出登录">
          <DoorOpen :size="15" />
        </button>
        <button class="yjb-icon-btn" @click="handleClose" title="收起">
          <X :size="15" />
        </button>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="yjbStore.loading" class="yjb-card-loading">
      <Loader2 :size="28" class="spin" />
      <span>加载中...</span>
    </div>

    <!-- Content -->
    <template v-else>
      <!-- Index Quotes -->
      <div class="yjb-card-section">
        <IndexQuotes :data="yjbStore.indexData" />
      </div>

      <div class="yjb-card-divider"></div>

      <!-- Account Summary -->
      <div class="yjb-card-section">
        <AccountSummary
          :data="yjbStore.accountCollect"
          :accounts="yjbStore.accounts"
          :selected-account-id="yjbStore.selectedAccountId"
          @switch-account="yjbStore.switchAccount"
        />
      </div>

      <div class="yjb-card-divider"></div>

      <!-- Holdings Table -->
      <div class="yjb-card-section">
        <FundHoldings :data="yjbStore.fundHoldings" />
      </div>
    </template>

    <!-- Footer -->
    <div class="yjb-card-footer">
      <button class="yjb-collapse-btn" @click="handleClose">收起持仓面板</button>
    </div>
  </div>
</template>

<style scoped>
.yjb-holdings-card {
  background: #ffffff;
  border-radius: 20px;
  border: 1px solid #E5E7EB;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06), 0 4px 16px rgba(0, 0, 0, 0.04);
  padding: 28px;
  animation: cardSlideUp 0.3s ease-out;
  overflow: hidden;
}

@keyframes cardSlideUp {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* Header */
.yjb-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.yjb-card-title-area {
  display: flex;
  align-items: center;
  gap: 8px;
}

.yjb-card-icon {
  color: var(--accent);
}

.yjb-card-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text);
}

.yjb-card-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.yjb-account-select {
  font-size: 12px;
  padding: 4px 8px;
  border-radius: 8px;
  border: 1px solid #E5E7EB;
  background: #f9fafb;
  color: var(--text);
  cursor: pointer;
  margin-right: 4px;
}

.yjb-icon-btn {
  background: none;
  border: none;
  color: #9ca3af;
  cursor: pointer;
  padding: 6px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}

.yjb-icon-btn:hover {
  background: #f3f4f6;
  color: var(--text);
}

/* Sections */
.yjb-card-section {
  margin-bottom: 0;
}

.yjb-card-divider {
  height: 1px;
  background: #F3F4F6;
  margin: 20px 0;
}

/* Loading */
.yjb-card-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 48px 0;
  color: #9ca3af;
  font-size: 14px;
}

.spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* Footer */
.yjb-card-footer {
  display: flex;
  justify-content: center;
  padding-top: 20px;
  margin-top: 4px;
}

.yjb-collapse-btn {
  background: none;
  border: none;
  color: #9ca3af;
  font-size: 13px;
  cursor: pointer;
  padding: 6px 16px;
  border-radius: 8px;
  transition: all 0.15s;
}

.yjb-collapse-btn:hover {
  background: #f3f4f6;
  color: var(--text);
}

/* Override sub-component spacing */
.yjb-card-section :deep(.index-quotes),
.yjb-card-section :deep(.account-summary),
.yjb-card-section :deep(.fund-holdings) {
  margin-bottom: 0;
}

.yjb-card-section :deep(.section-title) {
  font-size: 12px;
  font-weight: 600;
  color: #6b7280;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 12px;
}
</style>
