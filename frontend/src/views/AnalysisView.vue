<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Brain, MessageSquare, PanelLeftClose, PanelLeftOpen } from 'lucide-vue-next'
import { useAnalysisStore } from '@/stores/analysis'
import AnalysisInput from '@/components/analysis/AnalysisInput.vue'
import AnalysisList from '@/components/analysis/AnalysisList.vue'
import AnalysisDetail from '@/components/analysis/AnalysisDetail.vue'

const router = useRouter()
const analysisStore = useAnalysisStore()
const sidebarCollapsed = ref(false)

onMounted(() => {
  analysisStore.loadAnalyses()
})

function handleStartAnalysis(query: string) {
  analysisStore.handleStartAnalysis(query)
}

function goToChat() {
  router.push('/')
}
</script>

<template>
  <div class="analysis-page">
    <!-- 左侧导航栏（复用 ChatView 的侧边栏风格） -->
    <aside class="sidebar" :class="{ collapsed: sidebarCollapsed }">
      <div class="sidebar-header">
        <div class="sidebar-brand">
          <Brain :size="20" />
          <span v-if="!sidebarCollapsed" class="brand-text">深度分析</span>
        </div>
        <button class="collapse-btn" @click="sidebarCollapsed = !sidebarCollapsed">
          <PanelLeftClose v-if="!sidebarCollapsed" :size="18" />
        </button>
      </div>
      <div v-if="!sidebarCollapsed" class="sidebar-nav">
        <button class="nav-item" @click="goToChat">
          <MessageSquare :size="16" />
          <span>返回对话</span>
        </button>
      </div>
    </aside>

    <!-- 展开按钮（侧边栏折叠时） -->
    <button v-if="sidebarCollapsed" class="expand-btn" @click="sidebarCollapsed = false">
      <PanelLeftOpen :size="18" />
    </button>

    <!-- 主内容区 -->
    <div class="analysis-main">
      <AnalysisInput
        :is-running="analysisStore.isRunning"
        @submit="handleStartAnalysis"
      />
      <div class="analysis-content">
        <AnalysisList
          :analyses="analysisStore.analyses"
          :selected-id="analysisStore.selectedId"
          :loading="analysisStore.loading"
          @select="analysisStore.selectAnalysis"
          @delete="analysisStore.handleDeleteAnalysis"
        />
        <AnalysisDetail
          :detail="analysisStore.selectedDetail"
          :events="analysisStore.workflowEvents"
          :is-running="analysisStore.isRunning"
          @go-to-chat="goToChat"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.analysis-page {
  display: flex;
  height: 100vh;
  background: var(--bg, #f8fafc);
}

/* 侧边栏 - 复用 ChatView 的 sidebar 样式 */
.sidebar {
  width: 220px;
  min-width: 220px;
  background: var(--sidebar-bg, #f8fafc);
  border-right: 1px solid var(--border, #e2e8f0);
  display: flex;
  flex-direction: column;
  transition: width 0.2s, min-width 0.2s;
}
.sidebar.collapsed { width: 0; min-width: 0; overflow: hidden; }
.sidebar-header {
  padding: 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.sidebar-brand { display: flex; align-items: center; gap: 8px; color: var(--text, #1e293b); }
.brand-text { font-size: 15px; font-weight: 600; }
.collapse-btn, .expand-btn {
  background: none;
  border: none;
  color: var(--text-dim, #94a3b8);
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
}
.collapse-btn:hover, .expand-btn:hover { background: var(--sidebar-hover, #f1f5f9); }
.sidebar-nav { padding: 8px 12px; }
.nav-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 12px;
  background: none;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  color: var(--text, #1e293b);
}
.nav-item:hover { background: var(--sidebar-hover, #f1f5f9); }

.expand-btn {
  position: absolute;
  top: 12px;
  left: 8px;
  z-index: 10;
}

.analysis-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.analysis-content {
  flex: 1;
  display: flex;
  overflow: hidden;
}
</style>
