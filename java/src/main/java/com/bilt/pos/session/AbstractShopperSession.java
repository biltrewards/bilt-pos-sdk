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

import com.bilt.pos.session.basket.Basket;
import com.bilt.pos.session.basket.BasketMutation;
import com.bilt.pos.session.identity.IdentifyResult;
import com.bilt.pos.session.identity.IdentifyStatus;
import com.bilt.pos.session.internal.BasketEngine;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * The device-independent half of a shopper session, shared by the local and terminal
 * implementations: the lane identifiers, the basket engine behind {@link #basket()}, the identified
 * member, the operation machinery, and the session lock every state transition takes.
 *
 * <p>Subclasses own the lifecycle and decide when the basket may change; this class applies each
 * change atomically under the lock and hands the fresh snapshot to {@link #basketChanged(Basket)}
 * for whatever the subclass does with it (the terminal session pushes it to the customer display).
 */
abstract class AbstractShopperSession implements ShopperSession {

  private static final Logger LOGGER = Logger.getLogger(AbstractShopperSession.class.getName());

  private final String sessionId = UUID.randomUUID().toString();
  final ReentrantLock lock = new ReentrantLock();
  final SessionOperations operations;
  private final String saleId;
  private final String currency;
  private final String storeLocation;
  private BasketEngine basketEngine = new BasketEngine();
  private final SessionBasket basket;
  private volatile IdentifyResult member;

  AbstractShopperSession(
      String saleId,
      String currency,
      String storeLocation,
      Executor callbackExecutor,
      Consumer<SessionError> onBackgroundError) {
    this.operations = new SessionOperations(callbackExecutor, onBackgroundError);
    this.saleId = saleId;
    this.currency = currency;
    this.storeLocation = storeLocation;
    this.basket =
        new SessionBasket(
            new SessionBasket.Host() {
              @Override
              public Basket mutate(Consumer<BasketMutation> mutation) {
                return mutateBasket(mutation);
              }

              @Override
              public Basket snapshot() {
                lock.lock();
                try {
                  return basketEngine.snapshot();
                } finally {
                  lock.unlock();
                }
              }

              @Override
              public Basket clear() {
                return clearBasket();
              }
            });
  }

  @Override
  public String getSessionId() {
    return sessionId;
  }

  @Override
  public String getSaleId() {
    return saleId;
  }

  @Override
  public String getCurrency() {
    return currency;
  }

  @Override
  public String getStoreLocation() {
    return storeLocation;
  }

  // ─── Basket ───

  @Override
  public SessionBasket basket() {
    return basket;
  }

  /** The live basket engine; read only under {@link #lock}. */
  BasketEngine basketEngine() {
    return basketEngine;
  }

  /**
   * Refuses, with {@code IllegalStateException}, a mutation the session cannot accept in its
   * current state. Called under the lock before the mutation is applied.
   */
  abstract void requireBasketMutable();

  /** The {@link #requireBasketMutable()} counterpart for {@link SessionBasket#clear()}. */
  abstract void requireBasketClearable();

  /**
   * Called under the lock once a clear has installed the fresh engine and before its snapshot is
   * published, so a subclass can reset whatever state belonged to the previous basket.
   */
  void basketCleared() {}

  /** Called under the lock with every new snapshot, whether from a mutation or a clear. */
  void basketChanged(Basket snapshot) {}

  private Basket mutateBasket(Consumer<BasketMutation> mutation) {
    lock.lock();
    try {
      requireBasketMutable();
      // Atomic: a mutation (or batch) that throws restores the basket.
      basketEngine.mutateAtomically(mutation);
      Basket snapshot = basketEngine.snapshot();
      basketChanged(snapshot);
      return snapshot;
    } finally {
      lock.unlock();
    }
  }

  private Basket clearBasket() {
    lock.lock();
    try {
      requireBasketClearable();
      basketEngine = new BasketEngine();
      basketCleared();
      Basket snapshot = basketEngine.snapshot();
      basketChanged(snapshot);
      return snapshot;
    } finally {
      lock.unlock();
    }
  }

  // ─── Member ───

  @Override
  public IdentifyResult getMember() {
    IdentifyResult current = member;
    return current != null && current.getStatus() == IdentifyStatus.FOUND ? current : null;
  }

  /**
   * Applies an identification outcome to the session. The latest completed attempt wins: {@code
   * FOUND} attaches the member; {@code NOT_FOUND} and {@code SUSPENDED} are affirmative "no usable
   * member" outcomes and detach any previously identified member (so a re-identify cannot leave
   * loyalty running against a stale account). {@code CANCELLED} only means the customer dismissed
   * this prompt — a prior identification stands. Called under the lock.
   */
  void applyIdentification(IdentifyResult result) {
    if (result.getStatus() == IdentifyStatus.FOUND) {
      member = result;
    } else if (result.getStatus() != IdentifyStatus.CANCELLED) {
      member = null;
    }
  }

  // ─── Lifecycle ───

  /** True once the session has ended and nothing can run on it any more. */
  abstract boolean ended();

  @Override
  public final void close() {
    if (ended()) {
      return;
    }
    // synchronous deliberately: close() runs on teardown paths (often
    // try-with-resources or process exit) where a queued async end
    // would be lost with the closing scope
    end().onError(e -> LOGGER.warning("close() could not end the session: " + e)).executeSync();
  }

  // ─── Operations ───

  <T> SessionResult<T> operation(String name, Supplier<T> body) {
    return operations.operation(name, body);
  }

  /** The uniform guard failure for operations the session cannot honor. */
  static SessionException invalidState(String message) {
    return new SessionException(new SessionError(SessionErrorCode.INVALID_STATE, message));
  }
}
