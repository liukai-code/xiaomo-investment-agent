<script setup lang="ts">
import { ref, watch } from 'vue'
import { useGalaxyCanvas } from '@/composables/useGalaxyCanvas'

const props = withDefaults(defineProps<{
  dark?: boolean
  particleCount?: number
  nebulaCount?: number
}>(), {
  dark: true,
  particleCount: 200,
  nebulaCount: 4,
})

const containerRef = ref<HTMLElement>()
const { setTheme } = useGalaxyCanvas(containerRef, {
  particleCount: props.particleCount,
  nebulaCount: props.nebulaCount,
  isDark: props.dark,
})

watch(() => props.dark, (val) => setTheme(val))
</script>

<template>
  <div ref="containerRef" class="galaxy-container"></div>
</template>
