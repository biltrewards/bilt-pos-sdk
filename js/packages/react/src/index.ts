/**
 * `@bilt/pos-react`: React bindings for the Bilt POS SDK. A `BiltPosProvider` holds the
 * connection, hooks turn a session's basket, member, context and settlement into React state,
 * and `RetailMediaSurface` draws a retail-media placement in the DOM. Nothing here knows which
 * engine is behind `BiltPos`; the Terminal Bridge pieces (`useBridge`, `InstallBridgePrompt`,
 * `BridgeGate`) live in `@bilt/pos-react/bridge`.
 *
 * @packageDocumentation
 */
export {
  BiltPosProvider,
  useBiltPos,
  type BiltPosConnection,
  type BiltPosConnectionStatus,
  type BiltPosProviderProps,
} from './provider';
export {
  useShopperSession,
  useTerminalSession,
  type SessionStatus,
  type UseSessionOptions,
  type UseSessionResult,
} from './use-session';
export { useSessionEvent } from './use-session-event';
export { useBasket, type BasketActions, type UseBasketResult } from './use-basket';
export { useMember, type UseMemberResult } from './use-member';
export { useSessionContext, type UseSessionContextResult } from './use-session-context';
export {
  useOperation,
  type TrackedOperationStatus,
  type UseOperationResult,
} from './use-operation';
export {
  useSettlement,
  type InteractiveStepKind,
  type PendingBeforeStep,
  type PendingRecoveryStep,
  type PendingSettlementStep,
  type PendingStepBase,
  type PendingTotalStep,
  type SettlementStatus,
  type SettlementStepReply,
  type UseSettlementOptions,
  type UseSettlementResult,
} from './use-settlement';
export { RetailMediaSurface, type RetailMediaSurfaceProps } from './retail-media-surface';
