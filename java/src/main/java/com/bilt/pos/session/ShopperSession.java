/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.session;

import com.bilt.pos.session.identity.IdentifyResult;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * One shopper's visit at a lane, with or without a terminal.
 *
 * <p>A shopper session is the unit the register opens when a customer steps up and ends when they
 * walk away. It owns the basket being rung — {@link #basket()} — and the member identified for the
 * visit, if any — {@link #getMember()} — and carries the lane's identifiers: the register's sale
 * ID, the currency, and the store location. What it does not have is a device or a settlement:
 * everything that needs a Bilt terminal — identification prompts, customer input, the customer
 * display, stored value, settlement, refunds, and voids — lives on {@link TerminalShopperSession}.
 * A lane with a terminal uses {@link TerminalShopperSession#builder()}; a lane without one, or a
 * register that only wants the basket model and member state, uses the local session that {@link
 * #builder()} creates.
 *
 * <p>Basket mutations are pure local compute: each returns the updated snapshot synchronously and
 * never blocks on a device. A session can ring several baskets in sequence — {@link
 * SessionBasket#clear()} is the boundary between them.
 *
 * <p>A session ends once, with {@link #end()}, and cannot be restarted or reused afterwards; its
 * basket refuses changes from then on. Like every session operation, {@code end()} is lazy: it
 * returns a {@link SessionResult} that does nothing until {@code execute()} (asynchronous, outcome
 * through the registered handlers) or {@code executeSync()} (blocking) is invoked. Sessions are
 * {@link AutoCloseable}, so try-with-resources ends them even on exception paths — {@link #close()}
 * is a best-effort {@code end()} that logs rather than throws.
 *
 * <pre>{@code
 * try (ShopperSession session = ShopperSession.builder()
 *         .saleId("POS-LANE-3")
 *         .currency("USD")
 *         .start()) {
 *     session.basket().addItem(
 *             BasketItem.sale("KRK-CNDL-LRG-VAN", "Large Vanilla Candle", 1, new BigDecimal("24.99")));
 *     ...
 * }
 * }</pre>
 *
 * <p>Sessions are intended for use from a single register thread.
 */
public interface ShopperSession extends AutoCloseable {

  /** A builder for a local session — one with no terminal attached; see {@link Builder}. */
  static Builder builder() {
    return new Builder();
  }

  /** Unique identifier of this session instance. */
  String getSessionId();

  /** The register's identifier for this lane, sent as {@code SaleID} by a terminal session. */
  String getSaleId();

  /** ISO 4217 currency code used by this session. */
  String getCurrency();

  /** Store location identifier, or {@code null} if not configured. */
  String getStoreLocation();

  /**
   * The session's basket: item and tax mutations, batch edits, and snapshots. Mutations are refused
   * after the session has ended; a terminal session also refuses them while money is moving and
   * after the current basket has settled. Call {@link SessionBasket#clear()} to begin another
   * basket in the same session. Each mutation returns the updated snapshot without touching any
   * device.
   */
  SessionBasket basket();

  /**
   * The identified member, or {@code null} for a guest checkout. A local session has no way to
   * identify anyone yet, so this stays {@code null} there; on a terminal session, identification
   * never forces the customer — if they opted out, this stays {@code null} too.
   */
  IdentifyResult getMember();

  /**
   * Ends the session. After it succeeds no session operation is allowed and the basket is frozen;
   * create a new session for the next shopper. A terminal session also tells the terminal to
   * discard the session-scoped data it accumulated, and may refuse to end while money movement is
   * unresolved — see {@link TerminalShopperSession#end()}.
   */
  SessionResult<Void> end();

  /**
   * Best-effort {@link #end()} for try-with-resources: a failure to end, or a lifecycle refusal, is
   * logged, not thrown, and an already-ended session is left alone. Registers that need to react to
   * a failed end should call {@code end()} directly. It never bypasses the guards that make {@code
   * end()} refuse: automatic resource cleanup must not silently abandon financial recovery.
   *
   * <p>Blocking, and queued behind any in-flight operation — so with a callback executor
   * configured, never call it from that executor's thread while operations may be in flight: the
   * in-flight operation may need this thread for its handlers before it can finish, and both would
   * wait forever. UI-driven teardown should use {@code end().execute()} with an {@code onComplete}
   * instead; close() is for try-with-resources and process-exit paths.
   */
  @Override
  void close();

  /**
   * Builder for a local {@link ShopperSession}: a session with the basket, member state, and
   * lifecycle of every shopper session, but no terminal. Nothing is announced anywhere, so {@link
   * #start()} hands the session out directly.
   */
  final class Builder {

    String saleId;
    String currency;
    String storeLocation;
    Executor callbackExecutor;
    Consumer<SessionError> onBackgroundError;

    private Builder() {}

    /** The register's identifier for this lane. Required. */
    public Builder saleId(String saleId) {
      this.saleId = saleId;
      return this;
    }

    /** ISO 4217 currency code, e.g. {@code "USD"}. Required. */
    public Builder currency(String currency) {
      this.currency = currency;
      return this;
    }

    /** Store location identifier. Optional. */
    public Builder storeLocation(String storeLocation) {
      this.storeLocation = storeLocation;
      return this;
    }

    /**
     * Where asynchronously executed operations deliver their handlers — e.g. an Android main-thread
     * executor so handlers may touch UI directly. Applies to {@code execute()}; {@code
     * executeSync()} and the blocking accessors are unaffected. Overridable per call with {@code
     * callbackOn(executor)}. Without one, handlers run directly on the session's operation thread
     * and must be fast, non-blocking, and must never synchronously invoke another session
     * operation.
     */
    public Builder callbackExecutor(Executor callbackExecutor) {
      this.callbackExecutor = callbackExecutor;
      return this;
    }

    /**
     * Handler for failures of work the session performs on its own behalf, with no result object to
     * report through. A local session has no customer display to refresh, so nothing reports here
     * today; it is accepted so a register configures local and terminal sessions alike. Delivered
     * through the {@link #callbackExecutor(Executor) callbackExecutor} when one is configured,
     * directly on the failing thread otherwise.
     */
    public Builder onBackgroundError(Consumer<SessionError> onBackgroundError) {
      this.onBackgroundError = onBackgroundError;
      return this;
    }

    /**
     * Validates the configuration and returns the session, ready to ring. There is no device to
     * acknowledge a local session, so unlike {@link TerminalShopperSession.Builder#start()} this
     * returns the session itself rather than a lazy {@link SessionResult}.
     *
     * @throws IllegalStateException if a required field is missing
     */
    public ShopperSession start() {
      if (saleId == null || saleId.isEmpty()) {
        throw new IllegalStateException("saleId is required");
      }
      if (currency == null || currency.isEmpty()) {
        throw new IllegalStateException("currency is required");
      }
      return new LocalShopperSession(this);
    }
  }
}
