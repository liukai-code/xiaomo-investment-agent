import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  getIndexData,
  syncHoldings,
  saveYjbToken,
  checkYjbStatus,
  getFundValuations,
  getDayInfo,
} from '@/api/yangjibao'
import type { UserAccount, AccountCollect, FundHoldItem, IndexData, FundValuation, DayInfo } from '@/types/yangjibao'

export const useYangjibaoStore = defineStore('yangjibao', () => {
  const yjbLoggedIn = ref(false)

  const accounts = ref<UserAccount[]>([])
  const selectedAccountId = ref('')
  const accountCollect = ref<AccountCollect | null>(null)
  const fundHoldings = ref<FundHoldItem[]>([])
  const fundValuations = ref<FundValuation[]>([])
  const indexData = ref<IndexData[]>([])
  const dayInfo = ref<DayInfo | null>(null)

  const loading = ref(false)
  const cardVisible = ref(false)
  const qrModalVisible = ref(false)

  function clearYjbAuth() {
    yjbLoggedIn.value = false
    accounts.value = []
    selectedAccountId.value = ''
    accountCollect.value = null
    fundHoldings.value = []
    fundValuations.value = []
    indexData.value = []
    dayInfo.value = null
  }

  function logout() {
    clearYjbAuth()
    cardVisible.value = false
  }

  async function checkLogin() {
    try {
      const status = await checkYjbStatus()
      yjbLoggedIn.value = status.loggedIn
    } catch {
      yjbLoggedIn.value = false
    }
  }

  async function openCard() {
    if (cardVisible.value) {
      cardVisible.value = false
      return
    }
    await checkLogin()
    if (yjbLoggedIn.value) {
      cardVisible.value = true
      loadAllData()
    } else {
      clearYjbAuth()
      qrModalVisible.value = true
    }
  }

  async function onQrLoginSuccess(token: string) {
    console.log('[YJB] 登录成功, token:', token)
    try {
      await saveYjbToken(token)
      yjbLoggedIn.value = true
    } catch (err) {
      console.error('[YJB] 保存 token 失败', err)
      return
    }
    qrModalVisible.value = false
    cardVisible.value = true
    await loadAllData()
  }

  async function loadAllData() {
    loading.value = true
    accounts.value = []
    accountCollect.value = null
    fundHoldings.value = []
    fundValuations.value = []
    indexData.value = []
    dayInfo.value = null
    try {
      const [idxData, syncResult] = await Promise.all([
        getIndexData(),
        syncHoldings(),
      ])
      indexData.value = idxData
      accounts.value = syncResult.accounts
      accountCollect.value = syncResult.accountCollect
      fundHoldings.value = syncResult.holdings
      if (syncResult.accounts.length > 0 && !selectedAccountId.value) {
        selectedAccountId.value = syncResult.selectedAccountId || syncResult.accounts[0].id
      }
      // 持仓加载后，异步获取基金估值（不阻塞主流程）
      const fundIds = syncResult.holdings.map(h => h.fund_id)
      getFundValuations(fundIds).then(v => { fundValuations.value = v })

      // 行情中心数据（不阻塞主流程）
      getDayInfo().then(info => { dayInfo.value = info })
    } catch (err) {
      console.error('养基宝数据加载失败', err)
    } finally {
      loading.value = false
    }
  }

  async function loadAccountData() {
    if (!selectedAccountId.value) return
    try {
      const syncResult = await syncHoldings(selectedAccountId.value)
      accountCollect.value = syncResult.accountCollect
      fundHoldings.value = syncResult.holdings
      // 异步获取基金估值
      const fundIds = syncResult.holdings.map(h => h.fund_id)
      getFundValuations(fundIds).then(v => { fundValuations.value = v })
    } catch (err) {
      console.error('持仓数据加载失败', err)
    }
  }

  async function switchAccount(accountId: string) {
    selectedAccountId.value = accountId
    await loadAccountData()
  }

  return {
    yjbLoggedIn, accounts, selectedAccountId,
    accountCollect, fundHoldings, fundValuations, indexData, dayInfo,
    loading, cardVisible, qrModalVisible,
    openCard, onQrLoginSuccess, logout,
    loadAllData, loadAccountData, switchAccount, clearYjbAuth, checkLogin,
  }
})
