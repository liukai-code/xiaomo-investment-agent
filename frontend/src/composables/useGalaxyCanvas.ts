import { onMounted, onUnmounted, type Ref } from 'vue'

interface Bokeh {
  x: number
  y: number
  radius: number
  color: string
  baseOpacity: number
  driftSpeed: number
  angle: number
  pulseSpeed: number
  pulsePhase: number
}

interface GalaxyOptions {
  particleCount?: number
}

function hexToRgb(hex: string): [number, number, number] {
  const h = hex.replace('#', '')
  return [
    parseInt(h.substring(0, 2), 16),
    parseInt(h.substring(2, 4), 16),
    parseInt(h.substring(4, 6), 16),
  ]
}

export function useGalaxyCanvas(
  container: Ref<HTMLElement | undefined>,
  options: GalaxyOptions = {}
) {
  const { particleCount = 200 } = options

  let canvas: HTMLCanvasElement | null = null
  let ctx: CanvasRenderingContext2D | null = null
  let bokehArr: Bokeh[] = []
  let rafId = 0
  let width = 0
  let height = 0
  let resizeObserver: ResizeObserver | null = null

  function getAccentColor(): string {
    return getComputedStyle(document.documentElement).getPropertyValue('--accent').trim() || '#2563eb'
  }

  function initBokeh(w: number, h: number) {
    const accent = getAccentColor()
    const [ar, ag, ab] = hexToRgb(accent)
    const count = 15

    const palette = [
      `${Math.round(ar * 0.5 + 255 * 0.5)},${Math.round(ag * 0.5 + 255 * 0.5)},${Math.round(ab * 0.5 + 255 * 0.5)}`,
      '60,110,190',
      '190,110,150',
      `${Math.round(ar * 0.35 + 255 * 0.65)},${Math.round(ag * 0.35 + 255 * 0.65)},${Math.round(ab * 0.35 + 255 * 0.65)}`,
      `${Math.round(ar * 0.6 + 255 * 0.4)},${Math.round(ag * 0.6 + 255 * 0.4)},${Math.round(ab * 0.6 + 255 * 0.4)}`,
    ]

    bokehArr = []
    for (let i = 0; i < count; i++) {
      bokehArr.push({
        x: Math.random() * w,
        y: Math.random() * h,
        radius: 80 + Math.random() * 160,
        color: palette[i % palette.length],
        baseOpacity: 0.15 + Math.random() * 0.2,
        driftSpeed: 0.01 + Math.random() * 0.02,
        angle: Math.random() * Math.PI * 2,
        pulseSpeed: 0.0003 + Math.random() * 0.0005,
        pulsePhase: Math.random() * Math.PI * 2,
      })
    }
  }

  function drawBokeh(time: number) {
    if (!ctx) return
    for (const b of bokehArr) {
      b.x += Math.cos(b.angle) * b.driftSpeed
      b.y += Math.sin(b.angle) * b.driftSpeed

      const margin = b.radius
      if (b.x < -margin) b.x = width + margin
      if (b.x > width + margin) b.x = -margin
      if (b.y < -margin) b.y = height + margin
      if (b.y > height + margin) b.y = -margin

      const pulse = Math.sin(time * b.pulseSpeed + b.pulsePhase) * 0.4 + 0.6
      const opacity = b.baseOpacity * pulse

      const grad = ctx.createRadialGradient(b.x, b.y, 0, b.x, b.y, b.radius)
      grad.addColorStop(0, `rgba(${b.color},${opacity})`)
      grad.addColorStop(0.5, `rgba(${b.color},${opacity * 0.4})`)
      grad.addColorStop(1, `rgba(${b.color},0)`)
      ctx.fillStyle = grad
      ctx.beginPath()
      ctx.arc(b.x, b.y, b.radius, 0, Math.PI * 2)
      ctx.fill()
    }
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
    initBokeh(width, height)
  }

  function frame(time: number) {
    if (!ctx || !canvas) return
    ctx.clearRect(0, 0, width, height)
    drawBokeh(time)
    rafId = requestAnimationFrame(frame)
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
