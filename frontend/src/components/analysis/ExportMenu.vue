<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { Download, FileText, FileDown, FileType, Loader2 } from 'lucide-vue-next'

const props = defineProps<{
  disabled?: boolean
}>()

const emit = defineEmits<{
  export: [format: 'pdf' | 'word' | 'md']
}>()

const menuVisible = ref(false)
const menuRef = ref<HTMLElement | null>(null)

function toggleMenu() {
  if (props.disabled) return
  menuVisible.value = !menuVisible.value
}

function handleExport(format: 'pdf' | 'word' | 'md') {
  menuVisible.value = false
  emit('export', format)
}

function handleClickOutside(e: MouseEvent) {
  if (menuRef.value && !menuRef.value.contains(e.target as Node)) {
    menuVisible.value = false
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})
</script>

<template>
  <div class="export-menu" ref="menuRef">
    <button
      class="export-trigger"
      :class="{ disabled }"
      @click.stop="toggleMenu"
      title="导出报告"
    >
      <Loader2 v-if="disabled" :size="18" class="spin" />
      <Download v-else :size="18" />
    </button>
    <Transition name="menu-fade">
      <div v-if="menuVisible" class="export-dropdown">
        <button class="export-item" @click.stop="handleExport('pdf')">
          <FileDown :size="14" />
          <span>导出 PDF</span>
        </button>
        <button class="export-item" @click.stop="handleExport('word')">
          <FileType :size="14" />
          <span>导出 Word</span>
        </button>
        <button class="export-item" @click.stop="handleExport('md')">
          <FileText :size="14" />
          <span>导出 MD</span>
        </button>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.export-menu {
  position: relative;
}

.export-trigger {
  background: none;
  border: none;
  color: var(--text-dim, #94a3b8);
  cursor: pointer;
  padding: 10px;
  border-radius: 10px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}

.export-trigger:hover {
  color: var(--accent, #2563eb);
  background: var(--accent-dim, #2563eb18);
}

.export-trigger.disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.export-dropdown {
  position: absolute;
  right: 0;
  top: 100%;
  margin-top: 4px;
  background: var(--surface, #ffffff);
  border: 1px solid var(--border, #e2e8f0);
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  min-width: 140px;
  z-index: 100;
  padding: 4px;
}

.export-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 12px;
  border: none;
  background: none;
  color: var(--text, #1e293b);
  font-size: 13px;
  cursor: pointer;
  border-radius: 6px;
  transition: background 0.15s;
}

.export-item:hover {
  background: var(--surface-2, #f1f5f9);
}

.export-item svg {
  color: var(--text-dim, #94a3b8);
}

.menu-fade-enter-active {
  transition: all 0.15s ease-out;
}

.menu-fade-leave-active {
  transition: all 0.1s ease-in;
}

.menu-fade-enter-from,
.menu-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

.spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
