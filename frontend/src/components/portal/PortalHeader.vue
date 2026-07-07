<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { Menu, X } from 'lucide-vue-next'

const router = useRouter()
const isScrolled = ref(false)
const isMobileMenuOpen = ref(false)

function onScroll() {
  isScrolled.value = window.scrollY > 0
}

onMounted(() => window.addEventListener('scroll', onScroll, { passive: true }))
onUnmounted(() => window.removeEventListener('scroll', onScroll))

function goToLogin() {
  router.push('/login')
  isMobileMenuOpen.value = false
}
</script>

<template>
  <header
    class="portal-header"
    :class="{ scrolled: isScrolled }"
  >
    <div class="portal-header-inner">
      <div class="portal-header-brand" @click="router.push('/portal')">
        <div class="portal-header-logo">
          <img src="/portal/logo.png" alt="小墨" />
        </div>
        <span class="portal-serif">小墨</span>
      </div>

      <nav class="portal-header-nav portal-hide-mobile">
        <a href="#features">功能</a>
        <a href="#faq">常见问题</a>
      </nav>

      <div class="portal-header-actions portal-hide-mobile">
        <button class="portal-btn-primary" @click="goToLogin">
          开始使用
        </button>
      </div>

      <button
        class="portal-mobile-toggle portal-show-mobile"
        @click="isMobileMenuOpen = !isMobileMenuOpen"
      >
        <X v-if="isMobileMenuOpen" :size="24" />
        <Menu v-else :size="24" />
      </button>
    </div>

    <!-- Mobile menu -->
    <Transition name="mobile-menu">
      <div v-if="isMobileMenuOpen" class="portal-mobile-menu portal-show-mobile">
        <a href="#features" @click="isMobileMenuOpen = false">功能</a>
        <a href="#faq" @click="isMobileMenuOpen = false">常见问题</a>
        <button class="portal-btn-primary" style="width: 100%" @click="goToLogin">
          开始使用
        </button>
      </div>
    </Transition>
  </header>
</template>

<style scoped>
.portal-header {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  z-index: 50;
  transition: background-color 0.2s, backdrop-filter 0.2s;
}

.portal-header.scrolled {
  background: rgba(249, 249, 247, 0.8);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
}

.portal-header-inner {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 80px;
}

.portal-header-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
}

.portal-header-logo {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  overflow: hidden;
  flex-shrink: 0;
}

.portal-header-logo img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  padding: 2px;
  image-rendering: -webkit-optimize-contrast;
  image-rendering: crisp-edges;
}

.portal-header-brand span {
  font-size: 24px;
  font-weight: 600;
  color: var(--portal-text);
  letter-spacing: -0.5px;
}

.portal-header-nav {
  display: flex;
  align-items: center;
  gap: 32px;
}

.portal-header-nav a {
  color: var(--portal-text-muted);
  text-decoration: none;
  font-size: 15px;
  font-weight: 500;
  transition: color 0.2s;
}

.portal-header-nav a:hover {
  color: var(--portal-text);
}

.portal-header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.portal-mobile-toggle {
  background: none;
  border: none;
  color: var(--portal-text);
  cursor: pointer;
  padding: 8px;
  border-radius: 8px;
}

.portal-mobile-toggle:hover {
  background: rgba(0, 0, 0, 0.05);
}

.portal-mobile-menu {
  background: rgba(249, 249, 247, 0.95);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  padding: 16px 24px 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  border-top: 1px solid var(--portal-border);
}

.portal-mobile-menu a {
  color: var(--portal-text);
  text-decoration: none;
  font-size: 16px;
  font-weight: 500;
  padding: 8px 0;
}

.mobile-menu-enter-active,
.mobile-menu-leave-active {
  transition: opacity 0.2s, transform 0.2s;
}

.mobile-menu-enter-from,
.mobile-menu-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
