<script setup lang="ts">
import type { ListBlock } from '@/types/blocks'
import { renderInline } from '@/utils/markdown'

defineProps<{ block: ListBlock }>()
</script>

<template>
  <component :is="block.ordered ? 'ol' : 'ul'">
    <li v-for="(item, i) in block.items" :key="i">
      <span v-html="renderInline(item.text)" />
      <ListBlock
        v-if="item.children && item.children.length > 0"
        :block="{
          type: 'list',
          ordered: block.ordered,
          items: item.children,
          closed: true,
          key: '',
        }"
      />
    </li>
  </component>
</template>
