import type { OperationStatus, OperationType } from '@bilt/pos-protocol';

export type { OperationStatus, OperationType };

/**
 * A session operation in flight: the JavaScript form of a Java `SessionResult`,
 * `SettlementFlow` or `ReversalFlow`.
 *
 * It is a `Promise` of the operation's result, so `await session.settle(..)` reads naturally and
 * a rejected operation throws a `SessionError`. It is also a handle: the register can hold it
 * to show progress from `status`, or to `abort()` it while it runs. Operations on one session run
 * one at a time in the order they were started, as on the Java session's operation thread; the
 * promise settles when the operation reaches `succeeded`, `failed` or `aborted`.
 *
 * Unlike Java's lazy results nothing here needs an `execute()`: the operation is on its way the
 * moment the method returns.
 */
export interface Operation<T> extends Promise<T> {
  /** The engine's identifier for the operation; stable across a page reload on engines that survive one. */
  readonly id: string;

  /** Which operation this is, e.g. `"settle"`. */
  readonly type: OperationType;

  /** Where the operation stands right now; updated as the engine reports progress. */
  readonly status: OperationStatus;

  /**
   * Aborts the operation, as `TerminalShopperSession.abort()` does. The session continues. An
   * aborted payment stops at its next step boundary, reverses what it had committed and leaves
   * the basket intact so `settle()` may retry; prompts deliver their cancelled outcome;
   * money-moving operations (refunds, stored value) still deliver their real outcome when the
   * abort raced them. Voids, `end()` and `forceEnd()` cannot be aborted. Resolves once the
   * abort was issued, not when the operation finished; await the operation itself for that.
   */
  abort(): Promise<void>;
}
