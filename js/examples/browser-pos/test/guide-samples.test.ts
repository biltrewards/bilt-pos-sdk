// @vitest-environment node
// Every TypeScript block in docs/javascript-sdk-integration.md must appear verbatim in one of
// the files under src/guide-samples, which tsc and eslint check with the rest of the example.
// That keeps the guide's samples compiling as the SDK moves.
import { readdirSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

const GUIDE = fileURLToPath(
  new URL('../../../../docs/javascript-sdk-integration.md', import.meta.url),
);
const SAMPLES = fileURLToPath(new URL('../src/guide-samples/', import.meta.url));

function normalize(code: string): string {
  return code
    .split('\n')
    .map((line) => line.trimEnd())
    .join('\n')
    .trim();
}

function codeBlocks(markdown: string): string[] {
  const blocks: string[] = [];
  const fence = /```(?:ts|tsx|typescript)\n([\s\S]*?)```/g;
  for (const match of markdown.matchAll(fence)) blocks.push(match[1] ?? '');
  return blocks;
}

describe('the JavaScript SDK guide', () => {
  const guide = readFileSync(GUIDE, 'utf8');
  const blocks = codeBlocks(guide);
  const corpus = readdirSync(SAMPLES)
    .filter((name) => /\.tsx?$/.test(name))
    .map((name) => normalize(readFileSync(`${SAMPLES}${name}`, 'utf8')))
    .join('\n\n');

  it('has TypeScript samples', () => {
    expect(blocks.length).toBeGreaterThan(10);
  });

  it.each(blocks.map((block, index) => [index + 1, block] as const))(
    'sample %i is type-checked from src/guide-samples',
    (_index, block) => {
      expect(corpus).toContain(normalize(block));
    },
  );
});
