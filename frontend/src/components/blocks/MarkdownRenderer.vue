<script setup lang="ts">
import { computed, toRef } from 'vue'
import { useMarkdownBlocks } from '@/composables/useMarkdownBlocks'
import { useMessageProtocol } from '@/composables/useMessageProtocol'
import StreamingCursor from '@/components/common/StreamingCursor.vue'
import KpiBlock from './KpiBlock.vue'
import CardBlock from './CardBlock.vue'
import HeadingBlock from './HeadingBlock.vue'
import ParagraphBlock from './ParagraphBlock.vue'
import CodeBlock from './CodeBlock.vue'
import ListBlock from './ListBlock.vue'
import TableBlock from './TableBlock.vue'
import BlockquoteBlock from './BlockquoteBlock.vue'
import HrBlock from './HrBlock.vue'

const props = defineProps<{
  text: string
  isStreaming: boolean
}>()

const textRef = toRef(props, 'text')
const { parsed } = useMessageProtocol(textRef)

const contentRef = computed(() => parsed.value.content)
const { blocks } = useMarkdownBlocks(contentRef)
</script>

<template>
  <!-- Protocol-aware rendering -->
  <KpiBlock
    v-if="parsed.type === 'kpi'"
    :content="parsed.content"
  />

  <CardBlock
    v-else-if="parsed.type === 'card'"
    :title="parsed.title"
    :content="parsed.content"
    :is-streaming="isStreaming"
  />

  <!-- Default markdown rendering (text, table, or no marker) -->
  <template v-else>
    <template v-for="block in blocks" :key="block.key">
      <HeadingBlock v-if="block.type === 'heading'" :block="block" />
      <ParagraphBlock v-else-if="block.type === 'paragraph'" :block="block" />
      <CodeBlock v-else-if="block.type === 'code'" :block="block" />
      <ListBlock v-else-if="block.type === 'list'" :block="block" />
      <TableBlock v-else-if="block.type === 'table'" :block="block" />
      <BlockquoteBlock v-else-if="block.type === 'blockquote'" :block="block" />
      <HrBlock v-else-if="block.type === 'hr'" />
    </template>
  </template>

  <StreamingCursor v-if="isStreaming" />
</template>
