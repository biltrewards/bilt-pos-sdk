import process from 'node:process';
import { defineConfig } from 'vitest/config';

// The development bridge sends no CORS headers yet, so under `pnpm dev` the page reaches it
// through its own origin and Vite forwards `/health` and `/v1` (HTTP and WebSocket) to it; see
// `bridgeOptions()` in `src/main.ts`. `BRIDGE_URL` overrides the target.
const bridge = process.env.BRIDGE_URL ?? 'http://127.0.0.1:48333';

export default defineConfig({
  server: {
    proxy: {
      '/health': { target: bridge, changeOrigin: true },
      '/v1': { target: bridge, changeOrigin: true, ws: true },
    },
  },
  test: {
    environment: 'jsdom',
    include: ['test/**/*.test.ts'],
  },
});
