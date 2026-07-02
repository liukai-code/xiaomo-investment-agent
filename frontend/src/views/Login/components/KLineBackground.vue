<script setup lang="ts">
import { computed } from 'vue'

interface Candle {
  open: number
  high: number
  low: number
  close: number
}

function generateCandles(count: number): Candle[] {
  const candles: Candle[] = []
  let price = 50

  for (let i = 0; i < count; i++) {
    const change = (Math.random() - 0.48) * 8
    const open = price
    price = Math.max(10, Math.min(90, price + change))
    const close = price
    const high = Math.max(open, close) + Math.random() * 5
    const low = Math.min(open, close) - Math.random() * 5

    candles.push({
      open: Math.max(0, open),
      high: Math.max(0, high),
      low: Math.max(0, low),
      close: Math.max(0, close),
    })
  }

  return candles
}

const candles = computed(() => generateCandles(70))

const viewWidth = 70 * 14
const viewHeight = 100

function candleY(value: number): number {
  return viewHeight - (value / 100) * viewHeight
}
</script>

<template>
  <div class="kline-bg">
    <svg
      class="kline-svg"
      :viewBox="`0 0 ${viewWidth * 2} ${viewHeight}`"
      preserveAspectRatio="none"
      xmlns="http://www.w3.org/2000/svg"
    >
      <g v-for="(copy, copyIdx) in 2" :key="copyIdx">
        <g
          v-for="(candle, i) in candles"
          :key="`${copyIdx}-${i}`"
        >
          <!-- Wick -->
          <line
            :x1="copyIdx * viewWidth + i * 14 + 7"
            :y1="candleY(candle.high)"
            :x2="copyIdx * viewWidth + i * 14 + 7"
            :y2="candleY(candle.low)"
            :stroke="candle.close >= candle.open ? '#22c55e' : '#ef4444'"
            stroke-width="0.5"
          />
          <!-- Body -->
          <rect
            :x="copyIdx * viewWidth + i * 14 + 3"
            :y="candleY(Math.max(candle.open, candle.close))"
            width="8"
            :height="Math.max(0.5, Math.abs(candleY(candle.open) - candleY(candle.close)))"
            :fill="candle.close >= candle.open ? '#22c55e' : '#ef4444'"
            rx="0.5"
          />
        </g>
      </g>
    </svg>
  </div>
</template>

<style scoped>
.kline-bg {
  position: fixed;
  left: 0;
  top: 10%;
  width: 100%;
  height: 70%;
  z-index: 2;
  opacity: 0.25;
  pointer-events: none;
  overflow: hidden;
  animation: loginFadeIn 1.5s ease-out 0.4s both;
}

.kline-svg {
  width: 200%;
  height: 100%;
  animation: klineScroll 80s linear infinite;
}

@keyframes klineScroll {
  from { transform: translateX(0); }
  to { transform: translateX(-50%); }
}

@media (max-width: 768px) {
  .kline-bg {
    display: none;
  }
}
</style>
