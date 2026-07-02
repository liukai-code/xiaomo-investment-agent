<script setup lang="ts">
import { useYangjibaoStore } from '@/stores/yangjibao'
import IndexQuotes from './IndexQuotes.vue'
import AccountSummary from './AccountSummary.vue'
import FundHoldings from './FundHoldings.vue'
import { X, LogOut, Loader2, RefreshCw, DoorOpen } from 'lucide-vue-next'

const yjbStore = useYangjibaoStore()

function handleClose() {
  yjbStore.panelVisible = false
}

function handleLogout() {
  yjbStore.logout()
}
</script>

<template>
  <div class="yjb-panel">
    <div class="yjb-panel-header">
      <div class="yjb-panel-title">养基宝持仓</div>
      <div class="yjb-panel-actions">
        <button class="icon-btn" @click="yjbStore.loadAllData()" title="刷新数据">
          <RefreshCw :size="16" />
        </button>
        <button class="icon-btn" @click="handleLogout" title="退出登录">
          <DoorOpen :size="16" />
        </button>
        <button class="icon-btn" @click="handleClose" title="关闭">
          <X :size="18" />
        </button>
      </div>
    </div>

    <div class="yjb-panel-body">
      <div v-if="yjbStore.loading" class="panel-loading">
        <Loader2 :size="32" class="spin" />
        <span>加载中...</span>
      </div>
      <template v-else>
        <IndexQuotes :data="yjbStore.indexData" />
        <AccountSummary
          :data="yjbStore.accountCollect"
          :accounts="yjbStore.accounts"
          :selected-account-id="yjbStore.selectedAccountId"
          @switch-account="yjbStore.switchAccount"
        />
        <FundHoldings :data="yjbStore.fundHoldings" />
      </template>
    </div>
  </div>
</template>

<style scoped>
.yjb-panel {
  width: 420px;
  height: 100%;
  background: var(--surface);
  border-left: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  flex-shrink: 0;
}

.yjb-panel-header {
  padding: 16px 20px;
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.yjb-panel-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text);
}

.yjb-panel-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.icon-btn {
  background: none;
  border: none;
  color: var(--text-dim);
  cursor: pointer;
  padding: 6px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.icon-btn:hover {
  background: var(--surface-2);
  color: var(--text);
}

.yjb-panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
}

.panel-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 60px 0;
  color: var(--text-dim);
}

.spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
