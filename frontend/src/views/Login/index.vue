<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import GradientBackground from './components/GradientBackground.vue'
import GridBackground from './components/GridBackground.vue'
import KLineBackground from './components/KLineBackground.vue'
import ParticleCanvas from './components/ParticleCanvas.vue'
import WaveBackground from './components/WaveBackground.vue'
import LogoPanel from './LogoPanel.vue'
import LoginForm from './LoginForm.vue'

const router = useRouter()
const exiting = ref(false)

function onLoginSuccess() {
  exiting.value = true
  setTimeout(() => router.push('/'), 400)
}
</script>

<template>
  <div class="login-page" :class="{ 'page-exit': exiting }">
    <GradientBackground />
    <GridBackground />
    <KLineBackground />
    <ParticleCanvas />
    <WaveBackground />
    <div class="login-card-wrapper">
      <LogoPanel />
      <LoginForm @login-success="onLoginSuccess" />
    </div>
  </div>
</template>

<style scoped>
.login-page {
  position: fixed;
  inset: 0;
  overflow: hidden;
  z-index: 10000;
  transition: opacity 400ms ease, transform 400ms ease;
}

.page-exit {
  opacity: 0;
  transform: scale(0.98);
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
