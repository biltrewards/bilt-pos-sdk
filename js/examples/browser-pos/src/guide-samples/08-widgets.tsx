// Samples for "Widgets and RetailMediaSurface".
import { RetailMediaSurface, useSessionEvent } from '@bilt/pos-react';
import type { Cta, Offer, Rendering, SessionEventPayload, ShopperSession } from '@bilt/pos-sdk';
import type { ReactNode } from 'react';

declare function applyOffer(offer: Offer): void;
declare function draw(
  placement: string,
  rendering: Rendering,
  handlers: { onTap(cta: Cta): void; onSeen(): void; onClose(): void },
): void;
declare function clear(placement: string): void;
declare function analytics(payload: SessionEventPayload<'widget.interaction'>): void;

export function LaneBanner({ session }: { session: ShopperSession | null }): ReactNode {
  useSessionEvent(session, 'widget.offer', ({ offer }) => applyOffer(offer));
  return (
    <RetailMediaSurface
      session={session}
      placement="lane-banner"
      viewabilityMs={1000}
      dismissible
      onRendering={(rendering) => console.log(rendering ? rendering.creativeId : 'cleared')}
    />
  );
}

export function drawYourself(session: ShopperSession): void {
  const widget = session.widget('retail-media');
  session.on('widget.rendering', ({ placement, rendering }) => {
    draw(placement, rendering, {
      onTap: (cta) => void widget.perform(rendering, cta), // the token goes back exactly as received
      onSeen: () => void widget.viewed(rendering), // once, after about a second on screen
      onClose: () => void widget.dismissed(rendering),
    });
  });
  session.on('widget.clear', ({ placement }) => clear(placement));
  session.on('widget.offer', ({ offer }) => applyOffer(offer)); // validated by the host: act on it
  session.on('widget.interaction', (payload) => analytics(payload)); // measured, informational
}

export async function duringPinEntry(session: ShopperSession): Promise<void> {
  const widget = session.widget('retail-media');
  if (widget.inert) return; // could not attach (no ad service, no store location): the lane runs without it
  await widget.pause(); // clears its placements and keeps them clear
  // ...PIN entry on the shared display...
  await widget.resume();
}
