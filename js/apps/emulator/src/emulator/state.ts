import type { Money } from '@bilt/pos-sdk';
import type { Product } from '../catalog';
import { formatMinor } from '../money';

/**
 * The emulator's UI state and its controller, a port of the desktop emulator's `EmulatorState.kt`:
 * the same fields and actions, so the components render the same panels from the same facts. Kept
 * free of SDK objects, so the components never reach past the controller.
 */

export type PaymentRecoveryAction = 'RETRY' | 'SKIP' | 'CASH' | 'ABORT' | 'ABANDON';

export const RECOVERY_ACTIONS: Readonly<
  Record<PaymentRecoveryAction, { readonly label: string; readonly description: string }>
> = {
  RETRY: {
    label: 'Retry',
    description: 'Retry the failed step, or check its status again if the outcome is unknown.',
  },
  SKIP: { label: 'Skip step', description: 'Continue without this optional step.' },
  CASH: {
    label: 'Confirm cash received',
    description: 'Record the full amount due as cash. Choose only after collecting it.',
  },
  ABORT: {
    label: 'Abort and roll back',
    description: 'Stop settlement and reverse committed charge-side steps.',
  },
  ABANDON: {
    label: 'Abandon recovery',
    description: 'Stop without rollback. Reconcile all movements manually before settling again.',
  },
};

export interface PaymentRecoveryPrompt {
  readonly message: string;
  readonly actions: readonly PaymentRecoveryAction[];
  choose(action: PaymentRecoveryAction): void;
}

export type ConnectionPhase = 'DISCONNECTED' | 'CONNECTING' | 'CONNECTED' | 'ERROR';

export interface ConnectionStatus {
  readonly phase: ConnectionPhase;
  readonly detail?: string;
}

export type BasketLineType = 'SALE' | 'RETURN' | 'CREDIT';

export interface BasketLine {
  readonly sku: string;
  readonly description: string;
  readonly quantity: number;
  readonly lineTotal: Money;
  /** Unit price in cents when the keypad may re-price this line, else `null`. */
  readonly editablePriceMinor: number | null;
  readonly itemId: string;
  readonly type: BasketLineType;
  readonly originalTotal: Money;
  readonly discountTotal: Money;
  readonly discountLabels: readonly string[];
  /** True when this sale line has a settlement-time stored-value load. */
  readonly giftCard: boolean;
}

/** The last settlement, refund or stored-value operation, shown as a popup until dismissed. */
export interface PaymentOutcome {
  readonly success: boolean;
  readonly title: string;
  readonly message: string;
  readonly receipt?: string;
}

/** Which loyalty steps the next settlement runs. */
export interface LoyaltyOptions {
  readonly rebates: boolean;
  readonly redemption: boolean;
  readonly award: boolean;
}

/** A gift card tender, charged first; blank card number means the terminal reads it. */
export interface StoredValueOptions {
  readonly cardNumber: string;
}

export type MemberRewardKind = 'REWARD' | 'COUPON' | 'POINT' | 'UNKNOWN';

export interface MemberRewardUi {
  readonly rewardRef: string | null;
  readonly kind: MemberRewardKind;
  readonly description: string;
  readonly expiresAtLabel?: string;
}

/** One pass returned in a VAS read. */
export interface VasServiceUi {
  readonly serviceId: string | null;
  readonly serviceType: string | null;
  readonly statusWord: string | null;
  readonly encryptedData: string | null;
  readonly cipherTimestamp: string | null;
}

/**
 * Apple Wallet Value Added Services data the terminal read from a tapped pass; the payload is
 * still encrypted with the merchant's VAS key.
 */
export interface VasUi {
  readonly source: string | null;
  readonly merchantId: string | null;
  readonly services: readonly VasServiceUi[];
  /** The terminal's unparsed report, set only when it could not be broken into fields. */
  readonly raw: string | null;
}

export function vasServiceLine(service: VasServiceUi): string {
  return [
    service.serviceId ?? '(no serviceId)',
    service.serviceType,
    service.statusWord ? `status ${service.statusWord}` : null,
    // the payload is opaque without the merchant key, so a prefix identifies it and the size
    // shows it arrived whole
    service.encryptedData
      ? `data ${service.encryptedData.slice(0, 16)}${service.encryptedData.length > 16 ? '…' : ''} (${Math.floor(service.encryptedData.length / 2)} bytes)`
      : null,
    service.cipherTimestamp ? `timestamp ${service.cipherTimestamp}` : null,
  ]
    .filter(Boolean)
    .join(' · ');
}

/** One line per fact, for the sign-in card and the log. */
export function vasLines(vas: VasUi): string[] {
  const head = [vas.source, vas.merchantId ? `merchant ${vas.merchantId}` : null]
    .filter(Boolean)
    .join(' · ');
  return [
    ...(head ? [head] : []),
    ...(vas.raw ? [`raw report: ${vas.raw}`] : []),
    ...vas.services.map(vasServiceLine),
  ];
}

export type AbsentReason = 'NOT_FOUND' | 'SUSPENDED' | 'CANCELLED';

export const ABSENT_LABELS: Readonly<Record<AbsentReason, string>> = {
  NOT_FOUND: 'No member found',
  SUSPENDED: 'Member suspended',
  CANCELLED: 'Cancelled on the terminal',
};

/** The terminal's answer to the last loyalty sign-in on this checkout. */
export type MemberIdentity =
  | {
      readonly kind: 'found';
      readonly memberId: string;
      readonly loyaltyBrand?: string;
      /** `null` when the terminal reported none, which the prompted sign-in always does. */
      readonly pointBalance: number | null;
      readonly rewards: readonly MemberRewardUi[];
      /** True when this member predates the latest sign-in attempt. */
      readonly retained: boolean;
      /** What the terminal read from a tapped wallet pass; absent when none was read. */
      readonly vas?: VasUi;
    }
  | { readonly kind: 'absent'; readonly reason: AbsentReason }
  | { readonly kind: 'failed'; readonly detail?: string };

export function memberHeadline(member: MemberIdentity): string {
  switch (member.kind) {
    case 'found':
      return (
        [member.memberId, member.loyaltyBrand].filter(Boolean).join(' · ') +
        (member.retained ? ' — kept from an earlier sign-in' : '')
      );
    case 'absent':
      return ABSENT_LABELS[member.reason];
    case 'failed':
      return 'Sign-in failed';
  }
}

/** A terminal card read that returned a full card number; `sequence` grows per read. */
export interface AcquiredCard {
  readonly number: string;
  readonly sequence: number;
}

/** One cart line of a stored sale, as the Refund tab shows it. */
export interface SaleItemUi {
  readonly sku: string;
  readonly description: string;
  readonly quantity: number;
  /** Shelf price plus tax of the remaining quantity, in cents. */
  readonly refundMinor: number;
  readonly remainingQuantity: number;
}

export function refundLabel(item: SaleItemUi): string {
  return `$${formatMinor(item.refundMinor)}`;
}

/** A completed sale as the Refund tab lists it. */
export interface StoredSaleUi {
  readonly id: string;
  readonly completedAtLabel: string;
  readonly totalAmount: Money;
  readonly memberId: string | null;
  readonly items: readonly SaleItemUi[];
  readonly hasGiftCardPurchase: boolean;
  readonly refunded: boolean;
  readonly fullyRefunded: boolean;
  readonly voided: boolean;
  readonly fullRefundAvailable: boolean;
  readonly externalPaymentAmount: Money | null;
}

export function saleRefundable(sale: StoredSaleUi): boolean {
  return !sale.voided && !sale.fullyRefunded;
}

export function memberLabel(sale: StoredSaleUi): string {
  return sale.memberId ? `member ${sale.memberId}` : 'guest';
}

export function saleStatusLabel(sale: StoredSaleUi): string {
  return [
    memberLabel(sale),
    sale.externalPaymentAmount ? `cash $${sale.externalPaymentAmount} — refund manually` : null,
    sale.voided
      ? 'voided'
      : sale.fullyRefunded
        ? 'refunded'
        : sale.refunded
          ? 'partially refunded'
          : null,
  ]
    .filter(Boolean)
    .join(' · ');
}

/** What the bridge reported: its status, where it answered, and the terminal it drives. */
export interface BridgeInfo {
  readonly status: 'detecting' | 'missing' | 'outdated' | 'ready';
  readonly baseUrl?: string;
  readonly version?: string;
  readonly terminal?: { label?: string; model?: string; reachable?: boolean };
}

export interface EmulatorState {
  readonly connection: ConnectionStatus;
  /** Whether the connection brackets checkouts on a terminal or runs local sessions only. */
  readonly mode: 'terminal' | 'local';
  readonly sessionId: string | null;
  readonly basket: readonly BasketLine[];
  readonly basketTotal: Money;
  readonly basketTax: Money;
  readonly paymentInProgress: boolean;
  readonly cardReadInProgress: boolean;
  readonly storedValueInProgress: boolean;
  readonly identifyInProgress: boolean;
  readonly refundInProgress: boolean;
  readonly lastPayment: string | null;
  readonly paymentOutcome: PaymentOutcome | null;
  readonly paymentRecovery: PaymentRecoveryPrompt | null;
  readonly acquiredCard: AcquiredCard | null;
  readonly member: MemberIdentity | null;
  readonly sales: readonly StoredSaleUi[];
}

export const INITIAL_STATE: EmulatorState = {
  connection: { phase: 'DISCONNECTED' },
  mode: 'terminal',
  sessionId: null,
  basket: [],
  basketTotal: '0.00',
  basketTax: '0.00',
  paymentInProgress: false,
  cardReadInProgress: false,
  storedValueInProgress: false,
  identifyInProgress: false,
  refundInProgress: false,
  lastPayment: null,
  paymentOutcome: null,
  paymentRecovery: null,
  acquiredCard: null,
  member: null,
  sales: [],
};

export function sessionOperationInProgress(state: EmulatorState): boolean {
  return (
    state.paymentInProgress ||
    state.cardReadInProgress ||
    state.storedValueInProgress ||
    state.identifyInProgress ||
    state.refundInProgress
  );
}

/** A checkout is open and the terminal is not busy: the precondition of every session operation. */
export function canOperate(state: EmulatorState): boolean {
  return state.sessionId !== null && !sessionOperationInProgress(state);
}

/** Same as `canOperate`, and the checkout has a terminal: what terminal operations need. */
export function canOperateTerminal(state: EmulatorState): boolean {
  return canOperate(state) && state.mode === 'terminal';
}

/**
 * The actions of the desktop `EmulatorController`. `connect()` reaches the bridge rather than a
 * terminal address, so there is no address autodetection; the bridge probe stands in for it.
 */
export interface EmulatorController {
  getState(): EmulatorState;
  subscribe(listener: () => void): () => void;
  connect(): void;
  disconnect(): void;
  startSession(identifyOnStart: boolean, readVas?: boolean): void;
  endSession(): void;
  addProduct(product: Product): void;
  addCustomItem(priceMinor: number): Promise<boolean>;
  updateCustomItemPrice(sku: string, priceMinor: number): Promise<boolean>;
  removeCustomItem(sku: string): Promise<boolean>;
  addGiftCardPurchase(amount: string, cardNumber: string): void;
  inquireStoredValueBalance(cardNumber: string): void;
  activateStoredValue(cardNumber: string): void;
  applyCredit(itemId: string, amount: string, label: string): void;
  applyDiscount(itemId: string, amount: string, label: string): void;
  settle(loyalty: LoyaltyOptions, storedValue: StoredValueOptions | null, net: boolean): void;
  identifyMember(readVas?: boolean): void;
  acquireCard(): void;
  refundSale(saleId: string): void;
  addReturnToBasket(saleId: string, skus: ReadonlySet<string>): void;
  clearBasket(): void;
  abort(): void;
  dismissPaymentOutcome(): void;
}
