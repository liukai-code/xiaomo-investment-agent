<script setup lang="ts">
import type { HeadingBlock } from '@/types/blocks'
import { renderInline } from '@/utils/markdown'

defineProps<{ block: HeadingBlock }>()
</script>

<template>
  <component :is="'h' + block.level" v-html="renderInline(block.text)" />
</template>
