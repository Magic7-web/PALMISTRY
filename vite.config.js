import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

export default defineConfig({
  plugins: [uni()],
  server: {
    proxy: {
      '/api/qwen': {
        target: 'http://localhost:3001',
        changeOrigin: true
      },
      '/api/payment': {
        target: 'http://localhost:3001',
        changeOrigin: true
      }
    }
  }
})
