import type { IdentifyResult } from '@bilt/pos-protocol';
import { SessionError, type Operation } from '@bilt/pos-sdk';
import { act, screen, waitFor } from '@testing-library/react';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';
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
      <button onClick={() => settlement.reset()}>reset</button>
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
