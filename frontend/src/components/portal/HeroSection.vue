<script setup lang="ts">
import { useRouter } from 'vue-router'
import { Sparkles, ArrowRight, MessageSquare } from 'lucide-vue-next'

const router = useRouter()
</script>

<template>
  <section class="portal-hero">
    <!-- Background decorations -->
    <div class="portal-hero-bg">
      <div class="portal-hero-gradient" />
      <div class="portal-hero-blob portal-hero-blob-1 portal-blob" />
      <div class="portal-hero-blob portal-hero-blob-2 portal-blob portal-blob-delay-2" />
    </div>

    <div class="portal-hero-content">
      <!-- Badge -->
      <div v-animate class="portal-badge">
        <Sparkles :size="16" />
        <span>AI 驱动的智能投资助手</span>
      </div>

      <!-- Title -->
      <h1 v-animate="100" class="portal-hero-title portal-serif">
        你的专属金融 AI 导师
      </h1>

      <!-- Subtitle -->
      <p v-animate="200" class="portal-hero-subtitle">
        基于大语言模型与实时金融数据，为投资者提供智能问答、深度分析、行情监控一站式服务
      </p>

      <!-- CTA Buttons -->
      <div v-animate="300" class="portal-hero-actions">
        <button class="portal-btn-primary" @click="router.push('/login')">
          开始对话
          <ArrowRight :size="20" />
        </button>
        <a href="#features" class="portal-btn-outline">
          <MessageSquare :size="16" />
          了解更多
        </a>
      </div>

      <!-- Product Screenshot -->
      <div v-animate="400" class="portal-hero-screenshot">
        <div class="portal-hero-screenshot-glow" />
        <div class="portal-hero-screenshot-card">
          <img src="/portal/hero-screenshot.svg" alt="小墨 AI 对话界面" />
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.portal-hero {
  position: relative;
  min-height: 90vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 128px 24px 80px;
  overflow: hidden;
}

.portal-hero-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 0;
}

.portal-hero-gradient {
  position: absolute;
  inset: 0;
  background: linear-gradient(to bottom, rgba(27, 27, 24, 0.03), var(--portal-bg));
}

.portal-hero-blob {
  position: absolute;
  border-radius: 9999px;
  filter: blur(120px);
  opacity: 0.3;
}

.portal-hero-blob-1 {
  top: 80px;
  left: 40px;
  width: 384px;
  height: 384px;
  background: rgba(27, 27, 24, 0.08);
}

.portal-hero-blob-2 {
  bottom: 160px;
  right: 40px;
  width: 500px;
  height: 500px;
  background: rgba(37, 99, 235, 0.05);
}

.portal-hero-content {
  position: relative;
  z-index: 1;
  max-width: 896px;
  margin: 0 auto;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.portal-hero-title {
  font-size: 48px;
  font-weight: 600;
  letter-spacing: -1px;
  line-height: 1.1;
  color: var(--portal-text);
  margin-top: 40px;
}

@media (min-width: 768px) {
  .portal-hero-title {
    font-size: 72px;
  }
}

.portal-hero-subtitle {
  font-size: 18px;
  line-height: 1.8;
  color: var(--portal-text-muted);
  margin-top: 32px;
  max-width: 640px;
  font-weight: 300;
}

@media (min-width: 768px) {
  .portal-hero-subtitle {
    font-size: 22px;
  }
}

.portal-hero-actions {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  margin-top: 48px;
}

@media (min-width: 640px) {
  .portal-hero-actions {
    flex-direction: row;
    gap: 20px;
  }
}

.portal-hero-screenshot {
  margin-top: 80px;
  position: relative;
  width: 100%;
  padding: 0 16px;
}

@media (min-width: 640px) {
  .portal-hero-screenshot {
    padding: 0;
  }
}

.portal-hero-screenshot-glow {
  position: absolute;
  inset: -16px;
  background: linear-gradient(to bottom, rgba(27, 27, 24, 0.03), transparent);
  border-radius: 48px;
  filter: blur(16px);
  z-index: -1;
}

.portal-hero-screenshot-card {
  position: relative;
  border-radius: 24px;
  border: 1px solid rgba(230, 227, 227, 0.5);
  background: rgba(242, 240, 233, 0.3);
  padding: 8px;
  backdrop-filter: blur(4px);
  box-shadow:
    0 25px 50px -12px rgba(0, 0, 0, 0.15),
    0 0 0 1px rgba(0, 0, 0, 0.03);
  overflow: hidden;
  transition: transform 0.7s;
}

.portal-hero-screenshot-card:hover {
  transform: scale(1.01);
}

.portal-hero-screenshot-card img {
  width: 100%;
  border-radius: 16px;
  display: block;
}
</style>
