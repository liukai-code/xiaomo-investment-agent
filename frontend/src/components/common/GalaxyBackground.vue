<script setup lang="ts">
import { ref } from 'vue'
import { useGalaxyCanvas } from '@/composables/useGalaxyCanvas'

const props = withDefaults(defineProps<{
  particleCount?: number
}>(), {
  particleCount: 200,
})

const containerRef = ref<HTMLElement>()
useGalaxyCanvas(containerRef, {
  particleCount: props.particleCount,
})
</script>

<template>
  <div ref="containerRef" class="galaxy-container"></div>
</template>
