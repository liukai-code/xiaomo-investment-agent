<script setup lang="ts">
import type { TableBlock } from '@/types/blocks'
import { renderInline } from '@/utils/markdown'

defineProps<{ block: TableBlock }>()
</script>

<template>
  <table>
    <thead>
      <tr>
        <th
          v-for="(cell, i) in block.header"
          :key="i"
          :style="{ textAlign: block.alignments[i] || 'left' }"
          v-html="renderInline(cell)"
        />
      </tr>
    </thead>
    <tbody v-if="block.rows.length > 0">
      <tr v-for="(row, ri) in block.rows" :key="ri">
        <td
          v-for="(cell, ci) in row"
          :key="ci"
          :style="{ textAlign: block.alignments[ci] || 'left' }"
          v-html="renderInline(cell)"
        />
      </tr>
    </tbody>
  </table>
</template>
