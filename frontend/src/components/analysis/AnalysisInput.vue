<script setup lang="ts">
import { ref } from 'vue'
import { Search, Loader2 } from 'lucide-vue-next'

const props = defineProps<{
  isRunning: boolean
}>()

const emit = defineEmits<{
  submit: [query: string]
}>()

const inputValue = ref('')

function handleSubmit() {
  const query = inputValue.value.trim()
  if (!query || props.isRunning) return
  emit('submit', query)
  inputValue.value = ''
}
</script>

<template>
  <div class="analysis-input">
    <div class="input-wrapper">
      <Search class="input-icon" :size="18" />
      <input
        v-model="inputValue"
        type="text"
        placeholder="输入股票名称或代码，如：中国平安、000001"
        :disabled="isRunning"
        @keydown.enter="handleSubmit"
      />
      <button class="start-btn" :disabled="!inputValue.trim() || isRunning" @click="handleSubmit">
        <Loader2 v-if="isRunning" :size="16" class="spin" />
        <span v-else>开始分析</span>
      </button>
    </div>
    <p class="input-hint" v-if="isRunning">分析进行中，请等待完成后再发起新的分析...</p>
  </div>
</template>

<style scoped>
.analysis-input {
  padding: 16px 20px;
  border-bottom: 1px solid var(--border, #e2e8f0);
  background: var(--surface, #ffffff);
}
.input-wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--surface-2, #f1f5f9);
  border: 1px solid var(--border, #e2e8f0);
  border-radius: 8px;
  padding: 8px 12px;
}
.input-icon { color: var(--text-dim, #94a3b8); flex-shrink: 0; }
input {
  flex: 1;
  border: none;
  background: none;
  outline: none;
  font-size: 14px;
  color: var(--text, #1e293b);
}
input::placeholder { color: var(--text-dim, #94a3b8); }
.start-btn {
  padding: 6px 16px;
  border-radius: 6px;
  border: none;
  background: var(--accent, #2563eb);
  color: white;
  font-size: 13px;
  cursor: pointer;
  flex-shrink: 0;
}
.start-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.input-hint { margin: 8px 0 0; font-size: 12px; color: var(--text-dim, #94a3b8); }
</style>
