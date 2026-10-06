import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { Ajv2020 } from 'ajv/dist/2020';
import addFormats from 'ajv-formats';
import { describe, expect, it } from 'vitest';
import { loadSpecAsJsonSchema, specDir } from './spec';

interface ExampleEntry {
  file: string;
  schema: string;
}

const manifest = JSON.parse(
  readFileSync(resolve(specDir, 'examples/index.json'), 'utf8'),
) as ExampleEntry[];

const ajv = new Ajv2020({
  strict: false,
  allErrors: true,
  keywords: ['example', 'x-tagGroups'],
});
addFormats(ajv);
ajv.addSchema(loadSpecAsJsonSchema());

function validate(schema: string, value: unknown) {
  const validator = ajv.getSchema(
    `https://bilt.com/pos/session-protocol/openapi.yaml#/components/schemas/${schema}`,
  );
  if (!validator) {
    throw new Error(`no schema named ${schema}`);
  }
  const ok = validator(value);
  return { ok, errors: validator.errors ?? [] };
}

describe('schema/session-protocol/examples', () => {
  it('names only schemas the spec defines', () => {
    for (const entry of manifest) {
      expect(
        ajv.getSchema(
          `https://bilt.com/pos/session-protocol/openapi.yaml#/components/schemas/${entry.schema}`,
        ),
      ).toBeDefined();
    }
  });

  for (const entry of manifest) {
    it(`${entry.file} validates against ${entry.schema}`, () => {
      const value = JSON.parse(readFileSync(resolve(specDir, 'examples', entry.file), 'utf8'));
      const { ok, errors } = validate(entry.schema, value);
      expect(errors, JSON.stringify(errors, null, 2)).toEqual([]);
      expect(ok).toBe(true);
    });
  }

  it('rejects a reply that names no step', () => {
    const { ok } = validate('StepReply', { total: '1.00' });
    expect(ok).toBe(false);
  });

  it('rejects a money value that is not a decimal string', () => {
    const { ok } = validate('Money', '12,34');
    expect(ok).toBe(false);
  });

  it('rejects an event of an unknown type', () => {
    const { ok } = validate('Event', {
      seq: 1,
      at: '2026-10-06T00:00:00Z',
      type: 'nope',
      payload: {},
    });
    expect(ok).toBe(false);
  });
});
