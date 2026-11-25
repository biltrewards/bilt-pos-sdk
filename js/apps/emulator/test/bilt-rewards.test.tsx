// Bilt rewards (implicit loyalty) in the UI, against the SDK double: the settlement outcome that
// lists a rewards payment beside the card, the partial-approval dialog when the rewards leg was
// the partial payment, a rewards payment on the Refund tab (its own tender, its own refund
// allocation) and the void result that lists what went back to Bilt Rewards.
import type {
  PartialApprovalDecision,
  SettlementHandlers,
  SettlementOptions,
  SettlementResult,
  StepInfo,
  VoidResult,
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

const LAMP = fx.basket([
  fx.lineItem({
    sku: 'SKU-0013',
    description: 'Desk Lamp',
    unitPrice: '34.99',
    taxRate: '0.06625',
  }),
]);

const REWARDS_PAYMENT = {
  amount: '14.92',
  poiTransactionId: 'POI-R',
  poiTransactionTimestamp: '2026-10-06T14:03:05Z',
};

const CARD_PAYMENT = {
  amount: '22.39',
  biltRewardsAmount: '0',
  cardAmount: '22.39',
  poiTransactionId: 'POI-C',
  poiTransactionTimestamp: '2026-10-06T14:03:10Z',
  approvalCode: 'AP0001',
  paymentBrand: 'Visa',
  maskedPan: '************4242',
  partialApproval: false,
};

/** The terminal's points leg paid $14.92 as its own payment and a card paid the rest. */
function rewardsAndCard(): SettlementResult {
  return {
    ...fx.settlementResult(LAMP, '37.31'),
    biltRewardsAmount: '14.92',
    biltRewardsPayments: [REWARDS_PAYMENT],
    cardAmountCharged: '22.39',
    cardPayments: [CARD_PAYMENT],
    poiTransactionId: 'POI-C',
  };
}

/** One card payment of which Bilt rewards paid $9.33 (a payment amount ending in 321, say). */
function mixedCard(): SettlementResult {
  return {
    ...fx.settlementResult(LAMP, '37.31'),
    biltRewardsAmount: '9.33',
    cardAmountCharged: '27.98',
    cardPayments: [
      { ...CARD_PAYMENT, amount: '37.31', biltRewardsAmount: '9.33', cardAmount: '27.98' },
    ],
    poiTransactionId: 'POI-C',
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
): void {
  vi.spyOn(rendered.terminal(), 'settle').mockImplementation(((
    options: SettlementOptions = {},
    handlers = {},
  ) =>
    Object.assign(script({ options, handlers } as SettleCall), {
      id: 'op_settle',
      type: 'settle' as const,
      status: 'running' as const,
      abort: async () => undefined,
      guide: () => undefined,
    })) as never);
}

async function settleWith(result: SettlementResult) {
  const rendered = await renderApp({ checkout: true });
  addProduct('Desk Lamp');
  await waitFor(() => expect(button('Settle $37.31').disabled).toBe(false));
  scriptSettle(rendered, async () => result);
  fireEvent.click(button('Settle $37.31'));
  const popup = await screen.findByRole('dialog', { name: 'Settlement complete' });
  return { rendered, popup };
}

describe('the settlement outcome', () => {
  it('lists a rewards payment as Bilt Rewards beside the card, whose total leaves it out', async () => {
    const { popup } = await settleWith(rewardsAndCard());
    expect(popup.textContent).toContain('Settled $37.31\nBilt Rewards $14.92\ncard $22.39 (Visa)');
    expect(popup.textContent).not.toContain('Card 2');
    expect(screen.getByTestId('last-payment').textContent).toContain(
      'Settled $37.31 — Bilt Rewards $14.92, card $22.39 (Visa)',
    );
  });

  it('shows the rewards part of a single card payment, and the card money without it', async () => {
    const { popup } = await settleWith(mixedCard());
    expect(popup.textContent).toContain(
      'Settled $37.31\nBilt Rewards $9.33 (in the card payment)\ncard $27.98 (Visa)',
    );
    expect(screen.getByTestId('last-payment').textContent).toContain(
      'Bilt Rewards $9.33, card $27.98 (Visa)',
    );
  });

  it('words a rewards-only sale with no card at all', async () => {
    const { popup } = await settleWith({
      ...fx.settlementResult(LAMP, '37.31'),
      biltRewardsAmount: '37.31',
      biltRewardsPayments: [{ ...REWARDS_PAYMENT, amount: '37.31' }],
      cardAmountCharged: '0.00',
      cardPayments: [],
      poiTransactionId: 'POI-R',
    });
    expect(popup.textContent).toContain('Settled $37.31\nBilt Rewards $37.31');
    expect(popup.textContent).not.toMatch(/card/i);
  });

  it('marks the rewards part on each line of a split sale', async () => {
    const { popup } = await settleWith({
      ...mixedCard(),
      cardPayments: [
        { ...CARD_PAYMENT, amount: '20.00', biltRewardsAmount: '5.00', cardAmount: '15.00' },
        {
          ...CARD_PAYMENT,
          amount: '17.31',
          cardAmount: '17.31',
          poiTransactionId: 'POI-D',
          maskedPan: '************1111',
          paymentBrand: 'Mastercard',
          approvalCode: 'AP0002',
        },
      ],
      biltRewardsAmount: '5.00',
      cardAmountCharged: '32.31',
    });
    expect(popup.textContent).toContain(
      'Card 1 $20.00 · Visa ····4242 · approval AP0001 · incl. Bilt Rewards $5.00',
    );
    expect(popup.textContent).toContain('Card 2 $17.31 · Mastercard ····1111 · approval AP0002');
    expect(screen.getByTestId('last-payment').textContent).toContain(
      'Bilt Rewards $5.00, 2 cards $15.00 + $17.31',
    );
  });

  it('stores the sale with its rewards payment so a later refund can reach it', async () => {
    const { rendered } = await settleWith(rewardsAndCard());
    await waitFor(async () => {
      const [stored] = await rendered.store.listSales();
      expect(stored?.sale.legs.map((leg) => leg.type)).toEqual(['CARD', 'BILT_REWARDS']);
    });
    expect(events()).toContain('Sale stored (2 transaction leg(s)');
  });
});

describe('the partial-approval dialog for the rewards leg', () => {
  const PARTIAL = {
    approved: '14.92',
    requested: '37.31',
    shortfall: '22.39',
    amountDue: '22.39',
    cardPayments: [],
    biltRewardsAmount: '14.92',
    biltRewardsPayment: REWARDS_PAYMENT,
    movement: {
      step: 'CARD_CHARGE',
      target: { type: 'SALES' },
      amount: '14.92',
      biltRewardsAmount: '14.92',
    },
  };

  async function partlyApproved(partial: unknown) {
    const rendered = await renderApp({ checkout: true });
    addProduct('Desk Lamp');
    await waitFor(() => expect(button('Settle $37.31').disabled).toBe(false));
    const decisions: PartialApprovalDecision[] = [];
    scriptSettle(rendered, async ({ handlers }) => {
      decisions.push(await handlers.onPartialCardApproval!(partial, STEP));
      return rewardsAndCard();
    });
    fireEvent.click(button('Settle $37.31'));
    const dialog = await screen.findByRole('dialog', { name: 'Partial approval' });
    return { dialog, decisions };
  }

  it('says Bilt Rewards paid, not that a card approved', async () => {
    const { dialog } = await partlyApproved(PARTIAL);
    expect(
      within(dialog).getByText('Bilt Rewards paid $14.92 of $37.31, $22.39 still due'),
    ).toBeTruthy();
    expect(dialog.textContent).not.toContain('Card approved');
    expect(events()).toContain(
      'Partial approval: Bilt Rewards paid $14.92 of $37.31, $22.39 still due',
    );
    // the rest is still collected the usual ways, and Cancel sale reverses the rewards too
    const choices = within(dialog)
      .getAllByRole('button')
      .map((choice) => choice.firstChild?.textContent);
    expect(choices).toEqual(['Another card ($22.39)', 'Rest in cash ($22.39)', 'Cancel sale']);
    expect(
      within(dialog).getByText('Reverse every Bilt Rewards and card payment and stop the sale.'),
    ).toBeTruthy();
  });

  it('still answers Another card with ANOTHER_CARD', async () => {
    const { dialog, decisions } = await partlyApproved(PARTIAL);
    fireEvent.click(within(dialog).getByRole('button', { name: /^Another card/ }));
    await waitFor(() => expect(decisions).toEqual([{ action: 'ANOTHER_CARD' }]));
  });

  it('notes the rewards part of a card approval that is not rewards only', async () => {
    const { dialog } = await partlyApproved({
      ...PARTIAL,
      approved: '60.00',
      requested: '100.00',
      shortfall: '40.00',
      amountDue: '40.00',
      biltRewardsAmount: '15.00',
      biltRewardsPayment: undefined,
    });
    expect(
      within(dialog).getByText(
        'Card approved $60.00 of $100.00 (incl. Bilt Rewards $15.00), $40.00 still due',
      ),
    ).toBeTruthy();
  });
});

describe('a sale with a rewards payment on the Refund tab', () => {
  async function storedSale(settled: SettlementResult = rewardsAndCard()) {
    const store = freshStore();
    await store.recordSale(
      toSaleRecord(settled, {
        sessionId: 'ses_1',
        saleId: 'LANE-3',
        poiId: 'VictaLane-1',
        currency: 'USD',
        memberId: undefined,
        recordId: 'sale-rewards',
        completedAt: new Date('2026-10-06T14:03:11Z'),
      }),
    );
    const rendered = await renderApp({ sales: store });
    fireEvent.click(
      within(screen.getByRole('tablist', { name: 'Screens' })).getByRole('tab', { name: 'Refund' }),
    );
    return rendered;
  }

  it('lists the rewards payment beside the card, labelled Bilt Rewards, in the sales list and the details', async () => {
    await storedSale();
    const list = region('Completed sales');
    await waitFor(() => expect(within(list).getByText('Bilt Rewards $14.92')).toBeTruthy());
    expect(within(list).getByText('Card 1 $22.39 · Visa ····4242 · approval AP0001')).toBeTruthy();
    const details = region('Refund');
    const rewards = within(details).getByRole('list', { name: 'Bilt Rewards payments' });
    expect(within(rewards).getByText('Bilt Rewards $14.92')).toBeTruthy();
    // the card list names only the card
    const cards = within(details).getByRole('list', { name: 'Card payments' });
    expect(within(cards).queryByText(/Bilt Rewards/)).toBeNull();
    expect(
      within(details).getByText(
        'Reverses all 2 payments: the card payments newest first, then Bilt Rewards',
      ),
    ).toBeTruthy();
  });

  it('shows the rewards part of a card payment in the list', async () => {
    await storedSale(mixedCard());
    const list = region('Completed sales');
    await waitFor(() =>
      expect(
        within(list).getByText(
          'Card 1 $37.31 · Visa ····4242 · approval AP0001 · incl. Bilt Rewards $9.33',
        ),
      ).toBeTruthy(),
    );
  });

  it('voids the card and the rewards payment and lists what went back to Bilt Rewards', async () => {
    await storedSale();
    const voids = vi.spyOn(MockTerminalSession.prototype, 'voidTransaction');
    voids.mockImplementation((() =>
      Object.assign(
        Promise.resolve({
          success: true,
          reversedAmount: '37.31',
          cardReversals: [
            {
              tenderType: 'CARD',
              originalPoiTransactionId: 'POI-C',
              amount: '22.39',
              poiTransactionId: 'POI-RV-C',
              customerReceipt: { plainText: 'REVERSAL CARD' },
            },
          ],
          biltRewardsReversals: [
            {
              tenderType: 'BILT_REWARDS',
              originalPoiTransactionId: 'POI-R',
              amount: '14.92',
              biltRewardsAmount: '14.92',
              poiTransactionId: 'POI-RV-R',
              customerReceipt: { plainText: 'REVERSAL REWARDS' },
            },
          ],
          reversedAmountByTender: [
            { tender: 'CARD', poiTransactionId: 'POI-C', amount: '22.39' },
            { tender: 'BILT_REWARDS', poiTransactionId: 'POI-R', amount: '14.92' },
          ],
          pointsReversed: 0,
          remainingPointBalance: 0,
        } satisfies VoidResult),
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
    expect(argOf(voids, 0)).toMatchObject({
      cardPoiTransactionId: 'POI-C',
      biltRewardsPayments: [{ poiTransactionId: 'POI-R', amount: '14.92' }],
    });
    expect(popup.textContent).toContain(
      'Refunded $37.31\nCard reversed $22.39 (txn POI-RV-C)\nBilt Rewards reversed $14.92 (txn POI-RV-R)\nReturned by tender: Card $22.39, Bilt Rewards $14.92',
    );
    expect(within(popup).getByLabelText('Card reversal receipt').textContent).toBe('REVERSAL CARD');
    expect(within(popup).getByLabelText('Bilt Rewards reversal receipt').textContent).toBe(
      'REVERSAL REWARDS',
    );
    expect(events()).toContain('Bilt Rewards reversed $14.92 (txn POI-RV-R)');
    voids.mockRestore();
  });

  it('shows a mixed card payment’s two shares of a void', async () => {
    await storedSale(mixedCard());
    const voids = vi.spyOn(MockTerminalSession.prototype, 'voidTransaction');
    voids.mockImplementation((() =>
      Object.assign(
        Promise.resolve({
          success: true,
          reversedAmount: '37.31',
          cardReversals: [
            {
              tenderType: 'CARD',
              originalPoiTransactionId: 'POI-C',
              amount: '37.31',
              biltRewardsAmount: '9.33',
              poiTransactionId: 'POI-RV-C',
            },
          ],
          biltRewardsReversals: [],
          reversedAmountByTender: [
            { tender: 'CARD', poiTransactionId: 'POI-C', amount: '27.98' },
            { tender: 'BILT_REWARDS', poiTransactionId: 'POI-C', amount: '9.33' },
          ],
          pointsReversed: 0,
          remainingPointBalance: 0,
        } satisfies VoidResult),
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
    expect(argOf(voids, 0)).toMatchObject({
      cardPayments: [{ poiTransactionId: 'POI-C', biltRewardsAmount: '9.33' }],
    });
    expect(popup.textContent).toContain('Returned by tender: Card $27.98, Bilt Rewards $9.33');
    voids.mockRestore();
  });

  it('offers the rewards payment as its own tender in the return picker, and refunds it as BILT_REWARDS', async () => {
    const rendered = await storedSale();
    fireEvent.click(button('Start Checkout'));
    await waitFor(() => expect(button('End Checkout')).toBeTruthy());
    fireEvent.click(screen.getByLabelText('Selected items'));
    fireEvent.click(screen.getByLabelText('Return Desk Lamp'));
    const picker = within(screen.getByRole('group', { name: 'Return to' }));
    const card = picker.getByLabelText(
      'Card 1 $22.39 · Visa ····4242 · approval AP0001 · up to $22.39',
    ) as HTMLInputElement;
    const rewards = picker.getByLabelText('Bilt Rewards $14.92 · up to $14.92') as HTMLInputElement;
    // the card is the default; the rewards payment is not worded or listed as a card
    expect(card.checked).toBe(true);
    expect(picker.queryByLabelText(/^Card 2/)).toBeNull();
    fireEvent.click(rewards);
    fireEvent.click(button('Add return to basket ($37.31)'));
    await waitFor(() =>
      expect(within(region('Basket')).getByText('1× Desk Lamp (return)')).toBeTruthy(),
    );
    expect(events()).toContain('restores to Bilt Rewards on settlement');

    const settle = vi.spyOn(rendered.terminal(), 'settle');
    fireEvent.click(screen.getByLabelText('Net settlement'));
    fireEvent.click(await screen.findByRole('button', { name: /^Settle/ }));
    const popup = await screen.findByRole('dialog', { name: 'Settlement complete' });
    expect(settle.mock.calls[0]?.[0]?.refunds).toEqual([
      {
        type: 'BILT_REWARDS',
        amount: '14.92',
        originalPoiTransactionId: 'POI-R',
        originalPoiTransactionTimestamp: '2026-10-06T14:03:05Z',
      },
      { type: 'EXTERNAL', amount: '22.39' },
    ]);
    expect(within(popup).getByText(/\$14\.92 to Bilt Rewards/)).toBeTruthy();
  });

  it('refunds a rewards-only sale by its rewards payment alone', async () => {
    const rendered = await storedSale({
      ...fx.settlementResult(LAMP, '37.31'),
      biltRewardsAmount: '37.31',
      biltRewardsPayments: [{ ...REWARDS_PAYMENT, amount: '37.31' }],
      cardAmountCharged: '0.00',
      cardPayments: [],
      poiTransactionId: 'POI-R',
    });
    const list = region('Completed sales');
    await waitFor(() => expect(within(list).getByText('Bilt Rewards $37.31')).toBeTruthy());
    expect(within(region('Refund')).queryByRole('list', { name: 'Card payments' })).toBeNull();
    const voids = vi.spyOn(MockTerminalSession.prototype, 'voidTransaction');
    fireEvent.click(button('Refund $37.31'));
    await screen.findByRole('dialog', { name: 'Refund complete' });
    expect(argOf(voids, 0)).toMatchObject({
      biltRewardsPayments: [{ poiTransactionId: 'POI-R' }],
    });
    expect(argOf(voids, 0)).not.toHaveProperty('cardPoiTransactionId');
    expect(argOf(voids, 0)).not.toHaveProperty('cardPayments');
    expect(rendered.pos.sessions).toHaveLength(1);
    voids.mockRestore();
  });
});
