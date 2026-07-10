import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  getAnalysisList,
  getAnalysisDetail,
  startAnalysis as apiStartAnalysis,
  deleteAnalysis as apiDeleteAnalysis,
  cancelAnalysis as apiCancelAnalysis,
  streamAnalysis,
  type AnalysisRecord,
  type WorkflowEvent,
} from '@/api/analysis'
import { useAuthStore } from './auth'

export const useAnalysisStore = defineStore('analysis', () => {
  const analyses = ref<AnalysisRecord[]>([])
  const selectedId = ref<number | null>(null)
  const selectedDetail = ref<AnalysisRecord | null>(null)
  const isRunning = ref(false)
  const workflowEvents = ref<WorkflowEvent[]>([])
  const loading = ref(false)
  const detailLoading = ref(false)
  const panelVisible = ref(false)

  let abortController: AbortController | null = null

  async function loadAnalyses() {
    loading.value = true
    try {
      analyses.value = await getAnalysisList()
    } finally {
      loading.value = false
    }
  }

  async function selectAnalysis(id: number, reconnect = true) {
    selectedId.value = id
    detailLoading.value = true
    try {
      selectedDetail.value = await getAnalysisDetail(id)
    } catch {
      selectedDetail.value = null
    }

    // 连接 SSE 流回放事件（已完成的分析从数据库重建事件）
    if (reconnect) {
      if (abortController) {
        abortController.abort()
        abortController = null
      }
      workflowEvents.value = []
      const authStore = useAuthStore()
      abortController = streamAnalysis(id, authStore.token, {
        onEvent(event) {
          workflowEvents.value.push(event)
        },
        onDone() {
          abortController = null
          detailLoading.value = false
        },
        onError() {
          abortController = null
          detailLoading.value = false
        },
      })
    } else {
      detailLoading.value = false
    }
  }

  async function handleStartAnalysis(query: string) {
    const authStore = useAuthStore()
    const result = await apiStartAnalysis(query)

    // 立即在列表中添加一条 RUNNING 记录
    const newRecord: AnalysisRecord = {
      id: result.analysisId,
      userId: 0,
      conversationId: null,
      originalQuery: query,
      resolvedStockCode: result.stockCode,
      resolvedStockName: result.stockName,
      workflowStatus: 'RUNNING',
      action: null,
      confidence: null,
      targetPrice: null,
      summary: null,
      tradingProposal: null,
      investmentPlan: null,
      analystReportsJson: null,
      bullBearDebateJson: null,
      riskDebateJson: null,
      startedAt: new Date().toISOString(),
      completedAt: null,
      errorMessage: null,
      createdAt: new Date().toISOString(),
    }
    analyses.value.unshift(newRecord)
    selectedId.value = result.analysisId
    selectedDetail.value = newRecord
    isRunning.value = true
    workflowEvents.value = []

    // 建立 SSE 连接
    abortController = streamAnalysis(result.analysisId, authStore.token, {
      onEvent(event) {
        workflowEvents.value.push(event)
      },
      onDone() {
        isRunning.value = false
        abortController = null
        // 刷新列表和详情，不重连 SSE（事件已在 workflowEvents 中）
        loadAnalyses()
        selectAnalysis(result.analysisId, false)
      },
      onError(msg) {
        isRunning.value = false
        abortController = null
        console.error('分析流错误:', msg)
      },
    })

    return result
  }

  async function handleDeleteAnalysis(id: number) {
    await apiDeleteAnalysis(id)
    analyses.value = analyses.value.filter((a) => a.id !== id)
    if (selectedId.value === id) {
      selectedId.value = null
      selectedDetail.value = null
    }
  }

  async function stopAnalysis() {
    const id = selectedId.value
    // 中断前端 SSE 连接
    if (abortController) {
      abortController.abort()
      abortController = null
    }
    isRunning.value = false
    workflowEvents.value = []
    // 调用后端取消接口
    if (id) {
      try {
        await apiCancelAnalysis(id)
      } catch (e) {
        console.error('取消分析失败:', e)
      }
      // 同步前端状态
      const record = analyses.value.find((a) => a.id === id)
      if (record && record.workflowStatus === 'RUNNING') {
        record.workflowStatus = 'CANCELLED'
        record.errorMessage = '用户手动停止'
      }
      if (selectedDetail.value && selectedDetail.value.workflowStatus === 'RUNNING') {
        selectedDetail.value.workflowStatus = 'CANCELLED'
        selectedDetail.value.errorMessage = '用户手动停止'
      }
    }
  }

  function togglePanel() {
    panelVisible.value = !panelVisible.value
    if (panelVisible.value) {
      loadAnalyses()
    }
  }

  return {
    analyses,
    selectedId,
    selectedDetail,
    isRunning,
    workflowEvents,
    loading,
    detailLoading,
    panelVisible,
    loadAnalyses,
    selectAnalysis,
    handleStartAnalysis,
    handleDeleteAnalysis,
    stopAnalysis,
    togglePanel,
  }
})
