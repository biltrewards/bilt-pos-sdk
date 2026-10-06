import { SessionError } from '@bilt/pos-sdk';
import { act, renderHook, screen, waitFor } from '@testing-library/react';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import {
  useBasket,
  useMember,
  useSessionContext,
  useSessionEvent,
  useShopperSession,
  useTerminalSession,
} from '../src/index';
import { LANE, MockBiltPos, fx, renderWithPos, startLane } from './harness';

function Lane({ enabled = true }: { enabled?: boolean }) {
  const { session, status, error, end, restart } = useTerminalSession(LANE, { enabled });
  return (
    <div>
      <span data-testid="status">{status}</span>
      <span data-testid="id">{session?.id ?? ''}</span>
      <span data-testid="error">{error?.message ?? ''}</span>
      <button onClick={() => void end().catch(() => undefined)}>end</button>
      <button onClick={restart}>restart</button>
    </div>
  );
}

describe('useTerminalSession', () => {
  it('starts the session on mount and ends it on unmount', async () => {
    const pos = new MockBiltPos();
    const view = renderWithPos(pos, <Lane />);
    expect(screen.getByTestId('status').textContent).toBe('starting');
    await waitFor(() => expect(screen.getByTestId('status').textContent).toBe('open'));
    const session = pos.sessions[0]!;
    expect(screen.getByTestId('id').textContent).toBe(session.id);
    expect(session.kind).toBe('terminal');
    expect(session.state).toBe('open');

    view.unmount();
    await waitFor(() => expect(session.state).toBe('ended'));
  });

  it('stays idle while disabled and starts once enabled', async () => {
    const pos = new MockBiltPos();
    const view = renderWithPos(pos, <Lane enabled={false} />);
    expect(screen.getByTestId('status').textContent).toBe('idle');
    expect(pos.sessions).toHaveLength(0);

    view.rerender(<Lane enabled />);
    await waitFor(() => expect(screen.getByTestId('status').textContent).toBe('open'));
    expect(pos.sessions).toHaveLength(1);
  });

  it('follows end() and session.ended', async () => {
    const pos = new MockBiltPos();
    renderWithPos(pos, <Lane />);
    await waitFor(() => expect(screen.getByTestId('status').textContent).toBe('open'));
    act(() => screen.getByText('end').click());
    await waitFor(() => expect(screen.getByTestId('status').textContent).toBe('ended'));
    expect(pos.sessions[0]!.state).toBe('ended');
    expect(screen.getByTestId('id').textContent).toBe('');
  });

  it('drops the session when the host ends it', async () => {
    const pos = new MockBiltPos();
    renderWithPos(pos, <Lane />);
    await waitFor(() => expect(screen.getByTestId('status').textContent).toBe('open'));
    await act(() => pos.sessions[0]!.end());
    await waitFor(() => expect(screen.getByTestId('status').textContent).toBe('ended'));
    expect(screen.getByTestId('id').textContent).toBe('');
  });

  it('starts the replacement only after the previous session has ended', async () => {
    const pos = new MockBiltPos();
    renderWithPos(pos, <Lane />);
    await waitFor(() => expect(screen.getByTestId('status').textContent).toBe('open'));
    const first = pos.sessions[0]!;
    const dispose = first[Symbol.asyncDispose].bind(first);
    let release: () => void = () => undefined;
    first[Symbol.asyncDispose] = () =>
      new Promise<void>((resolve) => {
        release = () => void dispose().then(resolve);
      });

    act(() => screen.getByText('restart').click());
    await waitFor(() => expect(screen.getByTestId('status').textContent).toBe('starting'));
    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(pos.sessions).toHaveLength(1);

    release();
    await waitFor(() => expect(screen.getByTestId('status').textContent).toBe('open'));
    expect(first.state).toBe('ended');
    expect(pos.sessions).toHaveLength(2);
  });

  it('surfaces a refused start as an error', async () => {
    const pos = new MockBiltPos();
    vi.spyOn(pos, 'startTerminalSession').mockRejectedValue(
      new SessionError({ code: 'NETWORK', message: 'terminal unreachable' }),
    );
    renderWithPos(pos, <Lane />);
    await waitFor(() => expect(screen.getByTestId('status').textContent).toBe('error'));
    expect(screen.getByTestId('error').textContent).toBe('terminal unreachable');
  });
});

function LocalLane() {
  const { session, status } = useShopperSession({ saleId: 'KIOSK-1', currency: 'USD' });
  return (
    <span data-testid="status">
      {status}:{session?.kind ?? ''}
    </span>
  );
}

describe('useShopperSession', () => {
  it('starts a local session', async () => {
    renderWithPos(new MockBiltPos(), <LocalLane />);
    await waitFor(() => expect(screen.getByTestId('status').textContent).toBe('open:local'));
  });
});

function Checkout() {
  const { session } = useTerminalSession(LANE);
  const { basket, addItem, updateItemQuantity, clear } = useBasket(session);
  const { member, set, clear: signOut } = useMember(session);
  const { phase, attributes, setPhase, setAttribute } = useSessionContext(session);
  const [offers, setOffers] = useState<string[]>([]);
  useSessionEvent(session, 'widget.offer', ({ offer }) => setOffers((o) => [...o, offer.id]));
  return (
    <div>
      <span data-testid="offers">{offers.join(',')}</span>
      <button
        onClick={() => {
          const widget = session!.widget('retail-media');
          const rendering = fx.rendering('lane-banner');
          void widget.perform(rendering, rendering.cta!);
        }}
      >
        tap
      </button>
      <span data-testid="items">
        {basket?.items.map((l) => `${l.sku}x${l.quantity}`).join(',') ?? 'none'}
      </span>
      <span data-testid="total">{basket?.grandTotal ?? ''}</span>
      <span data-testid="member">
        {member === null ? 'guest' : member.resolved ? member.memberId : 'pending'}
      </span>
      <span data-testid="phase">{phase ?? ''}</span>
      <span data-testid="attrs">{JSON.stringify(attributes)}</span>
      <button
        onClick={() =>
          void addItem({ sku: 'SKU-4471', description: 'Toothpaste', unitPrice: '4.99' }, 'L1')
        }
      >
        add
      </button>
      <button onClick={() => void updateItemQuantity('L1', 3)}>qty</button>
      <button onClick={() => void clear()}>clear</button>
      <button onClick={() => void set({ resolver: { type: 'PHONE', value: '+12015550123' } })}>
        sign in
      </button>
      <button onClick={() => void signOut()}>sign out</button>
      <button onClick={() => void setPhase('TENDERING')}>tender</button>
      <button onClick={() => void setAttribute('lane', '3')}>attr</button>
    </div>
  );
}

describe('useBasket, useMember and useSessionContext', () => {
  it('calls the basket updaters with the basket as receiver', async () => {
    const session = await startLane();
    const receivers: unknown[] = [];
    const original = session.basket.clear;
    session.basket.clear = function (this: unknown, ...args: Parameters<typeof original>) {
      receivers.push(this);
      return original.apply(session.basket, args);
    };
    const { result } = renderHook(() => useBasket(session));
    await act(() => result.current.clear());
    expect(receivers).toEqual([session.basket]);
  });

  it('follow basket.changed, member.changed and context.changed', async () => {
    const pos = new MockBiltPos();
    renderWithPos(pos, <Checkout />);
    await waitFor(() => expect(screen.getByTestId('items').textContent).toBe(''));

    act(() => screen.getByText('add').click());
    await waitFor(() => expect(screen.getByTestId('items').textContent).toBe('SKU-4471x1'));
    expect(screen.getByTestId('total').textContent).toBe('4.99');

    act(() => screen.getByText('qty').click());
    await waitFor(() => expect(screen.getByTestId('items').textContent).toBe('SKU-4471x3'));
    expect(screen.getByTestId('total').textContent).toBe('14.97');

    // A change made outside React, through the session itself, is picked up the same way.
    await act(() => pos.sessions[0]!.basket.removeItem('L1'));
    expect(screen.getByTestId('items').textContent).toBe('');

    expect(screen.getByTestId('member').textContent).toBe('guest');
    act(() => screen.getByText('sign in').click());
    await waitFor(() => expect(screen.getByTestId('member').textContent).toBe('pending'));
    act(() => screen.getByText('sign out').click());
    await waitFor(() => expect(screen.getByTestId('member').textContent).toBe('guest'));

    expect(screen.getByTestId('phase').textContent).toBe('SCANNING');
    act(() => screen.getByText('tender').click());
    await waitFor(() => expect(screen.getByTestId('phase').textContent).toBe('TENDERING'));
    act(() => screen.getByText('attr').click());
    await waitFor(() => expect(screen.getByTestId('attrs').textContent).toBe('{"lane":"3"}'));

    act(() => screen.getByText('tap').click());
    await waitFor(() => expect(screen.getByTestId('offers').textContent).toBe('ofr_1'));
  });

  it('reject updaters without a session', async () => {
    const pos = new MockBiltPos();
    function Idle() {
      const { addItem } = useBasket(null);
      const { set } = useMember(undefined);
      return (
        <button
          onClick={() =>
            void Promise.allSettled([
              addItem({ sku: 'A', description: 'A', unitPrice: '1.00' }),
              set({ id: 'mbr_1' }),
            ]).then((results) => {
              document.title = results.map((r) => r.status).join(',');
            })
          }
        >
          go
        </button>
      );
    }
    renderWithPos(pos, <Idle />);
    act(() => screen.getByText('go').click());
    await waitFor(() => expect(document.title).toBe('rejected,rejected'));
  });
});
