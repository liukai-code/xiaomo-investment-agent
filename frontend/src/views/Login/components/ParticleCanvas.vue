<script setup lang="ts">
import { ref } from 'vue'
import { useParticles } from '@/composables/useParticles'

const containerRef = ref<HTMLElement>()
useParticles(containerRef, { particleCount: 80, connectionDistance: 150 })
</script>

<template>
  <div ref="containerRef" class="particle-layer"></div>
</template>

<style scoped>
.particle-layer {
  position: fixed;
  inset: 0;
  z-index: 3;
  pointer-events: none;
  animation: loginFadeIn 1s ease-out 1.0s both;
}

@media (max-width: 768px) {
  .particle-layer {
    opacity: 0.6;
  }
}
</style>
