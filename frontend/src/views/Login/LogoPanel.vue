<script setup lang="ts">
</script>

<template>
  <div class="logo-panel">
    <div class="logo-orbit">
      <div class="orbit-ring">
        <div class="orbit-dot" style="--angle: 0deg"></div>
        <div class="orbit-dot" style="--angle: 120deg"></div>
        <div class="orbit-dot" style="--angle: 240deg"></div>
      </div>
      <img src="/logo.png" alt="小墨" class="logo-img" />
    </div>
    <h1 class="logo-title">小墨</h1>
    <p class="logo-subtitle">金融投资 AI 助手</p>
  </div>
</template>

<style scoped>
.logo-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 32px;
  animation: loginFadeIn 0.8s ease-out 0.2s both;
}

.logo-orbit {
  position: relative;
  width: 140px;
  height: 140px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 20px;
}

.orbit-ring {
  position: absolute;
  inset: 0;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 50%;
  animation: loginOrbitRotate 20s linear infinite;
}

.orbit-dot {
  position: absolute;
  width: 5px;
  height: 5px;
  background: var(--login-glow-cyan);
  border-radius: 50%;
  box-shadow: 0 0 10px var(--login-glow-cyan);
  top: 50%;
  left: 50%;
  transform: rotate(var(--angle)) translateX(70px) translate(-50%, -50%);
}

.logo-img {
  width: 64px;
  height: 64px;
  border-radius: 16px;
  object-fit: cover;
  position: relative;
  z-index: 1;
}

.logo-title {
  font-size: 32px;
  font-weight: 800;
  color: var(--login-text);
  margin: 0 0 6px;
  letter-spacing: 2px;
}

.logo-subtitle {
  font-size: 14px;
  color: var(--login-text-dim);
  margin: 0;
  font-weight: 400;
}

@media (max-width: 480px) {
  .logo-orbit {
    width: 100px;
    height: 100px;
  }

  .orbit-dot {
    transform: rotate(var(--angle)) translateX(50px) translate(-50%, -50%);
  }

  .logo-img {
    width: 48px;
    height: 48px;
    border-radius: 12px;
  }

  .logo-title {
    font-size: 26px;
  }
}
</style>
