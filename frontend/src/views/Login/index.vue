<script setup lang="ts">
import GradientBackground from './components/GradientBackground.vue'
import GridBackground from './components/GridBackground.vue'
import KLineBackground from './components/KLineBackground.vue'
import ParticleCanvas from './components/ParticleCanvas.vue'
import WaveBackground from './components/WaveBackground.vue'
import LogoPanel from './LogoPanel.vue'
import LoginForm from './LoginForm.vue'
</script>

<template>
  <div class="login-page">
    <GradientBackground />
    <GridBackground />
    <KLineBackground />
    <ParticleCanvas />
    <WaveBackground />
    <div class="login-card-wrapper">
      <LogoPanel />
      <LoginForm />
    </div>
  </div>
</template>

<style scoped>
.login-page {
  position: fixed;
  inset: 0;
  overflow: hidden;
  z-index: 10000;
}

.login-card-wrapper {
  position: relative;
  z-index: 10;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 20px;
}

@media (max-width: 768px) {
  .login-card-wrapper {
    padding: 16px;
  }
}
</style>
