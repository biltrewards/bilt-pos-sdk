import { defineConfig } from 'tsup';

export default defineConfig({
  entry: { index: 'src/index.ts', internal: 'src/internal.ts', bridge: 'src/bridge/index.ts' },
  format: ['esm', 'cjs'],
  dts: true,
  sourcemap: true,
  clean: true,
  target: 'es2022',
  external: ['@bilt/pos-protocol'],
});
