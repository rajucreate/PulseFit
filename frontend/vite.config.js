import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

const gateway = 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: gateway,
        changeOrigin: true,
      },
    },
  },
  preview: {
    port: 4173,
    proxy: {
      '/api': {
        target: gateway,
        changeOrigin: true,
      },
    },
  },
});
