// Smoke tests: the page renders the install prompt while the bridge is missing and the register
// once it is ready, against a scripted probe and the SDK package's in-memory `BiltPos` double.
import type { BridgeDetect, BridgeDetection } from '@bilt/pos-react/bridge';
import type { Health } from '@bilt/pos-sdk';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { MockBiltPos } from '../../../packages/sdk/test/mock-pos';
import { App } from '../src/App';
import { DEFAULT_SETTINGS, STORAGE_KEY } from '../src/settings';

const HEALTH: Health = {
  host: 'bridge',
  hostVersion: '0.30.0',
  sdkVersion: '0.30.0',
  protocolVersions: ['1'],
  terminals: [{ poiId: DEFAULT_SETTINGS.poiId }],
};

function probe(status: BridgeDetection['status']): BridgeDetect {
  return async () =>
    status === 'missing'
      ? { status, probed: ['http://127.0.0.1:48333/health'] }
      : { status, baseUrl: 'http://127.0.0.1:48333', health: HEALTH };
}

describe('the browser POS page', () => {
  it('shows the install prompt while the bridge is missing', async () => {
    const connect = async () => {
      throw new Error('connect must not run while the bridge is missing');
    };
    render(<App detect={probe('missing')} connect={connect} />);
    await waitFor(() =>
      expect(screen.getByRole('alert').getAttribute('data-status')).toBe('missing'),
    );
    expect(screen.getByText(/Install the Bilt Terminal Bridge/)).toBeTruthy();
    expect(screen.getByText(/Download for/).getAttribute('href')).toContain('terminal-bridge.html');
  });

  it('starts a terminal session and rings an item once the bridge is ready', async () => {
    const pos = new MockBiltPos();
    render(<App detect={probe('ready')} connect={async () => pos} />);

    await waitFor(() => expect(screen.getByText(/session ses_/)).toBeTruthy());
    expect(pos.sessions).toHaveLength(1);
    expect(pos.sessions[0]?.kind).toBe('terminal');
    expect(screen.getByText(/engine mock/)).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: /Large Vanilla Candle/ }));
    await waitFor(() => expect(screen.getByTestId('grand-total').textContent).toContain('27.21'));
    expect(screen.getByText('last change: INCREMENTAL')).toBeTruthy();
    expect(screen.getByRole('button', { name: /^Pay/ })).toBeTruthy();
  });

  it('runs a local session without a terminal when the settings say so', async () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ ...DEFAULT_SETTINGS, mode: 'local' }));
    const pos = new MockBiltPos();
    render(<App detect={probe('ready')} connect={async () => pos} />);

    await waitFor(() => expect(screen.getByText(/Local session · lane/)).toBeTruthy());
    expect(pos.sessions[0]?.kind).toBe('local');
    expect(screen.getByText(/need a terminal session/)).toBeTruthy();
  });
});
