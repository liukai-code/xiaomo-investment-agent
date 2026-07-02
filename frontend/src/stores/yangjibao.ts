import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  getUserAccounts,
  getAccountCollect,
  getFundHoldings,
  getIndexData,
} from '@/api/yangjibao'
import type { UserAccount, AccountCollect, FundHoldItem, IndexData } from '@/types/yangjibao'

const TOKEN_KEY = 'yjb_token'

export const useYangjibaoStore = defineStore('yangjibao', () => {
  const yjbToken = ref(localStorage.getItem(TOKEN_KEY) || '')
  const isLoggedIn = computed(() => !!yjbToken.value)

  const accounts = ref<UserAccount[]>([])
  const selectedAccountId = ref('')
  const accountCollect = ref<AccountCollect | null>(null)
  const fundHoldings = ref<FundHoldItem[]>([])
  const indexData = ref<IndexData[]>([])

  const loading = ref(false)
  const panelVisible = ref(false)
  const qrModalVisible = ref(false)

  function setYjbToken(token: string) {
    yjbToken.value = token
    localStorage.setItem(TOKEN_KEY, token)
  }

  function clearYjbAuth() {
    yjbToken.value = ''
    accounts.value = []
    selectedAccountId.value = ''
    accountCollect.value = null
    fundHoldings.value = []
    indexData.value = []
    localStorage.removeItem(TOKEN_KEY)
  }

  function logout() {
    clearYjbAuth()
    panelVisible.value = false
  }

  async function openPanel() {
    if (panelVisible.value) {
      panelVisible.value = false
      return
    }
    if (isLoggedIn.value) {
      panelVisible.value = true
      if (fundHoldings.value.length === 0) {
        await loadAllData()
      }
    } else {
      qrModalVisible.value = true
    }
  }

  async function onQrLoginSuccess(token: string) {
    console.log('[YJB] 登录成功, token:', token)
    setYjbToken(token)
    qrModalVisible.value = false
    panelVisible.value = true
    await loadAllData()
  }

  async function loadAllData() {
    loading.value = true
    try {
      const [idxData, acctList] = await Promise.all([
        getIndexData(),
        getUserAccounts(),
      ])
      indexData.value = idxData
      accounts.value = acctList
      if (acctList.length > 0 && !selectedAccountId.value) {
        selectedAccountId.value = acctList[0].id
      }
      await loadAccountData()
    } catch (err) {
      console.error('养基宝数据加载失败', err)
    } finally {
      loading.value = false
    }
  }

  async function loadAccountData() {
    if (!selectedAccountId.value) return
    try {
      const [collect, holdings] = await Promise.all([
        getAccountCollect(),
        getFundHoldings(selectedAccountId.value),
      ])
      accountCollect.value = collect
      fundHoldings.value = holdings
    } catch (err) {
      console.error('持仓数据加载失败', err)
    }
  }

  async function switchAccount(accountId: string) {
    selectedAccountId.value = accountId
    await loadAccountData()
  }

  return {
    yjbToken, isLoggedIn, accounts, selectedAccountId,
    accountCollect, fundHoldings, indexData,
    loading, panelVisible, qrModalVisible,
    openPanel, onQrLoginSuccess, logout,
    loadAllData, loadAccountData, switchAccount, clearYjbAuth,
  }
})
