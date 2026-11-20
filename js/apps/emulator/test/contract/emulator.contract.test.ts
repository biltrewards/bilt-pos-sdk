// The emulator's checkout contract against the real Session Host and a scripted terminal, using
// the launcher and fake terminal of `@biltorg/pos-sdk`'s contract suite: a sale whose rebate step
// is re-taxed by the register (`TOTAL_REQUIRED`) and whose declined card charge is retried
// through the recovery decision (`RECOVERY_REQUIRED`), the sale persisted the way the Refunds
// tab needs it, a gift-card tender, and an item return of the persisted sale from a later
// session, refunded to the original card.
import { writeFileSync } from 'node:fs';
import type { OperationStep, SettlementFailure } from '@biltorg/pos-protocol';
import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest';
import type { BiltPos, TerminalShopperSession } from '@biltorg/pos-sdk';
import { FakeTerminal, paymentEcho } from '../../../../packages/sdk/test/contract/fake-terminal';
import {
  buildHost,
  hostAvailable,
  hostProbe,
  skipReason,
  startHost,
  type RunningHost,
} from '../../../../packages/sdk/test/contract/host';
import { connectTo } from '../../../../packages/sdk/test/contract/setup';
import { CATALOG, customItem, toBasketItem } from '../../src/catalog';
import { BrowserEmulatorController, totalAfterRebates } from '../../src/emulator/controller';
import type { MemberLookupKind } from '../../src/emulator/state';
import { LineLog } from '../../src/log';
import {
  planAllocations,
  planReturn,
  requiredRefundMinor,
  returnLine,
  settledReturns,
  toStoredSaleUi,
} from '../../src/emulator/returns';
import { legOf, remainingLegAmount, toSaleRecord } from '../../src/store/sale-record';
import { IndexedDbSaleStore } from '../../src/store/sales-store';

const POI = 'VictaLane-EMU';
const CARD = '6006491260550218157';

/** A $10 rebate on the lamp line, so the register has something to re-tax. */
const LAMP_REBATE =
  '{"SaleToPOIResponse":{"LoyaltyResponse":{' +
  '"Response":{"Result":"Success"},' +
  '"POIData":{"POITransactionID":{"TransactionID":"POI-RB-EMU","TimeStamp":"2026-10-06T10:00:01Z"}},' +
  '"LoyaltyResult":[{"Rebates":{"TotalRebate":10.00,"RebateLabel":"Gold Member",' +
  '"SaleItemRebate":[{"ItemID":1,"ProductCode":"SKU-0013","ItemAmount":10.00,"RebateLabel":"Gold: $10 off"}]}}]}}}';

const PAYMENT_DECLINED =
  '{"SaleToPOIResponse":{"PaymentResponse":{' +
  '"Response":{"Result":"Failure","ErrorCondition":"Refusal","AdditionalResponse":"Do not honour"},' +
  '"POIData":{"POITransactionID":{"TransactionID":"POI-PAY-DECLINED","TimeStamp":"2026-10-06T10:00:02Z"}},' +
  '"PaymentResult":{"AmountsResp":{"Currency":"USD","AuthorizedAmount":0}}}}}';

const REVERSAL_OK = '{"SaleToPOIResponse":{"ReversalResponse":{"Response":{"Result":"Success"}}}}';

const BALANCE_FOUND =
  '{"SaleToPOIResponse":{"BalanceInquiryResponse":{"Response":{"Result":"Success"},' +
  '"LoyaltyAccountStatus":{"LoyaltyAccount":{' +
  '"LoyaltyAccountID":{"IdentificationType":"AccountNumber","LoyaltyID":"98234"},' +
  '"LoyaltyBrand":"K-Club"},"CurrentBalance":1240}}}}';

const BALANCE_NOT_FOUND =
  '{"SaleToPOIResponse":{"BalanceInquiryResponse":{' +
  '"Response":{"Result":"Failure","ErrorCondition":"NotFound"}}}}';

/** The loyalty account a BalanceInquiry asked the terminal about. */
function accountOf(body: object): unknown {
  return (
    body as {
      SaleToPOIRequest: {
        BalanceInquiryRequest: { LoyaltyAccountReq: { LoyaltyAccountID: unknown } };
      };
    }
  ).SaleToPOIRequest.BalanceInquiryRequest.LoyaltyAccountReq.LoyaltyAccountID;
}

/** A card payment response: approved or partly approved for `amount`, under its own POI reference. */
function cardPayment(poiId: string, amount: number, partial = false, brand = 'Visa'): string {
  return (
    '{"SaleToPOIResponse":{"PaymentResponse":{' +
    `"Response":{"Result":"${partial ? 'Partial' : 'Success'}"},` +
    `"POIData":{"POITransactionID":{"TransactionID":"${poiId}","TimeStamp":"2026-10-06T10:00:0${poiId.slice(-1)}Z"}},` +
    `"PaymentResult":{"AmountsResp":{"Currency":"USD","AuthorizedAmount":${amount.toFixed(2)}},` +
    `"PaymentAcquirerData":{"ApprovalCode":"APPR-${poiId}","AcquirerTransactionID":{"TransactionID":"ACQ-${poiId}"}},` +
    `"PaymentInstrumentData":{"CardData":{"PaymentBrand":"${brand}","MaskedPAN":"************4242"}}}}}}`
  );
}

interface PaymentWire {
  readonly SaleToPOIRequest: {
    PaymentRequest: {
      SaleData: { SaleTransactionID: { TransactionID: string } };
      PaymentTransaction: { AmountsReq: { RequestedAmount: number; PaidAmount?: number } };
      PaymentData?: { SplitPaymentFlag?: boolean; PaymentType?: string };
    };
  };
}

/** What a card payment request told the terminal: its share, what was already paid, the split flag. */
function wireOf(body: object): {
  requested: number;
  paid: number | undefined;
  split: boolean | undefined;
  saleTransactionId: string;
} {
  const request = (body as PaymentWire).SaleToPOIRequest.PaymentRequest;
  return {
    requested: request.PaymentTransaction.AmountsReq.RequestedAmount,
    paid: request.PaymentTransaction.AmountsReq.PaidAmount,
    split: request.PaymentData?.SplitPaymentFlag,
    saleTransactionId: request.SaleData.SaleTransactionID.TransactionID,
  };
}

const lamp = CATALOG.find((product) => product.sku === 'SKU-0013')!;
const coffee = CATALOG.find((product) => product.sku === 'SKU-0006')!;

if (!hostAvailable) console.warn(`[contract] skipping: ${skipReason()}`);

describe.skipIf(!hostAvailable)('the emulator against the Session Host', () => {
  let terminal: FakeTerminal;
  let host: RunningHost;
  let pos: BiltPos;
  let store: IndexedDbSaleStore;
  const open: TerminalShopperSession[] = [];

  beforeAll(async () => {
    if (!('location' in hostProbe)) return;
    buildHost(hostProbe.location);
    terminal = new FakeTerminal();
    terminal.reply('Loyalty/Rebate', LAMP_REBATE);
    const address = await terminal.start();
    host = await startHost(hostProbe.location, { ...address, model: 'VictaLane' });
    pos = await connectTo(host.port);
    store = new IndexedDbSaleStore(indexedDB, `contract-${Date.now()}`);
  });

  afterAll(async () => {
    for (const session of open.splice(0)) {
      if (session.state === 'open') await session.forceEnd('test cleanup').catch(() => undefined);
    }
    await pos?.close();
    await host?.stop();
    await terminal?.stop();
    // `BILT_HOST_LOG=/path` keeps the host's output for a failed run.
    if (process.env.BILT_HOST_LOG && host) writeFileSync(process.env.BILT_HOST_LOG, host.log());
  });

  async function lane(): Promise<TerminalShopperSession> {
    const session = await pos.startTerminalSession({
      saleId: 'LANE-EMU',
      poiId: POI,
      currency: 'USD',
      storeLocation: 'STR-0142',
      autoDisplay: false,
    });
    open.push(session);
    return session;
  }

  it('sells a taxed item: re-taxes the rebate, retries the declined charge, and persists the sale', async () => {
    const session = await lane();
    await session.member.set({ id: '98234' });
    await session.basket.addItem(toBasketItem(lamp));
    expect(session.basket.current.grandTotal).toBe('37.31');

    // Decline the first card charge; the register answers RETRY and the second attempt approves.
    let charges = 0;
    terminal.reply('Payment', (request) =>
      ++charges === 1 ? PAYMENT_DECLINED : paymentEcho(request),
    );

    const steps: OperationStep['kind'][] = [];
    session.on('operation.step', (step) => steps.push(step.kind));
    const failures: SettlementFailure[] = [];
    const transactionIds: string[] = [];
    const result = await session.settle(undefined, {
      beforeStep: (context) => {
        transactionIds.push(context.defaultTransactionId);
        return context.defaultTransactionId;
      },
      onRebatesRedeemed: totalAfterRebates,
      onError: (failure) => {
        failures.push(failure);
        return 'RETRY';
      },
    });

    // 34.99 − 10.00 rebate = 24.99, taxed at 6.625% = 1.66 → 26.65, not the host's 27.31.
    expect(Number(result.cardAmountCharged)).toBe(26.65);
    expect(Number(result.totalRebateAmount)).toBe(10);
    expect(failures).toHaveLength(1);
    expect(failures[0]).toMatchObject({ step: 'CARD_CHARGE', outcomeCertainty: 'DEFINITIVE' });
    expect(failures[0]?.error.code).toBe('DECLINED');
    expect(charges).toBe(2);
    expect(steps).toContain('TOTAL_REQUIRED');
    expect(steps).toContain('RECOVERY_REQUIRED');
    expect(new Set(transactionIds).size).toBe(1);
    // The host moves the phase with a context event that can land after the settle result.
    await vi.waitFor(() => expect(session.context.phase()).toBe('COMPLETE'));

    const record = toSaleRecord(result, {
      sessionId: session.id,
      saleId: session.saleId,
      poiId: session.poiId,
      currency: session.currency,
      memberId: '98234',
      recordId: 'sale-contract-1',
      completedAt: new Date(),
    });
    expect(record.legs.map((leg) => leg.type).sort()).toEqual(['AWARD', 'CARD', 'REBATE']);
    expect(record.legs.find((leg) => leg.type === 'CARD')).toMatchObject({
      poiTransactionId: 'POI-PAY-1',
      amount: '26.65',
      approvalCode: 'APPR7',
      brand: 'Visa',
    });
    expect(record.items.map((item) => item.sku)).toEqual(['SKU-0013']);
    await store.recordSale(record);
    await session.end();
  });

  it('pays with a gift card first and records the stored value leg', async () => {
    terminal.reply('Payment', paymentEcho);
    const session = await lane();
    await session.basket.addItem(toBasketItem(coffee));
    expect(session.basket.current.grandTotal).toBe('3.75');
    await session.setStoredValueCard(CARD);

    let giftCardSuggested: string | undefined;
    const result = await session.settle(
      {
        loyalty: { handling: 'FORBIDDEN' },
      },
      {
        onGiftCardPayment: (giftCard) => {
          giftCardSuggested = giftCard.suggestedTotal;
          return giftCard.suggestedTotal;
        },
      },
    );

    expect(Number(result.storedValueAmountUsed)).toBe(3.75);
    expect(Number(giftCardSuggested)).toBe(0);
    expect(Number(result.cardAmountCharged)).toBe(0);
    expect(result.movements.map((m) => m.step)).toEqual(['STORED_VALUE_CHARGE']);
    const record = toSaleRecord(result, {
      sessionId: session.id,
      saleId: session.saleId,
      poiId: session.poiId,
      currency: session.currency,
      memberId: undefined,
      recordId: 'sale-contract-2',
      completedAt: new Date(),
    });
    expect(record.legs.map((leg) => leg.type)).toEqual(['STORED_VALUE']);
    expect(record.legs[0]?.amount).toBe('3.75');
    await store.recordSale(record);
    await session.end();
  });

  it('returns the persisted sale item from a later session against its card reference', async () => {
    const stored = await store.findSale('sale-contract-1');
    expect(stored).not.toBeNull();
    const plan = planReturn(stored!, new Set(['SKU-0013']), () => 0);
    if ('error' in plan) throw new Error(plan.error);
    // Shelf price plus tax, 37.31; the card collected only 26.65 after the rebate.
    expect(plan.pending).toMatchObject({ amountMinor: 3731, legCapacityMinor: 2665 });

    const session = await lane();
    await session.basket.addItem(returnLine(plan.pending.items[0]!));
    expect(session.basket.current.grandTotal).toBe('-37.31');
    const { returns, allocations } = planAllocations(
      [plan.pending],
      requiredRefundMinor(session.basket.current, false),
    );
    expect(allocations).toEqual([
      {
        type: 'CARD',
        amount: '26.65',
        originalPoiTransactionId: 'POI-PAY-1',
        originalPoiTransactionTimestamp: expect.any(String),
      },
      { type: 'EXTERNAL', amount: '10.66' },
    ]);
    const result = await session.settle({
      settlementType: 'REFUND_THEN_CHARGE',
      refunds: allocations,
      loyalty: { handling: 'FORBIDDEN' },
    });

    expect(Number(result.cardRefundedAmount)).toBe(26.65);
    expect(Number(result.cardAmountCharged)).toBe(0);
    const refundMovement = result.movements.find((m) => m.step === 'CARD_REFUND');
    expect(Number(refundMovement?.amount)).toBe(26.65);
    // The linked refund named the original card payment on the wire.
    const refundRequests = terminal
      .requestsOf('Payment')
      .filter((body) => JSON.stringify(body).includes('"POI-PAY-1"'));
    expect(refundRequests.length).toBeGreaterThan(0);

    const { records, parts } = settledReturns(returns, result);
    expect(parts[0]).toContain('returned $37.31 ($26.65 to the card, $10.66 register-paid');
    for (const record of records) await store.recordRefund(record);
    const after = await store.findSale('sale-contract-1');
    expect(after?.refunds[0]).toMatchObject({
      amount: '37.31',
      tenderAmount: '26.65',
      leg: 'CARD',
      full: false,
      items: [{ sku: 'SKU-0013', quantity: 1 }],
    });
    expect(remainingLegAmount(after!, legOf(after!.sale, 'CARD')!)).toBe('0.00');
    expect(toStoredSaleUi(after!, () => 0).items[0]?.remainingQuantity).toBe(0);
    await session.end();
  });

  // RET-6983: needs the host change (feature/ret-6983-vas-host) to map a wallet-pass read onto
  // IdentifyResult.vasData; the in-memory tests cover the emulator's rendering until it lands.
  it.todo('shows the wallet pass on the sign-in card when a keyed sign-in reads one');

  const controllers: BrowserEmulatorController[] = [];

  afterAll(async () => {
    for (const controller of controllers.splice(0)) {
      controller.endSession();
      await vi.waitFor(() => expect(controller.getState().sessionId).toBeNull(), 30_000);
      controller.dispose();
    }
  });

  /** A controller with an open terminal checkout, as after Connect and Start Checkout. */
  async function checkout() {
    const events = new LineLog();
    const controller = new BrowserEmulatorController(
      {
        connect: () => connectTo(host.port),
        sales: store,
        events,
        detailed: new LineLog(),
        protocol: new LineLog(),
      },
      {
        mode: 'terminal',
        poiId: POI,
        saleId: 'LANE-EMU',
        currency: 'USD',
        storeLocation: 'STR-0142',
      },
    );
    controllers.push(controller);
    controller.connect();
    await vi.waitFor(
      () => expect(controller.getState().connection.phase).toBe('CONNECTED'),
      30_000,
    );
    controller.startSession();
    await vi.waitFor(() => expect(controller.getState().sessionId).not.toBeNull(), 30_000);
    const logged = (text: string) => events.snapshot().some((line) => line.includes(text));
    return { controller, logged };
  }

  describe('split and partial card payments', () => {
    const NO_LOYALTY = { implicit: false, rebates: false, redemption: false, award: false };

    /** Settles the lamp ($37.31) through the controller and waits for the outcome popup. */
    async function sellLamp(options: { plannedAmounts: string[]; partialApprovals: boolean }) {
      return sellLampWith(NO_LOYALTY, options);
    }

    async function sellLampWith(
      loyalty: typeof NO_LOYALTY,
      options: { plannedAmounts: string[]; partialApprovals: boolean },
    ) {
      const { controller, logged } = await checkout();
      controller.addProduct(lamp);
      await vi.waitFor(() => expect(controller.getState().basketTotal).toBe('37.31'));
      const before = terminal.requestsOf('Payment').length;
      controller.settle(loyalty, null, true, options);
      return { controller, logged, payments: () => terminal.requestsOf('Payment').slice(before) };
    }

    it('refuses planned card payments with implicit loyalty and sends nothing', async () => {
      const { controller, logged, payments } = await sellLampWith(
        { implicit: true, rebates: false, redemption: false, award: false },
        { plannedAmounts: ['20.00'], partialApprovals: true },
      );
      await vi.waitFor(() =>
        expect(
          logged('Settlement not started: planned card payments cannot use implicit loyalty'),
        ).toBe(true),
      );
      expect(payments()).toHaveLength(0);
      expect(controller.getState().paymentInProgress).toBe(false);
    });

    it('charges a planned split on two cards, stores both and voids both', async () => {
      terminal.reply('Reversal', REVERSAL_OK);
      let call = 0;
      terminal.reply('Payment', (request) => {
        const { requested } = wireOf(request as object);
        return cardPayment(++call === 1 ? 'POI-SPLIT-1' : 'POI-SPLIT-2', requested);
      });
      const { controller, payments } = await sellLamp({
        plannedAmounts: ['20.00'],
        partialApprovals: true,
      });
      await vi.waitFor(() => expect(controller.getState().paymentOutcome).not.toBeNull(), 30_000);

      // the second card payment is a nexo split payment under the first one's sale transaction
      const [first, second] = payments().map((body) => wireOf(body as object));
      expect(first).toMatchObject({ requested: 20, paid: undefined });
      expect(first?.split).toBeUndefined();
      expect(second).toMatchObject({ requested: 17.31, paid: 20, split: true });
      expect(second?.saleTransactionId).toBe(first?.saleTransactionId);

      const outcome = controller.getState().paymentOutcome!;
      expect(outcome.title).toBe('Settlement complete');
      expect(outcome.message).toContain(
        'Card 1 $20.00 · Visa ····4242 · approval APPR-POI-SPLIT-1',
      );
      expect(outcome.message).toContain(
        'Card 2 $17.31 · Visa ····4242 · approval APPR-POI-SPLIT-2',
      );

      await vi.waitFor(async () => expect((await store.listSales())[0]?.sale.legs).toHaveLength(2));
      const [stored] = await store.listSales();
      const cards = stored!.sale.legs.filter((leg) => leg.type === 'CARD');
      expect(cards.map((leg) => [leg.poiTransactionId, leg.amount])).toEqual([
        ['POI-SPLIT-1', '20.00'],
        ['POI-SPLIT-2', '17.31'],
      ]);
      expect(toStoredSaleUi(stored!, () => 0).cardPayments).toHaveLength(2);

      // the full refund reverses every card payment, newest first
      const reversals = terminal.requestsOf('Reversal').length;
      await vi.waitFor(() => expect(controller.getState().sessionId).toBeNull(), 30_000);
      controller.refundSale(stored!.sale.id);
      await vi.waitFor(
        () => expect(controller.getState().paymentOutcome?.title).toBe('Refund complete'),
        30_000,
      );
      const reversed = terminal
        .requestsOf('Reversal')
        .slice(reversals)
        .map((body) => JSON.stringify(body).match(/"TransactionID":"(POI-SPLIT-\d)"/)?.[1]);
      expect(reversed).toEqual(['POI-SPLIT-2', 'POI-SPLIT-1']);
      // the outcome lists each reversed card payment, newest first
      const popup = controller.getState().paymentOutcome!.message;
      expect(popup).toContain('Card 2 reversed $17.31');
      expect(popup).toContain('Card 1 reversed $20.00');
      expect(popup.indexOf('Card 2 reversed')).toBeLessThan(popup.indexOf('Card 1 reversed'));
      await vi.waitFor(async () =>
        expect((await store.findSale(stored!.sale.id))?.refunds.some((refund) => refund.full)).toBe(
          true,
        ),
      );
    });

    it('answers a partial approval with Another card and continues on the next card', async () => {
      const requests: number[] = [];
      terminal.reply('Payment', (request) => {
        const { requested } = wireOf(request as object);
        requests.push(requested);
        // the first card approves 60% of what it was asked, the next one the whole shortfall
        return requests.length === 1
          ? cardPayment('POI-PART-1', Math.round(requested * 60) / 100, true)
          : cardPayment('POI-PART-2', requested, false, 'Mastercard');
      });
      const { controller, payments } = await sellLamp({
        plannedAmounts: [],
        partialApprovals: true,
      });
      await vi.waitFor(() => expect(controller.getState().partialApproval).not.toBeNull(), 30_000);
      const prompt = controller.getState().partialApproval!;
      expect(prompt).toMatchObject({
        approved: '22.39',
        requested: '37.31',
        shortfall: '14.92',
        amountDue: '14.92',
        plannedLeft: false,
        actions: ['ANOTHER_CARD', 'EXTERNAL', 'ABORT'],
      });
      prompt.choose('ANOTHER_CARD');
      await vi.waitFor(() => expect(controller.getState().paymentOutcome).not.toBeNull(), 30_000);

      const [first, second] = payments().map((body) => wireOf(body as object));
      expect(first?.requested).toBe(37.31);
      expect(second).toMatchObject({ requested: 14.92, paid: 22.39, split: true });
      expect(second?.saleTransactionId).toBe(first?.saleTransactionId);
      const message = controller.getState().paymentOutcome!.message;
      expect(message).toContain(
        'Card 1 $22.39 · Visa ····4242 · approval APPR-POI-PART-1 · partial',
      );
      expect(message).toContain('Card 2 $14.92 · Mastercard ····4242 · approval APPR-POI-PART-2');
    });

    it('records the rest in cash and keeps the partial card payment', async () => {
      terminal.reply('Payment', (request) =>
        cardPayment('POI-CASH-1', Math.round(wireOf(request as object).requested * 60) / 100, true),
      );
      const { controller, payments } = await sellLamp({
        plannedAmounts: [],
        partialApprovals: true,
      });
      await vi.waitFor(() => expect(controller.getState().partialApproval).not.toBeNull(), 30_000);
      controller.getState().partialApproval!.choose('EXTERNAL');
      await vi.waitFor(() => expect(controller.getState().paymentOutcome).not.toBeNull(), 30_000);
      expect(payments()).toHaveLength(1);
      const message = controller.getState().paymentOutcome!.message;
      expect(message).toContain('cash $14.92 (register-managed; refund manually)');
      expect(message).toContain('card $22.39 (Visa)');
    });

    it('cancels the sale and reverses the partial card payment', async () => {
      terminal.reply('Reversal', REVERSAL_OK);
      terminal.reply('Payment', (request) =>
        cardPayment('POI-VOID-1', Math.round(wireOf(request as object).requested * 60) / 100, true),
      );
      const { controller } = await sellLamp({ plannedAmounts: [], partialApprovals: true });
      await vi.waitFor(() => expect(controller.getState().partialApproval).not.toBeNull(), 30_000);
      const reversals = terminal.requestsOf('Reversal').length;
      controller.getState().partialApproval!.choose('ABORT');
      await vi.waitFor(
        () => expect(controller.getState().paymentOutcome?.title).toBe('Sale cancelled'),
        30_000,
      );
      expect(controller.getState().paymentOutcome?.message).toContain(
        'Every card payment was reversed',
      );
      expect(terminal.requestsOf('Reversal').length).toBe(reversals + 1);
    });

    it('with Partial approvals off, the SDK follows up itself and then fails into the recovery dialog', async () => {
      terminal.reply('Reversal', REVERSAL_OK);
      let call = 0;
      terminal.reply('Payment', (request) =>
        cardPayment(
          `POI-AUTO-${++call}`,
          Math.round(wireOf(request as object).requested * 60) / 100,
          true,
        ),
      );
      const { controller, payments } = await sellLamp({
        plannedAmounts: [],
        partialApprovals: false,
      });
      await vi.waitFor(() => expect(controller.getState().paymentRecovery).not.toBeNull(), 30_000);
      expect(controller.getState().partialApproval).toBeNull();
      // three card requests in all: the first and two automatic follow-ups
      expect(payments()).toHaveLength(3);
      expect(controller.getState().paymentRecovery?.message).toContain('DECLINED');
      controller.getState().paymentRecovery!.choose('ABORT');
      await vi.waitFor(
        () => expect(controller.getState().paymentOutcome?.title).toBe('Settlement failed'),
        30_000,
      );
    });

    it('refuses planned cards that reach the total before any card is charged', async () => {
      terminal.reply('Payment', paymentEcho);
      const { controller, payments } = await sellLamp({
        plannedAmounts: ['40.00'],
        partialApprovals: true,
      });
      await vi.waitFor(() => expect(controller.getState().paymentRecovery).not.toBeNull(), 30_000);
      expect(controller.getState().paymentRecovery?.message).toContain(
        'CARD_PAYMENTS_EXCEED_TOTAL',
      );
      expect(payments()).toHaveLength(0);
      controller.getState().paymentRecovery!.choose('ABORT');
      await vi.waitFor(() => expect(controller.getState().paymentOutcome).not.toBeNull(), 30_000);
    });
  });

  describe('the identify control', () => {
    function lastInquiry(): unknown {
      return accountOf(terminal.requestsOf('BalanceInquiry').at(-1)!);
    }

    it('signs in from the PIN pad with a keyed CardAcquisition', async () => {
      const { controller, logged } = await checkout();
      const before = terminal.requestsOf('CardAcquisition').length;
      controller.identifyMember();
      // unscripted: the prompt fails once the request is on the wire
      await vi.waitFor(() => expect(controller.getState().identifyInProgress).toBe(false), 30_000);
      const requests = terminal.requestsOf('CardAcquisition').slice(before);
      expect(requests).toHaveLength(1);
      expect(JSON.stringify(requests[0])).toContain('"ForceEntryMode":["Keyed"]');
      expect(logged('Loyalty sign-in on the terminal (key it in or tap a wallet pass)…')).toBe(
        true,
      );
    });

    it.each([
      [true, undefined],
      [false, 'Forbidden'],
    ])(
      'settles with implicit loyalty %s: the card payment carries LoyaltyHandling %s',
      async (implicit, handling) => {
        terminal.reply('Payment', paymentEcho);
        const { controller, logged } = await checkout();
        controller.addProduct(coffee);
        await vi.waitFor(() => expect(controller.getState().basketTotal).toBe('3.75'));
        const before = terminal.requestsOf('Payment').length;
        controller.settle(
          { implicit, rebates: false, redemption: false, award: false },
          null,
          true,
        );
        await vi.waitFor(() => expect(controller.getState().paymentOutcome).not.toBeNull(), 30_000);
        const payments = terminal.requestsOf('Payment').slice(before);
        expect(payments).toHaveLength(1);
        const wire = JSON.stringify(payments[0]);
        if (handling) expect(wire).toContain(`"LoyaltyHandling":"${handling}"`);
        else expect(wire).not.toContain('"LoyaltyHandling"');
        expect(
          logged(
            implicit
              ? 'settlement — loyalty implicit'
              : 'settlement — rebates off, redemption off, award off',
          ),
        ).toBe(true);
      },
    );

    it.each([
      ['PHONE', '2015550123', 'PhoneNumber'],
      ['EMAIL', 'aloy@example.com', 'AccountNumber'],
      ['ACCOUNT_NUMBER', 'ACCT-778812', 'AccountNumber'],
    ] as const)(
      'looks the member up by %s with a keyed BalanceInquiry',
      async (kind: MemberLookupKind, value, identificationType) => {
        terminal.reply('BalanceInquiry', BALANCE_FOUND);
        const { controller } = await checkout();
        controller.identifyMemberBy(kind, value);
        await vi.waitFor(() =>
          expect(controller.getState().member).toMatchObject({
            kind: 'found',
            memberId: '98234',
            loyaltyBrand: 'K-Club',
            pointBalance: 1240,
          }),
        );
        expect(lastInquiry()).toEqual({
          EntryMode: ['Keyed'],
          IdentificationType: identificationType,
          LoyaltyID: value,
        });
      },
    );

    it('shows no member for a lookup the terminal does not find', async () => {
      terminal.reply('BalanceInquiry', BALANCE_NOT_FOUND);
      const { controller } = await checkout();
      controller.identifyMemberBy('PHONE', '2015550199');
      await vi.waitFor(() =>
        expect(controller.getState().member).toEqual({ kind: 'absent', reason: 'NOT_FOUND' }),
      );
    });

    it('sends nothing for a blank value', async () => {
      const { controller, logged } = await checkout();
      const before = terminal.requestsOf('BalanceInquiry').length;
      controller.identifyMemberBy('ACCOUNT_NUMBER', ' ');
      controller.identifyMemberBy('RESOLVED_ID', '');
      expect(logged('Enter a account number to identify the member')).toBe(true);
      expect(logged('Enter a resolved member id to identify the member')).toBe(true);
      await new Promise((resolve) => setTimeout(resolve, 200));
      expect(terminal.requestsOf('BalanceInquiry')).toHaveLength(before);
      expect(controller.getState().member).toBeNull();
    });

    it('attaches a resolved id and takes the confirmed balance from the background File inquiry', async () => {
      terminal.reply('BalanceInquiry', BALANCE_FOUND);
      const { controller } = await checkout();
      controller.identifyMemberBy('RESOLVED_ID', '98234');
      await vi.waitFor(() =>
        expect(controller.getState().member).toMatchObject({
          kind: 'found',
          memberId: '98234',
          pointBalance: 1240,
        }),
      );
      expect(lastInquiry()).toEqual({
        EntryMode: ['File'],
        IdentificationType: 'AccountNumber',
        LoyaltyID: '98234',
      });
    });

    it('logs a declined resolved id and leaves it attached', async () => {
      terminal.reply('BalanceInquiry', BALANCE_NOT_FOUND);
      const { controller, logged } = await checkout();
      controller.identifyMemberBy('RESOLVED_ID', '98234');
      await vi.waitFor(() => expect(logged('Background operation failed (')).toBe(true));
      expect(controller.getState().member).toMatchObject({ kind: 'found', memberId: '98234' });
    });
  });
});

/**
 * The host's simulated terminal approves a card payment whose amount in cents ends in 456 for 60%,
 * rounded down. A branch stacked below the change that adds the trigger (#201) has none, so the
 * suite probes the behaviour once and skips its tests when a $4.56 payment is approved in full.
 */
describe.skipIf(!hostAvailable)('partial approvals against the simulated terminal', () => {
  let host: RunningHost;
  let store: IndexedDbSaleStore;
  let hasTrigger = false;
  const controllers: BrowserEmulatorController[] = [];
  const NO_LOYALTY = { implicit: false, rebates: false, redemption: false, award: false };

  beforeAll(async () => {
    if (!('location' in hostProbe)) return;
    buildHost(hostProbe.location);
    host = await startHost(hostProbe.location, { simulated: true });
    store = new IndexedDbSaleStore(indexedDB, `contract-sim-${Date.now()}`);
    // one $4.56 payment: a partial approval reaches the handler, which cancels the sale
    const probePos = await connectTo(host.port);
    const probe = await probePos.startTerminalSession({
      saleId: 'LANE-PROBE',
      poiId: POI,
      currency: 'USD',
      storeLocation: 'STR-0142',
      autoDisplay: false,
    });
    try {
      await probe.basket.addItem(customItem('PROBE-456', '4.56', 'Probe', false));
      await probe.settle(
        { loyalty: { handling: 'FORBIDDEN' } },
        {
          onPartialCardApproval: () => {
            hasTrigger = true;
            return 'ABORT';
          },
        },
      );
    } catch {
      // the cancelled probe sale fails; only whether the handler ran matters
    } finally {
      await probe.forceEnd('trigger probe').catch(() => undefined);
      await probePos.close();
    }
  }, 120_000);

  afterAll(async () => {
    for (const controller of controllers.splice(0)) {
      controller.endSession();
      await vi.waitFor(() => expect(controller.getState().sessionId).toBeNull(), 30_000);
      controller.dispose();
    }
    await host?.stop();
  });

  /** A checkout whose basket is one untaxed custom amount of `priceMinor` cents. */
  async function checkoutWith(priceMinor: number) {
    const controller = new BrowserEmulatorController(
      {
        connect: () => connectTo(host.port),
        sales: store,
        events: new LineLog(),
        detailed: new LineLog(),
        protocol: new LineLog(),
      },
      {
        mode: 'terminal',
        poiId: POI,
        saleId: 'LANE-SIM',
        currency: 'USD',
        storeLocation: 'STR-0142',
      },
    );
    controllers.push(controller);
    controller.connect();
    await vi.waitFor(
      () => expect(controller.getState().connection.phase).toBe('CONNECTED'),
      30_000,
    );
    controller.startSession();
    await vi.waitFor(() => expect(controller.getState().sessionId).not.toBeNull(), 30_000);
    await controller.addCustomItem(priceMinor);
    return controller;
  }

  it('approves $104.56 for $62.73 and charges the $41.83 shortfall on another card', async (context) => {
    if (!hasTrigger) context.skip('the simulated terminal has no partial-approval trigger (#201)');
    const controller = await checkoutWith(10456);
    expect(controller.getState().basketTotal).toBe('104.56');
    controller.settle(NO_LOYALTY, null, true, { plannedAmounts: [], partialApprovals: true });
    await vi.waitFor(() => expect(controller.getState().partialApproval).not.toBeNull(), 30_000);
    expect(controller.getState().partialApproval).toMatchObject({
      approved: '62.73',
      requested: '104.56',
      shortfall: '41.83',
      amountDue: '41.83',
      plannedLeft: false,
    });
    controller.getState().partialApproval!.choose('ANOTHER_CARD');
    await vi.waitFor(() => expect(controller.getState().paymentOutcome).not.toBeNull(), 30_000);
    const outcome = controller.getState().paymentOutcome!;
    expect(outcome.title).toBe('Settlement complete');
    expect(outcome.message).toContain('Card 1 $62.73');
    expect(outcome.message).toContain('· partial');
    expect(outcome.message).toContain('Card 2 $41.83');
    await vi.waitFor(async () => expect((await store.listSales())[0]?.sale.legs).toHaveLength(2));
  });

  it('splits $104.56 as $50.00 plus a remainder that approves partially', async (context) => {
    if (!hasTrigger) context.skip('the simulated terminal has no partial-approval trigger (#201)');
    const controller = await checkoutWith(10456);
    controller.settle(NO_LOYALTY, null, true, {
      plannedAmounts: ['50.00'],
      partialApprovals: true,
    });
    // card 1 is $50.00, card 2 asks for $54.56, which ends in 456
    await vi.waitFor(() => expect(controller.getState().partialApproval).not.toBeNull(), 30_000);
    expect(controller.getState().partialApproval).toMatchObject({
      approved: '32.73',
      requested: '54.56',
      shortfall: '21.83',
      amountDue: '21.83',
      plannedLeft: false,
    });
    controller.getState().partialApproval!.choose('EXTERNAL');
    await vi.waitFor(() => expect(controller.getState().paymentOutcome).not.toBeNull(), 30_000);
    const message = controller.getState().paymentOutcome!.message;
    expect(message).toContain('cash $21.83 (register-managed; refund manually)');
    expect(message).toContain('Card 1 $50.00');
    expect(message).toContain('Card 2 $32.73');
  });
});
