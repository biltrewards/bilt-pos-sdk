import { defineConfig } from 'vitest/config';
import { sourceAliases } from './vite.config';

// The contract smoke test drives a real Session Host and a scripted terminal (the launcher and
// fake terminal of `@bilt/pos-sdk`'s own contract suite); it needs a JDK and runs on its own:
// `pnpm test:contract`.
export default defineConfig({
  resolve: { alias: sourceAliases },
  test: {
    include: ['test/contract/**/*.test.ts'],
    setupFiles: ['test/contract/setup.ts'],
    testTimeout: 120_000,
    hookTimeout: 240_000,
    fileParallelism: false,
  },
});
