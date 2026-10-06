// The design page's integration sketch, run against the in-memory doubles. What this proves is
// that the public interfaces compose the way the sketch assumes; the SDK core is the follow-up.
import type { BasketChange, Offer, SessionEvent } from '@bilt/pos-protocol';
import { describe, expect, it } from 'vitest';
import { SessionError, type BiltPos, type TerminalShopperSession } from '../src/index';
import { MockEngine } from './mock-engine';
import { MockBiltPos, type MockTerminalSession } from './mock-pos';
import * as fx from './fixtures';

async function startLane(pos: MockBiltPos): Promise<MockTerminalSession> {
  return pos.startTerminalSession({
    saleId: 'LANE-3',
    poiId: 'VictaLane-275839164',
    currency: 'USD',
    storeLocation: 'STR-0142',
    widgets: [{ type: 'retail-media', placements: ['lane-banner'] }],
  });
}

describe('the design sketch against the public surface', () => {
  it('rings an item, signs the member in, settles with handlers, applies an offer and ends', async () => {
    const pos: BiltPos = await MockBiltPos.connect();
    const session: TerminalShopperSession = await startLane(pos as MockBiltPos);

    const changes: BasketChange[] = [];
    session.on('basket.changed', (change) => changes.push(change));

    const basket = await session.basket.addItem({
      sku: 'SKU-4471',
      description: 'Toothpaste',
      quantity: 1,
      unitPrice: '4.99',
      taxRate: '0.08875',
    });
    expect(basket.grandTotal).toBe('5.43');
    expect(session.basket.current).toBe(basket);
    expect(changes).toHaveLength(1);
    expect(changes[0]?.added.map((line) => line.sku)).toEqual(['SKU-4471']);

    const member = await session.member.set({ resolver: { type: 'PHONE', value: '+12015550123' } });
    expect(member.resolved).toBe(false);
    expect(session.member.current?.resolver?.type).toBe('PHONE');

    const totals: string[] = [];
    const settlement = session.settle({
      onRebatesRedeemed: (step) => {
        totals.push(step.suggestedTotal);
        return step.suggestedTotal;
      },
      onError: () => 'RETRY',
    });
    expect(settlement.type).toBe('settle');
    expect(['queued', 'running']).toContain(settlement.status);
    const result = await settlement;
    expect(settlement.status).toBe('succeeded');
    expect(totals).toEqual(['3.43']);
    expect(result.cardAmountCharged).toBe('3.43');
    expect(session.context.phase()).toBe('COMPLETE');

    const offers: Offer[] = [];
    session.on('widget.offer', ({ offer }) => offers.push(offer));
    const widget = session.widget('retail-media');
    const rendering = fx.rendering('lane-banner');
    await widget.perform(rendering, rendering.cta!);
    expect(offers.map((o) => o.creativeId)).toEqual(['crt_1']);

    const ended: SessionEvent['type'][] = [];
    session.once('session.ended', () => ended.push('session.ended'));
    await session.end();
    expect(session.state).toBe('ended');
    expect(ended).toEqual(['session.ended']);
  });

  it('consults onError and rejects with a SessionError when recovery does not retry', async () => {
    const pos = await MockBiltPos.connect();
    const session = await startLane(pos);
    await session.basket.addItem({ sku: 'A', description: 'A', unitPrice: '1.00' });
    session.failNextChargeWith = new SessionError({ code: 'DECLINED', message: 'declined' });

    const failure = await session
      .settle({
        onError: (f) => ({ action: f.outcomeCertainty === 'DEFINITIVE' ? 'ABORT' : 'RETRY' }),
      })
      .catch((e: unknown) => e);

    expect(failure).toBeInstanceOf(SessionError);
    expect((failure as SessionError).code).toBe('DECLINED');
    expect(session.recoveries).toEqual(['ABORT']);
    expect(session.context.phase()).toBe('SCANNING');
  });

  it('ends the session on the way out of an `await using` block', async () => {
    const pos = await MockBiltPos.connect();
    let captured: TerminalShopperSession | undefined;
    {
      await using session = await startLane(pos);
      captured = session;
      expect(session.state).toBe('open');
    }
    expect(captured?.state).toBe('ended');
  });

  it('pauses a widget by clearing its placements', async () => {
    const pos = await MockBiltPos.connect();
    const session = await startLane(pos);
    const cleared: string[] = [];
    session.on('widget.clear', ({ placement }) => cleared.push(placement));
    await session.widget('retail-media').pause();
    expect(session.widget('retail-media').isPaused()).toBe(true);
    expect(cleared).toEqual(['lane-banner']);
  });
});

describe('the internal Engine seam', () => {
  it('streams events in order with an exclusive since cursor', async () => {
    const engine = new MockEngine();
    const session = await engine.createSession({
      kind: 'terminal',
      saleId: 'LANE-3',
      poiId: 'P',
      currency: 'USD',
    });
    await engine.basket(session.id, {
      kind: 'add',
      item: { sku: 'A', description: 'A', unitPrice: '2.00' },
    });
    await engine.member(session.id, { kind: 'set', member: { id: 'mbr_1' } });
    await engine.request(session.id, { type: 'end' });

    const all: number[] = [];
    for await (const event of engine.events(session.id)) all.push(event.seq);
    expect(all).toEqual([1, 2, 3, 4, 5]);

    const types: string[] = [];
    for await (const event of engine.events(session.id, 2)) types.push(event.type);
    expect(types).toEqual(['member.changed', 'operation.completed', 'session.ended']);
  });

  it('rejects unknown sessions with a SessionError', async () => {
    const engine = new MockEngine();
    await expect(engine.session('nope')).rejects.toMatchObject({ code: 'NOT_FOUND' });
  });
});
