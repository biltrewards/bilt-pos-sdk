// Smoke tests: the page shows the install prompt while the bridge is missing, and runs a sale
// end to end against a scripted probe and the SDK package's in-memory `BiltPos` double.
import type { BridgeDetection } from '@bilt/pos-sdk/bridge';
import { afterEach, describe, expect, it } from 'vitest';
import { MockBiltPos } from '../../../packages/sdk/test/mock-pos';
import { mountRegister } from '../src/register';
import { DEFAULT_SETTINGS, STORAGE_KEY } from '../src/settings';

const ready: BridgeDetection = {
  status: 'ready',
  baseUrl: 'http://127.0.0.1:48333',
  health: {
    host: 'bridge',
    hostVersion: '0.0.0',
    sdkVersion: '0.0.0',
    protocolVersions: ['2'],
  },
};

async function until(check: () => boolean): Promise<void> {
  for (let i = 0; i < 50 && !check(); i++) await new Promise((r) => setTimeout(r, 10));
  expect(check()).toBe(true);
}

function button(root: HTMLElement, text: RegExp): HTMLButtonElement {
  const found = [...root.querySelectorAll('button')].find((b) => text.test(b.textContent ?? ''));
  if (!found) throw new Error(`no button ${text}`);
  return found;
}

function mount(detect: () => Promise<BridgeDetection>, pos = new MockBiltPos()) {
  const root = document.createElement('main');
  document.body.append(root);
  void mountRegister(root, { detect, connect: async () => pos });
  return root;
}

afterEach(() => {
  document.body.replaceChildren();
  localStorage.clear();
});

describe('the vanilla TypeScript register', () => {
  it('asks for the bridge while it is missing', async () => {
    const root = mount(async () => ({ status: 'missing', probed: [] }));
    await until(() => root.querySelector('[data-status="missing"]') !== null);
    expect(root.textContent).toContain('Install the Bilt Terminal Bridge');
  });

  it('offers a retry when the connection fails after the probe succeeds', async () => {
    const root = document.createElement('main');
    document.body.append(root);
    let attempts = 0;
    void mountRegister(root, {
      detect: async () => ready,
      connect: async () => {
        if (++attempts === 1) throw new Error('bridge restarted');
        return new MockBiltPos();
      },
    });
    await until(() => root.querySelector('[data-status="connect-failed"]') !== null);
    expect(root.textContent).toContain('bridge restarted');
    button(root, /Retry/).click();
    await until(() => root.querySelector('form') !== null);
  });

  it('rings, signs in, settles with the recomputed total and ends', async () => {
    const pos = new MockBiltPos();
    const root = mount(async () => ready, pos);
    await until(() => root.querySelector('form') !== null);
    root.querySelector('form')!.requestSubmit();

    await until(() => /Terminal session ses_/.test(root.textContent ?? ''));
    expect(pos.sessions[0]?.kind).toBe('terminal');
    expect(JSON.parse(localStorage.getItem(STORAGE_KEY)!).saleId).toBe(DEFAULT_SETTINGS.saleId);

    button(root, /Large Vanilla Candle/).click();
    const total = () => root.querySelector('[data-testid="grand-total"]')?.textContent ?? '';
    await until(() => total().includes('27.21'));

    root.querySelector<HTMLInputElement>('input[name="phone"]')!.value = '+12015550123';
    button(root, /Sign in/).click();
    await until(() => (root.textContent ?? '').includes('Member lookup pending'));

    button(root, /^Pay$/).click();
    // The double's rebate leaves line totals untouched, so the re-taxed total is the original;
    // the discounted case is covered in catalog.test.ts.
    await until(() => (root.querySelector('pre')?.textContent ?? '').includes('Paid 27.21 USD'));

    button(root, /End session/).click();
    await until(() => root.querySelector('form') !== null);
  });

  it('runs a local session without a terminal', async () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ ...DEFAULT_SETTINGS, mode: 'local' }));
    const pos = new MockBiltPos();
    const root = mount(async () => ready, pos);
    await until(() => root.querySelector('form') !== null);
    root.querySelector('form')!.requestSubmit();
    await until(() => /Local session/.test(root.textContent ?? ''));
    expect(pos.sessions[0]?.kind).toBe('local');
    expect(button(root, /^Pay$/).disabled).toBe(true);
  });
});
