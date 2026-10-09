/**
 * `@bilt/pos-sdk`: the Bilt POS SDK for JavaScript. A framework-free core mirroring the Java
 * `ShopperSession` and `TerminalShopperSession`, running over an engine that speaks the Session
 * Protocol to a host — the Terminal Bridge on the register machine, or the Cloud Session
 * Service. React bindings live in `@bilt/pos-react`.
 *
 * ```ts
 * import { BiltPos } from '@bilt/pos-sdk';
 * import { localBridge } from '@bilt/pos-sdk/bridge';
 *
 * const pos = await BiltPos.connect(localBridge());
 * ```
 *
 * @packageDocumentation
 */
export { BiltPos } from './pos';
export type { BiltPosStatic, EngineCapabilities, EngineContext, EngineFactory } from './pos';
export type {
  BasketMutationBuilder,
  RenderingCapabilities,
  RetailMediaOptions,
  RetailMediaWidget,
  SessionBasket,
  SessionContextApi,
  SessionEventHandler,
  SessionKind,
  SessionMember,
  SessionState,
  ShopperSession,
  ShopperSessionOptions,
  TerminalSessionOptions,
  Unsubscribe,
  WidgetHandle,
  WidgetOptions,
  WidgetType,
} from './session';
export type {
  MaybePromise,
  ReversalHandlers,
  ReversalResult,
  SettleOptions,
  SettlementHandlers,
  SettlementRecoveryAction,
  StepInfo,
  TerminalShopperSession,
} from './terminal-session';
export type { Terminal, TerminalInfo } from './terminal';
export type { Operation, OperationStatus, OperationType } from './operation';
export { EngineError, EngineOutdatedError, EngineUnavailableError, SessionError } from './errors';

// The domain model is `@bilt/pos-protocol`'s; these re-exports save registers a second import.
export type {
  AbandonedSettlementRecord,
  AdInteraction,
  Basket,
  BasketChange,
  BasketDiscount,
  BasketItem,
  BasketItemType,
  BasketLineItem,
  CardAcquisitionOptions,
  CardAcquisitionResult,
  CheckoutPhase,
  ConfirmationOptions,
  Cta,
  DiagnosisResult,
  DisplayPayload,
  Duration,
  GiftCardPaymentResult,
  Health,
  IdentifyOptions,
  IdentifyResult,
  IdentifyStatus,
  InputOptions,
  MediaSpec,
  MediaType,
  Member,
  MemberIdResolver,
  MemberInput,
  MenuOptions,
  MenuSelection,
  Money,
  Offer,
  OriginalSaleRecord,
  PinOptions,
  PinResult,
  PlacementConfig,
  PointRedemptionResult,
  PrintPayload,
  RebateRedemptionResult,
  Receipt,
  ReconciliationResult,
  RefundResult,
  Rendering,
  ReversalDecision,
  ReversalStep,
  ReversedMovement,
  Reward,
  SessionContext,
  SessionErrorCode,
  SessionEventMap,
  SessionEventPayload,
  SessionEventType,
  SettlementContext,
  SettlementFailure,
  SettlementMovement,
  SettlementOptions,
  SettlementRecovery,
  SettlementResult,
  Signature,
  StoredValueBalance,
  StoredValueCard,
  StoredValueOperationResult,
  SurfaceKind,
  TransactionStatusOptions,
  TransactionStatusResult,
  VasData,
  VasService,
  VoidResult,
} from '@bilt/pos-protocol';
