import { onMounted, onUnmounted, type Ref } from 'vue'

interface DataLine {
  data: number[]
  color: string
  lineWidth: number
  speed: number
  offset: number
}

interface ChartOptions {
  lineCount?: number
}

function hexToRgb(hex: string): [number, number, number] {
  const h = hex.replace('#', '')
  return [
    parseInt(h.substring(0, 2), 16),
    parseInt(h.substring(2, 4), 16),
    parseInt(h.substring(4, 6), 16),
  ]
}

export function useFinanceChartCanvas(
  container: Ref<HTMLElement | undefined>,
  options: ChartOptions = {}
) {
  const { lineCount = 4 } = options

  let canvas: HTMLCanvasElement | null = null
  let ctx: CanvasRenderingContext2D | null = null
  let lines: DataLine[] = []
  let rafId = 0
  let width = 0
  let height = 0
  let resizeObserver: ResizeObserver | null = null

  function getAccentColor(): string {
    return getComputedStyle(document.documentElement).getPropertyValue('--accent').trim() || '#2563eb'
  }

  function generateDataPoint(prev: number, volatility: number): number {
    const change = (Math.random() - 0.48) * volatility
    return Math.max(0.05, Math.min(0.95, prev + change))
  }

  function initLines(w: number, h: number) {
    const accent = getAccentColor()
    const [ar, ag, ab] = hexToRgb(accent)

    const palette = [
      `rgba(${ar},${ag},${ab},0.5)`,
      `rgba(${Math.round(ar * 0.7 + 100)},${Math.round(ag * 0.5 + 120)},${ab},0.4)`,
      `rgba(${Math.round(ar * 0.4 + 150)},${Math.round(ag * 0.3 + 130)},${Math.round(ab * 0.9)},0.35)`,
      `rgba(${Math.round(ar * 0.5 + 80)},${Math.round(ag * 0.6 + 90)},${Math.round(ab * 0.8 + 40)},0.3)`,
    ]

    lines = []
    const pointCount = Math.ceil(w / 3) + 20

    for (let i = 0; i < lineCount; i++) {
      const data: number[] = []
      let val = 0.3 + Math.random() * 0.4
      const volatility = 0.008 + Math.random() * 0.012
      for (let j = 0; j < pointCount; j++) {
        val = generateDataPoint(val, volatility)
        data.push(val)
      }
      lines.push({
        data,
        color: palette[i % palette.length],
        lineWidth: 1.5 - i * 0.2,
        speed: 0.3 + i * 0.15,
        offset: 0,
      })
    }
  }

  function drawGrid() {
    if (!ctx) return
    const gridColor = 'rgba(148, 163, 184, 0.08)'

    ctx.strokeStyle = gridColor
    ctx.lineWidth = 1

    const gridRows = 5
    for (let i = 1; i < gridRows; i++) {
      const y = (height / gridRows) * i
      ctx.beginPath()
      ctx.moveTo(0, y)
      ctx.lineTo(width, y)
      ctx.stroke()
    }

    const gridCols = 8
    for (let i = 1; i < gridCols; i++) {
      const x = (width / gridCols) * i
      ctx.beginPath()
      ctx.moveTo(x, 0)
      ctx.lineTo(x, height)
      ctx.stroke()
    }
  }

  function drawLine(line: DataLine, time: number) {
    if (!ctx || line.data.length < 2) return

    const step = 3
    const totalWidth = line.data.length * step
    line.offset = (line.offset + line.speed) % totalWidth

    const points: [number, number][] = []
    for (let i = 0; i < line.data.length; i++) {
      const x = i * step - line.offset + step
      if (x < -step || x > width + step) continue
      const y = line.data[i] * height * 0.8 + height * 0.1
      points.push([x, y])
    }

    if (points.length < 2) return

    ctx.beginPath()
    ctx.moveTo(points[0][0], points[0][1])
    for (let i = 1; i < points.length; i++) {
      const prev = points[i - 1]
      const curr = points[i]
      const cpx = (prev[0] + curr[0]) / 2
      ctx.quadraticCurveTo(prev[0], prev[1], cpx, (prev[1] + curr[1]) / 2)
    }
    const last = points[points.length - 1]
    ctx.lineTo(last[0], last[1])

    ctx.strokeStyle = line.color
    ctx.lineWidth = line.lineWidth
    ctx.stroke()

    ctx.lineTo(last[0], height)
    ctx.lineTo(points[0][0], height)
    ctx.closePath()

    const rgbMatch = line.color.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)/)
    if (rgbMatch) {
      const [, r, g, b] = rgbMatch
      const grad = ctx.createLinearGradient(0, 0, 0, height)
      grad.addColorStop(0, `rgba(${r},${g},${b},0.12)`)
      grad.addColorStop(1, `rgba(${r},${g},${b},0)`)
      ctx.fillStyle = grad
      ctx.fill()
    }
  }

  function pushNewData() {
    for (const line of lines) {
      const last = line.data[line.data.length - 1]
      const volatility = 0.008 + Math.random() * 0.012
      line.data.push(generateDataPoint(last, volatility))
      if (line.data.length > Math.ceil(width / 3) + 40) {
        line.data.shift()
      }
    }
  }

  let frameCount = 0

  function frame(time: number) {
    if (!ctx || !canvas) return
    ctx.clearRect(0, 0, width, height)

    drawGrid()

    for (const line of lines) {
      drawLine(line, time)
    }

    frameCount++
    if (frameCount % 8 === 0) {
      pushNewData()
    }

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
    initLines(width, height)
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
