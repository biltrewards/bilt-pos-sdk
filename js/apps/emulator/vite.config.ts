import process from 'node:process';
import { fileURLToPath } from 'node:url';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

// The development bridge sends no CORS headers yet, so a page served from the Vite dev server
// cannot reach `http://127.0.0.1:48333` directly. The "proxy" bridge route in the Settings pane
// points the SDK at this page's own origin and Vite forwards `/health` and `/v1` (HTTP and
// WebSocket) to the bridge. `BRIDGE_URL` overrides the target, e.g. when the bridge fell back
// to port 48334; the "direct" route uses the port from the Settings pane instead.
const bridge = process.env.BRIDGE_URL ?? 'http://127.0.0.1:48333';

const sibling = (path: string) => fileURLToPath(new URL(path, import.meta.url));

/**
 * The tests run against the SDK's in-memory doubles, which import the SDK from its sources;
 * aliasing the packages to those sources keeps one copy of `SessionError` in play.
 */
export const sourceAliases = [
  {
    find: /^@bilt\/pos-sdk\/bridge$/,
    replacement: sibling('../../packages/sdk/src/bridge/index.ts'),
  },
  { find: /^@bilt\/pos-sdk$/, replacement: sibling('../../packages/sdk/src/index.ts') },
  { find: /^@bilt\/pos-protocol$/, replacement: sibling('../../packages/protocol/src/index.ts') },
];

export default defineConfig(({ mode }) => ({
  plugins: [react()],
  resolve: mode === 'test' ? { alias: sourceAliases } : {},
  server: {
    proxy: {
      '/health': { target: bridge, changeOrigin: true },
      '/v1': { target: bridge, changeOrigin: true, ws: true },
    },
  },
  test: {
    environment: 'jsdom',
    include: ['test/**/*.test.{ts,tsx}'],
    exclude: ['test/contract/**'],
    setupFiles: ['test/setup.ts'],
  },
}));
