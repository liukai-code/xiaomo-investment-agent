<script setup lang="ts">
</script>

<template>
  <div class="wave-bg">
    <svg class="wave-svg" viewBox="0 0 2400 120" preserveAspectRatio="none" xmlns="http://www.w3.org/2000/svg">
      <path
        d="M0,60 C200,20 400,100 600,60 C800,20 1000,100 1200,60 C1400,20 1600,100 1800,60 C2000,20 2200,100 2400,60"
        fill="none"
        stroke="rgba(26,95,255,0.3)"
        stroke-width="1.5"
      />
      <path
        d="M0,75 C150,45 350,105 550,75 C750,45 950,105 1150,75 C1350,45 1550,105 1750,75 C1950,45 2150,105 2400,75"
        fill="none"
        stroke="rgba(0,212,255,0.2)"
        stroke-width="1"
      />
      <path
        d="M0,90 C250,60 450,120 650,90 C850,60 1050,120 1250,90 C1450,60 1650,120 1850,90 C2050,60 2250,120 2400,90"
        fill="none"
        stroke="rgba(124,58,237,0.15)"
        stroke-width="1"
      />
    </svg>
  </div>
</template>

<style scoped>
.wave-bg {
  position: fixed;
  bottom: 0;
  left: 0;
  width: 200%;
  height: 120px;
  z-index: 2;
  opacity: 0.15;
  pointer-events: none;
  animation: loginFadeIn 1.2s ease-out 0.5s both;
}

.wave-svg {
  width: 100%;
  height: 100%;
  animation: waveScroll 18s linear infinite;
}

@keyframes waveScroll {
  from { transform: translateX(0); }
  to { transform: translateX(-50%); }
}

@media (max-width: 768px) {
  .wave-bg {
    height: 60px;
  }
}
</style>
