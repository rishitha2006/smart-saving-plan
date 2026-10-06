import { defineConfig } from 'vite';

export default defineConfig({
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/css': 'http://localhost:8080',
      '/js': 'http://localhost:8080',
      '/expense': 'http://localhost:8080',
      '/income': 'http://localhost:8080',
      '/onboarding': 'http://localhost:8080',
      '/login': 'http://localhost:8080',
      '/signup': 'http://localhost:8080',
      '/logout': 'http://localhost:8080',
      '/budget': 'http://localhost:8080',
      '/profile': 'http://localhost:8080',
      '/settings': 'http://localhost:8080'
    }
  },
  build: { outDir: 'dist' }
});
