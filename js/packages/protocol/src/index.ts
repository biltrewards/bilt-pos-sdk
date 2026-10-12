/**
 * `@bilt/pos-protocol`: the TypeScript form of the Bilt POS Session Protocol, generated from
 * `schema/session-protocol/openapi.yaml`. It is the domain model every JavaScript engine and
 * the SDK core share — `Basket`, `Member`, `SettlementResult`, `Rendering`, the operation and
 * event unions — plus a typed `fetch` client for talking to a host directly.
 *
 * @packageDocumentation
 */
import type { components, operations, paths } from './generated/openapi';

export type { components, operations, paths };
export type {
  SessionEvent,
  SessionEventMap,
  SessionEventOf,
  SessionEventPayload,
  SessionEventType,
} from './events';
export { createProtocolClient, type ProtocolClient } from './client';

/** The protocol major version this package was generated from; hosts list theirs in `GET /health`. */
export const PROTOCOL_VERSION = '2';

/** The spec's `components.schemas`, by name. */
export type Schemas = components['schemas'];

/** A named schema, e.g. `Schema<'Basket'>`. */
export type Schema<N extends keyof Schemas> = Schemas[N];

export type Money = Schemas['Money'];
export type Duration = Schemas['Duration'];
export type SessionError = Schemas['SessionError'];
export type SessionErrorCode = Schemas['SessionErrorCode'];
export type Health = Schemas['Health'];
export type TerminalInfo = Schemas['TerminalInfo'];
export type DiagnosisResult = Schemas['DiagnosisResult'];
export type ReconciliationResult = Schemas['ReconciliationResult'];
export type PrintPayload = Schemas['PrintPayload'];
export type SoundRequest = Schemas['SoundRequest'];
export type Receipt = Schemas['Receipt'];
export type DisplayPayload = Schemas['DisplayPayload'];

export type Session = Schemas['Session'];
export type CreateSessionRequest = Schemas['CreateSessionRequest'];
export type SessionContext = Schemas['SessionContext'];
export type SessionContextPatch = Schemas['SessionContextPatch'];
export type CheckoutPhase = Schemas['CheckoutPhase'];
export type ForceEndRequest = Schemas['ForceEndRequest'];

export type Basket = Schemas['Basket'];
export type BasketLineItem = Schemas['BasketLineItem'];
export type BasketItem = Schemas['BasketItem'];
export type BasketDiscount = Schemas['BasketDiscount'];
export type BasketItemType = Schemas['BasketItemType'];
export type BasketChange = Schemas['BasketChange'];
export type BasketMutation = Schemas['BasketMutation'];
export type AddBasketItemRequest = Schemas['AddBasketItemRequest'];
export type BasketItemPatch = Schemas['BasketItemPatch'];
export type ReplaceBasketRequest = Schemas['ReplaceBasketRequest'];
export type BasketMutationsRequest = Schemas['BasketMutationsRequest'];
export type TaxTotalRequest = Schemas['TaxTotalRequest'];

export type Member = Schemas['Member'];
export type MemberInput = Schemas['MemberInput'];
export type MemberIdResolver = Schemas['MemberIdResolver'];
export type IdentifyStatus = Schemas['IdentifyStatus'];
export type IdentifyResult = Schemas['IdentifyResult'];
export type IdentifyOptions = Schemas['IdentifyOptions'];
export type VasData = Schemas['VasData'];
export type VasService = Schemas['VasService'];
export type Reward = Schemas['Reward'];
export type CardAcquisitionOptions = Schemas['CardAcquisitionOptions'];
export type CardAcquisitionResult = Schemas['CardAcquisitionResult'];

export type InputOptions = Schemas['InputOptions'];
export type ConfirmationOptions = Schemas['ConfirmationOptions'];
export type MenuOptions = Schemas['MenuOptions'];
export type MenuSelection = Schemas['MenuSelection'];
export type PinOptions = Schemas['PinOptions'];
export type PinResult = Schemas['PinResult'];
export type Signature = Schemas['Signature'];

export type StoredValueCard = Schemas['StoredValueCard'];
export type StoredValueBalance = Schemas['StoredValueBalance'];
export type StoredValueOperationResult = Schemas['StoredValueOperationResult'];

export type SettlementOptions = Schemas['SettlementOptions'];
export type SettlementResult = Schemas['SettlementResult'];
export type SettlementMovement = Schemas['SettlementMovement'];
export type SettlementFailure = Schemas['SettlementFailure'];
export type SettlementRecovery = Schemas['SettlementRecovery'];
export type SettlementContext = Schemas['SettlementContext'];
export type AbandonedSettlementRecord = Schemas['AbandonedSettlementRecord'];
export type OriginalSaleRecord = Schemas['OriginalSaleRecord'];

export type RebateRedemptionResult = Schemas['RebateRedemptionResult'];
export type PointRedemptionResult = Schemas['PointRedemptionResult'];
export type GiftCardPaymentResult = Schemas['GiftCardPaymentResult'];
export type ReversedMovement = Schemas['ReversedMovement'];

export type RefundResult = Schemas['RefundResult'];
export type VoidResult = Schemas['VoidResult'];
export type ReversalDecision = Schemas['ReversalDecision'];
export type ReversalStep = Schemas['ReversalStep'];
export type TransactionStatusOptions = Schemas['TransactionStatusOptions'];
export type TransactionStatusResult = Schemas['TransactionStatusResult'];

export type OperationRequest = Schemas['OperationRequest'];
export type Operation = Schemas['Operation'];
export type OperationStatus = Schemas['OperationStatus'];
export type OperationStep = Schemas['OperationStep'];
export type StepKind = Schemas['StepKind'];
export type StepReply = Schemas['StepReply'];

export type WidgetConfig = Schemas['WidgetConfig'];
export type WidgetState = Schemas['WidgetState'];
export type WidgetAction = Schemas['WidgetAction'];
export type Rendering = Schemas['Rendering'];
export type Offer = Schemas['Offer'];
export type AdInteraction = Schemas['AdInteraction'];
export type Cta = Schemas['Cta'];
export type MediaSpec = Schemas['MediaSpec'];
export type MediaType = Schemas['MediaType'];
export type SurfaceKind = Schemas['SurfaceKind'];
export type Action = Schemas['Action'];
export type ClientCapabilities = Schemas['ClientCapabilities'];
export type PlacementConfig = Schemas['PlacementConfig'];

/** Every operation `type`, the request types plus the lifecycle signals `end` and `forceEnd`. */
export type OperationType = Operation['type'];

/**
 * The member of a `type`-discriminated union whose `type` admits `T`. Several operation
 * schemas cover more than one `type` (the stored value operations, the PIN prompts), so a
 * plain `Extract` would find nothing for them.
 */
type ByType<U, T extends string> = U extends { type: infer K } ? (T extends K ? U : never) : never;

/** The request body that starts an operation of the given type. */
export type OperationRequestOf<T extends OperationRequest['type']> = ByType<OperationRequest, T>;

/** The operation resource for a given type, with its `result` typed. */
export type OperationOf<T extends OperationType> = ByType<Operation, T>;

/** What an operation of the given type yields once `succeeded`; `undefined` for the void ones. */
export type OperationResult<T extends OperationType> = 'result' extends keyof OperationOf<T>
  ? NonNullable<OperationOf<T>['result']>
  : undefined;

/** One step kind's event payload, e.g. `OperationStepOf<'TOTAL_REQUIRED'>`. */
export type OperationStepOf<K extends StepKind> = Extract<OperationStep, { kind: K }>;

/** The request paths and their operation ids, for clients that build URLs themselves. */
export type { paths as ProtocolPaths, operations as ProtocolOperations };
