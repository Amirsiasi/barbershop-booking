import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// In development every /api call is forwarded to Spring Boot, so the browser
// sees one origin and no CORS configuration is needed (same as nginx in production).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': process.env.API_TARGET || 'http://localhost:8080',
    },
  },
})
