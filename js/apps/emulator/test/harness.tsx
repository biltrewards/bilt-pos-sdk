// Shared pieces of the pane tests: the scripted bridge probe, the SDK package's in-memory
// `BiltPos` double, an IndexedDB-backed sale store per test, and a render helper that waits for
// the lane to be open.
import type { BridgeDetect, BridgeDetection } from '@bilt/pos-react/bridge';
import type { Health } from '@bilt/pos-sdk';
import { render, screen, waitFor, type RenderResult } from '@testing-library/react';
import { expect } from 'vitest';
import {
  MockBiltPos,
  MockShopperSession,
  MockTerminalSession,
} from '../../../packages/sdk/test/mock-pos';
import * as fx from '../../../packages/sdk/test/fixtures';
import { App, type AppProps, type Tab } from '../src/App';
import { DEFAULT_SETTINGS, STORAGE_KEY, type Settings } from '../src/settings';
import { IndexedDbSaleStore } from '../src/store/sales-store';

export { MockBiltPos, MockShopperSession, MockTerminalSession, fx };

export const HEALTH: Health = {
  host: 'bridge',
  hostVersion: '0.30.0',
  sdkVersion: '0.30.0',
  protocolVersions: ['1'],
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
  /** The session the lane started, once open. */
  session(): MockShopperSession;
  terminal(): MockTerminalSession;
}

/** Renders the app against a ready bridge and the double, and waits for the lane to be open. */
export async function renderApp(
  options: { tab?: Tab; settings?: Partial<Settings>; pos?: MockBiltPos } & Partial<AppProps> = {},
): Promise<Rendered> {
  if (options.settings) saveSettings(options.settings);
  const pos = options.pos ?? new MockBiltPos();
  const store = (options.sales as IndexedDbSaleStore | undefined) ?? freshStore();
  const view = render(
    <App
      detect={options.detect ?? probe('ready')}
      connect={async () => pos}
      sales={store}
      {...(options.tab ? { initialTab: options.tab } : {})}
    />,
  );
  await waitFor(() => expect(screen.getByTestId('session-id').textContent).toMatch(/^ses_/));
  const session = () => {
    const started = pos.sessions[pos.sessions.length - 1];
    if (!started) throw new Error('no session started');
    return started;
  };
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

/** Lets pending promise callbacks run. */
export function flush(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, 0));
}
