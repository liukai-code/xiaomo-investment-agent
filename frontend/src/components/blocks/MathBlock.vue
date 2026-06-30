<script setup lang="ts">
import type { MathBlock } from '@/types/blocks'
import { renderMath } from '@/utils/markdown'

const props = defineProps<{ block: MathBlock }>()
</script>

<template>
  <div
    v-if="block.closed"
    class="math-block"
    :class="{ 'math-display': block.display }"
    v-html="renderMath(block.tex, block.display)"
  />
  <pre v-else class="math-block math-raw">{{ block.tex }}</pre>
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
.math-raw {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 13px;
  color: var(--text-dim);
  background: var(--surface-2);
  padding: 12px 14px;
  border-radius: 8px;
  white-space: pre-wrap;
}
</style>
