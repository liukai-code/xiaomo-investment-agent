import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as apiLogin, register as apiRegister, logout as apiLogout, getMe } from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userId = ref(Number(localStorage.getItem('userId')) || 0)
  const username = ref(localStorage.getItem('username') || '')
  const isAuthenticated = ref(false)

  function setAuth(t: string, uid: number, uname: string) {
    token.value = t
    userId.value = uid
    username.value = uname
    isAuthenticated.value = true
    localStorage.setItem('token', t)
    localStorage.setItem('userId', String(uid))
    localStorage.setItem('username', uname)
  }

  function clearAuth() {
    token.value = ''
    userId.value = 0
    username.value = ''
    isAuthenticated.value = false
    localStorage.removeItem('token')
    localStorage.removeItem('userId')
    localStorage.removeItem('username')
  }

  async function login(uname: string, password: string) {
    const res = await apiLogin(uname, password)
    if (res.code === 1) {
      setAuth(res.data.token, res.data.userId, res.data.username)
      return { success: true }
    }
    return { success: false, msg: res.msg || '登录失败' }
  }

  async function register(uname: string, password: string) {
    const res = await apiRegister(uname, password)
    if (res.code === 1) {
      return { success: true }
    }
    return { success: false, msg: res.msg || '注册失败' }
  }

  async function logout() {
    try {
      await apiLogout()
    } catch {
      // ignore
    }
    clearAuth()
  }

  async function checkAuth() {
    if (!token.value) {
      isAuthenticated.value = false
      return false
    }
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

  return { token, userId, username, isAuthenticated, login, register, logout, checkAuth, clearAuth }
})
