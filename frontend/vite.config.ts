import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// In development the browser talks only to Vite (localhost:5173); Vite forwards /api/* to Spring Boot.
// Same origin for the browser = no CORS needed in dev.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
