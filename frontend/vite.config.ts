import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5656,
    proxy: {
      '/api': 'http://localhost:4545',
      '/agent': 'http://localhost:4545',
      '/yjb-market-api': {
        target: 'https://app-api.yangjibao.com',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/yjb-market-api/, ''),
      },
    },
  },
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
  },
})
