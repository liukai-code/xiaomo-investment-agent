<script setup lang="ts">
import { onMounted } from 'vue'
import { useThemeStore } from '@/stores/theme'
import { useAuthStore } from '@/stores/auth'
import { useRouter } from 'vue-router'

const themeStore = useThemeStore()
const authStore = useAuthStore()
const router = useRouter()

onMounted(async () => {
  themeStore.init()
  const ok = await authStore.checkAuth()
  if (!ok) {
    router.push('/login')
  }
})
</script>

<template>
  <RouterView />
</template>
