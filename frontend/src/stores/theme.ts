import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useThemeStore = defineStore('theme', () => {
  const isLight = ref(false)

  function init() {
    const saved = localStorage.getItem('theme')
    if (saved === 'light') {
      isLight.value = true
      document.documentElement.classList.add('light')
    }
  }

  function toggle() {
    isLight.value = !isLight.value
    document.documentElement.classList.toggle('light', isLight.value)
    localStorage.setItem('theme', isLight.value ? 'light' : 'dark')
  }

  return { isLight, init, toggle }
})
