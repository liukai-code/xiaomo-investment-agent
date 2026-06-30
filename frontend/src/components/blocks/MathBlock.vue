<script setup lang="ts">
import type { MathBlock } from '@/types/blocks'
import { renderMath } from '@/utils/markdown'

const props = defineProps<{ block: MathBlock }>()
</script>

<template>
  <div
    class="math-block"
    :class="{ 'math-display': block.display }"
    v-html="renderMath(block.tex, block.display)"
  />
</template>

<style scoped>
.math-block {
  overflow-x: auto;
  overflow-y: hidden;
  padding: 4px 0;
}
.math-display {
  text-align: center;
  margin: 12px 0;
}
</style>
