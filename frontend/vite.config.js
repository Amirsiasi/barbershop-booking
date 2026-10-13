import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// In development every /api call is forwarded to Spring Boot, so the browser
// sees one origin and no CORS configuration is needed (same as nginx in production).
// changeOrigin must stay false: the Host header then still matches the browser's
// Origin header, otherwise Spring Security rejects POST requests as cross-origin (403).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.API_TARGET || 'http://localhost:8080',
        changeOrigin: false,
      },
    },
  },
})
