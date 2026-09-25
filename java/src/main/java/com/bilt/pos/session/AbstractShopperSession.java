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

import com.bilt.pos.platform.BiltCredentials;
import com.bilt.pos.platform.BiltEnvironment;
import com.bilt.pos.session.basket.Basket;
import com.bilt.pos.session.basket.BasketChange;
import com.bilt.pos.session.basket.BasketItem;
import com.bilt.pos.session.basket.BasketMutation;
import com.bilt.pos.session.identity.IdentifyResult;
import com.bilt.pos.session.identity.Member;
import com.bilt.pos.session.internal.BasketEngine;
import com.bilt.pos.widget.SessionObserver;
import com.bilt.pos.widget.Widget;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * change atomically under the lock and hands the resulting {@link BasketChange} to {@link
 * #basketChanged(BasketChange)}. That hook, like {@link #contextChanged} and {@link
 * #memberChanged}, is the single dispatch point that fans the change out to the session's {@link
 * SessionObserver}s — the registered widgets and, on a terminal session, the customer-display push
 * — through {@link SessionObservers}, which delivers on the operation lane rather than under the
 * lock.
 */
abstract class AbstractShopperSession implements ShopperSession {

  private static final Logger LOGGER = Logger.getLogger(AbstractShopperSession.class.getName());

  private final String sessionId = UUID.randomUUID().toString();
  final ReentrantLock lock = new ReentrantLock();
  final SessionOperations operations;
  final SessionObservers observers;
  private final String saleId;
  private final String currency;
  private final String storeLocation;
  private BasketEngine basketEngine = new BasketEngine();
  private final SessionBasket basket;
  private final DefaultSessionContext context;
  private final List<Widget> widgets;
  private final SessionWidgetHost widgetHost;

  AbstractShopperSession(
      String saleId,
      String currency,
      String storeLocation,
      Executor callbackExecutor,
      Consumer<SessionError> onBackgroundError,
      String poiId,
      CheckoutPhase initialPhase,
      Map<String, String> initialAttributes,
      List<Widget> widgets,
      BiltCredentials credentials,
      BiltEnvironment environment) {
    this.operations = new SessionOperations(callbackExecutor, onBackgroundError);
    this.observers = new SessionObservers(operations);
    this.saleId = saleId;
    this.currency = currency;
    this.storeLocation = storeLocation;
    this.widgets = Collections.unmodifiableList(new ArrayList<>(widgets));
    this.widgetHost = new SessionWidgetHost(this, credentials, environment);
    for (Widget widget : this.widgets) {
      observers.add(widget);
    }
    this.context =
        new DefaultSessionContext(
            lock,
            saleId,
            currency,
            storeLocation,
            poiId,
            initialPhase,
            initialAttributes,
            this::ended,
            this::contextChanged);
    this.basket =
        new SessionBasket(
            new SessionBasket.Host() {
              @Override
              public Basket mutate(Consumer<BasketMutation> mutation, BasketChange.Source source) {
                return mutateBasket(mutation, source);
              }

              @Override
              public Basket replace(Basket snapshot) {
                return replaceBasket(engine -> engine.replace(snapshot));
              }

              @Override
              public Basket replace(List<BasketItem> items) {
                return replaceBasket(engine -> engine.replace(items));
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
   * Whether the current basket has been consumed by a settlement, so that a {@link
   * SessionBasket#replace(Basket)} starts a fresh basket under the {@link
   * #requireBasketClearable()} guards instead of failing under {@link #requireBasketMutable()}.
   * Read under the lock.
   */
  boolean basketConsumed() {
    return false;
  }

  /**
   * Called under the lock once a clear has installed the fresh engine and before its snapshot is
   * published, so a subclass can reset whatever state belonged to the previous basket.
   */
  void basketCleared() {}

  /**
   * Called under the lock with every non-empty change, whatever updater produced it: an incremental
   * mutation, a batch, a replacement, or a clear. An updater that leaves the basket as it was (a
   * {@code replace} with an identical snapshot, a {@code clear} of an empty basket) is not
   * reported. Fans the change out to the observers; a subclass that overrides it calls through.
   */
  void basketChanged(BasketChange change) {
    observers.basketChanged(change);
  }

  // ─── Context ───

  @Override
  public SessionContext context() {
    return context;
  }

  /**
   * Called under the lock with a fresh snapshot after every context write that changed something,
   * whether the POS made it or the session itself did (a terminal session's phase transitions). The
   * {@link #basketChanged(BasketChange)} counterpart for the context: fans the snapshot out to the
   * observers.
   */
  void contextChanged(SessionContextSnapshot snapshot) {
    observers.contextChanged(snapshot);
  }

  private Basket mutateBasket(Consumer<BasketMutation> mutation, BasketChange.Source source) {
    lock.lock();
    try {
      requireBasketMutable();
      Basket previous = basketEngine.snapshot();
      // Atomic: a mutation (or batch) that throws restores the basket.
      basketEngine.mutateAtomically(mutation);
      return publishBasketChange(previous, source);
    } finally {
      lock.unlock();
    }
  }

  private Basket replaceBasket(Consumer<BasketEngine> replacement) {
    lock.lock();
    try {
      Basket previous = basketEngine.snapshot();
      if (basketConsumed()) {
        // a settled basket is gone for editing purposes; a whole new
        // snapshot is the next cart, so this is clear() plus install,
        // validated before anything is swapped in
        requireBasketClearable();
        BasketEngine fresh = new BasketEngine();
        replacement.accept(fresh);
        basketEngine = fresh;
        basketCleared();
      } else {
        requireBasketMutable();
        replacement.accept(basketEngine);
      }
      return publishBasketChange(previous, BasketChange.Source.REPLACE);
    } finally {
      lock.unlock();
    }
  }

  private Basket clearBasket() {
    lock.lock();
    try {
      requireBasketClearable();
      Basket previous = basketEngine.snapshot();
      basketEngine = new BasketEngine();
      basketCleared();
      return publishBasketChange(previous, BasketChange.Source.CLEAR);
    } finally {
      lock.unlock();
    }
  }

  private Basket publishBasketChange(Basket previous, BasketChange.Source source) {
    Basket current = basketEngine.snapshot();
    BasketChange change = BasketChange.between(previous, current, source);
    if (!change.isEmpty()) {
      basketChanged(change);
    }
    return current;
  }

  // ─── Member ───

  /**
   * The member state, owned by the subclass because the resolver behind a pending member is
   * implementation-specific (the terminal session looks it up on the terminal; the local session
   * has nothing to look it up with yet).
   */
  abstract SessionMember memberState();

  @Override
  public Member member() {
    return memberState().current();
  }

  @Override
  public void member(Member member) {
    memberState().set(member);
  }

  @Override
  @Deprecated
  public IdentifyResult getMember() {
    return identifiedMember();
  }

  /** The resolved member as the settlement path consumes it; null when none or still pending. */
  IdentifyResult identifiedMember() {
    return memberState().identified();
  }

  /**
   * Called by {@link SessionMember} with every member change, whatever path made it — the {@link
   * #basketChanged(BasketChange)} counterpart for the member: fans it out to the observers.
   */
  void memberChanged(Member member) {
    observers.memberChanged(member);
  }

  // ─── Widgets ───

  @Override
  public <W extends Widget> W widget(Class<W> type) {
    Objects.requireNonNull(type, "type");
    W found = null;
    for (Widget widget : widgets) {
      if (!type.isInstance(widget)) {
        continue;
      }
      if (found != null) {
        throw new IllegalArgumentException(
            "more than one widget of type "
                + type.getName()
                + " is registered on this session; use widgets() to pick one");
      }
      found = type.cast(widget);
    }
    if (found == null) {
      throw new IllegalArgumentException(
          "no widget of type "
              + type.getName()
              + " is registered on this session; register it with widget(..) on the builder");
    }
    return found;
  }

  @Override
  public List<Widget> widgets() {
    return widgets;
  }

  /**
   * The start of the observers' world, for the subclass's {@code start()} path: attaches every
   * widget on the operation lane — waiting for it, so the widgets are bound before {@code start()}
   * yields the session — then queues {@code started} and, when the session begins with a member
   * attached, {@code memberChanged} with that member. A widget whose {@code attach} throws is
   * reported through {@code onBackgroundError} and dropped from delivery; the session goes on
   * without it.
   */
  void announceStarted() {
    if (!widgets.isEmpty()) {
      operations.callOrdered(
          () -> {
            for (Widget widget : widgets) {
              try {
                widget.attach(widgetHost);
              } catch (RuntimeException e) {
                observers.remove(widget);
                operations.backgroundError(
                    "attaching the " + widget.getClass().getSimpleName() + " widget", e);
              }
            }
            return null;
          });
    }
    observers.started(context().snapshot());
    Member seed = member();
    if (seed != null) {
      observers.memberChanged(seed);
    }
  }

  /**
   * The end of the observers' world, for the subclass's end path: queues {@code ended} for every
   * observer, followed on the lane by {@code detach} of every widget still attached and the release
   * of the platform client they shared. Must run before {@link SessionOperations#shutdown()}.
   */
  void announceEnded() {
    observers.ended(
        () -> {
          List<SessionObserver> attached = observers.observers();
          for (Widget widget : widgets) {
            if (!attached.contains(widget)) {
              continue;
            }
            try {
              widget.detach();
            } catch (RuntimeException e) {
              operations.backgroundError(
                  "detaching the " + widget.getClass().getSimpleName() + " widget", e);
            }
          }
          widgetHost.close();
        });
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
