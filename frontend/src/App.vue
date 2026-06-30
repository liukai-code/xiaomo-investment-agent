<script setup lang="ts">
import { onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useRouter } from 'vue-router'

const authStore = useAuthStore()
const router = useRouter()

onMounted(async () => {
  const ok = await authStore.checkAuth()
  if (!ok) {
    router.push('/login')
  }
})
</script>

<template>
  <RouterView />
</template>
