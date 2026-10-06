import { defineConfig } from 'tsup';

export default defineConfig({
  entry: { index: 'src/index.ts', bridge: 'src/bridge.ts' },
  format: ['esm', 'cjs'],
  dts: true,
  sourcemap: true,
  clean: true,
  target: 'es2022',
  external: ['react', 'react/jsx-runtime', '@bilt/pos-sdk', '@bilt/pos-protocol'],
});
