// The Refunds pane against the SDK double and a seeded IndexedDB store: listing, a referenced
// refund settled with an allocation, a void by OriginalSaleRecord with the reversal decision
// prompt, an unreferenced refund, and the ledger each leaves behind.
import {
  SessionError,
  type Operation,
  type OriginalSaleRecord,
  type ReversalHandlers,
  type SettleOptions,
  type SettlementResult,
  type TerminalSessionOptions,
  type VoidResult,
} from '@bilt/pos-sdk';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { originalSaleRecord, type SaleRecord } from '../src/store/sale-record';
import { freshStore, MockBiltPos, MockTerminalSession, renderApp } from './harness';

const SALE: SaleRecord = {
  id: 'sale-seeded',
  sessionId: 'ses_old',
  saleId: 'LANE-3',
  poiId: 'VictaLane-275839164',
  currency: 'USD',
  completedAt: '2026-10-06T10:00:00.000Z',
  memberId: '98234',
  items: [
    {
      sku: 'SKU-0013',
      description: 'Desk Lamp',
      quantity: 1,
      unitPrice: '34.99',
      lineTotal: '34.99',
    },
  ],
  authorizedAmount: '37.31',
  pointsRedeemed: 0,
  totalPointsEarned: 37,
  legs: [
    {
      type: 'CARD',
      poiTransactionId: 'POI-PAY-9',
      poiTimestamp: '2026-10-06T10:00:00Z',
      amount: '37.31',
      brand: 'Visa',
    },
    { type: 'AWARD', poiTransactionId: 'POI-AW-9' },
  ],
  giftCardLoads: [],
  externalPaymentAmount: '0.00',
};

/** A terminal double whose void asks the reversal handler once and records what it was told. */
class ReversalTerminal extends MockTerminalSession {
  voids: Array<OriginalSaleRecord | undefined> = [];
  decisions: string[] = [];
  settles: SettleOptions[] = [];
  failVoidStep: 'CARD' | 'AWARD' | null = null;
  /** Fails the next refund settle after its card allocation committed, as the session does. */
  failRefundAfterCommit = false;
  private refundCommitted = false;

  constructor(options: TerminalSessionOptions) {
    super(options);
    const clear = this.basket.clear.bind(this.basket);
    this.basket.clear = () =>
      this.refundCommitted
        ? Promise.reject(new Error('retry settle() with the same allocations'))
        : clear();
  }

  override settle(options: SettleOptions = {}) {
    this.settles.push(options);
    if (this.failRefundAfterCommit) {
      this.failRefundAfterCommit = false;
      this.refundCommitted = true;
      const failed: Promise<SettlementResult> = Promise.reject(
        new SessionError({ code: 'TERMINAL_ERROR', message: 'award reversal refused' }),
      );
      return Object.defineProperties(failed, {
        id: { value: 'op_refund', enumerable: true },
        type: { value: 'settle', enumerable: true },
        status: { get: () => 'failed', enumerable: true },
        abort: { value: async () => undefined, enumerable: true },
      }) as Operation<SettlementResult>;
    }
    this.refundCommitted = false;
    return super.settle(options);
  }

  override voidTransaction(
    originalSale?: OriginalSaleRecord,
    handlers?: ReversalHandlers,
  ): Operation<VoidResult> {
    this.voids.push(originalSale);
    const step = this.failVoidStep;
    this.failVoidStep = null;
    const run = async (): Promise<VoidResult> => {
      if (step && handlers?.onError) {
        const decision = await handlers.onError(
          step,
          new SessionError({ code: 'TERMINAL_ERROR', message: `${step} reversal refused` }),
          { deadline: new Date(Date.now() + 120_000), signal: new AbortController().signal },
        );
        this.decisions.push(decision ?? 'ABORT');
        if (decision === 'ABORT' || decision === null || decision === undefined) {
          throw new SessionError({
            code: 'TERMINAL_ERROR',
            message: 'void stopped',
            reversedMovements:
              step === 'AWARD' ? [{ step: 'CARD', poiTransactionId: 'POI-REV-1' }] : [],
          });
        }
      }
      return {
        success: true,
        reversedAmount: '37.31',
        poiTransactionId: 'POI-V-1',
        pointsReversed: 37,
        remainingPointBalance: 0,
      };
    };
    const promise = run();
    return Object.defineProperties(promise, {
      id: { value: 'op_void', enumerable: true },
      type: { value: 'voidTransaction', enumerable: true },
      status: { get: () => 'running', enumerable: true },
      abort: { value: async () => undefined, enumerable: true },
    }) as Operation<VoidResult>;
  }
}

class ReversalPos extends MockBiltPos {
  override startTerminalSession(options: TerminalSessionOptions): Promise<ReversalTerminal> {
    const session = new ReversalTerminal(options);
    this.sessions.push(session);
    return Promise.resolve(session);
  }
}

async function seeded() {
  const store = freshStore();
  await store.recordSale(SALE);
  const pos = new ReversalPos();
  const rendered = await renderApp({ tab: 'refunds', pos, sales: store });
  await waitFor(() => expect(screen.getByTestId('sale-sale-seeded')).toBeTruthy());
  const terminal = () => rendered.terminal() as ReversalTerminal;
  return { ...rendered, store, terminal };
}

describe('the Refunds pane', () => {
  it('lists the stored sale with its legs and refuses to act until one is selected', async () => {
    await seeded();
    const card = screen.getByTestId('sale-sale-seeded');
    expect(card.textContent).toContain('$37.31');
    expect(card.textContent).toContain('CARD $37.31 · txn POI-PAY-9 · Visa · $37.31 left');
    expect(card.textContent).toContain('member 98234');
    expect(screen.getByText('Select a sale to refund or void it.')).toBeTruthy();
  });

  it('runs a referenced partial refund as a RETURN line settled with a card allocation', async () => {
    const { terminal, store } = await seeded();
    fireEvent.click(screen.getByRole('button', { name: 'select' }));
    fireEvent.change(screen.getByLabelText('Refund amount'), { target: { value: '10.00' } });
    fireEvent.click(screen.getByRole('button', { name: 'Referenced refund' }));

    await waitFor(() => expect(screen.getByTestId('reversal-outcome')).toBeTruthy());
    const options = terminal().settles[0];
    expect(options?.refunds).toEqual([
      {
        type: 'CARD',
        amount: '10.00',
        originalPoiTransactionId: 'POI-PAY-9',
        originalPoiTransactionTimestamp: '2026-10-06T10:00:00Z',
      },
    ]);
    expect(options?.settlementType).toBe('REFUND_THEN_CHARGE');
    const stored = await store.findSale('sale-seeded');
    expect(stored?.refunds[0]).toMatchObject({ amount: '10.00', leg: 'CARD', full: false });
    await waitFor(() =>
      expect(screen.getByTestId('sale-sale-seeded').textContent).toContain('$27.31 left'),
    );
    expect(screen.getByTestId('sale-sale-seeded').textContent).toContain('partially refunded');
    // The basket was used for the return line and is empty again.
    expect(terminal().basket.current.items).toHaveLength(0);
    // A void would now over-return.
    expect((screen.getByRole('button', { name: 'Void' }) as HTMLButtonElement).disabled).toBe(true);
  });

  it('retries a refund that stopped after committing with the same allocations', async () => {
    const { terminal, store } = await seeded();
    terminal().failRefundAfterCommit = true;
    fireEvent.click(screen.getByRole('button', { name: 'select' }));
    fireEvent.click(screen.getByRole('button', { name: 'Referenced refund' }));

    const prompt = await screen.findByRole('alertdialog', { name: 'Refund incomplete' });
    // The session holds the return line; nothing else may reverse this sale meanwhile.
    expect(terminal().basket.current.items).toHaveLength(1);
    expect((screen.getByRole('button', { name: 'Void' }) as HTMLButtonElement).disabled).toBe(true);

    fireEvent.click(within(prompt).getByRole('button', { name: 'Retry the refund' }));
    await waitFor(() =>
      expect(screen.getByTestId('reversal-outcome').textContent).toContain('Refunded'),
    );
    expect(terminal().settles).toHaveLength(2);
    expect(terminal().settles[1]?.refunds).toEqual(terminal().settles[0]?.refunds);
    expect(screen.queryByRole('alertdialog', { name: 'Refund incomplete' })).toBeNull();
    expect(terminal().basket.current.items).toHaveLength(0);
    await waitFor(async () =>
      expect((await store.findSale('sale-seeded'))?.refunds).toHaveLength(1),
    );
  });

  it('voids by OriginalSaleRecord, asking the cashier when a leg fails', async () => {
    const { terminal, store } = await seeded();
    terminal().failVoidStep = 'AWARD';
    fireEvent.click(screen.getByRole('button', { name: 'select' }));
    fireEvent.click(screen.getByRole('button', { name: 'Void' }));

    const prompt = await screen.findByRole('alertdialog', { name: 'Reversal step failed' });
    expect(prompt.textContent).toContain('AWARD failed: TERMINAL_ERROR');
    expect(prompt.textContent).toContain('Default (skip)');
    fireEvent.click(within(prompt).getByRole('button', { name: /Skip it/ }));

    await waitFor(() =>
      expect(screen.getByTestId('reversal-outcome').textContent).toContain('success'),
    );
    expect(terminal().decisions).toEqual(['SKIP']);
    expect(terminal().voids[0]).toEqual({
      cardPoiTransactionId: 'POI-PAY-9',
      cardPoiTransactionTimestamp: '2026-10-06T10:00:00Z',
      awardPoiTransactionId: 'POI-AW-9',
      memberId: '98234',
    });
    expect((await store.findSale('sale-seeded'))?.voided?.poiTransactionId).toBe('POI-V-1');
    await waitFor(() =>
      expect(screen.getByTestId('sale-sale-seeded').textContent).toContain('voided'),
    );
  });

  it('retries a stopped void with the same record and keeps its progress for another session', async () => {
    const { terminal, store } = await seeded();
    terminal().failVoidStep = 'AWARD';
    fireEvent.click(screen.getByRole('button', { name: 'select' }));
    fireEvent.click(screen.getByRole('button', { name: 'Void' }));
    const prompt = await screen.findByRole('alertdialog', { name: 'Reversal step failed' });
    fireEvent.click(within(prompt).getByRole('button', { name: /Abort/ }));
    await waitFor(() =>
      expect(screen.getByTestId('reversal-outcome').textContent).toContain('void stopped'),
    );
    expect(screen.getByTestId('reversal-outcome').textContent).toContain('CARD POI-REV-1');
    const stored = await store.findSale('sale-seeded');
    expect(stored?.refunds[0]).toMatchObject({ leg: 'CARD', full: true, reversalProgress: true });

    // The session resumes only an identical record, and skips the reversed leg itself.
    fireEvent.click(screen.getByRole('button', { name: 'Void' }));
    await waitFor(() => expect(terminal().voids).toHaveLength(2));
    expect(terminal().voids[1]).toEqual(terminal().voids[0]);
    // A void started afresh elsewhere leaves out what the stored progress says is reversed.
    expect(originalSaleRecord(stored!)).toEqual({
      awardPoiTransactionId: 'POI-AW-9',
      memberId: '98234',
    });
  });

  it('locks the Sale tab while a reversal is in flight', async () => {
    const { terminal } = await seeded();
    terminal().failVoidStep = 'AWARD';
    fireEvent.click(screen.getByRole('button', { name: 'select' }));
    fireEvent.click(screen.getByRole('button', { name: 'Void' }));
    const prompt = await screen.findByRole('alertdialog', { name: 'Reversal step failed' });

    fireEvent.click(screen.getByRole('tab', { name: 'Sale' }));
    const lamp = () => screen.getByRole('button', { name: /^Desk Lamp/ }) as HTMLButtonElement;
    expect(lamp().disabled).toBe(true);

    fireEvent.click(screen.getByRole('tab', { name: 'Refunds' }));
    fireEvent.click(within(prompt).getByRole('button', { name: /Skip it/ }));
    await waitFor(() =>
      expect(screen.getByTestId('reversal-outcome').textContent).toContain('success'),
    );
    fireEvent.click(screen.getByRole('tab', { name: 'Sale' }));
    expect(lamp().disabled).toBe(false);
  });

  it('refunds without a sale', async () => {
    await seeded();
    fireEvent.change(screen.getByLabelText('Unreferenced refund amount'), {
      target: { value: '4.50' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Refund without a sale' }));
    await waitFor(() =>
      expect(screen.getByTestId('reversal-outcome').textContent).toContain('Refunded'),
    );
  });

  it('uses the linked refund for this session’s own last payment', async () => {
    const { terminal, store } = await seeded();
    const settled = terminal();
    // Pay on the Sale tab first: the recorded sale is then this session's.
    fireEvent.click(screen.getByRole('tab', { name: 'Sale' }));
    fireEvent.click(screen.getByRole('button', { name: /Banana/ }));
    await waitFor(() => expect(screen.getByTestId('grand-total').textContent).toContain('0.35'));
    fireEvent.click(screen.getByRole('button', { name: /^Pay/ }));
    await waitFor(() =>
      expect(screen.getByTestId('settlement-status').textContent).toContain('succeeded'),
    );
    await waitFor(async () => expect(await store.listSales()).toHaveLength(2));

    fireEvent.click(screen.getByRole('tab', { name: 'Refunds' }));
    const cards = await screen.findAllByRole('button', { name: 'select' });
    fireEvent.click(cards[0]!);
    expect(screen.getByText(/this session’s last payment/)).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Referenced refund' }));
    await waitFor(() =>
      expect(screen.getByTestId('reversal-outcome').textContent).toContain('Refunded'),
    );
    // No second settle: the session's own refund() ran.
    expect(settled.settles).toHaveLength(1);
    const [latest] = await store.listSales();
    expect(latest?.refunds).toHaveLength(1);
  });
});
