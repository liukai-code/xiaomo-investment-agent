<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import GalaxyBackground from '@/components/GalaxyBackground.vue'

const router = useRouter()
const authStore = useAuthStore()
const themeStore = useThemeStore()

const activeTab = ref<'login' | 'register'>('login')
const loginUsername = ref('')
const loginPassword = ref('')
const loginError = ref('')
const regUsername = ref('')
const regPassword = ref('')
const regPassword2 = ref('')
const regError = ref('')

function switchTab(tab: 'login' | 'register') {
  activeTab.value = tab
  loginError.value = ''
  regError.value = ''
}

async function handleLogin() {
  loginError.value = ''
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
    loginError.value = '注册成功，请登录'
  } else {
    regError.value = result.msg || '注册失败'
  }
}
</script>

<template>
  <div class="auth-page">
    <GalaxyBackground :dark="!themeStore.isLight" />
    <div class="auth-container">
      <div class="auth-header">
        <div class="auth-logo">&gt;_ TERMINAL</div>
        <button class="auth-theme" @click="themeStore.toggle()" :title="themeStore.isLight ? '深色模式' : '浅色模式'">
          {{ themeStore.isLight ? '☾' : '☀' }}
        </button>
      </div>
      <div class="auth-tabs">
        <button
          class="auth-tab"
          :class="{ active: activeTab === 'login' }"
          @click="switchTab('login')"
        >
          LOGIN
        </button>
        <button
          class="auth-tab"
          :class="{ active: activeTab === 'register' }"
          @click="switchTab('register')"
        >
          REGISTER
        </button>
      </div>

      <form v-if="activeTab === 'login'" class="auth-form" @submit.prevent="handleLogin">
        <input v-model="loginUsername" type="text" placeholder="用户名" autocomplete="username" required />
        <input v-model="loginPassword" type="password" placeholder="密码" autocomplete="current-password" required />
        <div class="auth-error">{{ loginError }}</div>
        <button type="submit" class="auth-submit">LOGIN</button>
      </form>

      <form v-else class="auth-form" @submit.prevent="handleRegister">
        <input v-model="regUsername" type="text" placeholder="用户名" autocomplete="username" required />
        <input v-model="regPassword" type="password" placeholder="密码 (至少6位)" autocomplete="new-password" required />
        <input v-model="regPassword2" type="password" placeholder="确认密码" autocomplete="new-password" required />
        <div class="auth-error">{{ regError }}</div>
        <button type="submit" class="auth-submit">REGISTER</button>
      </form>
    </div>
  </div>
</template>
