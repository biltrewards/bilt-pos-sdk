// The SDK core over the in-memory MockEngine: connect, sessions, mirrors, events, end.
import type { BasketChange, SessionEvent } from '@bilt/pos-protocol';
import { describe, expect, it, vi } from 'vitest';
import { BiltPos, EngineOutdatedError, SessionError } from '../src/index';
import type { Engine } from '../src/internal';
import { MockEngine } from './mock-engine';

async function connect(engine: Engine = new MockEngine()) {
  return BiltPos.connect(() => engine);
}

describe('BiltPos.connect', () => {
  it('runs the factory with the SDK context and exposes the engine capabilities', async () => {
    const engine = new MockEngine();
    const factory = vi.fn(() => engine);
    const pos = await BiltPos.connect(factory);
    expect(factory).toHaveBeenCalledWith({ sdkVersion: expect.any(String), protocolVersion: '1' });
    expect(pos.capabilities).toBe(engine.capabilities);
    expect((await pos.health()).protocolVersions).toEqual(['1']);
    await pos.close();
  });

  it('rejects with EngineOutdatedError when the host lacks protocol version 1', async () => {
    const engine = new MockEngine();
    engine.health = async () => ({
      host: 'bridge',
      hostVersion: '0',
      sdkVersion: '0',
      protocolVersions: ['0'],
    });
    const close = vi.spyOn(engine, 'close');
    await expect(connect(engine)).rejects.toBeInstanceOf(EngineOutdatedError);
    expect(close).toHaveBeenCalled();
  });

  it('closes the engine when health itself fails', async () => {
    const engine = new MockEngine();
    engine.health = async () => {
      throw new Error('boom');
    };
    const close = vi.spyOn(engine, 'close');
    await expect(connect(engine)).rejects.toThrow('boom');
    expect(close).toHaveBeenCalled();
  });
});

describe('a local session over the engine', () => {
  it('mirrors basket, member and context from the event stream and ends', async () => {
    const pos = await connect();
    const session = await pos.startShopperSession({
      saleId: 'LANE-3',
      currency: 'USD',
      storeLocation: 'STR-0142',
      attributes: { register: '3' },
    });
    expect(session.kind).toBe('local');
    expect(session.state).toBe('open');
    expect(session.storeLocation).toBe('STR-0142');
    expect(session.context.attributes()).toEqual({ register: '3' });

    const types: SessionEvent['type'][] = [];
    const changes: BasketChange[] = [];
    session.on('basket.changed', (change) => {
      changes.push(change);
      expect(session.basket.current).toBe(change.current);
    });
    for (const type of ['member.changed', 'context.changed', 'session.ended'] as const) {
      session.on(type, () => types.push(type));
    }

    const basket = await session.basket.addItem({ sku: 'A', description: 'A', unitPrice: '2.00' });
    expect(basket.items).toHaveLength(1);
    expect(session.basket.current).toBe(basket);
    await vi.waitFor(() => expect(changes).toHaveLength(1));

    const member = await session.member.set({ id: 'mbr_1' });
    expect(member.resolved).toBe(true);
    expect(session.member.current).toBe(member);
    await session.context.setPhase('TENDERING');
    expect(session.context.phase()).toBe('TENDERING');
    await session.context.setAttribute('till', '7');
    await session.context.removeAttribute('register');
    expect(session.context.attributes()).toEqual({ till: '7' });

    await session.end();
    expect(session.state).toBe('ended');
    await vi.waitFor(() => expect(types).toContain('session.ended'));
    expect(types.filter((t) => t === 'member.changed')).toHaveLength(1);
    expect(types.filter((t) => t === 'context.changed')).toHaveLength(3);

    await expect(
      session.basket.addItem({ sku: 'B', description: 'B', unitPrice: '1.00' }),
    ).resolves.toBeDefined(); // the mock engine does not freeze baskets; the host does
    await session.end(); // idempotent once ended
    await pos.close();
  });

  it('ends on the way out of an await using block', async () => {
    const pos = await connect();
    let captured;
    {
      await using session = await pos.startShopperSession({ saleId: 'L', currency: 'USD' });
      captured = session;
    }
    expect(captured?.state).toBe('ended');
  });

  it('refuses a widget that was not configured', async () => {
    const pos = await connect();
    const session = await pos.startShopperSession({ saleId: 'L', currency: 'USD' });
    expect(() => session.widget('retail-media')).toThrow(/no retail-media widget/);
    expect(session.widgets()).toEqual([]);
  });

  it('reports a throwing event handler and keeps the others running', async () => {
    const errors: unknown[] = [];
    const spy = vi.spyOn(console, 'error').mockImplementation((...args) => errors.push(args));
    try {
      const pos = await connect();
      const session = await pos.startShopperSession({ saleId: 'L', currency: 'USD' });
      let second = 0;
      session.on('basket.changed', () => {
        throw new Error('handler failed');
      });
      session.on('basket.changed', () => second++);
      await session.basket.addItem({ sku: 'A', description: 'A', unitPrice: '1.00' });
      await vi.waitFor(() => expect(second).toBe(1));
      expect(errors).toHaveLength(1);
    } finally {
      spy.mockRestore();
    }
  });

  it('turns an unknown session into a SessionError on write', async () => {
    const engine = new MockEngine();
    const pos = await connect(engine);
    const session = await pos.startShopperSession({ saleId: 'L', currency: 'USD' });
    engine.basket = async () => {
      throw new SessionError({ code: 'INVALID_STATE', message: 'frozen' });
    };
    await expect(
      session.basket.addItem({ sku: 'A', description: 'A', unitPrice: '1.00' }),
    ).rejects.toMatchObject({ code: 'INVALID_STATE' });
  });
});
