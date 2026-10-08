import {
  RetailMediaSurface,
  useBasket,
  useBiltPos,
  useOperation,
  useSessionContext,
  useSessionEvent,
  useSettlement,
  useShopperSession,
  useTerminalSession,
  type UseSessionResult,
} from '@bilt/pos-react';
import type {
  BasketChange,
  BasketItem,
  CheckoutPhase,
  Offer,
  Operation,
  ShopperSession,
  ShopperSessionOptions,
  VoidResult,
} from '@bilt/pos-sdk';
import { useState, type ReactNode } from 'react';
import { toBasketItem, type CatalogEntry } from './catalog';
import { formatMoney, offerAmount, offerTarget, recomputeTotal } from './money';
import { describeSession, type Settings } from './settings';
import { BasketPanel } from './components/BasketPanel';
import { CartDraft } from './components/CartDraft';
import { CatalogPanel } from './components/CatalogPanel';
import { MemberPanel } from './components/MemberPanel';
import { SettlementPanel } from './components/SettlementPanel';
import { describeError, Toasts, useToasts } from './components/Toasts';

const PHASES: readonly CheckoutPhase[] = ['SCANNING', 'MEMBER_IDENTIFIED', 'TENDERING', 'COMPLETE'];

export interface RegisterProps {
  readonly settings: Settings;
}

function sessionOptions(settings: Settings): ShopperSessionOptions {
  return {
    saleId: settings.saleId,
    currency: settings.currency,
    storeLocation: settings.storeLocation,
    ...(settings.retailMedia
      ? {
          widgets: [{ type: 'retail-media' as const, placements: ['lane-banner'] }],
          rendering: {
            formats: ['IMAGE' as const, 'VIDEO' as const, 'HTML' as const],
            surfaceKind: 'WEB' as const,
          },
        }
      : {}),
  };
}

/**
 * One lane. The session comes from `useTerminalSession` or `useShopperSession` depending on the
 * settings; both hooks are called every render and the disabled one stays idle. Everything
 * below the session is the same in both modes except settlement, which needs the terminal.
 */
export function Register({ settings }: RegisterProps): ReactNode {
  const connection = useBiltPos();
  const toasts = useToasts();
  const options = sessionOptions(settings);
  const terminalLane = useTerminalSession(
    settings.poiId ? { ...options, poiId: settings.poiId } : options,
    { enabled: settings.mode === 'terminal' },
  );
  const localLane = useShopperSession(options, { enabled: settings.mode === 'local' });
  const lane: UseSessionResult<ShopperSession> =
    settings.mode === 'terminal' ? terminalLane : localLane;
  const session = lane.session;
  const terminalSession = settings.mode === 'terminal' ? terminalLane.session : null;

  const basketApi = useBasket(session);
  const { basket } = basketApi;
  const { phase, setPhase } = useSessionContext(session);
  const settlement = useSettlement(terminalSession, { interactive: ['RECOVERY_REQUIRED'] });
  const [lastChange, setLastChange] = useState<BasketChange['source'] | null>(null);
  const [voidOp, setVoidOp] = useState<Operation<VoidResult> | null>(null);
  const voiding = useOperation(voidOp);

  const report = (error: unknown) => toasts.push('warning', describeError(error));
  const run = (action: Promise<unknown>) => void action.catch(report);

  useSessionEvent(session, 'basket.changed', (change) => setLastChange(change.source));
  useSessionEvent(session, 'background.error', (error) =>
    toasts.push('warning', `${error.code}: ${error.message}`),
  );
  useSessionEvent(session, 'session.ended', ({ forced }) =>
    toasts.push('info', forced ? 'Session force-ended' : 'Session ended'),
  );
  useSessionEvent(session, 'widget.offer', ({ offer }) => applyOffer(offer));

  // A validated offer from the retail-media surface, applied as a register discount on one line.
  const applyOffer = (offer: Offer) => {
    const target = basket ? offerTarget(offer, basket) : undefined;
    if (!target) {
      toasts.push('info', `Offer ${offer.id} arrived with nothing to apply it to`);
      return;
    }
    const amount = offerAmount(offer, target);
    const discounts = [
      ...target.discounts,
      { reference: offer.id, label: `Offer ${offer.id}`, amount },
    ];
    basketApi
      .setDiscounts(target.itemId, discounts)
      .then(
        () =>
          toasts.push(
            'success',
            `Offer applied: −${formatMoney(amount, settings.currency)} on ${target.description}`,
          ),
        report,
      );
  };

  const scan = (entry: CatalogEntry) => run(basketApi.addItem(toBasketItem(entry)));
  const sync = (items: readonly BasketItem[]) => run(basketApi.replace(items));
  const removeDiscounts = () =>
    run(
      basketApi.mutate((m) => {
        for (const line of basket?.items ?? []) {
          if (line.discounts.length > 0) m.setDiscounts(line.itemId, []);
        }
      }),
    );

  const pay = () => {
    try {
      run(
        settlement.settle({
          // Rebates lower the price, so the register re-taxes the rebated lines rather than
          // accepting the suggested total, which keeps tax on the undiscounted amount.
          onRebatesRedeemed: (result) => recomputeTotal(result.updatedBasket),
          // Points and a gift card are tender, not price changes: tax is unchanged, the
          // suggested total (previous total minus the amount covered) is right.
          onPointsRedeemed: (result) => result.suggestedTotal,
          onGiftCardPayment: (result) => result.suggestedTotal,
          onAbandoned: (record) =>
            toasts.push('warning', `Settlement ${record.settlementId} abandoned to the register`),
        }),
      );
    } catch (error) {
      report(error);
    }
  };

  const nextShopper = () => {
    // The basket and the member are both the previous shopper's; the session itself carries on.
    // The register resets only once the session accepted the clear: it refuses it while money
    // is still moving, and the result stays on screen then.
    run(
      basketApi
        .clear()
        .then(() => session?.member.clear())
        .then(() => {
          settlement.reset();
          setVoidOp(null);
        }),
    );
  };

  if (connection.status === 'connecting') {
    return <p className="notice">Connecting to the Terminal Bridge…</p>;
  }
  if (connection.status === 'error' || !connection.pos) {
    return (
      <div className="notice error">
        <p>Could not connect: {connection.error ? describeError(connection.error) : 'closed'}</p>
        <button type="button" onClick={connection.reconnect}>
          Try again
        </button>
      </div>
    );
  }
  if (lane.status === 'idle' || lane.status === 'starting') {
    return <p className="notice">Starting a {describeSession(settings)}…</p>;
  }
  // A refused `end()` also lands in `error` but leaves the session open (a settlement may still be
  // moving money), so only a lane without a session is a start failure.
  if (lane.status === 'error' && !session) {
    return (
      <div className="notice error">
        <p>
          The session could not be started: {lane.error ? describeError(lane.error) : 'unknown'}
        </p>
        <button type="button" onClick={lane.restart}>
          Try again
        </button>
      </div>
    );
  }
  if (!session || lane.status === 'ended') {
    return (
      <div className="notice">
        <p>The session has ended.</p>
        <button type="button" onClick={lane.restart}>
          Start the next session
        </button>
      </div>
    );
  }

  const locked =
    settlement.status === 'running' ||
    settlement.status === 'awaitingReply' ||
    settlement.status === 'succeeded';

  return (
    <>
      <div className="lane-bar">
        <span>
          {terminalSession ? `Terminal session · POIID ${terminalSession.poiId}` : 'Local session'}{' '}
          · lane {session.saleId} · session {session.id}
        </span>
        <span className="lane-phase">
          Phase: {phase}
          {session.kind === 'local'
            ? PHASES.map((candidate) => (
                <button
                  key={candidate}
                  type="button"
                  className="link"
                  disabled={candidate === phase}
                  onClick={() => run(setPhase(candidate))}
                >
                  {candidate}
                </button>
              ))
            : null}
        </span>
        <span className="lane-actions">
          <button
            type="button"
            className="secondary"
            onClick={() => lane.end().catch(report)}
            disabled={lane.status !== 'open' && lane.status !== 'error'}
          >
            End session
          </button>
        </span>
      </div>

      {settings.retailMedia ? (
        <RetailMediaSurface
          session={session}
          placement="lane-banner"
          dismissible
          placeholder={<span className="muted">lane-banner: nothing to show</span>}
          onError={report}
        />
      ) : null}

      <div className="columns">
        <div className="column">
          <CatalogPanel currency={settings.currency} disabled={locked} onScan={scan} />
          <CartDraft basket={basket} disabled={locked} onSync={sync} />
        </div>
        <div className="column">
          <BasketPanel
            basket={basket}
            currency={settings.currency}
            lastChange={lastChange}
            locked={locked}
            onQuantity={(line, quantity) =>
              run(basketApi.updateItemQuantity(line.itemId, quantity))
            }
            onRemove={(line) => run(basketApi.removeItem(line.itemId))}
            onRemoveDiscounts={removeDiscounts}
            onClear={() => run(basketApi.clear())}
          />
        </div>
        <div className="column">
          <MemberPanel session={session} terminalSession={terminalSession} onError={report} />
          {terminalSession ? (
            <SettlementPanel
              settlement={settlement}
              currency={settings.currency}
              total={basket?.grandTotal ?? null}
              canPay={(basket?.items.length ?? 0) > 0}
              onPay={pay}
              onVoid={() => setVoidOp(terminalSession.voidTransaction())}
              voiding={voiding}
              onNextShopper={nextShopper}
            />
          ) : (
            <section className="panel">
              <h2>Settle</h2>
              <p className="muted">
                Settlement, prompts and the customer display need a terminal session. Switch the
                mode in the settings to pay.
              </p>
            </section>
          )}
        </div>
      </div>
      <Toasts toasts={toasts} />
    </>
  );
}
