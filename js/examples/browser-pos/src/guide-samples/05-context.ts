// Samples for "Session context".
import type { ShopperSession } from '@bilt/pos-sdk';

export async function markTendering(session: ShopperSession): Promise<void> {
  await session.context.setPhase('TENDERING');
  await session.context.setAttribute('lane-type', 'pharmacy');
  await session.context.removeAttribute('promo-code');
  const snapshot = session.context.snapshot(); // phase, attributes, saleId, currency, storeLocation
  console.log(snapshot.phase, session.context.attributes());
}
