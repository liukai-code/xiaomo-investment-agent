<script setup lang="ts">
import { onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const authStore = useAuthStore()

// 应用启动时校验登录状态并同步免费额度
onMounted(() => {
  authStore.checkAuth()
})

// Portal 路由需要解除 body 的 overflow: hidden
watch(
  () => route.name,
  (name) => {
    document.body.classList.toggle('portal-scrollable', name === 'portal')
  },
  { immediate: true }
)
</script>

<template>
  <RouterView />
</template>
