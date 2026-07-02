import { onMounted, onUnmounted, type Ref } from 'vue'

interface Particle {
  x: number
  y: number
  vx: number
  vy: number
  char: string
  size: number
  opacity: number
  color: string
}

interface ParticleOptions {
  particleCount?: number
  connectionDistance?: number
}

const CHARS = ['$', '%', '▲', '▼', '¥', '₿']

const COLORS = [
  'rgba(59, 130, 246, 0.6)',
  'rgba(0, 212, 255, 0.5)',
  'rgba(148, 163, 184, 0.4)',
  'rgba(255, 255, 255, 0.35)',
  'rgba(124, 58, 237, 0.45)',
  'rgba(34, 197, 94, 0.4)',
]

export function useParticles(
  container: Ref<HTMLElement | undefined>,
  options: ParticleOptions = {}
) {
  const { particleCount = 80, connectionDistance = 150 } = options

  let canvas: HTMLCanvasElement | null = null
  let ctx: CanvasRenderingContext2D | null = null
  let particles: Particle[] = []
  let rafId = 0
  let width = 0
  let height = 0
  let resizeObserver: ResizeObserver | null = null

  function initParticles(w: number, h: number) {
    particles = []
    for (let i = 0; i < particleCount; i++) {
      particles.push({
        x: Math.random() * w,
        y: Math.random() * h,
        vx: (Math.random() - 0.5) * 0.6,
        vy: (Math.random() - 0.5) * 0.6,
        char: CHARS[Math.floor(Math.random() * CHARS.length)],
        size: 10 + Math.random() * 6,
        opacity: 0.15 + Math.random() * 0.35,
        color: COLORS[Math.floor(Math.random() * COLORS.length)],
      })
    }
  }

  function drawConnections() {
    if (!ctx) return
    const distSq = connectionDistance * connectionDistance

    for (let i = 0; i < particles.length; i++) {
      for (let j = i + 1; j < particles.length; j++) {
        const dx = particles[i].x - particles[j].x
        const dy = particles[i].y - particles[j].y
        const dSq = dx * dx + dy * dy

        if (dSq < distSq) {
          const d = Math.sqrt(dSq)
          const alpha = (1 - d / connectionDistance) * 0.25
          ctx.strokeStyle = `rgba(59, 130, 246, ${alpha})`
          ctx.lineWidth = 0.5
          ctx.beginPath()
          ctx.moveTo(particles[i].x, particles[i].y)
          ctx.lineTo(particles[j].x, particles[j].y)
          ctx.stroke()
        }
      }
    }
  }

  function drawParticles() {
    if (!ctx) return
    for (const p of particles) {
      p.x += p.vx
      p.y += p.vy

      if (p.x < 0) p.x = width
      if (p.x > width) p.x = 0
      if (p.y < 0) p.y = height
      if (p.y > height) p.y = 0

      ctx.font = `${p.size}px monospace`
      ctx.fillStyle = p.color
      ctx.globalAlpha = p.opacity
      ctx.fillText(p.char, p.x, p.y)
    }
    ctx.globalAlpha = 1
  }

  function frame() {
    if (!ctx || !canvas) return
    ctx.clearRect(0, 0, width, height)
    drawConnections()
    drawParticles()
    rafId = requestAnimationFrame(frame)
  }

  function resizeCanvas() {
    const el = container.value
    if (!el || !canvas || !ctx) return

    const rect = el.getBoundingClientRect()
    const dpr = Math.min(window.devicePixelRatio || 1, 2)

    width = rect.width
    height = rect.height

    canvas.width = width * dpr
    canvas.height = height * dpr
    canvas.style.width = width + 'px'
    canvas.style.height = height + 'px'

    ctx.setTransform(dpr, 0, 0, dpr, 0, 0)

    const count = window.innerWidth <= 768 ? 30 : particleCount
    initParticles(width, height)
    // Re-init with adjusted count for mobile
    if (count !== particleCount) {
      particles = particles.slice(0, count)
    }
  }

  function start() {
    if (rafId) return
    rafId = requestAnimationFrame(frame)
  }

  function stop() {
    if (rafId) {
      cancelAnimationFrame(rafId)
      rafId = 0
    }
  }

  function handleVisibility() {
    if (document.hidden) {
      stop()
    } else {
      start()
    }
  }

  onMounted(() => {
    const el = container.value
    if (!el) return

    canvas = document.createElement('canvas')
    canvas.style.cssText = 'position:absolute;inset:0;width:100%;height:100%;pointer-events:none;'
    el.appendChild(canvas)
    ctx = canvas.getContext('2d')

    resizeCanvas()
    start()

    resizeObserver = new ResizeObserver(() => resizeCanvas())
    resizeObserver.observe(el)

    document.addEventListener('visibilitychange', handleVisibility)
  })

  onUnmounted(() => {
    stop()
    resizeObserver?.disconnect()
    resizeObserver = null
    document.removeEventListener('visibilitychange', handleVisibility)
    if (canvas?.parentNode) {
      canvas.parentNode.removeChild(canvas)
    }
    canvas = null
    ctx = null
  })

  return { start, stop }
}
