import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as apiLogin, register as apiRegister, logout as apiLogout, getMe, changePassword as apiChangePassword } from '@/api/auth'
import { useChatStore } from './chat'
import { useYangjibaoStore } from './yangjibao'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userId = ref(Number(localStorage.getItem('userId')) || 0)
  const email = ref(localStorage.getItem('email') || '')
  const accountId = ref(localStorage.getItem('accountId') || '')
  const freeTokenQuota = ref(Number(localStorage.getItem('freeTokenQuota')) || 0)
  const freeTokenUsed = ref(Number(localStorage.getItem('freeTokenUsed')) || 0)
  const createdAt = ref(localStorage.getItem('createdAt') || '')
  const isAuthenticated = ref(false)
  const loading = ref(false)

  function setAuth(t: string, uid: number, uemail: string, uaccountId: string) {
    token.value = t
    userId.value = uid
    email.value = uemail
    accountId.value = uaccountId
    isAuthenticated.value = true
    localStorage.setItem('token', t)
    localStorage.setItem('userId', String(uid))
    localStorage.setItem('email', uemail)
    localStorage.setItem('accountId', uaccountId)
  }

  function clearAuth() {
    token.value = ''
    userId.value = 0
    email.value = ''
    accountId.value = ''
    freeTokenQuota.value = 0
    freeTokenUsed.value = 0
    isAuthenticated.value = false
    localStorage.removeItem('token')
    localStorage.removeItem('userId')
    localStorage.removeItem('email')
    localStorage.removeItem('accountId')
    localStorage.removeItem('username')
    localStorage.removeItem('freeTokenQuota')
    localStorage.removeItem('freeTokenUsed')
  }

  async function login(uemail: string, password: string) {
    loading.value = true
    try {
      const res = await apiLogin(uemail, password)
      if (res.code === 1) {
        setAuth(res.data.token, res.data.userId, res.data.email, res.data.accountId)
        // 登录后立即获取免费额度信息
        await checkAuth()
        return { success: true }
      }
      return { success: false, msg: res.msg || '登录失败' }
    } finally {
      loading.value = false
    }
  }

  async function register(uemail: string, password: string) {
    loading.value = true
    try {
      const res = await apiRegister(uemail, password)
      if (res.code === 1) {
        return { success: true }
      }
      return { success: false, msg: res.msg || '注册失败' }
    } finally {
      loading.value = false
    }
  }

  async function logout() {
    try {
      await apiLogout()
    } catch {
      // ignore
    }
    clearAuth()
    const chatStore = useChatStore()
    chatStore.reset()
    const yangjibaoStore = useYangjibaoStore()
    yangjibaoStore.logout()
  }

  async function changePassword(oldPassword: string, newPassword: string) {
    return await apiChangePassword(oldPassword, newPassword)
  }

  async function checkAuth() {
    if (!token.value) {
      isAuthenticated.value = false
      return false
    }
    localStorage.removeItem('username')
    try {
      const res = await getMe()
      if (res.code === 1) {
        isAuthenticated.value = true
        // 同步免费额度信息
        if (res.data.freeTokenQuota !== undefined) {
          freeTokenQuota.value = res.data.freeTokenQuota
          freeTokenUsed.value = res.data.freeTokenUsed || 0
          localStorage.setItem('freeTokenQuota', String(res.data.freeTokenQuota))
          localStorage.setItem('freeTokenUsed', String(res.data.freeTokenUsed || 0))
        }
        if (res.data.createdAt) {
          createdAt.value = res.data.createdAt
          localStorage.setItem('createdAt', res.data.createdAt)
        }
        return true
      }
      clearAuth()
      return false
    } catch {
      clearAuth()
      return false
    }
  }

  return { token, userId, email, accountId, freeTokenQuota, freeTokenUsed, createdAt, isAuthenticated, loading, login, register, logout, changePassword, checkAuth, clearAuth }
})
