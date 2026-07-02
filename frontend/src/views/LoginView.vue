<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useFinanceChartCanvas } from '@/composables/useFinanceChartCanvas'

const router = useRouter()
const authStore = useAuthStore()

const brandPanel = ref<HTMLElement>()
useFinanceChartCanvas(brandPanel)

const activeTab = ref<'login' | 'register'>('login')
const loginUsername = ref('')
const loginPassword = ref('')
const loginError = ref('')
const loginMsgType = ref<'error' | 'success'>('error')
const regUsername = ref('')
const regPassword = ref('')
const regPassword2 = ref('')
const regError = ref('')

function switchTab(tab: 'login' | 'register') {
  activeTab.value = tab
  loginError.value = ''
  loginMsgType.value = 'error'
  regError.value = ''
}

async function handleLogin() {
  loginError.value = ''
  loginMsgType.value = 'error'
  const result = await authStore.login(loginUsername.value.trim(), loginPassword.value)
  if (result.success) {
    router.push('/')
  } else {
    loginError.value = result.msg || '登录失败'
  }
}

async function handleRegister() {
  regError.value = ''
  if (regPassword.value !== regPassword2.value) {
    regError.value = '两次密码不一致'
    return
  }
  const result = await authStore.register(regUsername.value.trim(), regPassword.value)
  if (result.success) {
    switchTab('login')
    loginUsername.value = regUsername.value.trim()
    loginMsgType.value = 'success'
    loginError.value = '注册成功，请登录'
  } else {
    regError.value = result.msg || '注册失败'
  }
}
</script>

<template>
  <div class="auth-page">
    <!-- 左栏品牌区 -->
    <div ref="brandPanel" class="auth-brand">
      <div class="auth-brand-content">
        <h1 class="auth-brand-title">小墨</h1>
        <p class="auth-brand-subtitle">金融投资 AI 助手</p>
        <div class="auth-brand-features">
          <div class="auth-brand-feature">
            <span class="auth-brand-feature-icon">⚡</span>
            <span>AI Agent 对话，智能问答交互</span>
          </div>
          <div class="auth-brand-feature">
            <span class="auth-brand-feature-icon">📊</span>
            <span>实时行情分析，数据驱动决策</span>
          </div>
          <div class="auth-brand-feature">
            <span class="auth-brand-feature-icon">🎯</span>
            <span>智能投资决策，专业量化工具</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 右栏表单区 -->
    <div class="auth-form-panel">
      <div class="auth-form-container">
        <div class="auth-form-header">
          <h2 class="auth-form-title">{{ activeTab === 'login' ? '欢迎回来' : '创建账户' }}</h2>
          <p class="auth-form-desc">{{ activeTab === 'login' ? '登录您的账户以继续' : '注册一个新账户' }}</p>
        </div>

        <Transition name="auth-form" mode="out-in">
          <form v-if="activeTab === 'login'" key="login" class="auth-form" @submit.prevent="handleLogin">
            <div class="auth-field">
              <input
                v-model="loginUsername"
                type="text"
                :class="{ 'has-value': loginUsername.length > 0 }"
                autocomplete="username"
                required
              />
              <label>用户名</label>
            </div>
            <div class="auth-field">
              <input
                v-model="loginPassword"
                type="password"
                :class="{ 'has-value': loginPassword.length > 0 }"
                autocomplete="current-password"
                required
              />
              <label>密码</label>
            </div>
            <div :class="loginMsgType === 'success' ? 'auth-success' : 'auth-error'">{{ loginError }}</div>
            <button type="submit" class="auth-submit" :disabled="authStore.loading">
              <span v-if="authStore.loading" class="spinner"></span>
              {{ authStore.loading ? '登录中...' : '登 录' }}
            </button>
          </form>

          <form v-else key="register" class="auth-form" @submit.prevent="handleRegister">
            <div class="auth-field">
              <input
                v-model="regUsername"
                type="text"
                :class="{ 'has-value': regUsername.length > 0 }"
                autocomplete="username"
                required
              />
              <label>用户名</label>
            </div>
            <div class="auth-field">
              <input
                v-model="regPassword"
                type="password"
                :class="{ 'has-value': regPassword.length > 0 }"
                autocomplete="new-password"
                required
              />
              <label>密码 (至少6位)</label>
            </div>
            <div class="auth-field">
              <input
                v-model="regPassword2"
                type="password"
                :class="{ 'has-value': regPassword2.length > 0 }"
                autocomplete="new-password"
                required
              />
              <label>确认密码</label>
            </div>
            <div class="auth-error">{{ regError }}</div>
            <button type="submit" class="auth-submit" :disabled="authStore.loading">
              <span v-if="authStore.loading" class="spinner"></span>
              {{ authStore.loading ? '注册中...' : '注 册' }}
            </button>
          </form>
        </Transition>

        <div class="auth-switch">
          <template v-if="activeTab === 'login'">
            没有账户？<a @click="switchTab('register')">立即注册</a>
          </template>
          <template v-else>
            已有账户？<a @click="switchTab('login')">立即登录</a>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>
