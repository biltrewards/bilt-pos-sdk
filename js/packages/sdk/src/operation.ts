import type { OperationStatus, OperationType } from '@biltorg/pos-protocol';
import type { ReversalHandlers, SettlementHandlers } from './terminal-session';

export type { OperationStatus, OperationType };

/**
 * Narrows `ShopperSession.operations`: only operations in one of these states come back, so
 * `{ status: ['running', 'awaitingReply'] }` lists what is still in flight. No filter lists
 * every operation, newest first either way.
 */
export interface OperationFilter {
  readonly status?: readonly OperationStatus[];
}

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
   * The Nexo `ServiceID` the engine assigned when it accepted a single-request money operation
   * (`refundUnlinked`, the stored value changes) and sends its terminal request with; known once
   * the operation is accepted, before anything is sent. A retried `refundUnlinked` is sent with a
   * fresh id, reported through `beforeStep`. `undefined` for other operations, whose legs report
   * theirs through `beforeStep`.
   */
  readonly serviceId?: string;

  /**
   * Aborts the operation, as `TerminalShopperSession.abort()` does. The session continues. An
   * aborted payment stops at its next step boundary, reverses what it had committed and leaves
   * the basket intact so `settle()` may retry; prompts deliver their cancelled outcome;
   * money-moving operations (refunds, stored value) still deliver their real outcome when the
   * abort raced them. Voids, `end()` and `forceEnd()` cannot be aborted. Resolves once the
   * abort was issued, not when the operation finished; await the operation itself for that.
   */
  abort(): Promise<void>;

  /**
   * Registers the handlers this operation answers its steps and reports its movements with:
   * `SettlementHandlers` on a `settle`, `ReversalHandlers` on a `refund`, `refundUnlinked`
   * or `voidTransaction` — the calls a Java `SettlementFlow`/`ReversalFlow` takes, in one
   * object. Its main use is taking over an operation the page did not start — one
   * `operations()` listed or an attach adopted: such an operation holds a pending step open
   * until `guide` registers the handlers, which then answer it right away. Calling it on an
   * operation the page started replaces the handlers its method call registered. Without it
   * a pending step simply stays unanswered; the host's step deadline resolves it in time.
   */
  guide(handlers: SettlementHandlers | ReversalHandlers): void;
}
