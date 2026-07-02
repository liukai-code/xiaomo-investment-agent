<script setup lang="ts">
</script>

<template>
  <div class="gradient-bg"></div>
</template>

<style scoped>
.gradient-bg {
  position: fixed;
  inset: 0;
  z-index: 0;
  background:
    radial-gradient(circle at 20% 30%, rgba(26, 95, 255, 0.18), transparent 50%),
    radial-gradient(circle at 70% 60%, rgba(0, 212, 255, 0.12), transparent 45%),
    radial-gradient(circle at 50% 80%, rgba(124, 58, 237, 0.1), transparent 40%),
    linear-gradient(135deg, var(--login-bg-primary), var(--login-bg-secondary), var(--login-bg-tertiary));
  animation: loginFadeIn 0.8s ease-out 0s both;
}
</style>
