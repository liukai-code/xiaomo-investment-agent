<script setup lang="ts">
import { ref, watch } from 'vue'
import type { CodeBlock } from '@/types/blocks'
import hljs from 'highlight.js'

const props = defineProps<{ block: CodeBlock }>()

const highlightedHtml = ref('')

function highlight(code: string, lang: string): string {
  if (lang && hljs.getLanguage(lang)) {
    return hljs.highlight(code, { language: lang }).value
  }
  return hljs.highlightAuto(code).value
}

watch(
  () => props.block.closed,
  (closed) => {
    if (closed) {
      highlightedHtml.value = highlight(props.block.code, props.block.language)
    }
  },
  { immediate: true },
)
</script>

<template>
  <pre><code
    :class="block.language ? `hljs language-${block.language}` : ''"
  ><template v-if="block.closed"><span v-html="highlightedHtml" /></template><template v-else>{{ block.code }}</template></code></pre>
</template>
