<script setup lang="ts">
import MarkdownRenderer from './MarkdownRenderer.vue'

defineProps<{
  title?: string
  content: string
  isStreaming?: boolean
}>()
</script>

<template>
  <div class="card-block">
    <div v-if="title" class="card-title">{{ title }}</div>
    <div class="card-body">
      <MarkdownRenderer :text="content" :is-streaming="isStreaming ?? false" />
    </div>
  </div>
</template>

<style scoped>
.card-block {
  width: 100%;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border);
}

.card-body {
  font-size: 14px;
  line-height: 1.7;
  color: var(--text);
}
</style>
