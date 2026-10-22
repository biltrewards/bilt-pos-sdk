import type {
  Basket,
  CardAcquisitionOptions,
  CardAcquisitionResult,
  ConfirmationOptions,
  DisplayPayload,
  IdentifyOptions,
  IdentifyResult,
  InputOptions,
  MemberIdResolver,
  MenuOptions,
  MenuSelection,
  Money,
  OperationRequest,
  OperationType,
  OriginalSaleRecord,
  PinOptions,
  PinResult,
  RefundResult,
  Session,
  SettlementOptions,
  SettlementResult,
  Signature,
  StoredValueBalance,
  StoredValueCard,
  StoredValueOperationResult,
  TransactionStatusOptions,
  TransactionStatusResult,
  VoidResult,
} from '@biltorg/pos-protocol';
import type { Operation } from '../operation';
import type { TerminalSessionOptions } from '../session';
import type { Terminal } from '../terminal';
import type {
  AttachTerminalSessionOptions,
  ReversalHandlers,
  SettleOptions,
  SettlementHandlers,
  TerminalShopperSession,
} from '../terminal-session';
import { newIdempotencyKey } from './ids';
import { ShopperSessionImpl, type SessionRuntime } from './session';
import { handledSteps, type StepHandlers } from './steps';
import { TerminalImpl } from './terminal';

const SETTLEMENT_OPTION_KEYS = [
  'disableRebates',
  'disablePoints',
  'disableAward',
  'cashback',
  'paymentProcessingDisplay',
  'refunds',
  'fulfillments',
  'settlementType',
] as const satisfies readonly (keyof SettlementOptions)[];

const SETTLEMENT_HANDLER_KEYS = [
  'beforeStep',
  'onRebatesRedeemed',
  'onPointsRedeemed',
  'onGiftCardPayment',
  'onMovement',
  'onCardCharged',
  'onExternallyPaid',
  'onAwarded',
  'onStoredValueLoaded',
  'onCardRefunded',
  'onGiftCardRefunded',
  'onExternalRefunded',
  'onPointsRefunded',
  'onRebateRefunded',
  'onAwardRefunded',
  'onError',
  'onAbandoned',
] as const satisfies readonly (keyof SettlementHandlers)[];

/** Splits `settle()`'s one options object into the wire options and the handlers. */
export function splitSettleOptions(options: SettleOptions): {
  options: SettlementOptions;
  handlers: SettlementHandlers;
} {
  const wire: Record<string, unknown> = {};
  for (const key of SETTLEMENT_OPTION_KEYS) {
    if (options[key] !== undefined) wire[key] = options[key];
  }
  const handlers: Record<string, unknown> = {};
  for (const key of SETTLEMENT_HANDLER_KEYS) {
    if (options[key] !== undefined) handlers[key] = options[key];
  }
  return { options: wire as SettlementOptions, handlers: handlers as SettlementHandlers };
}

/** A bare card number is a keyed PAN, as in Java. */
function toStoredValueCard(card: StoredValueCard | string): StoredValueCard {
  return typeof card === 'string'
    ? { storedValueId: card, identificationType: 'PAN', entryMode: 'KEYED' }
    : card;
}

function reversalSteps(handlers: ReversalHandlers | undefined): StepHandlers | undefined {
  return handlers ? { kind: 'reversal', handlers } : undefined;
}

/**
 * The SDK's `TerminalShopperSession`: every terminal method is one `POST .../operations`
 * through `startOperation`, with the request body the protocol defines for it. Settlements,
 * refunds and voids declare in `handledSteps` the step kinds their handlers cover.
 */
export class TerminalShopperSessionImpl
  extends ShopperSessionImpl
  implements TerminalShopperSession
{
  override readonly kind = 'terminal' as const;
  readonly poiId: string;

  private readonly attachHandlers: AttachTerminalSessionOptions | undefined;

  constructor(
    runtime: SessionRuntime,
    session: Session,
    options: TerminalSessionOptions | AttachTerminalSessionOptions,
  ) {
    super(runtime, session, options);
    this.poiId = session.poiId ?? ('poiId' in options ? options.poiId : undefined) ?? '';
    this.attachHandlers = 'settlement' in options || 'reversal' in options ? options : undefined;
  }

  /** The handlers an adopted `settle` or reversal answers its steps with, from the attach options. */
  protected override stepHandlersFor(type: OperationType): StepHandlers | undefined {
    const handlers = this.attachHandlers;
    if (type === 'settle' && handlers?.settlement) {
      return { kind: 'settlement', handlers: handlers.settlement };
    }
    if (
      (type === 'refund' || type === 'refundUnlinked' || type === 'voidTransaction') &&
      handlers?.reversal
    ) {
      return { kind: 'reversal', handlers: handlers.reversal };
    }
    return undefined;
  }

  private request<T>(request: OperationRequest, steps?: StepHandlers): Operation<T> {
    return this.startOperation<T>(request, steps);
  }

  identifyMember(options?: IdentifyOptions): Operation<IdentifyResult>;
  identifyMember(pending: { resolver: MemberIdResolver }): Operation<IdentifyResult>;
  identifyMember(
    arg?: IdentifyOptions | { resolver: MemberIdResolver },
  ): Operation<IdentifyResult> {
    if (arg && 'resolver' in arg && arg.resolver) {
      return this.request({ type: 'identifyMember', resolver: arg.resolver });
    }
    return this.request(
      arg
        ? { type: 'identifyMember', options: arg as IdentifyOptions }
        : { type: 'identifyMember' },
    );
  }

  acquireCard(options?: CardAcquisitionOptions): Operation<CardAcquisitionResult> {
    return this.request(options ? { type: 'acquireCard', options } : { type: 'acquireCard' });
  }

  requestDigitString(prompt: string, options?: InputOptions): Operation<string> {
    return this.request(
      options
        ? { type: 'requestDigitString', prompt, options }
        : { type: 'requestDigitString', prompt },
    );
  }

  requestDecimalString(prompt: string, options?: InputOptions): Operation<Money> {
    return this.request(
      options
        ? { type: 'requestDecimalString', prompt, options }
        : { type: 'requestDecimalString', prompt },
    );
  }

  requestTextString(prompt: string, options?: InputOptions): Operation<string> {
    return this.request(
      options
        ? { type: 'requestTextString', prompt, options }
        : { type: 'requestTextString', prompt },
    );
  }

  requestConfirmation(prompt: string, options?: ConfirmationOptions): Operation<boolean> {
    return this.request(
      options
        ? { type: 'requestConfirmation', prompt, options }
        : { type: 'requestConfirmation', prompt },
    );
  }

  requestMenuEntry(
    prompt: string,
    entries: readonly string[],
    options?: MenuOptions,
  ): Operation<MenuSelection> {
    return this.request(
      options
        ? { type: 'requestMenuEntry', prompt, entries: [...entries], options }
        : { type: 'requestMenuEntry', prompt, entries: [...entries] },
    );
  }

  requestSignature(prompt: string): Operation<Signature> {
    return this.request({ type: 'requestSignature', prompt });
  }

  requestAmountConfirmation(amount: Money, prompt: string): Operation<boolean> {
    return this.request({ type: 'requestAmountConfirmation', amount, prompt });
  }

  requestPinEntry(options?: PinOptions): Operation<PinResult> {
    return this.request(
      options ? { type: 'requestPinEntry', options } : { type: 'requestPinEntry' },
    );
  }

  requestPinVerify(options?: PinOptions): Operation<PinResult> {
    return this.request(
      options ? { type: 'requestPinVerify', options } : { type: 'requestPinVerify' },
    );
  }

  requestPinVerifyOnly(options?: PinOptions): Operation<PinResult> {
    return this.request(
      options ? { type: 'requestPinVerifyOnly', options } : { type: 'requestPinVerifyOnly' },
    );
  }

  async setStoredValueCard(card: StoredValueCard | string | null): Promise<void> {
    await this.request<void>({
      type: 'setStoredValueCard',
      card: card === null ? null : toStoredValueCard(card),
    });
  }

  storedValueBalance(card: StoredValueCard): Operation<StoredValueBalance> {
    return this.request({ type: 'storedValueBalance', card });
  }

  storedValueActivate(
    card: StoredValueCard,
    initialAmount: Money,
  ): Operation<StoredValueOperationResult> {
    return this.request({ type: 'storedValueActivate', card, amount: initialAmount });
  }

  storedValueLoad(card: StoredValueCard, amount: Money): Operation<StoredValueOperationResult> {
    return this.request({ type: 'storedValueLoad', card, amount });
  }

  storedValueUnload(card: StoredValueCard, amount: Money): Operation<StoredValueOperationResult> {
    return this.request({ type: 'storedValueUnload', card, amount });
  }

  storedValueDeactivate(card: StoredValueCard): Operation<StoredValueOperationResult> {
    return this.request({ type: 'storedValueDeactivate', card });
  }

  storedValueReserve(card: StoredValueCard, amount: Money): Operation<StoredValueOperationResult> {
    return this.request({ type: 'storedValueReserve', card, amount });
  }

  storedValueReverse(
    originalPoiTransactionId: string,
    originalPoiTransactionTimestamp?: Date,
  ): Operation<StoredValueOperationResult> {
    return this.request(
      originalPoiTransactionTimestamp
        ? {
            type: 'storedValueReverse',
            originalPoiTransactionId,
            originalPoiTransactionTimestamp: originalPoiTransactionTimestamp.toISOString(),
          }
        : { type: 'storedValueReverse', originalPoiTransactionId },
    );
  }

  storedValueDuplicate(card: StoredValueCard): Operation<StoredValueOperationResult> {
    return this.request({ type: 'storedValueDuplicate', card });
  }

  settle(options: SettleOptions = {}): Operation<SettlementResult> {
    const split = splitSettleOptions(options);
    const steps: StepHandlers = { kind: 'settlement', handlers: split.handlers };
    const request: OperationRequest = { type: 'settle' };
    if (Object.keys(split.options).length > 0) request.options = split.options;
    const declared = handledSteps(steps);
    if (declared.length > 0) request.handledSteps = declared;
    return this.request(request, steps);
  }

  refund(amount?: Money, handlers?: ReversalHandlers): Operation<RefundResult> {
    const steps = reversalSteps(handlers);
    const request: OperationRequest = { type: 'refund' };
    if (amount !== undefined) request.amount = amount;
    const declared = handledSteps(steps);
    if (declared.length > 0) request.handledSteps = declared;
    return this.request(request, steps);
  }

  refundUnlinked(amount: Money, handlers?: ReversalHandlers): Operation<RefundResult> {
    const steps = reversalSteps(handlers);
    const request: OperationRequest = { type: 'refundUnlinked', amount };
    const declared = handledSteps(steps);
    if (declared.length > 0) request.handledSteps = declared;
    return this.request(request, steps);
  }

  voidTransaction(
    originalSale?: OriginalSaleRecord,
    handlers?: ReversalHandlers,
  ): Operation<VoidResult> {
    const steps = reversalSteps(handlers);
    const request: OperationRequest = { type: 'voidTransaction' };
    if (originalSale !== undefined) request.originalSale = originalSale;
    const declared = handledSteps(steps);
    if (declared.length > 0) request.handledSteps = declared;
    return this.request(request, steps);
  }

  getTransactionStatus(
    originalServiceId: string,
    options?: TransactionStatusOptions,
  ): Operation<TransactionStatusResult> {
    return this.request(
      options
        ? { type: 'getTransactionStatus', originalServiceId, options }
        : { type: 'getTransactionStatus', originalServiceId },
    );
  }

  updateDisplay(content: Basket | DisplayPayload): Operation<void> {
    return 'cartId' in content
      ? this.request({ type: 'updateDisplay', basket: content })
      : this.request({ type: 'updateDisplay', display: content });
  }

  updateInputDisplay(display: DisplayPayload): Operation<void> {
    return this.request({ type: 'updateInputDisplay', display });
  }

  abort(): Promise<void> {
    return this.engine.abort(this.id, undefined, { idempotencyKey: newIdempotencyKey() });
  }

  forceEnd(reason: string): Promise<void> {
    return this.endWith({ type: 'forceEnd', reason });
  }

  terminal(): Terminal {
    return new TerminalImpl(this.engine, this.poiId, this.storeLocation);
  }
}
