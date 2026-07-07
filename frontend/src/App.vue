<script setup lang="ts">
import { watch } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

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
