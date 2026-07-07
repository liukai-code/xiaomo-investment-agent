<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ArrowRight } from 'lucide-vue-next'

const router = useRouter()
</script>

<template>
  <section class="portal-cta">
    <!-- Giant background text -->
    <div class="portal-cta-watermark" aria-hidden="true">
      <div class="portal-cta-watermark-line-1 portal-serif">XIAOMO</div>
      <div class="portal-cta-watermark-line-2 portal-serif">FINANCE</div>
    </div>

    <!-- Grid overlay -->
    <div class="portal-grid-bg" />

    <div class="portal-cta-content">
      <div v-animate class="portal-cta-inner">
        <h2 class="portal-serif">开启智能投资之旅</h2>

        <p>加入数千名投资者的行列，让 AI 为你的投资决策赋能</p>

        <button
          class="portal-btn-cta"
          @click="router.push('/login')"
        >
          免费开始
          <ArrowRight :size="24" />
        </button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.portal-cta {
  position: relative;
  min-height: 80vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 128px 24px;
  overflow: hidden;
}

.portal-cta-watermark {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  pointer-events: none;
  user-select: none;
}

.portal-cta-watermark-line-1 {
  font-size: 20vw;
  font-weight: 900;
  color: var(--portal-text);
  opacity: 0.04;
  line-height: 0.8;
  letter-spacing: -4px;
}

.portal-cta-watermark-line-2 {
  font-size: 18vw;
  font-weight: 900;
  color: transparent;
  -webkit-text-stroke: 2px var(--portal-text);
  opacity: 0.04;
  line-height: 0.8;
  letter-spacing: -4px;
}

.portal-cta-content {
  position: relative;
  z-index: 1;
  max-width: 896px;
  margin: 0 auto;
  text-align: center;
}

.portal-cta-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.portal-cta h2 {
  font-size: 48px;
  font-weight: 500;
  letter-spacing: -1px;
  line-height: 1.1;
  color: var(--portal-text);
  margin-bottom: 32px;
}

@media (min-width: 768px) {
  .portal-cta h2 {
    font-size: 72px;
  }
}

@media (min-width: 1024px) {
  .portal-cta h2 {
    font-size: 96px;
  }
}

.portal-cta p {
  font-size: 18px;
  color: var(--portal-text-muted);
  margin-bottom: 64px;
  max-width: 560px;
  line-height: 1.8;
  font-weight: 300;
}

@media (min-width: 768px) {
  .portal-cta p {
    font-size: 22px;
  }
}
</style>
