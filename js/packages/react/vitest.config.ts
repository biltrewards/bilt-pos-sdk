import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vitest/config';

const sibling = (path: string) => fileURLToPath(new URL(path, import.meta.url));

export default defineConfig({
  // The tests run the hooks against the SDK's in-memory doubles, which import the SDK from its
  // sources; aliasing the packages to those sources keeps one copy of `SessionError` in play.
  resolve: {
    alias: [
      { find: /^@biltorg\/pos-sdk\/bridge$/, replacement: sibling('../sdk/src/bridge/index.ts') },
      { find: /^@biltorg\/pos-sdk$/, replacement: sibling('../sdk/src/index.ts') },
      { find: /^@biltorg\/pos-protocol$/, replacement: sibling('../protocol/src/index.ts') },
    ],
  },
  test: {
    environment: 'jsdom',
    include: ['test/**/*.test.{ts,tsx}'],
    setupFiles: ['test/setup.ts'],
    typecheck: {
      enabled: true,
      include: ['test/**/*.test-d.{ts,tsx}'],
      tsconfig: './tsconfig.json',
    },
  },
});
