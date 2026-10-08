// The Log pane: session events, operation lifecycle and errors land there with a sequence, the
// filters narrow them, and the diagnostics copy carries the settings and the log.
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { renderApp } from './harness';

describe('the Log pane', () => {
  it('logs the lane, session events and operations in order, filters them and copies diagnostics', async () => {
    const written: string[] = [];
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText: vi.fn(async (text: string) => void written.push(text)) },
    });
    await renderApp({ tab: 'log' });
    fireEvent.click(screen.getByRole('tab', { name: 'Sale' }));
    fireEvent.click(screen.getByRole('button', { name: /Notebook/ }));
    fireEvent.click(screen.getByRole('button', { name: 'Identify on terminal' }));
    await screen.findByTestId('member-card');
    fireEvent.click(screen.getByRole('tab', { name: 'Log' }));

    await waitFor(() => expect(screen.getAllByTestId('log-entry').length).toBeGreaterThan(4));
    const rows = screen.getAllByTestId('log-entry').map((row) => row.textContent ?? '');
    expect(rows.some((row) => row.includes('connectionconnected'))).toBe(true);
    expect(
      rows.some((row) => row.includes('basket.changed') && row.includes('incremental: 1 line(s)')),
    ).toBe(true);
    expect(rows.some((row) => row.includes('identifyMember') && row.includes('started'))).toBe(
      true,
    );
    expect(rows.some((row) => row.includes('identifyMember') && row.includes('succeeded'))).toBe(
      true,
    );
    expect(rows.some((row) => row.includes('member.changed') && row.includes('mbr_8f2a'))).toBe(
      true,
    );
    const seqs = screen
      .getAllByTestId('log-entry')
      .map((row) => Number(row.querySelector('.seq')?.textContent));
    expect(seqs).toEqual([...seqs].sort((a, b) => a - b));

    fireEvent.change(screen.getByLabelText('Filter log'), { target: { value: 'basket.changed' } });
    expect(screen.getAllByTestId('log-entry')).toHaveLength(1);
    fireEvent.change(screen.getByLabelText('Filter log'), { target: { value: '' } });
    fireEvent.click(screen.getByLabelText('event'));
    expect(
      screen
        .getAllByTestId('log-entry')
        .every((row) => !row.textContent?.includes('basket.changed')),
    ).toBe(true);

    fireEvent.click(screen.getByRole('button', { name: 'Copy diagnostics' }));
    await screen.findByText(/Copied \d+ entries/);
    const diagnostics = JSON.parse(written[0] ?? '{}') as {
      settings: { poiId: string };
      engine: { name: string };
      log: unknown[];
    };
    expect(diagnostics.settings.poiId).toBe('');
    expect(diagnostics.engine.name).toBe('mock');
    expect(diagnostics.log.length).toBeGreaterThan(4);

    fireEvent.click(screen.getByRole('button', { name: 'Clear' }));
    expect(screen.getByText('Nothing logged yet.')).toBeTruthy();
  });

  it('records SDK errors reported by the page', async () => {
    const { terminal } = await renderApp({ tab: 'log' });
    terminal().basket.addItem = () => Promise.reject(new Error('basket refused'));
    fireEvent.click(screen.getByRole('tab', { name: 'Sale' }));
    fireEvent.click(screen.getByRole('button', { name: /Notebook/ }));
    await screen.findAllByText('basket refused');
    fireEvent.click(screen.getByRole('tab', { name: 'Log' }));
    const errors = screen
      .getAllByTestId('log-entry')
      .filter((row) => row.classList.contains('kind-error'));
    expect(errors.some((row) => row.textContent?.includes('basket refused'))).toBe(true);
  });
});
