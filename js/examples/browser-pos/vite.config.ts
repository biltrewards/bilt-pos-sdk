import process from 'node:process';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

// The development Session Host and the Terminal Bridge do not send CORS headers yet, so a page
// served from another origin (the Vite dev server) cannot reach `http://127.0.0.1:48333`
// directly. The "proxy" bridge route in the settings panel points the SDK at this page's own
// origin instead, and Vite forwards `/health` and `/v1` (HTTP and WebSocket) to the bridge.
// `BRIDGE_URL` overrides the target, e.g. when the bridge had to fall back to port 48334.
const bridge = process.env.BRIDGE_URL ?? 'http://127.0.0.1:48333';

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/health': { target: bridge, changeOrigin: true },
      '/v1': { target: bridge, changeOrigin: true, ws: true },
    },
  },
  test: {
    environment: 'jsdom',
    include: ['test/**/*.test.{ts,tsx}'],
    setupFiles: ['test/setup.ts'],
  },
});
