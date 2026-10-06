import { RetailMediaSurface, useSessionEvent } from '@bilt/pos-react';
import type { Offer, Rendering, WidgetHandle } from '@bilt/pos-sdk';
import { useEffect, useState, type ReactNode } from 'react';
import { useLane } from '../lane/LaneProvider';
import { describeError } from '../log';
import { formatMoney } from '../money';

/**
 * The shopper-facing side of the lane: the retail-media `lane-banner` placement drawn by
 * `RetailMediaSurface`, the offers the host validated (applied as register discounts on request),
 * the widget's pause/resume for when the display is needed for PIN entry or a signature, and a
 * mirror of the basket as a customer display would show it.
 */
export function DisplayPane(): ReactNode {
  const { session, settings, basket, member, applyOffer, report, context } = useLane();
  const [rendering, setRendering] = useState<Rendering | null>(null);
  const [offers, setOffers] = useState<readonly Offer[]>([]);
  const [paused, setPaused] = useState(false);
  const widget: WidgetHandle | null =
    session && settings.retailMedia
      ? (() => {
          try {
            return session.widget('retail-media');
          } catch {
            return null;
          }
        })()
      : null;
  useEffect(() => {
    setPaused(widget?.isPaused() ?? false);
    setRendering(null);
    setOffers([]);
  }, [widget]);
  useSessionEvent(session, 'widget.offer', ({ offer }) =>
    setOffers((previous) => [offer, ...previous].slice(0, 10)),
  );
  const fmt = (value: string | undefined) => formatMoney(value, settings.currency);
  const current = basket.basket;

  if (!session) return null;

  const toggle = () => {
    if (!widget) return;
    const action = paused ? widget.resume() : widget.pause();
    action.then(() => setPaused(!paused), report);
  };

  return (
    <div className="columns two">
      <div className="column">
        <section className="panel">
          <div className="panel-header">
            <h2>lane-banner</h2>
            {widget ? (
              <span className={widget.inert ? 'badge warn' : 'badge'} data-testid="widget-status">
                {widget.inert
                  ? `inert: ${widget.error ? describeError(widget.error) : 'no ad decision service'}`
                  : paused
                    ? 'paused'
                    : 'live'}
              </span>
            ) : (
              <span className="badge">retail media off</span>
            )}
          </div>
          {widget ? (
            <>
              <RetailMediaSurface
                session={session}
                placement="lane-banner"
                dismissible
                placeholder={
                  <span className="muted" data-testid="no-creatives">
                    No creatives served yet: the host&apos;s retail media engine is an API skeleton
                    today, so this placement stays empty until a decision service is wired in.
                  </span>
                }
                onRendering={setRendering}
                onError={report}
              />
              <div className="actions">
                <button type="button" className="secondary" onClick={toggle}>
                  {paused ? 'Resume widget' : 'Pause widget (PIN entry, signature)'}
                </button>
              </div>
              {rendering ? (
                <p className="small muted">
                  Showing creative <code>{rendering.creativeId}</code> ({rendering.media.type}), TTL{' '}
                  {rendering.ttl}
                  {rendering.cta ? ` · CTA ${rendering.cta.action}` : ''}
                </p>
              ) : null}
            </>
          ) : (
            <p className="muted">Turn retail media on in the Settings tab to carry the widget.</p>
          )}
        </section>
        <section className="panel">
          <h2>Offers</h2>
          <p className="muted small">
            A tap on an <code>APPLY_OFFER</code> call to action comes back as{' '}
            <code>widget.offer</code> once the host validated it; the register decides how to honour
            it.
          </p>
          {offers.length === 0 ? (
            <p className="muted small">No offers yet.</p>
          ) : (
            <ul className="ledger">
              {offers.map((offer) => (
                <li key={offer.id}>
                  <code>{offer.id}</code> · {offer.scope.toLowerCase()}
                  {offer.sku ? ` ${offer.sku}` : ''} ·{' '}
                  {offer.amount
                    ? `${fmt(offer.amount)} off`
                    : offer.percentage
                      ? `${offer.percentage}% off`
                      : ''}
                  {offer.expiry ? ` · until ${new Date(offer.expiry).toLocaleTimeString()}` : ''}{' '}
                  <button type="button" className="link" onClick={() => applyOffer(offer)}>
                    apply as discount
                  </button>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
      <div className="column">
        <section className="display-mirror" aria-label="Customer display">
          <h3>
            {settings.storeLocation} · lane {session.saleId} · {context.phase ?? ''}
          </h3>
          {member.member?.resolved ? (
            <p>
              Welcome back, member {member.member.memberId} · {member.member.pointBalance} points
            </p>
          ) : null}
          {current && current.items.length > 0 ? (
            <>
              {current.items.map((line) => (
                <div key={line.itemId} className="total">
                  <span>
                    {line.quantity} × {line.description}
                  </span>
                  <span>{fmt(line.adjustedTotal)}</span>
                </div>
              ))}
              <div className="total">
                <span>Total</span>
                <span>{fmt(current.grandTotal)}</span>
              </div>
            </>
          ) : (
            <p className="muted">Welcome</p>
          )}
        </section>
      </div>
    </div>
  );
}
