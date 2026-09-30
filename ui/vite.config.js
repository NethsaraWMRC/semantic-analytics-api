import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// /chat is forwarded to the Spring Boot app, so the browser never makes a
// cross-origin request and the backend needs no CORS configuration.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/chat': 'http://localhost:8080',
    },
  },
})
