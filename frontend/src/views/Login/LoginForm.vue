<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

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
  <div class="login-card">
    <div class="login-header">
      <h2 class="login-title">{{ activeTab === 'login' ? '欢迎回来' : '创建账户' }}</h2>
      <p class="login-desc">{{ activeTab === 'login' ? '登录您的账户以继续' : '注册一个新账户' }}</p>
    </div>

    <Transition name="login-form" mode="out-in">
      <form v-if="activeTab === 'login'" key="login" class="login-form" @submit.prevent="handleLogin">
        <div class="login-field">
          <input
            v-model="loginUsername"
            type="text"
            :class="{ 'has-value': loginUsername.length > 0 }"
            autocomplete="username"
            required
          />
          <label>用户名</label>
        </div>
        <div class="login-field">
          <input
            v-model="loginPassword"
            type="password"
            :class="{ 'has-value': loginPassword.length > 0 }"
            autocomplete="current-password"
            required
          />
          <label>密码</label>
        </div>
        <div :class="loginMsgType === 'success' ? 'login-success' : 'login-error'">{{ loginError }}</div>
        <button type="submit" class="login-submit" :disabled="authStore.loading">
          <span v-if="authStore.loading" class="spinner"></span>
          {{ authStore.loading ? '登录中...' : '登 录' }}
        </button>
      </form>

      <form v-else key="register" class="login-form" @submit.prevent="handleRegister">
        <div class="login-field">
          <input
            v-model="regUsername"
            type="text"
            :class="{ 'has-value': regUsername.length > 0 }"
            autocomplete="username"
            required
          />
          <label>用户名</label>
        </div>
        <div class="login-field">
          <input
            v-model="regPassword"
            type="password"
            :class="{ 'has-value': regPassword.length > 0 }"
            autocomplete="new-password"
            required
          />
          <label>密码 (至少6位)</label>
        </div>
        <div class="login-field">
          <input
            v-model="regPassword2"
            type="password"
            :class="{ 'has-value': regPassword2.length > 0 }"
            autocomplete="new-password"
            required
          />
          <label>确认密码</label>
        </div>
        <div class="login-error">{{ regError }}</div>
        <button type="submit" class="login-submit" :disabled="authStore.loading">
          <span v-if="authStore.loading" class="spinner"></span>
          {{ authStore.loading ? '注册中...' : '注 册' }}
        </button>
      </form>
    </Transition>

    <div class="login-switch">
      <template v-if="activeTab === 'login'">
        没有账户？<a @click="switchTab('register')">立即注册</a>
      </template>
      <template v-else>
        已有账户？<a @click="switchTab('login')">立即登录</a>
      </template>
    </div>
  </div>
</template>

<style scoped>
.login-card {
  width: 100%;
  max-width: 400px;
  background: rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 20px;
  box-shadow: 0 25px 50px rgba(0, 0, 0, 0.4);
  padding: 40px;
  animation: loginFadeSlideUp 0.6s ease-out 0.6s both;
}

.login-header {
  margin-bottom: 32px;
  text-align: center;
}

.login-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--login-text);
  margin: 0 0 6px;
}

.login-desc {
  font-size: 14px;
  color: var(--login-text-dim);
  margin: 0;
}

.login-form {
  display: flex;
  flex-direction: column;
}

.login-field {
  position: relative;
  margin-bottom: 20px;
}

.login-field input {
  width: 100%;
  padding: 24px 16px 8px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 10px;
  color: var(--login-text);
  font-size: 14px;
  font-family: 'Inter', 'Noto Sans SC', sans-serif;
  outline: none;
  transition: border-color 0.2s, box-shadow 0.2s;
  box-sizing: border-box;
}

.login-field input:focus {
  border-color: var(--login-accent);
  box-shadow: 0 0 0 3px var(--login-accent-glow);
}

.login-field label {
  position: absolute;
  left: 16px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 14px;
  color: rgba(255, 255, 255, 0.4);
  pointer-events: none;
  transition: all 0.2s ease;
}

.login-field input:focus + label,
.login-field input.has-value + label {
  top: 6px;
  transform: translateY(0);
  font-size: 11px;
  color: var(--login-accent);
}

/* Submit button */
.login-submit {
  position: relative;
  width: 100%;
  padding: 14px;
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  color: white;
  border: none;
  border-radius: 10px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
  margin-top: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  overflow: hidden;
}

.login-submit::after {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 60%;
  height: 100%;
  background: linear-gradient(
    90deg,
    transparent,
    rgba(255, 255, 255, 0.15),
    transparent
  );
  pointer-events: none;
}

.login-submit:hover:not(:disabled) {
  filter: brightness(1.1);
  box-shadow: 0 4px 20px var(--login-accent-glow);
}

.login-submit:hover:not(:disabled)::after {
  animation: loginSweepLight 0.6s ease-out;
}

.login-submit:active:not(:disabled) {
  transform: scale(0.98);
}

.login-submit:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.login-submit .spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: loginSpin 0.6s linear infinite;
}

/* Messages */
.login-error {
  color: var(--login-danger);
  font-size: 13px;
  min-height: 20px;
  margin-bottom: 4px;
  animation: loginFadeSlideUp 0.2s ease;
}

.login-success {
  color: var(--login-success);
  font-size: 13px;
  min-height: 20px;
  margin-bottom: 4px;
  animation: loginFadeSlideUp 0.2s ease;
}

/* Tab switch */
.login-switch {
  text-align: center;
  margin-top: 24px;
  font-size: 14px;
  color: var(--login-text-dim);
}

.login-switch a {
  color: var(--login-accent);
  text-decoration: none;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.15s;
}

.login-switch a:hover {
  opacity: 0.8;
}

/* Form transition */
.login-form-enter-active,
.login-form-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.login-form-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.login-form-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

@media (max-width: 768px) {
  .login-card {
    max-width: 100%;
    border-radius: 16px;
    padding: 24px;
  }
}
</style>
