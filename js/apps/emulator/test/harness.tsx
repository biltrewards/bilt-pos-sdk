// Shared pieces of the UI tests: the scripted bridge probe, the SDK package's in-memory `BiltPos`
// double, an IndexedDB-backed sale store per test, and a render helper that waits for the
// connection and, on request, starts a checkout.
import type { BridgeDetect, BridgeDetection } from '@bilt/pos-react/bridge';
import type { Health } from '@bilt/pos-sdk';
import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
  type RenderResult,
} from '@testing-library/react';
import { expect } from 'vitest';
import {
  MockBiltPos,
  MockShopperSession,
  MockTerminalSession,
} from '../../../packages/sdk/test/mock-pos';
import * as fx from '../../../packages/sdk/test/fixtures';
import { App, type AppProps } from '../src/App';
import { DEFAULT_SETTINGS, STORAGE_KEY, type Settings } from '../src/settings';
import { IndexedDbSaleStore } from '../src/store/sales-store';

export { MockBiltPos, MockShopperSession, MockTerminalSession, fx };

export const HEALTH: Health = {
  host: 'bridge',
  hostVersion: '0.30.0',
  sdkVersion: '0.30.0',
  protocolVersions: ['2'],
  terminal: { label: 'Lane 3', model: 'VictaLane', reachable: true },
};

export function probe(status: BridgeDetection['status']): BridgeDetect {
  return async () =>
    status === 'missing'
      ? { status, probed: ['http://127.0.0.1:48333/health'] }
      : { status, baseUrl: 'http://127.0.0.1:48333', health: HEALTH };
}

let storeCounter = 0;

/** A fresh IndexedDB database per call, so tests do not see each other's sales. */
export function freshStore(): IndexedDbSaleStore {
  return new IndexedDbSaleStore(indexedDB, `bilt-pos-emulator-test-${++storeCounter}`);
}

export function saveSettings(overrides: Partial<Settings>): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify({ ...DEFAULT_SETTINGS, ...overrides }));
}

export interface Rendered {
  readonly pos: MockBiltPos;
  readonly store: IndexedDbSaleStore;
  readonly view: RenderResult;
  /** The last session started, the checkout once one is open. */
  session(): MockShopperSession;
  terminal(): MockTerminalSession;
}

export function button(name: string | RegExp): HTMLButtonElement {
  return screen.getByRole('button', { name }) as HTMLButtonElement;
}

export function region(name: string): HTMLElement {
  return screen.getByRole('region', { name });
}

/** The Events feed as text, newest first. */
export function events(): string {
  return screen.getByTestId('log-events').textContent ?? '';
}

/** Renders the app against a ready bridge and the double, waits for the connection, and optionally opens a checkout. */
export async function renderApp(
  options: {
    settings?: Partial<Settings>;
    pos?: MockBiltPos;
    checkout?: boolean;
  } & Partial<AppProps> = {},
): Promise<Rendered> {
  if (options.settings) saveSettings(options.settings);
  const pos = options.pos ?? new MockBiltPos();
  const store = (options.sales as IndexedDbSaleStore | undefined) ?? freshStore();
  const view = render(
    <App
      detect={options.detect ?? probe('ready')}
      connect={async () => pos}
      sales={store}
      {...(options.initialTab ? { initialTab: options.initialTab } : {})}
      {...(options.initialSalePane ? { initialSalePane: options.initialSalePane } : {})}
    />,
  );
  await waitFor(() => expect(screen.getByTestId('status').textContent).toMatch(/Connected/));
  const session = () => {
    const started = pos.sessions[pos.sessions.length - 1];
    if (!started) throw new Error('no session started');
    return started;
  };
  if (options.checkout) {
    fireEvent.click(button('Start Checkout'));
    await waitFor(() => expect(screen.getByTestId('session-id').textContent).toMatch(/^ses_/));
  }
  return {
    pos,
    store,
    view,
    session,
    terminal: () => {
      const started = session();
      if (!(started instanceof MockTerminalSession)) throw new Error('the session has no terminal');
      return started;
    },
  };
}

/** Taps a product on the Sale tab's grid. */
export function addProduct(name: string): void {
  fireEvent.click(
    within(screen.getByLabelText('Products')).getByRole('button', { name: new RegExp(name) }),
  );
}

/** The `index`th argument of a spy's first call; the double's methods declare no parameters. */
export function argOf(
  spy: { mock: { calls: readonly (readonly unknown[])[] } },
  index: number,
): unknown {
  return spy.mock.calls[0]?.[index];
}

/** Lets pending promise callbacks run. */
export function flush(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, 0));
}
