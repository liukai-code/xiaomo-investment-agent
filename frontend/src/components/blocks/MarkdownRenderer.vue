<script setup lang="ts">
import { toRef } from 'vue'
import { useMarkdownBlocks } from '@/composables/useMarkdownBlocks'
import StreamingCursor from '@/components/common/StreamingCursor.vue'
import HeadingBlock from './HeadingBlock.vue'
import ParagraphBlock from './ParagraphBlock.vue'
import CodeBlock from './CodeBlock.vue'
import ListBlock from './ListBlock.vue'
import TableBlock from './TableBlock.vue'
import BlockquoteBlock from './BlockquoteBlock.vue'
import HrBlock from './HrBlock.vue'
import MathBlock from './MathBlock.vue'

const props = defineProps<{
  text: string
  isStreaming: boolean
}>()

const { blocks } = useMarkdownBlocks(toRef(props, 'text'))
</script>

<template>
  <template v-for="block in blocks" :key="block.key">
    <HeadingBlock v-if="block.type === 'heading'" :block="block" />
    <ParagraphBlock v-else-if="block.type === 'paragraph'" :block="block" />
    <CodeBlock v-else-if="block.type === 'code'" :block="block" />
    <ListBlock v-else-if="block.type === 'list'" :block="block" />
    <TableBlock v-else-if="block.type === 'table'" :block="block" />
    <BlockquoteBlock v-else-if="block.type === 'blockquote'" :block="block" />
    <HrBlock v-else-if="block.type === 'hr'" />
    <MathBlock v-else-if="block.type === 'math'" :block="block" />
  </template>
  <StreamingCursor v-if="isStreaming" />
</template>
