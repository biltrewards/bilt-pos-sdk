// The shell and the connection card: the desktop's panel structure, the install prompt while the
// bridge is missing, the bridge terminal from `/health`, and the explicit checkout lifecycle.
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { App } from '../src/App';
import { loadSettings } from '../src/settings';
import { addProduct, button, events, freshStore, probe, region, renderApp } from './harness';

describe('the emulator shell', () => {
  it('lays the page out as the desktop emulator does', async () => {
    await renderApp();
    expect(screen.getByRole('heading', { name: 'Bilt POS Emulator' })).toBeTruthy();
    for (const name of ['Connection', 'Basket', 'Sale', 'Log']) expect(region(name)).toBeTruthy();
    expect(screen.queryByRole('region', { name: 'Loyalty sign-in' })).toBeNull();
    const screens = screen.getByRole('tablist', { name: 'Screens' });
    expect(Array.from(screens.querySelectorAll('[role=tab]')).map((t) => t.textContent)).toEqual([
      'Sale',
      'Stored Value',
      'Refund',
    ]);
    const feeds = screen.getByRole('tablist', { name: 'Log feeds' });
    expect(Array.from(feeds.querySelectorAll('[role=tab]')).map((t) => t.textContent)).toEqual([
      'Events',
      'Detailed',
      'Protocol',
    ]);
    for (const name of [
      'Disconnect',
      'Start Checkout',
      'Loyalty Sign-In',
      'Clear basket',
      'Abort operation',
    ]) {
      expect(button(name)).toBeTruthy();
    }
    expect(screen.getByLabelText('Identify')).toBeTruthy();
    expect(screen.queryByText(/Companion display/)).toBeNull();
  });

  it('shows the install prompt in the connection card while the bridge is missing', async () => {
    const connect = async () => {
      throw new Error('connect must not run while the bridge is missing');
    };
    render(<App detect={probe('missing')} connect={connect} sales={freshStore()} />);
    await waitFor(() =>
      expect(screen.getByRole('alert').getAttribute('data-status')).toBe('missing'),
    );
    expect(screen.getByTestId('bridge-status').textContent).toBe('Bridge: not found');
    expect(button('Connect').disabled).toBe(true);
    expect(screen.getByTestId('status').textContent).toContain('Disconnected');
  });

  it('shows the bridge terminal and passes the POI id through to the checkout', async () => {
    const { pos } = await renderApp();
    expect(screen.getByTestId('bridge-status').textContent).toBe(
      'Bridge: ready at http://127.0.0.1:48333 (0.30.0)',
    );
    expect(screen.getByTestId('bridge-terminal').textContent).toBe(
      'Terminal: Lane 3 · VictaLane · reachable',
    );
    const poiId = screen.getByLabelText('POI id') as HTMLInputElement;
    expect(poiId.value).toBe('');
    expect(poiId.disabled).toBe(true);

    fireEvent.click(button('Disconnect'));
    expect(poiId.disabled).toBe(false);
    fireEvent.change(poiId, { target: { value: 'VictaLane-2' } });
    fireEvent.click(button('Connect'));
    await waitFor(() => expect(screen.getByTestId('status').textContent).toMatch(/Connected/));
    expect(loadSettings().poiId).toBe('VictaLane-2');

    fireEvent.click(button('Start Checkout'));
    await waitFor(() => expect(pos.sessions).toHaveLength(1));
    expect(pos.sessions[0]).toMatchObject({ kind: 'terminal', poiId: 'VictaLane-2' });
    await waitFor(() => expect(button('End Checkout')).toBeTruthy());

    fireEvent.click(button('End Checkout'));
    await waitFor(() => expect(button('Start Checkout')).toBeTruthy());
    expect(pos.sessions[0]?.state).toBe('ended');
    expect(events()).toContain('Checkout session ended');
  });

  it('keeps a saved POI id and runs a local session when asked', async () => {
    const { pos } = await renderApp({ settings: { poiId: 'Lab-7' } });
    expect((screen.getByLabelText('POI id') as HTMLInputElement).value).toBe('Lab-7');
    fireEvent.click(button('Disconnect'));
    fireEvent.click(screen.getByLabelText('Local session'));
    fireEvent.click(button('Connect'));
    await waitFor(() => expect(screen.getByTestId('status').textContent).toMatch(/local sessions/));
    fireEvent.click(button('Start Checkout'));
    await waitFor(() => expect(pos.sessions[0]?.kind).toBe('local'));
    await waitFor(() => expect(screen.getByTestId('session-id').textContent).toMatch(/^ses_/));
    expect(button('Loyalty Sign-In').disabled).toBe(true);
    expect(screen.getByTestId('status').textContent).toContain('Mode: local session');
  });

  it('feeds the log tabs: curated events, the detailed log and the session events', async () => {
    await renderApp({ checkout: true });
    addProduct('Coffee');
    await waitFor(() => expect(events()).toContain('Added Coffee ($3.75)'));
    // Newest first, as the desktop log.
    expect(events().indexOf('Added Coffee')).toBeLessThan(
      events().indexOf('Checkout session started'),
    );
    const feeds = screen.getByRole('tablist', { name: 'Log feeds' });
    fireEvent.click(within(feeds).getByRole('tab', { name: 'Protocol' }));
    expect(screen.getByTestId('log-protocol').textContent).toContain(
      '⇠ basket.changed incremental: 1 line(s), total 3.75',
    );
    fireEvent.click(within(feeds).getByRole('tab', { name: 'Detailed' }));
    expect(screen.getByTestId('log-detailed').textContent).toContain('Added Coffee ($3.75)');
  });
});
