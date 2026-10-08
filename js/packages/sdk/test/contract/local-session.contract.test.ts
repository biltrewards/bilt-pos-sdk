// Local sessions against the real Session Host: create, basket, member, context, events, end.
import type { SessionEventType } from '@bilt/pos-protocol';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import type { BiltPos } from '../../src/index';
import { BridgeMissingError, detectBridge, localBridge } from '../../src/bridge/index';
import {
  buildHost,
  hostAvailable,
  hostProbe,
  skipReason,
  startHost,
  type RunningHost,
} from './host';
import { bridgeOptions, connectTo } from './setup';

if (!hostAvailable) console.warn(`[contract] skipping: ${skipReason()}`);

describe.skipIf(!hostAvailable)('local sessions over the Terminal Bridge engine', () => {
  let host: RunningHost;
  let pos: BiltPos;

  beforeAll(async () => {
    if (!('location' in hostProbe)) return;
    buildHost(hostProbe.location);
    host = await startHost(hostProbe.location);
    pos = await connectTo(host.port);
  });

  afterAll(async () => {
    await pos?.close();
    await host?.stop();
  });

  it('detects the bridge and reports its capabilities and health', async () => {
    expect(pos.capabilities).toMatchObject({
      name: 'bridge',
      survivesPageReload: true,
      worksOffline: true,
      supportsWidgets: true,
    });
    const health = await pos.health();
    expect(health.host).toBe('bridge');
    expect(health.protocolVersions).toContain('1');
    expect(health.terminal).toBeUndefined();
    expect(await pos.terminalInfo()).toBeNull();
  });

  it('reports a missing bridge on a silent port', async () => {
    const detection = await detectBridge(bridgeOptions(1));
    expect(detection.status).toBe('missing');
    await expect(
      localBridge(bridgeOptions(1))({ sdkVersion: 'test', protocolVersion: '1' }),
    ).rejects.toBeInstanceOf(BridgeMissingError);
  });

  it('runs a visit: basket, member, context, ordered events, end', async () => {
    const session = await pos.startShopperSession({
      saleId: 'LANE-1',
      currency: 'USD',
      storeLocation: 'STR-1',
      attributes: { register: '3' },
    });
    expect(session.kind).toBe('local');
    expect(session.state).toBe('open');
    expect(session.storeLocation).toBe('STR-1');
    expect(session.context.attributes()).toEqual({ register: '3' });
    expect(session.basket.current.items).toEqual([]);
    expect(session.member.current).toBeNull();

    const events: SessionEventType[] = [];
    const types: SessionEventType[] = [
      'basket.changed',
      'member.changed',
      'context.changed',
      'operation.completed',
      'session.ended',
    ];
    for (const type of types) session.on(type, () => events.push(type));

    let basket = await session.basket.addItem({
      sku: 'SKU-1',
      description: 'Toothpaste',
      quantity: 2,
      unitPrice: '4.99',
    });
    expect(basket.grandTotal).toBe('9.98');
    expect(session.basket.current).toBe(basket);
    const itemId = basket.items[0]!.itemId;

    basket = await session.basket.updateItemQuantity(itemId, 3);
    expect(basket.items[0]!.quantity).toBe(3);
    basket = await session.basket.setTaxRateBySku('SKU-1', '0.10');
    expect(basket.items[0]!.taxAmount).toBe('1.50');
    basket = await session.basket.mutate((m) =>
      m.addItem({ sku: 'SKU-2', description: 'Floss', unitPrice: '2.50' }).removeItem(itemId),
    );
    expect(basket.items.map((l) => l.sku)).toEqual(['SKU-2']);
    basket = await session.basket.replace([
      { sku: 'SKU-9', description: 'Replaced', unitPrice: '1.00' },
    ]);
    expect(basket.items.map((l) => l.sku)).toEqual(['SKU-9']);
    expect((await session.basket.refresh()).items).toEqual(basket.items);

    const member = await session.member.set({ id: '98234' });
    expect(member).toMatchObject({ resolved: true, memberId: '98234' });
    expect(session.member.current).toEqual(member);
    const pending = await session.member.set({
      resolver: { type: 'PHONE', value: '+12015550123' },
    });
    expect(pending.resolved).toBe(false);
    expect(pending.resolver?.value).toBe('********0123');
    await session.member.clear();
    expect(session.member.current).toBeNull();
    expect(await session.member.get()).toBeNull();

    await session.context.setPhase('TENDERING');
    await session.context.setAttribute('till', '7');
    await session.context.removeAttribute('register');
    expect(session.context.phase()).toBe('TENDERING');
    expect(session.context.attributes()).toEqual({ till: '7' });
    expect(session.context.snapshot()).toMatchObject({ saleId: 'LANE-1', currency: 'USD' });

    await session.end();
    expect(session.state).toBe('ended');
    await expect(session.end()).resolves.toBeUndefined();

    // Five basket writes, three member changes, three context patches, the end operation, ended.
    expect(events).toEqual([
      ...Array<SessionEventType>(5).fill('basket.changed'),
      ...Array<SessionEventType>(3).fill('member.changed'),
      ...Array<SessionEventType>(3).fill('context.changed'),
      'operation.completed',
      'session.ended',
    ]);
  });

  it('turns host refusals into SessionErrors and ends on dispose', async () => {
    let id = '';
    {
      await using session = await pos.startShopperSession({ saleId: 'LANE-2', currency: 'USD' });
      id = session.id;
      await expect(
        session.basket.addItem({ sku: '', description: 'x', unitPrice: '1' }),
      ).rejects.toMatchObject({ name: 'SessionError', code: 'VALIDATION' });
      await expect(session.basket.removeItem('nope')).rejects.toMatchObject({ code: 'NOT_FOUND' });
    }
    const after = await pos.startShopperSession({ saleId: 'LANE-2', currency: 'USD' });
    expect(after.id).not.toBe(id);
    await after.end();
  });
});
