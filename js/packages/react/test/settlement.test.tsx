import type { IdentifyResult } from '@bilt/pos-protocol';
import { SessionError, type Operation } from '@bilt/pos-sdk';
import { act, renderHook, screen, waitFor } from '@testing-library/react';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { useOperation, useSettlement, type InteractiveStepKind } from '../src/index';
import { MockBiltPos, type MockTerminalSession, renderWithPos, startLane } from './harness';

function Pay({
  session,
  interactive,
}: {
  session: MockTerminalSession;
  interactive: readonly InteractiveStepKind[];
}) {
  const settlement = useSettlement(session, { interactive });
  const [mismatch, setMismatch] = useState('');
  const step = settlement.pendingStep;
  return (
    <div>
      <span data-testid="mismatch">{mismatch}</span>
      <button
        onClick={() => {
          try {
            settlement.reply({ recovery: 'RETRY' });
          } catch (error: unknown) {
            setMismatch(error instanceof Error ? error.message : String(error));
          }
        }}
      >
        wrong reply
      </button>
      <span data-testid="status">{settlement.status}</span>
      <span data-testid="step">{step ? `${step.kind}` : ''}</span>
      <span data-testid="suggested">
        {step?.kind === 'TOTAL_REQUIRED' ? step.suggestedTotal : ''}
      </span>
      <span data-testid="failure">
        {step?.kind === 'RECOVERY_REQUIRED' ? step.failure.error.code : ''}
      </span>
      <span data-testid="charged">{settlement.result?.cardAmountCharged ?? ''}</span>
      <span data-testid="movements">{settlement.movements.map((m) => m.step).join(',')}</span>
      <span data-testid="error">{settlement.error?.message ?? ''}</span>
      <button onClick={() => void settlement.settle().catch(() => undefined)}>pay</button>
      <button onClick={() => settlement.reply({ total: '3.00' })}>reply total</button>
      <button onClick={() => settlement.reply({ recovery: 'RETRY' })}>retry</button>
      <button onClick={() => settlement.reply({ recovery: 'ABORT' })}>give up</button>
      <button onClick={() => step?.useDefault()}>default</button>
      <button
        onClick={() => {
          try {
            settlement.reset();
          } catch (error: unknown) {
            setMismatch(error instanceof Error ? error.message : String(error));
          }
        }}
      >
        reset
      </button>
    </div>
  );
}

async function laneWithItem(): Promise<MockTerminalSession> {
  const session = await startLane();
  await session.basket.addItem({
    sku: 'SKU-4471',
    description: 'Toothpaste',
    unitPrice: '4.99',
    taxRate: '0.08875',
  });
  return session;
}

describe('useSettlement', () => {
  it('surfaces TOTAL_REQUIRED as a pending step and continues with the reply', async () => {
    const session = await laneWithItem();
    renderWithPos(new MockBiltPos(), <Pay session={session} interactive={['TOTAL_REQUIRED']} />);

    act(() => screen.getByText('pay').click());
    await waitFor(() => expect(screen.getByTestId('status').textContent).toBe('awaitingReply'));
    expect(screen.getByTestId('step').textContent).toBe('TOTAL_REQUIRED');
    expect(screen.getByTestId('suggested').textContent).toBe('3.43');

    act(() => screen.getByText('reply total').click());
    await waitFor(() => expect(screen.getByTestId('status').textContent).toBe('succeeded'));
    expect(screen.getByTestId('charged').textContent).toBe('3.00');
    expect(screen.getByTestId('movements').textContent).toBe('CARD_CHARGE');
    expect(screen.getByTestId('step').textContent).toBe('');
  });

  it('answers a pending step with its default on useDefault()', async () => {
    const session = await laneWithItem();
    renderWithPos(new MockBiltPos(), <Pay session={session} interactive={['TOTAL_REQUIRED']} />);
    act(() => screen.getByText('pay').click());
    await waitFor(() => expect(screen.getByTestId('step').textContent).toBe('TOTAL_REQUIRED'));
    act(() => screen.getByText('default').click());
    await waitFor(() => expect(screen.getByTestId('status').textContent).toBe('succeeded'));
    expect(screen.getByTestId('charged').textContent).toBe('3.43');
  });

  it('takes the SDK default without an interactive step', async () => {
    const session = await laneWithItem();
    renderWithPos(new MockBiltPos(), <Pay session={session} interactive={[]} />);
    act(() => screen.getByText('pay').click());
    await waitFor(() => expect(screen.getByTestId('status').textContent).toBe('succeeded'));
    expect(screen.getByTestId('charged').textContent).toBe('5.43');
  });

  it('surfaces RECOVERY_REQUIRED and retries on { recovery: "RETRY" }', async () => {
    const session = await laneWithItem();
    session.failNextChargeWith = new SessionError({ code: 'DECLINED', message: 'declined' });
    renderWithPos(new MockBiltPos(), <Pay session={session} interactive={['RECOVERY_REQUIRED']} />);
    act(() => screen.getByText('pay').click());
    await waitFor(() => expect(screen.getByTestId('step').textContent).toBe('RECOVERY_REQUIRED'));
    expect(screen.getByTestId('failure').textContent).toBe('DECLINED');

    act(() => screen.getByText('retry').click());
    await waitFor(() => expect(screen.getByTestId('status').textContent).toBe('succeeded'));
    expect(session.recoveries).toEqual(['RETRY']);
  });

  it('fails with the SessionError when recovery aborts, and resets', async () => {
    const session = await laneWithItem();
    session.failNextChargeWith = new SessionError({ code: 'DECLINED', message: 'declined' });
    renderWithPos(new MockBiltPos(), <Pay session={session} interactive={['RECOVERY_REQUIRED']} />);
    act(() => screen.getByText('pay').click());
    await waitFor(() => expect(screen.getByTestId('step').textContent).toBe('RECOVERY_REQUIRED'));
    act(() => screen.getByText('give up').click());
    await waitFor(() => expect(screen.getByTestId('status').textContent).toBe('failed'));
    expect(screen.getByTestId('error').textContent).toBe('declined');

    act(() => screen.getByText('reset').click());
    expect(screen.getByTestId('status').textContent).toBe('idle');
  });

  it('refuses to reset while a settlement is running', async () => {
    const session = await laneWithItem();
    renderWithPos(new MockBiltPos(), <Pay session={session} interactive={['TOTAL_REQUIRED']} />);
    act(() => screen.getByText('pay').click());
    await waitFor(() => expect(screen.getByTestId('step').textContent).toBe('TOTAL_REQUIRED'));
    act(() => screen.getByText('reset').click());
    expect(screen.getByTestId('mismatch').textContent).toMatch(
      /reset\(\) called while a settlement is running/,
    );
    expect(screen.getByTestId('status').textContent).toBe('awaitingReply');
  });

  it('drops a pending settlement when the session changes', async () => {
    const first = await laneWithItem();
    const second = await laneWithItem();
    const pos = new MockBiltPos();
    const view = renderWithPos(pos, <Pay session={first} interactive={['TOTAL_REQUIRED']} />);
    act(() => screen.getByText('pay').click());
    await waitFor(() => expect(screen.getByTestId('step').textContent).toBe('TOTAL_REQUIRED'));

    view.rerender(<Pay session={second} interactive={['TOTAL_REQUIRED']} />);
    await waitFor(() => expect(screen.getByTestId('status').textContent).toBe('idle'));
    expect(screen.getByTestId('step').textContent).toBe('');

    act(() => screen.getByText('pay').click());
    await waitFor(() => expect(screen.getByTestId('step').textContent).toBe('TOTAL_REQUIRED'));
    act(() => screen.getByText('reply total').click());
    await waitFor(() => expect(screen.getByTestId('status').textContent).toBe('succeeded'));
  });

  it('clears a finished settlement when the session changes', async () => {
    const first = await laneWithItem();
    const second = await laneWithItem();
    const view = renderWithPos(new MockBiltPos(), <Pay session={first} interactive={[]} />);
    act(() => screen.getByText('pay').click());
    await waitFor(() => expect(screen.getByTestId('status').textContent).toBe('succeeded'));
    expect(screen.getByTestId('charged').textContent).not.toBe('');

    view.rerender(<Pay session={second} interactive={[]} />);
    await waitFor(() => expect(screen.getByTestId('status').textContent).toBe('idle'));
    expect(screen.getByTestId('charged').textContent).toBe('');
  });

  it('aborts before answering the open step when the session changes', async () => {
    const first = await laneWithItem();
    const second = await laneWithItem();
    const settle = first.settle.bind(first);
    let releaseAbort: () => void = () => undefined;
    let aborted = false;
    let finished = false;
    first.settle = (options) => {
      const operation = settle(options);
      void operation.then(
        () => (finished = true),
        () => (finished = true),
      );
      return Object.defineProperties(operation.then(), {
        id: { value: operation.id },
        type: { value: operation.type },
        status: { get: () => operation.status },
        abort: {
          value: () => {
            aborted = true;
            return new Promise<void>((resolve) => (releaseAbort = resolve));
          },
        },
      }) as typeof operation;
    };
    const view = renderWithPos(
      new MockBiltPos(),
      <Pay session={first} interactive={['TOTAL_REQUIRED']} />,
    );
    act(() => screen.getByText('pay').click());
    await waitFor(() => expect(screen.getByTestId('step').textContent).toBe('TOTAL_REQUIRED'));

    view.rerender(<Pay session={second} interactive={['TOTAL_REQUIRED']} />);
    await waitFor(() => expect(aborted).toBe(true));
    await new Promise((resolve) => setTimeout(resolve, 20));
    // The default total must not reach the host while the abort is still on its way.
    expect(finished).toBe(false);

    releaseAbort();
    await waitFor(() => expect(finished).toBe(true));
  });

  it('refuses a second settle() in the same turn', async () => {
    const session = await laneWithItem();
    const settle = vi.spyOn(session, 'settle');
    let second = '';
    function Double() {
      const settlement = useSettlement(session);
      return (
        <button
          onClick={() => {
            void settlement.settle().catch(() => undefined);
            try {
              void settlement.settle();
            } catch (error: unknown) {
              second = error instanceof Error ? error.message : String(error);
            }
          }}
        >
          pay twice
        </button>
      );
    }
    renderWithPos(new MockBiltPos(), <Double />);
    act(() => screen.getByText('pay twice').click());
    expect(second).toBe('a settlement is already running on this hook');
    expect(settle).toHaveBeenCalledTimes(1);
  });

  it('rejects a reply that does not fit the step', async () => {
    const session = await laneWithItem();
    renderWithPos(new MockBiltPos(), <Pay session={session} interactive={['TOTAL_REQUIRED']} />);
    act(() => screen.getByText('pay').click());
    await waitFor(() => expect(screen.getByTestId('step').textContent).toBe('TOTAL_REQUIRED'));
    act(() => screen.getByText('wrong reply').click());
    expect(screen.getByTestId('mismatch').textContent).toBe(
      'a TOTAL_REQUIRED step takes { total }',
    );
    // The step is still pending and a fitting reply still goes through.
    expect(screen.getByTestId('step').textContent).toBe('TOTAL_REQUIRED');
    act(() => screen.getByText('reply total').click());
    await waitFor(() => expect(screen.getByTestId('status').textContent).toBe('succeeded'));
  });
});

function Identify({ session }: { session: MockTerminalSession }) {
  const [op, setOp] = useState<Operation<IdentifyResult> | null>(null);
  const identify = useOperation(op);
  return (
    <div>
      <span data-testid="status">{identify.status}</span>
      <span data-testid="pending">{String(identify.pending)}</span>
      <span data-testid="result">{identify.result?.status ?? ''}</span>
      <button onClick={() => setOp(session.identifyMember())}>identify</button>
    </div>
  );
}

describe('useOperation', () => {
  it('follows the handle status through intermediate transitions', async () => {
    let status: Operation<string>['status'] = 'running';
    const handle = Object.defineProperties(new Promise<string>(() => undefined), {
      status: { get: () => status, enumerable: true },
      abort: { value: () => Promise.resolve(), enumerable: true },
    }) as Operation<string>;
    const { result } = renderHook(() => useOperation(handle));
    expect(result.current.status).toBe('running');

    status = 'awaitingReply';
    await waitFor(() => expect(result.current.status).toBe('awaitingReply'));
    expect(result.current.pending).toBe(true);
  });

  it('stops polling the handle once it has settled', async () => {
    let reads = 0;
    const handle = Object.defineProperties(Promise.resolve('done'), {
      status: {
        get: () => {
          reads += 1;
          return 'succeeded' as const;
        },
        enumerable: true,
      },
      abort: { value: () => Promise.resolve(), enumerable: true },
    }) as Operation<string>;
    renderHook(() => useOperation(handle));
    await new Promise((resolve) => setTimeout(resolve, 50));
    const settled = reads;
    await new Promise((resolve) => setTimeout(resolve, 350));
    expect(reads).toBe(settled);
  });

  it('tracks an operation from running to succeeded', async () => {
    const session = await startLane();
    renderWithPos(new MockBiltPos(), <Identify session={session} />);
    expect(screen.getByTestId('status').textContent).toBe('idle');
    act(() => screen.getByText('identify').click());
    expect(screen.getByTestId('pending').textContent).toBe('true');
    await waitFor(() => expect(screen.getByTestId('status').textContent).toBe('succeeded'));
    expect(screen.getByTestId('result').textContent).toBe('FOUND');
    expect(session.member.current?.resolved).toBe(true);
  });
});
