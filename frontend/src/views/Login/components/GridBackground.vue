<script setup lang="ts">
</script>

<template>
  <div class="grid-bg"></div>
</template>

<style scoped>
.grid-bg {
  position: fixed;
  inset: 0;
  z-index: 1;
  opacity: 0.08;
  pointer-events: none;
  background:
    repeating-linear-gradient(0deg, rgba(255, 255, 255, 0.04) 0px, transparent 1px, transparent 80px),
    repeating-linear-gradient(90deg, rgba(255, 255, 255, 0.04) 0px, transparent 1px, transparent 80px);
  animation: loginFadeIn 1s ease-out 0.2s both;
}

@media (max-width: 768px) {
  .grid-bg {
    opacity: 0.04;
  }
}
</style>
