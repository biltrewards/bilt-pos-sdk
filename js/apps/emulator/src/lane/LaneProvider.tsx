import {
  useBasket,
  useBiltPos,
  useMember,
  useSessionContext,
  useSessionEvent,
  useSettlement,
  useShopperSession,
  useTerminalSession,
  type BiltPosConnection,
  type UseBasketResult,
  type UseMemberResult,
  type UseSessionContextResult,
  type UseSessionResult,
  type UseSettlementResult,
} from '@bilt/pos-react';
import type {
  AbandonedSettlementRecord,
  BasketChange,
  Money,
  SettlementContext,
  SettlementResult,
  ShopperSession,
  ShopperSessionOptions,
  StoredValueCard,
  TerminalShopperSession,
} from '@bilt/pos-sdk';
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { Toasts, useToasts, type ToastApi } from '../components/Toasts';
import {
  giftCardLine,
  storedValueCard,
  toFulfillment,
  type PendingGiftCard,
  type StoredValueLoadType,
} from '../gift-cards';
import { describeError, track, useSessionLogging, type LogStore } from '../log';
import {
  formatMoney,
  recomputeTotal,
} from '../money';
import { describeSession, type Settings } from '../settings';
import { toSaleRecord, type SaleRecord } from '../store/sale-record';
import type { PendingRefund } from '../store/reversals';
import type { SaleStore } from '../store/sales-store';

/** What the register chose for this payment; the loyalty flags map onto `SettlementOptions`. */
export interface PayOptions {
  readonly net: boolean;
  readonly rebates: boolean;
  readonly redemption: boolean;
  readonly award: boolean;
}

export const DEFAULT_PAY_OPTIONS: PayOptions = {
  net: false,
  rebates: true,
  redemption: true,
  award: true,
};

/** What `beforeStep` persisted for the step about to run, so a crash mid-payment leaves a trail. */
export interface PendingStepRecord {
  readonly sessionId: string;
  readonly cartId: string;
  readonly step: SettlementContext['step'];
  readonly saleTransactionId: string;
  readonly currentTotal: Money;
  readonly at: string;
}

export const PENDING_STEP_KEY = 'bilt-pos-emulator.pendingStep';

export interface LaneValue {
  readonly settings: Settings;
  readonly connection: BiltPosConnection;
  readonly lane: UseSessionResult<ShopperSession>;
  readonly session: ShopperSession | null;

  /** The same session when it has a terminal; `null` in local mode. */
  readonly terminalSession: TerminalShopperSession | null;
  readonly basket: UseBasketResult;
  readonly member: UseMemberResult;
  readonly context: UseSessionContextResult;
  readonly settlement: UseSettlementResult;
  readonly toasts: ToastApi;
  readonly log: LogStore;
  readonly sales: SaleStore;

  /** Which updater produced the last `basket.changed`. */
  readonly lastChange: BasketChange['source'] | null;

  /** Reports a failure to the cashier (toast) and the log. */
  report(error: unknown): void;

  /** Runs an action, reporting a rejection instead of throwing. */
  run(action: Promise<unknown>): void;

  readonly pendingGiftCards: readonly PendingGiftCard[];

  /** Rings a gift-card line and arranges for the terminal to load the card at settlement. */
  addGiftCard(amount: Money, cardNumber: string, type: StoredValueLoadType): Promise<void>;

  /** The gift card registered for split tender, or `null`. */
  readonly tenderCard: StoredValueCard | null;
  setTenderCard(cardNumber: string | null): Promise<void>;


  /** Starts the settlement with the lane's handlers. */
  pay(options: PayOptions): void;
  readonly pendingStep: PendingStepRecord | null;

  /** The sale this session last recorded, until the next shopper. */
  readonly lastSale: SaleRecord | null;

  /** Clears the basket and the member for the next shopper and resets the settlement. */
  nextShopper(): void;

  /**
   * The Refunds tab's reversal in flight (`void sale-…`, `refund sale-…`, `unlinked refund`), or
   * `null`. Those run on the terminal session outside the settlement hook, so the Sale tab locks
   * on this as well; it lives here so a tab switch mid-reversal does not lose it.
   */
  readonly reversal: string | null;
  setReversal(reversal: string | null): void;

  /**
   * A settlement abandoned to the register with movements committed, until the cashier marks it
   * reconciled. `ABANDON` leaves the basket reusable and hands duplicate prevention to the
   * register, so another payment is refused meanwhile: it could move the same points, rebates or
   * stored value again.
   */
  readonly abandoned: AbandonedSettlementRecord | null;
  resolveAbandoned(): void;

  /**
   * A referenced refund that stopped after committing an allocation. The session holds its
   * `RETURN` line and accepts only a retry of the same basket and allocations, so the Sale tab
   * locks until the Refunds tab's retry settles it.
   */
  readonly pendingRefund: PendingRefund | null;
  setPendingRefund(pending: PendingRefund | null): void;
}

const LaneContext = createContext<LaneValue | null>(null);
LaneContext.displayName = 'LaneContext';

export function useLane(): LaneValue {
  const lane = useContext(LaneContext);
  if (!lane) throw new Error('useLane() needs a <LaneProvider> above the component calling it');
  return lane;
}

export interface LaneProviderProps {
  readonly settings: Settings;
  readonly log: LogStore;
  readonly sales: SaleStore;
  readonly children?: ReactNode;
}

function sessionOptions(settings: Settings): ShopperSessionOptions {
  return {
    saleId: settings.saleId,
    currency: settings.currency,
    storeLocation: settings.storeLocation,
  };
}

function persistPendingStep(record: PendingStepRecord | null): void {
  try {
    if (record) localStorage.setItem(PENDING_STEP_KEY, JSON.stringify(record));
    else localStorage.removeItem(PENDING_STEP_KEY);
  } catch {
    // Storage unavailable: the record stays in memory only.
  }
}

/**
 * One lane, shared by every pane. The session comes from `useTerminalSession` or
 * `useShopperSession` depending on the settings; both hooks are called every render and the
 * disabled one stays idle. The provider owns everything that must outlive a tab switch: the
 * settlement hook, the pending gift-card fulfilments, the split-tender card, the last sale.
 * Changing a setting remounts it (see `laneKey`), which ends the old session and starts a new one.
 */
export function LaneProvider({ settings, log, sales, children }: LaneProviderProps): ReactNode {
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

  const basket = useBasket(session);
  const member = useMember(session);
  const context = useSessionContext(session);
  const settlement = useSettlement(terminalSession, { interactive: ['RECOVERY_REQUIRED'] });
  const [lastChange, setLastChange] = useState<BasketChange['source'] | null>(null);
  const [pendingGiftCards, setPendingGiftCards] = useState<readonly PendingGiftCard[]>([]);
  const [tenderCard, setTenderCardState] = useState<StoredValueCard | null>(null);
  const [pendingStep, setPendingStep] = useState<PendingStepRecord | null>(null);
  const [lastSale, setLastSale] = useState<SaleRecord | null>(null);
  const [reversal, setReversal] = useState<string | null>(null);
  const [abandoned, setAbandoned] = useState<AbandonedSettlementRecord | null>(null);
  const [pendingRefund, setPendingRefund] = useState<PendingRefund | null>(null);

  useSessionLogging(session, log);

  const report = useCallback(
    (error: unknown) => {
      log.error('page', error);
      toasts.push('warning', describeError(error));
    },
    [log, toasts],
  );
  const run = useCallback((action: Promise<unknown>) => void action.catch(report), [report]);

  useEffect(() => {
    log.info(
      'connection',
      connection.status,
      connection.error ? describeError(connection.error) : undefined,
    );
  }, [log, connection.status, connection.error]);

  useEffect(() => {
    const where = describeSession(settings);
    log.info(
      'lane',
      `${lane.status}${session ? ` (${session.id}, ${where})` : ''}${lane.error ? `: ${describeError(lane.error)}` : ''}`,
    );
  }, [log, lane.status, lane.error, session, settings]);

  useSessionEvent(session, 'basket.changed', (change) => {
    setLastChange(change.source);
    // A fulfilment belongs to its line: when the line leaves the basket (removed, replaced away,
    // or the basket cleared), the terminal instruction goes with it. A clear also drops the
    // split-tender card, as the SDK does.
    setPendingGiftCards((pending) =>
      pending.filter((gift) =>
        change.current.items.some((line) => line.reference === gift.reference),
      ),
    );
    if (change.source === 'CLEAR') setTenderCardState(null);
  });
  useSessionEvent(session, 'background.error', (error) =>
    toasts.push('warning', `${error.code}: ${error.message}`),
  );
  useSessionEvent(session, 'session.ended', ({ forced }) =>
    toasts.push('info', forced ? 'Session force-ended' : 'Session ended'),
  );

  const addGiftCard = useCallback(
    async (amount: Money, cardNumber: string, type: StoredValueLoadType) => {
      const { item, reference } = giftCardLine(amount, type);
      const card = storedValueCard(cardNumber);
      await basket.addItem(item);
      setPendingGiftCards((pending) => [...pending, { reference, card, type, amount }]);
      log.info(
        'gift-card',
        `${type.toLowerCase()} ${amount} on ${card.storedValueId ?? 'a swiped card'} at settlement (${reference})`,
      );
    },
    [basket, log],
  );

  const setTenderCard = useCallback(
    async (cardNumber: string | null) => {
      if (!terminalSession) throw new Error('a gift-card tender needs a terminal session');
      const card = cardNumber === null ? null : storedValueCard(cardNumber);
      await terminalSession.setStoredValueCard(card);
      setTenderCardState(card);
      log.info(
        'gift-card',
        card ? `tender card ${card.storedValueId ?? 'swiped'} set` : 'tender card cleared',
      );
    },
    [terminalSession, log],
  );

  // The handlers read the latest member and session through refs: a settlement runs for a while.
  const latest = useRef({ member: member.member, terminalSession, pendingGiftCards, abandoned });
  latest.current = { member: member.member, terminalSession, pendingGiftCards, abandoned };

  const resolveAbandoned = useCallback(() => {
    if (!latest.current.abandoned) return;
    log.info('settle', `abandoned settlement ${latest.current.abandoned.settlementId} reconciled`);
    setAbandoned(null);
  }, [log]);

  const pay = useCallback(
    (options: PayOptions) => {
      const current = latest.current.terminalSession;
      if (!current) {
        report(new Error('settlement needs a terminal session'));
        return;
      }
      if (latest.current.abandoned) {
        report(
          new Error(
            `settlement ${latest.current.abandoned.settlementId} was abandoned with committed movements; reconcile it first`,
          ),
        );
        return;
      }
      const fulfillments = latest.current.pendingGiftCards.map(toFulfillment);
      log.info(
        'settle',
        `${options.net ? 'net ' : ''}settlement: rebates ${options.rebates ? 'on' : 'off'}, redemption ${options.redemption ? 'on' : 'off'}, award ${options.award ? 'on' : 'off'}, ${fulfillments.length} gift card(s) to load`,
      );
      let operation;
      try {
        operation = settlement.settle({
          settlementType: options.net ? 'NET' : 'REFUND_THEN_CHARGE',
          disableRebates: !options.rebates,
          disablePoints: !options.redemption,
          disableAward: !options.award,
          ...(fulfillments.length > 0 ? { fulfillments } : {}),
          // Persist what is about to move before it moves, and keep the basket's shared sale
          // transaction id for every step, as the Java default does.
          beforeStep: (ctx) => {
            const record: PendingStepRecord = {
              sessionId: current.id,
              cartId: ctx.currentBasket.cartId,
              step: ctx.step,
              saleTransactionId: ctx.defaultTransactionId,
              currentTotal: ctx.currentTotal,
              at: new Date().toISOString(),
            };
            persistPendingStep(record);
            setPendingStep(record);
            log.info(
              'settle',
              `before ${ctx.step}: sale transaction ${ctx.defaultTransactionId}, total ${ctx.currentTotal}`,
            );
            return ctx.defaultTransactionId;
          },
          // Rebates lower the price, so the register re-taxes the rebated lines rather than
          // accepting the suggested total, which keeps tax on the undiscounted amount.
          onRebatesRedeemed: (result) => {
            const total = recomputeTotal(result.updatedBasket);
            log.info(
              'settle',
              `rebates −${result.totalRebateAmount}: suggested ${result.suggestedTotal}, re-taxed total ${total}`,
            );
            return total;
          },
          // Points and a gift card are tender, not price changes: the suggested total is right.
          onPointsRedeemed: (result) => {
            log.info(
              'settle',
              `${result.pointsUsed} points redeemed (−${result.monetaryValue}) → ${result.suggestedTotal}`,
            );
            return result.suggestedTotal;
          },
          onGiftCardPayment: (result) => {
            log.info(
              'settle',
              `gift card charged ${result.amountCharged}${result.remainingCardBalance ? ` (balance ${result.remainingCardBalance})` : ''} → ${result.suggestedTotal}`,
            );
            return result.suggestedTotal;
          },
          onAbandoned: (record) => {
            log.info(
              'settle',
              `abandoned: ${record.committedMovements.length} committed movement(s), ${record.outstandingAmount} outstanding`,
              record,
            );
            toasts.push('warning', `Settlement ${record.settlementId} abandoned to the register`);
            if (record.committedMovements.length > 0) setAbandoned(record);
          },
        });
      } catch (error) {
        report(error);
        return;
      }
      track(log, 'settle', operation);
      operation.then(
        (result: SettlementResult) => {
          persistPendingStep(null);
          setPendingStep(null);
          setPendingGiftCards([]);
          const memberId = latest.current.member?.resolved
            ? latest.current.member.memberId
            : undefined;
          const record = toSaleRecord(result, {
            sessionId: current.id,
            saleId: current.saleId,
            poiId: current.poiId,
            currency: current.currency,
            memberId,
            recordId: `sale-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 6)}`,
            completedAt: new Date(),
          });
          setLastSale(record);
          sales.recordSale(record).then(
            () =>
              log.info(
                'sales',
                `recorded ${record.id}: ${record.legs.map((leg) => leg.type).join(', ') || 'no legs'}`,
              ),
            (cause: unknown) => report(cause),
          );
          toasts.push('success', `Paid ${formatMoney(result.authorizedAmount, current.currency)}`);
        },
        () => {
          persistPendingStep(null);
          setPendingStep(null);
        },
      );
    },
    [settlement, log, sales, toasts, report],
  );

  const nextShopper = useCallback(() => {
    // The basket and the member are both the previous shopper's; the session itself carries on.
    // The register resets only once the session accepted the clear: it refuses it while money
    // is still moving, and the result stays on screen then.
    run(
      basket
        .clear()
        .then(() => session?.member.clear())
        .then(() => {
          settlement.reset();
          setLastSale(null);
        }),
    );
  }, [run, basket, session, settlement]);

  const value = useMemo<LaneValue>(
    () => ({
      settings,
      connection,
      lane,
      session,
      terminalSession,
      basket,
      member,
      context,
      settlement,
      toasts,
      log,
      sales,
      lastChange,
      report,
      run,
      pendingGiftCards,
      addGiftCard,
      tenderCard,
      setTenderCard,
      pay,
      pendingStep,
      lastSale,
      nextShopper,
      reversal,
      setReversal,
      abandoned,
      resolveAbandoned,
      pendingRefund,
      setPendingRefund,
    }),
    [
      settings,
      connection,
      lane,
      session,
      terminalSession,
      basket,
      member,
      context,
      settlement,
      toasts,
      log,
      sales,
      lastChange,
      report,
      run,
      pendingGiftCards,
      addGiftCard,
      tenderCard,
      setTenderCard,
      pay,
      pendingStep,
      lastSale,
      nextShopper,
      reversal,
      abandoned,
      resolveAbandoned,
      pendingRefund,
    ],
  );

  return (
    <LaneContext.Provider value={value}>
      {children}
      <Toasts toasts={toasts} />
    </LaneContext.Provider>
  );
}
