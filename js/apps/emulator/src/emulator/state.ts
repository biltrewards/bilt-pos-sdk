import type { Money } from '@biltorg/pos-sdk';
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

/** What the cashier can do when a card approves less than it was asked for. */
export type PartialApprovalAction = 'ANOTHER_CARD' | 'ADD_TO_NEXT' | 'EXTERNAL' | 'ABORT';

/** One card payment of a settlement, as the partial-approval dialog and the outcome list it. */
export interface CardPaymentUi {
  readonly amount: Money;
  readonly brand?: string;
  /** The last four digits of the masked card number, when the terminal reported it. */
  readonly lastDigits?: string;
  readonly approvalCode?: string;
  readonly poiTransactionId?: string;
  /** True when the terminal approved less than this payment asked for. */
  readonly partial: boolean;
}

/**
 * One line per card payment, `Card 1 $60.00 · Visa ····1111 · approval APPR-1 · partial`: the
 * outcome popup, the event log and the Refund tab all word a card payment this way.
 */
export function cardLine(
  index: number,
  card: {
    readonly amount?: Money | undefined;
    readonly brand?: string | undefined;
    readonly lastDigits?: string | undefined;
    readonly approvalCode?: string | undefined;
    readonly partial: boolean;
  },
): string {
  return [
    `Card ${index + 1}${card.amount === undefined ? '' : ` $${card.amount}`}`,
    [card.brand, card.lastDigits ? `····${card.lastDigits}` : null].filter(Boolean).join(' '),
    card.approvalCode ? `approval ${card.approvalCode}` : null,
    card.partial ? 'partial' : null,
  ]
    .filter(Boolean)
    .join(' · ');
}

/**
 * A card approved less than it was asked for and the host waits for the cashier: the amounts the
 * `PartialApprovalDialog` shows and the actions it offers. `plannedLeft` is true when a planned
 * card payment is still to come, which is when Add to next card applies.
 */
export interface PartialApprovalPrompt {
  readonly approved: Money;
  readonly requested: Money;
  readonly shortfall: Money;
  /** Everything the card phase still owes: the shortfall plus the card payments still to come. */
  readonly amountDue: Money;
  readonly plannedLeft: boolean;
  readonly actions: readonly PartialApprovalAction[];
  choose(action: PartialApprovalAction): void;
}

export function partialApprovalActions(plannedLeft: boolean): readonly PartialApprovalAction[] {
  return plannedLeft
    ? ['ANOTHER_CARD', 'ADD_TO_NEXT', 'EXTERNAL', 'ABORT']
    : ['ANOTHER_CARD', 'EXTERNAL', 'ABORT'];
}

/** The button text and one-line description of a partial-approval choice, from the prompt's amounts. */
export function partialApprovalChoice(
  action: PartialApprovalAction,
  prompt: Pick<PartialApprovalPrompt, 'shortfall' | 'amountDue'>,
): { readonly label: string; readonly description: string } {
  switch (action) {
    case 'ANOTHER_CARD':
      return {
        label: `Another card ($${prompt.shortfall})`,
        description: `Charge the $${prompt.shortfall} shortfall on another card, then continue the sale.`,
      };
    case 'ADD_TO_NEXT':
      return {
        label: 'Add to next card',
        description: `Add the $${prompt.shortfall} shortfall to the next planned card payment.`,
      };
    case 'EXTERNAL':
      return {
        label: `Rest in cash ($${prompt.amountDue})`,
        description: `Record $${prompt.amountDue} as cash and keep the card payments. Choose only after collecting it.`,
      };
    case 'ABORT':
      return {
        label: 'Cancel sale',
        description: 'Reverse every card payment and stop the sale.',
      };
  }
}

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

/** One receipt of an outcome; `caption` names the card payment it belongs to, when there are several. */
export interface OutcomeReceipt {
  readonly caption?: string;
  readonly text: string;
}

/** The last settlement, refund or stored-value operation, shown as a popup until dismissed. */
export interface PaymentOutcome {
  readonly success: boolean;
  readonly title: string;
  readonly message: string;
  readonly receipt?: string;
  /** A settlement's card receipts, one per card payment in order; replaces `receipt` when set. */
  readonly receipts?: readonly OutcomeReceipt[];
}

/** Which loyalty steps the next settlement runs. */
export interface LoyaltyOptions {
  /** The terminal handles loyalty within the card payment; the steps below are then ignored. */
  readonly implicit: boolean;
  readonly rebates: boolean;
  readonly redemption: boolean;
  readonly award: boolean;
}

/**
 * How the card amount is charged. `plannedAmounts` are the fixed card payments of a split sale,
 * each on its own card, with the remainder on one more; none means one card payment for the
 * whole amount. With `partialApprovals` on, a card that approves less asks the cashier what to
 * do; off, the short approval fails the step into the recovery dialog.
 */
export interface CardOptions {
  readonly plannedAmounts: readonly string[];
  readonly partialApprovals: boolean;
}

export const DEFAULT_CARD_OPTIONS: CardOptions = { plannedAmounts: [], partialApprovals: true };

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

/**
 * What a register-entered member identity is: a customer-known handle the terminal looks up, or a
 * member id already resolved elsewhere.
 */
export type MemberLookupKind = 'PHONE' | 'EMAIL' | 'ACCOUNT_NUMBER' | 'RESOLVED_ID';

export const MEMBER_LOOKUP_LABELS: Readonly<Record<MemberLookupKind, string>> = {
  PHONE: 'Phone',
  EMAIL: 'Email',
  ACCOUNT_NUMBER: 'Account number',
  RESOLVED_ID: 'Resolved member id',
};

/** The terminal's answer to the last loyalty sign-in on this checkout. */
export type MemberIdentity =
  | {
      readonly kind: 'found';
      readonly memberId: string;
      readonly loyaltyBrand?: string;
      /**
       * `null` when the terminal reported none, which the prompted sign-in always does unless its
       * shopper context carries one.
       */
      readonly pointBalance: number | null;
      /** The shopper's name from the terminal's shopper context; absent when not reported. */
      readonly name?: string;
      /** The shopper's loyalty tier from the terminal's shopper context; absent when not reported. */
      readonly tierName?: string;
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

/** One card payment of a stored sale, as the Refund tab lists it and its return picker offers it. */
export interface SaleCardUi {
  readonly poiTransactionId: string;
  readonly amount?: Money;
  readonly brand?: string;
  readonly lastDigits?: string;
  readonly approvalCode?: string;
  readonly partial: boolean;
  /** True once a full refund or void reversed this payment. */
  readonly refunded: boolean;
  /** What the payment can still return, in cents; `null` when unknown. */
  readonly remainingMinor: number | null;
}

/** A completed sale as the Refund tab lists it. */
export interface StoredSaleUi {
  readonly id: string;
  readonly completedAtLabel: string;
  readonly totalAmount: Money;
  readonly memberId: string | null;
  readonly items: readonly SaleItemUi[];
  readonly hasGiftCardPurchase: boolean;
  /** Every card payment the sale made, in order. */
  readonly cardPayments: readonly SaleCardUi[];
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
  readonly partialApproval: PartialApprovalPrompt | null;
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
  partialApproval: null,
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
  startSession(): void;
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
  settle(
    loyalty: LoyaltyOptions,
    storedValue: StoredValueOptions | null,
    net: boolean,
    card?: CardOptions,
  ): void;
  identifyMember(): void;
  /**
   * Identifies the member from a value the register supplies, with no terminal prompt. A phone,
   * email or account number is looked up on the terminal and lands on the card like a sign-in; a
   * resolved member id attaches at once and the terminal confirms it in the background. A blank
   * value sends nothing.
   */
  identifyMemberBy(kind: MemberLookupKind, value: string): void;
  acquireCard(): void;
  refundSale(saleId: string): void;
  /**
   * Rings the selected lines of a stored sale into the basket as returns. `cardPoiTransactionId`
   * picks which card payment of a split sale the refund goes back to; the first card payment
   * still standing when absent.
   */
  addReturnToBasket(saleId: string, skus: ReadonlySet<string>, cardPoiTransactionId?: string): void;
  clearBasket(): void;
  abort(): void;
  dismissPaymentOutcome(): void;
}
