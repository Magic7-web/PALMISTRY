import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

export default defineConfig({
  plugins: [uni()],
  server: {
    proxy: {
      '/api/qwen': {
        target: 'https://dashscope.aliyuncs.com',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/qwen/, '/api/v1/services/aigc/multimodal-generation/generation'),
        secure: true,
        headers: {
          'Origin': 'https://dashscope.aliyuncs.com'
        }
      },
      '/api/paddle-js': {
        target: 'https://cdn.paddle.com',
        changeOrigin: true,
        rewrite: () => '/paddle/v2/paddle.js',
        secure: true
      }
    }
  }
})
