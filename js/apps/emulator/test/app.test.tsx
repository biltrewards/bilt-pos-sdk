// The shell and the Settings pane: the install prompt in front of the gate with the settings
// still reachable, the bridge's terminal from `/health`, and applying settings restarting the lane.
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { App } from '../src/App';
import { loadSettings } from '../src/settings';
import { freshStore, probe, renderApp } from './harness';

describe('the emulator shell', () => {
  it('shows the install prompt while the bridge is missing, with the settings one tab away', async () => {
    const connect = async () => {
      throw new Error('connect must not run while the bridge is missing');
    };
    render(<App detect={probe('missing')} connect={connect} sales={freshStore()} />);
    await waitFor(() =>
      expect(screen.getByRole('alert').getAttribute('data-status')).toBe('missing'),
    );
    expect(screen.getByText(/Install the Bilt Terminal Bridge/)).toBeTruthy();
    expect(screen.getByText(/The port is in the Settings tab/)).toBeTruthy();

    fireEvent.click(screen.getByRole('tab', { name: 'Settings' }));
    expect(screen.getByTestId('bridge-status').textContent).toContain('bridge missing');
    expect(screen.getByTestId('bridge-terminal').textContent).toContain('unknown');
  });

  it('shows the bridge terminal, passes any POI id through and restarts the lane on apply', async () => {
    const { pos } = await renderApp({ tab: 'settings' });
    expect(screen.getByTestId('bridge-status').textContent).toContain(
      'bridge ready at http://127.0.0.1:48333',
    );
    expect(screen.getByTestId('bridge-terminal').textContent).toBe(
      'Terminal: Lane 3 · VictaLane · reachable',
    );
    expect((screen.getByLabelText('POI id') as HTMLInputElement).value).toBe('');
    expect(pos.sessions[0]).toMatchObject({ kind: 'terminal', poiId: 'bilt-session-host' });

    fireEvent.change(screen.getByLabelText('POI id'), { target: { value: 'VictaLane-2' } });
    fireEvent.change(screen.getByLabelText('Sale id'), { target: { value: 'LANE-9' } });
    fireEvent.click(screen.getByRole('button', { name: 'Apply and restart the lane' }));

    await waitFor(() => expect(pos.sessions).toHaveLength(2));
    expect(pos.sessions[1]).toMatchObject({
      kind: 'terminal',
      saleId: 'LANE-9',
      poiId: 'VictaLane-2',
    });
    expect(loadSettings()).toMatchObject({ poiId: 'VictaLane-2', saleId: 'LANE-9' });
    expect(pos.sessions[0]?.state).toBe('ended');
  });

  it('keeps a saved POI id and runs a local session when asked', async () => {
    const { pos } = await renderApp({ tab: 'settings', settings: { poiId: 'Lab-7' } });
    expect((screen.getByLabelText('POI id') as HTMLInputElement).value).toBe('Lab-7');
    fireEvent.click(screen.getByLabelText(/Local session:/));
    fireEvent.click(screen.getByRole('button', { name: 'Apply and restart the lane' }));
    await waitFor(() => expect(pos.sessions[1]?.kind).toBe('local'));
    expect(screen.getByText(/Local session · lane/)).toBeTruthy();
  });
});
