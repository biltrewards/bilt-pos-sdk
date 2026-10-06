// Samples for "Reliability".
import type { ShopperSession } from '@bilt/pos-sdk';

declare const log: { warn(message: string): void; info(message: string): void };

export function watchHealth(session: ShopperSession): void {
  // A rejected widget tap, a failed automatic display push, a step handler that threw.
  session.on('background.error', (error) => log.warn(`${error.code}: ${error.message}`));
  // The host ended the session, from this page or from another client of the same host.
  session.on('session.ended', ({ forced, reason }) =>
    log.info(forced ? `force-ended: ${reason ?? 'no reason'}` : 'ended'),
  );
}
