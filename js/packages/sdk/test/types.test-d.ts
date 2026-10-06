// Compile-time checks of the public contract: the design sketch typed verbatim, and the shapes a
// register relies on. `BiltPos.connect` is declared here because this release ships the
// interface and the core that provides the value is the follow-up.
import type { Basket, Member, Offer, SettlementResult, VoidResult } from '@bilt/pos-protocol';
import { describe, expectTypeOf, it } from 'vitest';
import type {
  BiltPosStatic,
  EngineFactory,
  Operation,
  SessionError,
  SettleOptions,
  ShopperSession,
  StepInfo,
  TerminalShopperSession,
} from '../src/index';
import type { Engine } from '../src/internal';
import type { MockEngine } from './mock-engine';
import type { MockBiltPos } from './mock-pos';

declare const BiltPos: BiltPosStatic;
declare function localBridge(): EngineFactory;
declare function recomputeTax(total: string): Promise<string>;

describe('the design sketch', () => {
  it('type-checks as written on the design page', async () => {
    const pos = await BiltPos.connect(localBridge());

    const session = await pos.startTerminalSession({
      saleId: 'LANE-3',
      poiId: 'VictaLane-275839164',
      currency: 'USD',
      storeLocation: 'STR-0142',
      widgets: [{ type: 'retail-media', placements: ['lane-banner'] }],
    });

    await session.basket.addItem({
      sku: 'SKU-4471',
      description: 'Toothpaste',
      quantity: 1,
      unitPrice: '4.99',
    });
    await session.member.set({ resolver: { type: 'PHONE', value: '+12015550123' } });

    const result = await session.settle({
      onRebatesRedeemed: async (step) => recomputeTax(step.suggestedTotal),
      // The design page writes `async (failure) => "RETRY"`; TypeScript widens that literal to
      // `string` inside an async arrow, so a recovery is answered synchronously or `as const`.
      onError: () => 'RETRY',
    });
    expectTypeOf(result).toEqualTypeOf<SettlementResult>();

    session.on('widget.offer', (payload) => {
      expectTypeOf(payload.offer).toEqualTypeOf<Offer>();
    });
    await session.end();
  });
});

describe('the public surface', () => {
  it('returns operations that are promises with a handle', () => {
    expectTypeOf<TerminalShopperSession['settle']>().returns.toEqualTypeOf<
      Operation<SettlementResult>
    >();
    expectTypeOf<Operation<SettlementResult>>().toMatchTypeOf<Promise<SettlementResult>>();
    expectTypeOf<Operation<VoidResult>['abort']>().returns.resolves.toBeVoid();
    expectTypeOf<TerminalShopperSession['requestDecimalString']>().returns.toEqualTypeOf<
      Operation<string>
    >();
    expectTypeOf<TerminalShopperSession['end']>().returns.resolves.toBeVoid();
  });

  it('types the settlement handlers and their step info', () => {
    expectTypeOf<NonNullable<SettleOptions['onRebatesRedeemed']>>()
      .parameter(1)
      .toEqualTypeOf<StepInfo>();
    expectTypeOf<NonNullable<SettleOptions['onError']>>().returns.resolves.toMatchTypeOf<
      { action: string } | 'RETRY' | 'SKIP' | 'EXTERNAL' | 'ABORT' | 'ABANDON'
    >();
    expectTypeOf<SettleOptions['settlementType']>().toEqualTypeOf<
      'REFUND_THEN_CHARGE' | 'NET' | undefined
    >();
  });

  it('keeps the member and basket mirrors synchronous and the writes async', () => {
    expectTypeOf<ShopperSession['basket']['current']>().toEqualTypeOf<Basket>();
    expectTypeOf<ShopperSession['member']['current']>().toEqualTypeOf<Member | null>();
    expectTypeOf<ShopperSession['basket']['addItem']>().returns.resolves.toEqualTypeOf<Basket>();
    expectTypeOf<ShopperSession['context']['phase']>().returns.toEqualTypeOf<
      'SCANNING' | 'MEMBER_IDENTIFIED' | 'TENDERING' | 'COMPLETE'
    >();
  });

  it('types events by name', () => {
    const session = {} as ShopperSession;
    session.on('background.error', (error) => {
      expectTypeOf(error.code).toMatchTypeOf<string>();
    });
    session.on('basket.changed', (change) => {
      expectTypeOf(change.current).toEqualTypeOf<Basket>();
    });
    // @ts-expect-error unknown event names are rejected
    session.on('basket.exploded', () => undefined);
  });

  it('rejects wrong member input shapes', () => {
    const session = {} as ShopperSession;
    // @ts-expect-error a resolver needs a value
    void session.member.set({ resolver: { type: 'PHONE' } });
  });

  it('is implementable: the doubles satisfy the interfaces', () => {
    expectTypeOf<MockEngine>().toMatchTypeOf<Engine>();
    expectTypeOf<InstanceType<typeof MockBiltPos>>().toMatchTypeOf<
      Awaited<ReturnType<BiltPosStatic['connect']>>
    >();
    expectTypeOf<SessionError['code']>().toMatchTypeOf<string>();
  });
});
