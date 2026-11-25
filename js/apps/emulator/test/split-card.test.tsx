// Split card payments and partial card approvals in the UI, against the SDK double: the Split card
// controls, the partial-approval dialog and the decision it hands the SDK, the outcome's one line
// and one receipt per card payment, and a split sale on the Refund tab.
import { SessionError } from '@biltorg/pos-sdk';
import type {
  PartialApprovalDecision,
  SettlementHandlers,
  SettlementOptions,
  SettlementResult,
  StepInfo,
} from '@biltorg/pos-sdk';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { toSaleRecord } from '../src/store/sale-record';
import {
  addProduct,
  argOf,
  button,
  events,
  freshStore,
  fx,
  MockTerminalSession,
  region,
  renderApp,
  type Rendered,
} from './harness';

const STEP: StepInfo = {
  deadline: new Date(Date.now() + 120_000),
  signal: new AbortController().signal,
};

function twoCards(): SettlementResult {
  const basket = fx.basket([
    fx.lineItem({
      sku: 'SKU-0013',
      description: 'Desk Lamp',
      unitPrice: '34.99',
      taxRate: '0.06625',
    }),
  ]);
  return {
    ...fx.settlementResult(basket, '37.31'),
    cardPayments: [
      {
        amount: '20.00',
        biltRewardsAmount: '0',
        cardAmount: '20.00',
        poiTransactionId: 'POI-A',
        poiTransactionTimestamp: '2026-10-06T14:03:10Z',
        approvalCode: 'AP0001',
        paymentBrand: 'Visa',
        maskedPan: '************4242',
        customerReceipt: { plainText: 'RECEIPT ONE' },
        partialApproval: true,
      },
      {
        amount: '17.31',
        biltRewardsAmount: '0',
        cardAmount: '17.31',
        poiTransactionId: 'POI-B',
        poiTransactionTimestamp: '2026-10-06T14:04:10Z',
        approvalCode: 'AP0002',
        paymentBrand: 'Mastercard',
        maskedPan: '************1111',
        customerReceipt: { plainText: 'RECEIPT TWO' },
        partialApproval: false,
      },
    ],
    poiTransactionId: 'POI-A',
  };
}

interface SettleCall {
  readonly options: SettlementOptions;
  readonly handlers: SettlementHandlers & {
    onPartialCardApproval?: (
      partial: unknown,
      step: StepInfo,
    ) => Promise<PartialApprovalDecision> | PartialApprovalDecision;
  };
}

/** Replaces the double's `settle` with a script that gets the options and handlers the page passed. */
function scriptSettle(
  rendered: Rendered,
  script: (call: SettleCall) => Promise<SettlementResult>,
): SettleCall[] {
  const calls: SettleCall[] = [];
  vi.spyOn(rendered.terminal(), 'settle').mockImplementation(((
    options: SettlementOptions = {},
    handlers = {},
  ) => {
    const call = { options, handlers } as SettleCall;
    calls.push(call);
    return Object.assign(script(call), {
      id: 'op_settle',
      type: 'settle' as const,
      status: 'running' as const,
      abort: async () => undefined,
      guide: () => undefined,
    });
  }) as never);
  return calls;
}

const PARTIAL = {
  approved: '60.00',
  requested: '100.00',
  shortfall: '40.00',
  amountDue: '40.00',
  cardPayments: [
    {
      amount: '60.00',
      poiTransactionId: 'POI-P',
      paymentBrand: 'Visa',
      approvalCode: 'PA1',
      partialApproval: true,
    },
  ],
  movement: { step: 'CARD_CHARGE', target: { type: 'SALES' }, amount: '60.00' },
};

describe('the Split card controls', () => {
  it('shows planned amounts, + Add card and the remainder from the basket total', async () => {
    await renderApp({ checkout: true });
    addProduct('Desk Lamp');
    await waitFor(() => expect(button('Settle $37.31').disabled).toBe(false));
    expect(screen.queryByLabelText('Card 1 amount')).toBeNull();
    expect((screen.getByLabelText('Partial approvals') as HTMLInputElement).checked).toBe(true);
    // the same order as the desktop emulator
    const labels = Array.from(document.querySelectorAll('.payment-controls label.check')).map(
      (label) => label.textContent,
    );
    expect(labels).toEqual([
      'Implicit Loyalty',
      'Rebates',
      'Redemption',
      'Award',
      'Gift card',
      'Split card',
      'Partial approvals',
      'Net settlement',
    ]);

    fireEvent.click(screen.getByLabelText('Implicit Loyalty'));
    fireEvent.click(screen.getByLabelText('Split card'));
    // a blank amount is not a plan yet
    expect(button('Settle $37.31').disabled).toBe(true);
    fireEvent.change(screen.getByLabelText('Card 1 amount'), { target: { value: '20.00' } });
    expect(screen.getByTestId('card-remainder').textContent).toBe('Card 2: remainder $17.31');
    expect(button('Settle $37.31').disabled).toBe(false);

    fireEvent.click(button('+ Add card'));
    fireEvent.change(screen.getByLabelText('Card 2 amount'), { target: { value: '7.31' } });
    expect(screen.getByTestId('card-remainder').textContent).toBe('Card 3: remainder $10.00');
    fireEvent.click(button('Remove card 1'));
    expect(screen.getByTestId('card-remainder').textContent).toBe('Card 2: remainder $30.00');

    // amounts that reach the total leave the last card nothing
    fireEvent.change(screen.getByLabelText('Card 1 amount'), { target: { value: '40.00' } });
    expect(screen.getByTestId('card-remainder').textContent).toBe('Card 2: remainder $0.00');
    // not blocking: the SDK refuses the plan with CARD_PAYMENTS_EXCEED_TOTAL
    expect(
      screen.getByText('The planned cards reach the total: the settlement will be refused'),
    ).toBeTruthy();
    expect(button('Settle $37.31').disabled).toBe(false);
    fireEvent.change(screen.getByLabelText('Card 1 amount'), { target: { value: '' } });
    expect(screen.getByText('Enter a positive amount for every card')).toBeTruthy();
  });

  it('settles with the planned card payments and registers the partial-approval handler', async () => {
    const rendered = await renderApp({ checkout: true });
    addProduct('Desk Lamp');
    await waitFor(() => expect(button('Settle $37.31').disabled).toBe(false));
    const calls = scriptSettle(rendered, async () => twoCards());
    fireEvent.click(screen.getByLabelText('Implicit Loyalty'));
    fireEvent.click(screen.getByLabelText('Split card'));
    fireEvent.change(screen.getByLabelText('Card 1 amount'), { target: { value: '20' } });
    fireEvent.click(button('Settle $37.31'));

    const outcome = await screen.findByRole('dialog', { name: 'Settlement complete' });
    expect(calls[0]?.options.cardPayments).toEqual([{ amount: '20.00' }]);
    expect(calls[0]?.handlers.onPartialCardApproval).toBeTypeOf('function');
    expect(events()).toContain('split card: $20.00 then the remainder, partial approvals on');
    // one line per card payment
    const message = within(outcome).getAllByText(/^Settled/)[0]?.textContent;
    expect(message).toContain('Card 1 $20.00 · Visa ····4242 · approval AP0001 · partial\n');
    expect(message).toContain('Card 2 $17.31 · Mastercard ····1111 · approval AP0002');
    // and every card payment's receipt
    expect(within(outcome).getByLabelText('Card 1 receipt').textContent).toBe('RECEIPT ONE');
    expect(within(outcome).getByLabelText('Card 2 receipt').textContent).toBe('RECEIPT TWO');
    expect(screen.getByTestId('last-payment').textContent).toContain('2 cards $20.00 + $17.31');
    expect(events()).toContain('Card 2 $17.31 · Mastercard ····1111 · approval AP0002');
  });

  it('does not accept a trailing decimal point as a planned amount', async () => {
    await renderApp({ checkout: true });
    addProduct('Desk Lamp');
    await waitFor(() => expect(button('Settle $37.31').disabled).toBe(false));
    fireEvent.click(screen.getByLabelText('Implicit Loyalty'));
    fireEvent.click(screen.getByLabelText('Split card'));
    for (const bad of ['20.', '20.001', '0', 'abc']) {
      fireEvent.change(screen.getByLabelText('Card 1 amount'), { target: { value: bad } });
      expect(button('Settle $37.31').disabled).toBe(true);
    }
    fireEvent.change(screen.getByLabelText('Card 1 amount'), { target: { value: '20.5' } });
    expect(button('Settle $37.31').disabled).toBe(false);
  });

  it('needs explicit loyalty: Split card is disabled and cleared while Implicit Loyalty is on', async () => {
    const rendered = await renderApp({ checkout: true });
    addProduct('Desk Lamp');
    await waitFor(() => expect(button('Settle $37.31').disabled).toBe(false));
    const split = screen.getByLabelText('Split card') as HTMLInputElement;
    expect(split.disabled).toBe(true);
    expect(
      screen.getByText('Split card needs explicit loyalty: clear Implicit Loyalty first'),
    ).toBeTruthy();
    // Partial approvals does not depend on loyalty
    expect((screen.getByLabelText('Partial approvals') as HTMLInputElement).disabled).toBe(false);

    fireEvent.click(screen.getByLabelText('Implicit Loyalty'));
    expect(split.disabled).toBe(false);
    expect(screen.queryByTestId('split-card-caption')).toBeNull();
    fireEvent.click(split);
    expect(screen.getByLabelText('Card 1 amount')).toBeTruthy();
    // checking Implicit Loyalty again takes the split back out
    fireEvent.click(screen.getByLabelText('Implicit Loyalty'));
    expect(split.checked).toBe(false);
    expect(screen.queryByLabelText('Card 1 amount')).toBeNull();

    const calls = scriptSettle(rendered, async () => twoCards());
    fireEvent.click(button('Settle $37.31'));
    await screen.findByRole('dialog', { name: 'Settlement complete' });
    expect(calls[0]?.options.cardPayments).toBeUndefined();
  });

  it('shows a card receipt-less sale the settlement receipt instead', async () => {
    const rendered = await renderApp({ checkout: true });
    addProduct('Desk Lamp');
    await waitFor(() => expect(button('Settle $37.31').disabled).toBe(false));
    scriptSettle(rendered, async () => {
      const result = twoCards();
      return {
        ...result,
        cardPayments: result.cardPayments.map(({ customerReceipt: _, ...payment }) => payment),
        customerReceipt: { plainText: 'GIFT CARD RECEIPT' },
      };
    });
    fireEvent.click(button('Settle $37.31'));
    const outcome = await screen.findByRole('dialog', { name: 'Settlement complete' });
    expect(within(outcome).getByLabelText('Receipt').textContent).toBe('GIFT CARD RECEIPT');
  });

  it('sends no card payments for a plain sale, and no handler with Partial approvals off', async () => {
    const rendered = await renderApp({ checkout: true });
    addProduct('Desk Lamp');
    await waitFor(() => expect(button('Settle $37.31').disabled).toBe(false));
    const calls = scriptSettle(rendered, async () => twoCards());
    fireEvent.click(screen.getByLabelText('Partial approvals'));
    fireEvent.click(button('Settle $37.31'));
    await screen.findByRole('dialog', { name: 'Settlement complete' });
    expect(calls[0]?.options.cardPayments).toBeUndefined();
    expect(calls[0]?.handlers.onPartialCardApproval).toBeUndefined();
  });
});

describe('the partial-approval dialog', () => {
  async function partlyApproved(plannedLeft: boolean) {
    const rendered = await renderApp({ checkout: true });
    addProduct('Desk Lamp');
    await waitFor(() => expect(button('Settle $37.31').disabled).toBe(false));
    const decisions: PartialApprovalDecision[] = [];
    scriptSettle(rendered, async ({ handlers }) => {
      const partial = plannedLeft ? { ...PARTIAL, amountDue: '70.00' } : PARTIAL;
      decisions.push(await handlers.onPartialCardApproval!(partial, STEP));
      return twoCards();
    });
    fireEvent.click(button('Settle $37.31'));
    const dialog = await screen.findByRole('dialog', { name: 'Partial approval' });
    return { dialog, decisions };
  }

  it('states what was approved and what is due, and offers the choices with descriptions', async () => {
    const { dialog } = await partlyApproved(false);
    expect(
      within(dialog).getByText('Card approved $60.00 of $100.00, $40.00 still due'),
    ).toBeTruthy();
    expect(within(dialog).getByText('Choose how to collect the rest:')).toBeTruthy();
    const choices = within(dialog)
      .getAllByRole('button')
      .map((choice) => choice.firstChild?.textContent);
    // no planned card payment is left, so there is nothing to add the shortfall to
    expect(choices).toEqual(['Another card ($40.00)', 'Rest in cash ($40.00)', 'Cancel sale']);
    expect(
      within(dialog).getByText(
        'Charge the $40.00 shortfall on another card, then continue the sale.',
      ),
    ).toBeTruthy();
    expect(events()).toContain(
      'Partial approval: card approved $60.00 of $100.00, $40.00 still due',
    );
  });

  it('is not dismissible', async () => {
    await partlyApproved(false);
    fireEvent.keyDown(window, { key: 'Escape' });
    fireEvent.click(document.querySelector('.backdrop')!);
    expect(screen.getByRole('dialog', { name: 'Partial approval' })).toBeTruthy();
  });

  it('answers Another card with ANOTHER_CARD', async () => {
    const { dialog, decisions } = await partlyApproved(false);
    fireEvent.click(within(dialog).getByRole('button', { name: /Another card/ }));
    await screen.findByRole('dialog', { name: 'Settlement complete' });
    expect(decisions).toEqual([{ action: 'ANOTHER_CARD' }]);
    expect(events()).toContain('Cashier chose Another card ($40.00) for the partial approval');
  });

  it('offers Add to next card while a planned payment is left, and answers ADD_TO_NEXT', async () => {
    const { dialog, decisions } = await partlyApproved(true);
    expect(
      within(dialog).getByText('Card approved $60.00 of $100.00, $70.00 still due'),
    ).toBeTruthy();
    expect(
      within(dialog).getByText('Add the $40.00 shortfall to the next planned card payment.'),
    ).toBeTruthy();
    fireEvent.click(within(dialog).getByRole('button', { name: /Add to next card/ }));
    await screen.findByRole('dialog', { name: 'Settlement complete' });
    expect(decisions).toEqual([{ action: 'ADD_TO_NEXT' }]);
  });

  it('answers Rest in cash with the whole amount still due as cash', async () => {
    const { dialog, decisions } = await partlyApproved(true);
    fireEvent.click(within(dialog).getByRole('button', { name: /Rest in cash \(\$70\.00\)/ }));
    await screen.findByRole('dialog', { name: 'Settlement complete' });
    expect(decisions).toEqual([
      { action: 'EXTERNAL', externalPayment: { tenderType: 'CASH', amount: '70.00' } },
    ]);
  });

  it('answers Cancel sale, and the Abort operation button, with ABORT', async () => {
    const { dialog, decisions } = await partlyApproved(false);
    fireEvent.click(within(dialog).getByRole('button', { name: /Cancel sale/ }));
    await screen.findByRole('dialog', { name: 'Settlement complete' });
    expect(decisions).toEqual([{ action: 'ABORT' }]);
  });

  it('answers ABORT when the host deadline passes', async () => {
    const rendered = await renderApp({ checkout: true });
    addProduct('Desk Lamp');
    await waitFor(() => expect(button('Settle $37.31').disabled).toBe(false));
    const deadline = new AbortController();
    const decisions: PartialApprovalDecision[] = [];
    scriptSettle(rendered, async ({ handlers }) => {
      decisions.push(
        await handlers.onPartialCardApproval!(PARTIAL, { ...STEP, signal: deadline.signal }),
      );
      return twoCards();
    });
    fireEvent.click(button('Settle $37.31'));
    await screen.findByRole('dialog', { name: 'Partial approval' });
    deadline.abort();
    await screen.findByRole('dialog', { name: 'Settlement complete' });
    expect(decisions).toEqual([{ action: 'ABORT' }]);
    expect(events()).toContain('Partial approval deadline passed');
  });
  it('publishes Sale cancelled when the settlement fails after Cancel sale', async () => {
    const rendered = await renderApp({ checkout: true });
    addProduct('Desk Lamp');
    await waitFor(() => expect(button('Settle $37.31').disabled).toBe(false));
    scriptSettle(rendered, async ({ handlers }) => {
      await handlers.onPartialCardApproval!(PARTIAL, STEP);
      throw new SessionError({ code: 'DECLINED', message: 'partial approval cancelled' });
    });
    fireEvent.click(button('Settle $37.31'));
    await screen.findByRole('dialog', { name: 'Partial approval' });
    // the action row's Abort operation button also picks Cancel sale while the dialog is up
    fireEvent.click(button('Abort operation'));
    const cancelled = await screen.findByRole('dialog', { name: 'Sale cancelled' });
    expect(cancelled.textContent).toContain(
      'Every card payment was reversed and the sale stopped. The basket is intact.',
    );
    await waitFor(() => expect(button('Settle $37.31').disabled).toBe(false));
  });

  const CHARGE = {
    step: 'CARD_CHARGE',
    target: { type: 'SALES' },
    amount: '60.00',
    poiTransactionId: 'POI-C1',
  } as const;
  const NOTICE = {
    step: 'CARD_CHARGE',
    target: { type: 'SALES' },
    amount: '60.00',
    poiTransactionId: 'POI-REV-1',
    reversal: true,
    reversedMovement: CHARGE,
  } as const;

  it('words Sale cancelled from the reversal notices: nothing standing', async () => {
    const rendered = await renderApp({ checkout: true });
    addProduct('Desk Lamp');
    await waitFor(() => expect(button('Settle $37.31').disabled).toBe(false));
    scriptSettle(rendered, async ({ handlers }) => {
      handlers.onMovement?.(CHARGE);
      await handlers.onPartialCardApproval!(PARTIAL, STEP);
      handlers.onMovement?.(NOTICE);
      handlers.onCardChargeReversed?.(NOTICE);
      throw new SessionError({ code: 'DECLINED', message: 'partial approval cancelled' });
    });
    fireEvent.click(button('Settle $37.31'));
    const dialog = await screen.findByRole('dialog', { name: 'Partial approval' });
    fireEvent.click(within(dialog).getByRole('button', { name: /Cancel sale/ }));
    const cancelled = await screen.findByRole('dialog', { name: 'Sale cancelled' });
    expect(cancelled.textContent).toContain(
      'Every card payment was reversed and the sale stopped. The basket is intact.',
    );
    expect(events()).toContain(
      'Card payment reversed: $60.00 (reversal txn POI-REV-1, reverses txn POI-C1)',
    );
  });

  it('warns on Sale cancelled with the charges that still stand', async () => {
    const rendered = await renderApp({ checkout: true });
    addProduct('Desk Lamp');
    await waitFor(() => expect(button('Settle $37.31').disabled).toBe(false));
    scriptSettle(rendered, async ({ handlers }) => {
      handlers.onMovement?.(CHARGE);
      handlers.onMovement?.({ ...CHARGE, amount: '5.00', poiTransactionId: 'POI-C2' });
      await handlers.onPartialCardApproval!(PARTIAL, STEP);
      // only the second payment was reversed
      handlers.onMovement?.({
        ...NOTICE,
        reversedMovement: { ...CHARGE, amount: '5.00', poiTransactionId: 'POI-C2' },
      });
      throw new SessionError({ code: 'DECLINED', message: 'cancelled' });
    });
    fireEvent.click(button('Settle $37.31'));
    const dialog = await screen.findByRole('dialog', { name: 'Partial approval' });
    fireEvent.click(within(dialog).getByRole('button', { name: /Cancel sale/ }));
    const cancelled = await screen.findByRole('dialog', { name: 'Sale cancelled' });
    expect(cancelled.querySelector('pre.message')?.textContent).toBe(
      'WARNING: the sale was cancelled but these charges could not be reversed and may still stand:\n' +
        '• card payment $60.00 (txn POI-C1)\n' +
        'Reconcile them manually before settling again.',
    );
    expect(events()).toContain('Still standing: card payment $60.00 (txn POI-C1)');
    expect(events()).toContain('Sale cancelled by the cashier: WARNING:');
  });

  it('adds the standing charges to the Settlement failed popup of an aborted recovery', async () => {
    const rendered = await renderApp({ checkout: true });
    addProduct('Desk Lamp');
    await waitFor(() => expect(button('Settle $37.31').disabled).toBe(false));
    scriptSettle(rendered, async ({ handlers }) => {
      handlers.onMovement?.(CHARGE);
      handlers.onMovement?.({ ...CHARGE, step: 'AWARD', amount: '0', poiTransactionId: 'POI-AW' });
      await handlers.onError!(
        {
          step: 'CARD_CHARGE',
          error: { code: 'DECLINED', message: 'Do not honour' },
          amountDue: '37.31',
          committedMovements: [],
          outcomeCertainty: 'DEFINITIVE',
        },
        STEP,
      );
      throw new SessionError({ code: 'DECLINED', message: 'Do not honour' });
    });
    fireEvent.click(button('Settle $37.31'));
    const recovery = await screen.findByRole('dialog', { name: 'Payment step failed' });
    fireEvent.click(within(recovery).getByRole('button', { name: /Abort and roll back/ }));
    const failed = await screen.findByRole('dialog', { name: 'Settlement failed' });
    expect(failed.textContent).toContain('DECLINED\nDo not honour');
    expect(failed.textContent).toContain(
      'WARNING: the rollback was incomplete; these charges may still stand:\n' +
        '• card payment $60.00 (txn POI-C1)\n• points award $0 (txn POI-AW)\n' +
        'Reconcile them manually before settling again.',
    );
    expect(events()).toContain(
      'WARNING: the rollback was incomplete; these charges may still stand:',
    );
  });

  it('offers only Abort and Abandon for planned cards that reach the total', async () => {
    const rendered = await renderApp({ checkout: true });
    addProduct('Desk Lamp');
    await waitFor(() => expect(button('Settle $37.31').disabled).toBe(false));
    scriptSettle(rendered, async ({ handlers }) => {
      await handlers.onError!(
        {
          step: 'CARD_CHARGE',
          error: { code: 'CARD_PAYMENTS_EXCEED_TOTAL', message: 'planned cards reach the total' },
          amountDue: '37.31',
          committedMovements: [],
          outcomeCertainty: 'DEFINITIVE',
        },
        STEP,
      );
      return twoCards();
    });
    fireEvent.click(button('Settle $37.31'));
    const recovery = await screen.findByRole('dialog', { name: 'Payment step failed' });
    const choices = within(recovery)
      .getAllByRole('button')
      .map((choice) => choice.firstChild?.textContent);
    expect(choices).toEqual(['Abort and roll back', 'Abandon recovery']);
  });
});

describe('a split sale on the Refund tab', () => {
  async function storedSplitSale() {
    const store = freshStore();
    await store.recordSale(
      toSaleRecord(twoCards(), {
        sessionId: 'ses_1',
        saleId: 'LANE-3',
        poiId: 'VictaLane-1',
        currency: 'USD',
        memberId: undefined,
        recordId: 'sale-split',
        completedAt: new Date('2026-10-06T14:03:11Z'),
      }),
    );
    const rendered = await renderApp({ sales: store });
    fireEvent.click(
      within(screen.getByRole('tablist', { name: 'Screens' })).getByRole('tab', { name: 'Refund' }),
    );
    return rendered;
  }

  it('lists the card payments and refunds in full with a void of each', async () => {
    const rendered = await storedSplitSale();
    const list = region('Completed sales');
    // the list entry shows each card payment under the status
    await waitFor(() => expect(within(list).getByText('guest')).toBeTruthy());
    expect(
      within(list).getByText('Card 1 $20.00 · Visa ····4242 · approval AP0001 · partial'),
    ).toBeTruthy();
    expect(
      within(list).getByText('Card 2 $17.31 · Mastercard ····1111 · approval AP0002'),
    ).toBeTruthy();
    const cards = within(region('Refund')).getByRole('list', { name: 'Card payments' });
    expect(
      within(cards).getByText('Card 1 $20.00 · Visa ····4242 · approval AP0001 · partial'),
    ).toBeTruthy();
    expect(
      within(region('Refund')).getByText('Reverses all 2 card payments, newest first'),
    ).toBeTruthy();

    const voids = vi.spyOn(MockTerminalSession.prototype, 'voidTransaction');
    fireEvent.click(button('Refund $37.31'));
    await screen.findByRole('dialog', { name: 'Refund complete' });
    expect(argOf(voids, 0)).toMatchObject({
      cardPayments: [
        { poiTransactionId: 'POI-A', amount: '20.00' },
        { poiTransactionId: 'POI-B', amount: '17.31' },
      ],
    });
    expect(argOf(voids, 0)).not.toHaveProperty('cardPoiTransactionId');
    expect(rendered.pos.sessions).toHaveLength(1);
    voids.mockRestore();
  });

  it('lists each reversed leg of a void with its own receipt', async () => {
    await storedSplitSale();
    const voids = vi.spyOn(MockTerminalSession.prototype, 'voidTransaction');
    voids.mockImplementation((() =>
      Object.assign(
        Promise.resolve({
          success: true,
          reversedAmount: '37.31',
          cardReversals: [
            {
              tenderType: 'CARD',
              originalPoiTransactionId: 'POI-B',
              amount: '17.31',
              poiTransactionId: 'POI-RV-B',
              customerReceipt: { plainText: 'REVERSAL B' },
            },
            {
              tenderType: 'CARD',
              originalPoiTransactionId: 'POI-A',
              amount: '20.00',
              serviceId: 'SVC-A',
              merchantReceipt: { plainText: 'REVERSAL A' },
            },
          ],
          reversedAmountByTender: [],
          pointsReversed: 0,
          remainingPointBalance: 0,
        }),
        {
          id: 'op_void',
          type: 'voidTransaction' as const,
          status: 'running' as const,
          abort: async () => undefined,
          guide: () => undefined,
        },
      )) as never);
    fireEvent.click(button('Refund $37.31'));
    const popup = await screen.findByRole('dialog', { name: 'Refund complete' });
    expect(popup.textContent).toContain(
      'Refunded $37.31\nCard 2 reversed $17.31 (txn POI-RV-B)\nCard 1 reversed $20.00 (txn SVC-A)',
    );
    expect(within(popup).getByLabelText('Card 2 reversal receipt').textContent).toBe('REVERSAL B');
    expect(within(popup).getByLabelText('Card 1 reversal receipt').textContent).toBe('REVERSAL A');
    expect(within(popup).queryByLabelText('Receipt')).toBeNull();
    voids.mockRestore();
  });

  it('disables a card the earlier returns drew down to nothing and defaults to one with capacity', async () => {
    const store = freshStore();
    const record = toSaleRecord(twoCards(), {
      sessionId: 'ses_1',
      saleId: 'LANE-3',
      poiId: 'VictaLane-1',
      currency: 'USD',
      memberId: undefined,
      recordId: 'sale-drawn',
      completedAt: new Date('2026-10-06T14:03:11Z'),
    });
    await store.recordSale(record);
    await store.recordRefund({
      saleId: 'sale-drawn',
      recordedAt: '2026-10-07T10:00:00Z',
      amount: '20.00',
      leg: 'CARD',
      legPoiTransactionId: 'POI-A',
      tenderAmount: '20.00',
      full: false,
      awardReversed: false,
      reversalProgress: false,
      items: [],
    });
    await renderApp({ sales: store });
    fireEvent.click(
      within(screen.getByRole('tablist', { name: 'Screens' })).getByRole('tab', { name: 'Refund' }),
    );
    await screen.findByRole('region', { name: 'Refund' });
    fireEvent.click(screen.getByLabelText('Selected items'));
    const picker = within(screen.getByRole('group', { name: 'Return to' }));
    const drawn = picker.getByLabelText(/^Card 1 .* · up to \$0\.00$/) as HTMLInputElement;
    const other = picker.getByLabelText(/^Card 2 .* · up to \$17\.31$/) as HTMLInputElement;
    expect(drawn.disabled).toBe(true);
    expect(other.disabled).toBe(false);
    expect(other.checked).toBe(true);
  });

  it('shows every card refunded and hides the Return to picker after a void', async () => {
    const store = freshStore();
    await store.recordSale(
      toSaleRecord(twoCards(), {
        sessionId: 'ses_1',
        saleId: 'LANE-3',
        poiId: 'VictaLane-1',
        currency: 'USD',
        memberId: undefined,
        recordId: 'sale-voided',
        completedAt: new Date('2026-10-06T14:03:11Z'),
      }),
    );
    await store.recordRefund({
      saleId: 'sale-voided',
      recordedAt: '2026-10-07T10:00:00Z',
      amount: '37.31',
      full: true,
      awardReversed: false,
      reversalProgress: false,
    });
    await renderApp({ sales: store });
    fireEvent.click(
      within(screen.getByRole('tablist', { name: 'Screens' })).getByRole('tab', { name: 'Refund' }),
    );
    const cards = await within(await screen.findByRole('region', { name: 'Refund' })).findByRole(
      'list',
      { name: 'Card payments' },
    );
    expect(within(cards).getAllByText(/· refunded$/)).toHaveLength(2);
    expect(screen.queryByRole('group', { name: 'Return to' })).toBeNull();
  });

  it('asks which card a returned item goes back to', async () => {
    const rendered = await storedSplitSale();
    fireEvent.click(button('Start Checkout'));
    await waitFor(() => expect(button('End Checkout')).toBeTruthy());
    fireEvent.click(screen.getByLabelText('Selected items'));
    fireEvent.click(screen.getByLabelText('Return Desk Lamp'));
    const picker = within(screen.getByRole('group', { name: 'Return to' }));
    const first = picker.getByLabelText(/^Card 1 \$20\.00/) as HTMLInputElement;
    const second = picker.getByLabelText(
      'Card 2 $17.31 · Mastercard ····1111 · approval AP0002 · up to $17.31',
    ) as HTMLInputElement;
    expect(first.checked).toBe(true);
    expect(picker.getByLabelText(/^Card 1 .* · up to \$20\.00$/)).toBe(first);
    fireEvent.click(second);
    fireEvent.click(button('Add return to basket ($37.31)'));
    await waitFor(() =>
      expect(within(region('Basket')).getByText('1× Desk Lamp (return)')).toBeTruthy(),
    );

    const settle = vi.spyOn(rendered.terminal(), 'settle');
    fireEvent.click(screen.getByLabelText('Net settlement'));
    fireEvent.click(await screen.findByRole('button', { name: /^Settle/ }));
    await screen.findByRole('dialog', { name: 'Settlement complete' });
    expect(events()).toContain('restores to the card 2 on settlement');
    expect(settle.mock.calls[0]?.[0]?.refunds).toEqual([
      {
        type: 'CARD',
        amount: '17.31',
        originalPoiTransactionId: 'POI-B',
        originalPoiTransactionTimestamp: '2026-10-06T14:04:10Z',
      },
      { type: 'EXTERNAL', amount: '20.00' },
    ]);
  });
});
