import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useThemeStore = defineStore('theme', () => {
  const isLight = ref(true)

  function init() {
    // 浅色主题已固定，无需初始化
  }

  return { isLight, init }
})
