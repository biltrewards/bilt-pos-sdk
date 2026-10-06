import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    // The contract suite under test/contract needs a JDK and a Session Host; `pnpm test:contract`.
    include: ['test/**/*.test.ts'],
    exclude: ['test/contract/**', '**/node_modules/**', '**/dist/**'],
    typecheck: {
      enabled: true,
      include: ['test/**/*.test-d.ts'],
      tsconfig: './tsconfig.json',
    },
  },
});
