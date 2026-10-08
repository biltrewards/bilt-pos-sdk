// Samples for "Start and end a session" and "Terminal operations outside a session".
import { SessionError } from '@bilt/pos-sdk';
import type { BiltPos, ShopperSession, TerminalShopperSession } from '@bilt/pos-sdk';

declare function recordIncident(message: string): void;

export async function startLane(pos: BiltPos): Promise<TerminalShopperSession> {
  const session = await pos.startTerminalSession({
    saleId: 'LANE-3',
    poiId: 'VictaLane-275839164', // optional: passed through as the Nexo POIID
    currency: 'USD',
    storeLocation: 'STR-0142',
    widgets: [{ type: 'retail-media', placements: ['lane-banner'] }],
  });
  return session; // the terminal has acknowledged; a refused start rejects instead
}

export function startLocalLane(pos: BiltPos): Promise<ShopperSession> {
  return pos.startShopperSession({ saleId: 'LANE-3', currency: 'USD', storeLocation: 'STR-0142' });
}

export async function endLane(session: TerminalShopperSession): Promise<void> {
  try {
    await session.end();
  } catch (error) {
    if (error instanceof SessionError && error.code === 'INVALID_STATE') {
      // Money is unresolved: finish the unwind first (retry settle(), voidTransaction()), or,
      // once the incident is recorded for reconciliation, abandon it explicitly.
      recordIncident(error.message);
      await session.forceEnd('operator escalated incomplete recovery');
      return;
    }
    throw error;
  }
}

export async function oneVisit(pos: BiltPos): Promise<void> {
  await using session = await pos.startTerminalSession({ saleId: 'LANE-3', currency: 'USD' });
  await session.basket.addItem({
    sku: 'GRC-OJ-1L',
    description: 'Orange Juice 1L',
    unitPrice: '4.49',
  });
  await session.settle();
} // end() runs here, best-effort: a refusal is reported, not thrown

export async function beforeOpening(pos: BiltPos): Promise<void> {
  const terminal = pos.terminal();
  const diagnosis = await terminal.diagnose();
  console.log(diagnosis.hostStatuses);
  const totals = await terminal.totals(); // running totals; reconcile() closes the period
  console.log(totals.transactionTotals);
}

export async function pingMidCheckout(session: TerminalShopperSession): Promise<void> {
  await session.terminal().diagnose(); // its own exchange, never queued behind a payment
}
