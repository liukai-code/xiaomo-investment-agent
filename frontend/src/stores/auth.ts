import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as apiLogin, register as apiRegister, logout as apiLogout, getMe } from '@/api/auth'
import { useChatStore } from './chat'
import { useYangjibaoStore } from './yangjibao'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userId = ref(Number(localStorage.getItem('userId')) || 0)
  const email = ref(localStorage.getItem('email') || '')
  const accountId = ref(localStorage.getItem('accountId') || '')
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
    isAuthenticated.value = false
    localStorage.removeItem('token')
    localStorage.removeItem('userId')
    localStorage.removeItem('email')
    localStorage.removeItem('accountId')
    localStorage.removeItem('username')
  }

  async function login(uemail: string, password: string) {
    loading.value = true
    try {
      const res = await apiLogin(uemail, password)
      if (res.code === 1) {
        setAuth(res.data.token, res.data.userId, res.data.email, res.data.accountId)
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
        return true
      }
      clearAuth()
      return false
    } catch {
      clearAuth()
      return false
    }
  }

  return { token, userId, email, accountId, isAuthenticated, loading, login, register, logout, checkAuth, clearAuth }
})
