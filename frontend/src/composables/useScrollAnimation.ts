import { ref, onMounted, onUnmounted, type Directive } from 'vue'

/**
 * Composable: 为单个元素提供滚动可见性检测
 */
export function useScrollAnimation(options?: IntersectionObserverInit) {
  const el = ref<HTMLElement | null>(null)
  const isVisible = ref(false)
  let observer: IntersectionObserver | null = null

  onMounted(() => {
    if (!el.value) return
    observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          isVisible.value = true
          observer?.disconnect()
        }
      },
      { threshold: 0.1, ...options }
    )
    observer.observe(el.value)
  })

  onUnmounted(() => {
    observer?.disconnect()
  })

  return { el, isVisible }
}

/**
 * 指令: v-animate — 元素进入视口时添加 portal-visible 类
 * 用法: v-animate 或 v-animate="300"（300ms 延迟）
 */
export const vAnimate: Directive<HTMLElement, number | undefined> = {
  mounted(el, binding) {
    const delay = binding.value ?? 0
    el.style.transitionDelay = `${delay}ms`
    el.classList.add('portal-animate')

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          el.classList.add('portal-visible')
          observer.disconnect()
        }
      },
      { threshold: 0.1 }
    )
    observer.observe(el)
  },
}
