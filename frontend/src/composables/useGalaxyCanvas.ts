import { ref, onMounted, onUnmounted, watch, type Ref } from 'vue'

interface Particle {
  x: number
  y: number
  radius: number
  baseOpacity: number
  twinkleSpeed: number
  twinklePhase: number
  isDrift: boolean
  vx: number
  vy: number
  isAccent: boolean
}

interface Nebula {
  x: number
  y: number
  radius: number
  color: string
  opacity: number
}

interface GalaxyOptions {
  particleCount?: number
  nebulaCount?: number
  isDark?: boolean
}

function hexToRgb(hex: string): [number, number, number] {
  const h = hex.replace('#', '')
  return [
    parseInt(h.substring(0, 2), 16),
    parseInt(h.substring(2, 4), 16),
    parseInt(h.substring(4, 6), 16),
  ]
}

function hueShift(r: number, g: number, b: number, degrees: number): [number, number, number] {
  const angle = (degrees / 360) * Math.PI * 2
  const cos = Math.cos(angle)
  const sin = Math.sin(angle)
  const rr = r * (0.213 + cos * 0.787 - sin * 0.213) + g * (0.715 - cos * 0.715 - sin * 0.715) + b * (0.072 - cos * 0.072 + sin * 0.928)
  const gg = r * (0.213 - cos * 0.213 + sin * 0.143) + g * (0.715 + cos * 0.285 + sin * 0.140) + b * (0.072 - cos * 0.072 - sin * 0.283)
  const bb = r * (0.213 - cos * 0.213 - sin * 0.787) + g * (0.715 - cos * 0.715 + sin * 0.715) + b * (0.072 + cos * 0.928 + sin * 0.072)
  return [Math.round(Math.max(0, Math.min(255, rr))), Math.round(Math.max(0, Math.min(255, gg))), Math.round(Math.max(0, Math.min(255, bb)))]
}

export function useGalaxyCanvas(
  container: Ref<HTMLElement | undefined>,
  options: GalaxyOptions = {}
) {
  const {
    particleCount = 800,
    nebulaCount = 8,
    isDark = true,
  } = options

  let canvas: HTMLCanvasElement | null = null
  let ctx: CanvasRenderingContext2D | null = null
  let particles: Particle[] = []
  let nebulae: Nebula[] = []
  let rafId = 0
  let width = 0
  let height = 0
  let darkMode = isDark
  let resizeObserver: ResizeObserver | null = null
  let lastTime = 0

  const opacityScale = ref(darkMode ? 1 : 0.15)

  function getAccentColor(): string {
    return getComputedStyle(document.documentElement).getPropertyValue('--accent').trim() || '#00e676'
  }

  function initParticles(w: number, h: number) {
    const accent = getAccentColor()
    const [ar, ag, ab] = hexToRgb(accent)
    const [br, bg, bb] = hueShift(ar, ag, ab, 200)
    const [pr, pg, pb] = hueShift(ar, ag, ab, 260)

    const staticCount = Math.floor(particleCount * 0.6)
    const driftCount = particleCount - staticCount

    particles = []
    for (let i = 0; i < staticCount; i++) {
      particles.push({
        x: Math.random() * w,
        y: Math.random() * h,
        radius: 0.5 + Math.random() * 1.5,
        baseOpacity: 0.2 + Math.random() * 0.6,
        twinkleSpeed: 0.0005 + Math.random() * 0.0015,
        twinklePhase: Math.random() * Math.PI * 2,
        isDrift: false,
        vx: 0,
        vy: 0,
        isAccent: Math.random() < 0.1,
      })
    }

    for (let i = 0; i < driftCount; i++) {
      const angle = (Math.random() * 0.3 + 0.1) * Math.PI + Math.random() * 0.2
      const speed = 0.03 + Math.random() * 0.08
      particles.push({
        x: Math.random() * w,
        y: Math.random() * h,
        radius: 1 + Math.random() * 2,
        baseOpacity: 0.3 + Math.random() * 0.4,
        twinkleSpeed: 0.0008 + Math.random() * 0.0012,
        twinklePhase: Math.random() * Math.PI * 2,
        isDrift: true,
        vx: Math.cos(angle) * speed,
        vy: Math.sin(angle) * speed,
        isAccent: Math.random() < 0.15,
      })
    }

    nebulae = []
    const nebulaColors = [
      `${ar},${ag},${ab}`,
      `${br},${bg},${bb}`,
      `${pr},${pg},${pb}`,
      `255,255,255`,
    ]
    for (let i = 0; i < nebulaCount; i++) {
      nebulae.push({
        x: w * (0.15 + Math.random() * 0.7),
        y: h * (0.15 + Math.random() * 0.7),
        radius: Math.max(w, h) * (0.2 + Math.random() * 0.25),
        color: nebulaColors[i % nebulaColors.length],
        opacity: 0.015 + Math.random() * 0.04,
      })
    }
  }

  function resizeCanvas() {
    const el = container.value
    if (!el || !canvas || !ctx) return

    const rect = el.getBoundingClientRect()
    const dpr = Math.min(window.devicePixelRatio || 1, 2)
    const maxW = 1920
    const maxH = 1080

    width = Math.min(rect.width, maxW)
    height = Math.min(rect.height, maxH)

    canvas.width = width * dpr
    canvas.height = height * dpr
    canvas.style.width = width + 'px'
    canvas.style.height = height + 'px'

    ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
    initParticles(width, height)
  }

  function drawNebulae() {
    if (!ctx) return
    const scale = opacityScale.value
    for (const n of nebulae) {
      const grad = ctx.createRadialGradient(n.x, n.y, 0, n.x, n.y, n.radius)
      grad.addColorStop(0, `rgba(${n.color},${n.opacity * scale})`)
      grad.addColorStop(0.5, `rgba(${n.color},${n.opacity * scale * 0.4})`)
      grad.addColorStop(1, `rgba(${n.color},0)`)
      ctx.fillStyle = grad
      ctx.fillRect(n.x - n.radius, n.y - n.radius, n.radius * 2, n.radius * 2)
    }
  }

  function drawParticles(time: number) {
    if (!ctx) return
    const scale = opacityScale.value

    for (const p of particles) {
      if (p.isDrift) {
        p.x += p.vx
        p.y += p.vy
        if (p.x < -10) p.x = width + 10
        if (p.x > width + 10) p.x = -10
        if (p.y < -10) p.y = height + 10
        if (p.y > height + 10) p.y = -10
      }

      const twinkle = Math.sin(time * p.twinkleSpeed + p.twinklePhase) * 0.3 + 0.7
      const opacity = p.baseOpacity * twinkle * scale

      ctx.beginPath()
      ctx.arc(p.x, p.y, p.radius, 0, Math.PI * 2)

      if (p.isAccent) {
        const accent = getAccentColor()
        ctx.fillStyle = `rgba(${hexToRgb(accent).join(',')},${opacity})`
        ctx.shadowColor = accent
        ctx.shadowBlur = p.radius * 3
      } else {
        ctx.fillStyle = `rgba(255,255,255,${opacity})`
        ctx.shadowColor = 'transparent'
        ctx.shadowBlur = 0
      }

      ctx.fill()
    }

    ctx.shadowColor = 'transparent'
    ctx.shadowBlur = 0
  }

  function frame(time: number) {
    if (!ctx || !canvas) return
    ctx.clearRect(0, 0, width, height)
    drawNebulae()
    drawParticles(time)
    rafId = requestAnimationFrame(frame)
  }

  function start() {
    if (rafId) return
    lastTime = performance.now()
    rafId = requestAnimationFrame(frame)
  }

  function stop() {
    if (rafId) {
      cancelAnimationFrame(rafId)
      rafId = 0
    }
  }

  function setTheme(isDark: boolean) {
    darkMode = isDark
    opacityScale.value = isDark ? 1 : 0.15
    if (width && height) {
      initParticles(width, height)
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

  return { start, stop, setTheme }
}
