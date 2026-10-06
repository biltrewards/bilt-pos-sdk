import type { BiltPos } from '@bilt/pos-sdk';
import { act, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { BiltPosProvider, useBiltPos } from '../src/index';
import { MockBiltPos } from './harness';

function Status() {
  const { status, error, pos, reconnect } = useBiltPos();
  return (
    <div>
      <span data-testid="status">{status}</span>
      <span data-testid="error">{error?.message ?? ''}</span>
      <span data-testid="engine">{pos?.capabilities.name ?? ''}</span>
      <button onClick={reconnect}>reconnect</button>
    </div>
  );
}

describe('BiltPosProvider', () => {
  it('is connected at once with a ready-made pos', () => {
    render(
      <BiltPosProvider pos={new MockBiltPos()}>
        <Status />
      </BiltPosProvider>,
    );
    expect(screen.getByTestId('status').textContent).toBe('connected');
    expect(screen.getByTestId('engine').textContent).toBe('mock');
  });

  it('runs an async connect factory and closes the connection on unmount', async () => {
    const pos = new MockBiltPos();
    const close = vi.spyOn(pos, 'close');
    let release: (value: BiltPos) => void = () => undefined;
    const connect = vi.fn(() => new Promise<BiltPos>((resolve) => (release = resolve)));

    const view = render(
      <BiltPosProvider connect={connect}>
        <Status />
      </BiltPosProvider>,
    );
    expect(screen.getByTestId('status').textContent).toBe('connecting');
    expect(connect).toHaveBeenCalledTimes(1);

    await act(async () => release(pos));
    expect(screen.getByTestId('status').textContent).toBe('connected');

    view.unmount();
    expect(close).toHaveBeenCalledTimes(1);
  });

  it('reports a failed connect and tries again on reconnect()', async () => {
    const onError = vi.fn();
    const connect = vi
      .fn<() => Promise<BiltPos>>()
      .mockRejectedValueOnce(new Error('no bridge'))
      .mockResolvedValueOnce(new MockBiltPos());

    render(
      <BiltPosProvider connect={connect} onError={onError}>
        <Status />
      </BiltPosProvider>,
    );
    await waitFor(() => expect(screen.getByTestId('status').textContent).toBe('error'));
    expect(screen.getByTestId('error').textContent).toBe('no bridge');
    expect(onError).toHaveBeenCalledWith(expect.objectContaining({ message: 'no bridge' }));

    act(() => screen.getByText('reconnect').click());
    await waitFor(() => expect(screen.getByTestId('status').textContent).toBe('connected'));
    expect(connect).toHaveBeenCalledTimes(2);
  });

  it('throws outside a provider', () => {
    const spy = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    expect(() => render(<Status />)).toThrow(/BiltPosProvider/);
    spy.mockRestore();
  });
});
