// Regenerates src/generated/openapi.ts from schema/session-protocol/openapi.yaml.
// The output is committed; CI fails when it is out of date (`pnpm generate:check`).
import { mkdir, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import openapiTS, { astToString } from 'openapi-typescript';

const here = dirname(fileURLToPath(import.meta.url));
const spec = resolve(here, '../../../../schema/session-protocol/openapi.yaml');
const out = resolve(here, '../src/generated/openapi.ts');

const header = `/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \\| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\\__|
 *
 *   Bilt POS SDK
 *
 *   This file is generated from schema/session-protocol/openapi.yaml by openapi-typescript.
 *   Do not edit by hand; run \`pnpm generate\` in js/ instead.
 */
`;

const ast = await openapiTS(pathToFileURL(spec), {
  // Money, Duration and the rest are plain strings; keeping the aliases named makes the
  // generated types read like the spec.
  rootTypes: false,
});
await mkdir(dirname(out), { recursive: true });
await writeFile(out, header + astToString(ast));
console.log(`generated ${out}`);
