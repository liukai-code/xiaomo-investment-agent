<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { getQrCode, getQrCodeState } from '@/api/yangjibao'
import { X, Loader2 } from 'lucide-vue-next'

const props = defineProps<{ visible: boolean }>()
const emit = defineEmits<{
  success: [token: string]
  close: []
}>()

const qrUrl = ref('')
const qrId = ref('')
const loading = ref(true)
const error = ref('')
let timer: ReturnType<typeof setInterval> | null = null

const qrImgSrc = computed(() => {
  if (!qrUrl.value) return ''
  return `https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=${encodeURIComponent(qrUrl.value)}`
})

async function fetchQr() {
  loading.value = true
  error.value = ''
  qrUrl.value = ''
  qrId.value = ''
  try {
    const qr = await getQrCode()
    qrUrl.value = qr.url
    qrId.value = qr.id
    loading.value = false

    timer = setInterval(async () => {
      try {
        const state = await getQrCodeState(qrId.value)
        console.log('[YJB] 扫码状态:', state)
        if (state.state === 2 && state.token) {
          console.log('[YJB] 扫码成功, token:', state.token)
          stopPolling()
          emit('success', state.token)
        }
      } catch (e) {
        console.error('[YJB] 轮询异常:', e)
      }
    }, 2000)
  } catch (e: any) {
    error.value = '获取二维码失败，请重试'
    loading.value = false
  }
}

function stopPolling() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

watch(() => props.visible, (v) => {
  if (v) fetchQr()
  else stopPolling()
})

onMounted(() => {
  if (props.visible) fetchQr()
})

onUnmounted(() => {
  stopPolling()
})

function handleClose() {
  stopPolling()
  emit('close')
}
</script>

<template>
  <Transition name="qr-fade">
    <div v-if="visible" class="yjb-qr-popover" @click.stop>
      <div class="popover-header">
        <span class="popover-title">扫码登录养基宝</span>
        <button class="close-btn" @click="handleClose">
          <X :size="16" />
        </button>
      </div>
      <div class="popover-body">
        <div v-if="loading" class="qr-loading">
          <Loader2 :size="32" class="spin" />
          <span>获取二维码中...</span>
        </div>
        <div v-else-if="error" class="qr-error">{{ error }}</div>
        <template v-else>
          <img :src="qrImgSrc" alt="登录二维码" class="qr-img" />
          <div class="qr-tip">请使用微信扫码登录</div>
        </template>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
.yjb-qr-popover {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  z-index: 1000;
  width: 280px;
  background: var(--sidebar-bg);
  border: 1px solid var(--border);
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.25);
  overflow: hidden;
}

.popover-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border);
}

.popover-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
}

.close-btn {
  background: none;
  border: none;
  color: var(--text-dim);
  cursor: pointer;
  padding: 4px;
  border-radius: 6px;
}

.close-btn:hover {
  background: var(--surface-2);
  color: var(--text);
}

.popover-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px 16px;
}

.qr-img {
  width: 180px;
  height: 180px;
  border-radius: 8px;
}

.qr-tip {
  margin-top: 12px;
  font-size: 13px;
  color: var(--text-dim);
}

.qr-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 32px 0;
  color: var(--text-dim);
}

.qr-error {
  padding: 32px 0;
  color: var(--red);
  font-size: 14px;
}

.spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* 淡入淡出动画 */
.qr-fade-enter-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}

.qr-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.qr-fade-enter-from,
.qr-fade-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

.qr-fade-enter-to,
.qr-fade-leave-from {
  opacity: 1;
  transform: translateY(0);
}
</style>
