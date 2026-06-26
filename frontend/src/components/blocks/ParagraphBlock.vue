<script setup lang="ts">
import type { ParagraphBlock } from '@/types/blocks'
import { renderInline } from '@/utils/markdown'

defineProps<{ block: ParagraphBlock }>()
</script>

<template>
  <p v-html="renderInline(block.lines.join('\n'))" />
</template>
