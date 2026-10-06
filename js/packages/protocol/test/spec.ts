// Loads the multi-file OpenAPI document into one JSON Schema root, so tests can validate
// examples against `#/components/schemas/<Name>` with Ajv. The spec's own files use relative
// `$ref`s between them; this resolves them the simple way (every file is read once and refs
// are rewritten to point into the merged document) rather than pulling in a bundler.
import { readFileSync, readdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { load } from 'js-yaml';

const here = dirname(fileURLToPath(import.meta.url));
export const specDir = resolve(here, '../../../../schema/session-protocol');

type Json = Record<string, unknown>;

function rewriteRefs(node: unknown, file: string): unknown {
  if (Array.isArray(node)) {
    return node.map((child) => rewriteRefs(child, file));
  }
  if (node && typeof node === 'object') {
    const out: Json = {};
    for (const [key, value] of Object.entries(node as Json)) {
      if (key === 'discriminator') {
        // Ajv does not implement discriminator mappings; the oneOf branches each pin their
        // `type`, so validation is unambiguous without it.
        continue;
      }
      if (key === '$ref' && typeof value === 'string') {
        out[key] = resolveRef(value, file);
      } else {
        out[key] = rewriteRefs(value, file);
      }
    }
    return out;
  }
  return node;
}

/** Maps `./basket.yaml#/Basket` and `#/Basket` (inside a component file) to `#/$defs/<file>/Basket`. */
function resolveRef(ref: string, file: string): string {
  const [target, pointer = ''] = ref.split('#');
  if (target === '' || target === undefined) {
    return `#/$defs/${file}${pointer}`;
  }
  if (target.endsWith('openapi.yaml')) {
    return `#${pointer}`;
  }
  const name = target.replace(/^(\.\.\/|\.\/)+/, '').replace(/^components\//, '');
  return `#/$defs/${name}${pointer}`;
}

/** The root document: `components.schemas` from openapi.yaml plus every component file under `$defs`. */
export function loadSpecAsJsonSchema(): Json {
  const root = load(readFileSync(resolve(specDir, 'openapi.yaml'), 'utf8')) as Json;
  const components = root.components as Json;
  const schemas = rewriteRefs(components.schemas, 'openapi.yaml') as Json;
  const defs: Json = {};
  const componentDir = resolve(specDir, 'components');
  for (const file of readdirSync(componentDir)) {
    const doc = load(readFileSync(resolve(componentDir, file), 'utf8'));
    defs[file] = rewriteRefs(doc, file);
  }
  return {
    $id: 'https://bilt.com/pos/session-protocol/openapi.yaml',
    components: { schemas },
    $defs: defs,
  };
}
