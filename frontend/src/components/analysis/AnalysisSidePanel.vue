<script setup lang="ts">
import { ref, computed } from 'vue'
import { Brain, X, ChevronDown, Square } from 'lucide-vue-next'
import { useAnalysisStore } from '@/stores/analysis'
import AnalysisInput from '@/components/analysis/AnalysisInput.vue'
import AnalysisList from '@/components/analysis/AnalysisList.vue'
import AnalysisDetail from '@/components/analysis/AnalysisDetail.vue'

const analysisStore = useAnalysisStore()
const listCollapsed = ref(false)

const showStopBtn = computed(() =>
  analysisStore.isRunning || analysisStore.selectedDetail?.workflowStatus === 'RUNNING'
)

function handleSelect(id: number) {
  analysisStore.selectAnalysis(id)
  listCollapsed.value = true
}

function handleBack() {
  analysisStore.selectedId = null
  analysisStore.selectedDetail = null
  listCollapsed.value = false
}
</script>

<template>
  <div class="analysis-side-panel">
    <!-- 面板头部 -->
    <div class="panel-header">
      <div class="panel-title">
        <Brain :size="16" />
        <span>个股深度分析</span>
      </div>
      <div class="panel-actions">
        <button
          v-if="showStopBtn"
          class="panel-stop-btn"
          @click="analysisStore.stopAnalysis()"
          title="停止分析"
        >
          <Square :size="14" />
          <span>停止</span>
        </button>
        <button class="panel-close-btn" @click="analysisStore.panelVisible = false">
          <X :size="16" />
        </button>
      </div>
    </div>

    <!-- 分析输入 -->
    <AnalysisInput
      :is-running="showStopBtn"
      @submit="analysisStore.handleStartAnalysis"
    />

    <!-- 分析列表（可折叠） -->
    <div class="panel-list-section">
      <div class="list-toggle" @click="listCollapsed = !listCollapsed">
        <span>分析记录 ({{ analysisStore.analyses.length }})</span>
        <ChevronDown :size="14" :class="{ rotated: listCollapsed }" />
      </div>
      <Transition name="list-collapse">
        <div v-if="!listCollapsed" class="list-body">
          <AnalysisList
            :analyses="analysisStore.analyses"
            :selected-id="analysisStore.selectedId"
            :loading="analysisStore.loading"
            @select="handleSelect"
            @delete="analysisStore.handleDeleteAnalysis"
          />
        </div>
      </Transition>
    </div>

    <!-- 分析详情 -->
    <AnalysisDetail
      :detail="analysisStore.selectedDetail"
      :events="analysisStore.workflowEvents"
      :is-running="analysisStore.isRunning"
      :detail-loading="analysisStore.detailLoading"
      @back="handleBack"
    />
  </div>
</template>

<style scoped>
.analysis-side-panel {
  width: 720px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  background: var(--bg, #f8fafc);
  border-left: 1px solid var(--border, #e2e8f0);
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border, #e2e8f0);
  background: var(--surface, #ffffff);
  flex-shrink: 0;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text, #1e293b);
}

.panel-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.panel-stop-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border: 1px solid var(--danger, #dc2626);
  background: none;
  color: var(--danger, #dc2626);
  border-radius: 6px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
}

.panel-stop-btn:hover {
  background: #fef2f2;
}

.panel-close-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  background: none;
  color: var(--text-dim, #94a3b8);
  cursor: pointer;
  border-radius: 6px;
  transition: all 0.15s;
}

.panel-close-btn:hover {
  background: var(--surface-2, #f1f5f9);
  color: var(--text, #1e293b);
}

.panel-list-section {
  border-bottom: 1px solid var(--border, #e2e8f0);
  flex-shrink: 0;
}

.list-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  font-size: 13px;
  font-weight: 500;
  color: var(--text, #1e293b);
  cursor: pointer;
  user-select: none;
  transition: background 0.15s;
}

.list-toggle:hover {
  background: var(--surface-2, #f1f5f9);
}

.list-toggle .rotated {
  transform: rotate(-90deg);
}

.list-toggle svg {
  transition: transform 0.2s ease;
  color: var(--text-dim, #94a3b8);
}

.list-body {
  max-height: 240px;
  overflow-y: auto;
}
.list-collapse-enter-active { transition: all 0.25s ease-out; }
.list-collapse-leave-active { transition: all 0.2s ease-in; }
.list-collapse-enter-from,
.list-collapse-leave-to { opacity: 0; max-height: 0; }
.list-collapse-enter-to,
.list-collapse-leave-from { opacity: 1; max-height: 240px; }
</style>
