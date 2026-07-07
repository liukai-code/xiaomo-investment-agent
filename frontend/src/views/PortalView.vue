<script setup lang="ts">
import PortalHeader from '@/components/portal/PortalHeader.vue'
import HeroSection from '@/components/portal/HeroSection.vue'
import FeaturesSection from '@/components/portal/FeaturesSection.vue'
import FaqSection from '@/components/portal/FaqSection.vue'
import CtaSection from '@/components/portal/CtaSection.vue'
import PortalFooter from '@/components/portal/PortalFooter.vue'
</script>

<template>
  <div class="portal-root">
    <PortalHeader />
    <main>
      <HeroSection />
      <FeaturesSection />
      <FaqSection />
      <CtaSection />
    </main>
    <PortalFooter />
  </div>
</template>

<style scoped>
.portal-root {
  width: 100%;
  min-height: 100vh;
  background: var(--portal-bg);
  color: var(--portal-text);
  font-family: 'Inter', 'Noto Sans SC', sans-serif;
}
</style>
