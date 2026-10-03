import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  return {
    plugins: [react()],
    server: {
      // in development /chat is forwarded to Spring Boot, so the browser never makes a
      // cross-origin request and the backend needs no CORS configuration
      proxy: {
        '/chat': env.VITE_PROXY_TARGET || 'http://localhost:8080',
      },
    },
  }
})
