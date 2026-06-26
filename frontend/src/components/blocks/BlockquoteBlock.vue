<script setup lang="ts">
import type { BlockquoteBlock } from '@/types/blocks'
import { renderInline } from '@/utils/markdown'

defineProps<{ block: BlockquoteBlock }>()
</script>

<template>
  <blockquote>
    <p v-html="renderInline(block.lines.join('\n'))" />
  </blockquote>
</template>
