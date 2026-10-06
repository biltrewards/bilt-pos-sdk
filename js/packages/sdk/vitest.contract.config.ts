import { defineConfig } from 'vitest/config';

// The contract suite drives a real Session Host (see test/contract/host.ts); it is slow and
// needs a JDK, so it runs on its own: `pnpm test:contract`.
export default defineConfig({
  test: {
    include: ['test/contract/**/*.test.ts'],
    testTimeout: 60_000,
    hookTimeout: 240_000,
    fileParallelism: false,
  },
});
