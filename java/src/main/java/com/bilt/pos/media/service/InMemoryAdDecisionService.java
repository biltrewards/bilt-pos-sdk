/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.media.service;

import com.bilt.pos.media.AdInteraction;
import com.bilt.pos.media.Capabilities;
import com.bilt.pos.media.Offer;
import com.bilt.pos.media.Placement;
import com.bilt.pos.widget.Action;
import com.bilt.pos.widget.Cta;
import com.bilt.pos.widget.Rendering;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An {@link AdDecisionService} that lives entirely in memory, for tests and the emulator.
 *
 * <p>The fake is scripted rather than clever. {@link #onPlacement(Placement, Rendering...)} rotates
 * through the given renderings on successive decisions for that placement; {@link
 * #onPlacement(Placement, Function)} lets a rule pick from the latest snapshot instead. Decisions
 * honour {@link Capabilities}: a rendering whose media type or CTA actions the caller did not
 * declare is refused and the decision is empty, as the contract requires. {@link #latency} adds an
 * artificial delay so timeout handling can be exercised — a latency longer than the caller's
 * timeout waits out the timeout and answers empty. {@link #failNext} injects one failure: the next
 * {@code decide} answers empty, the next {@code validateAction} answers rejected, and the next
 * {@code registerSession}, {@code updateSession} or {@code report} throws it, whichever comes
 * first.
 *
 * <p>Tokens are the ones on the scripted renderings; the fake remembers which tokens it served to
 * which session for which creative and action, and {@link #validateAction} rejects anything else.
 * An {@code APPLY_OFFER} token validates only if {@link #offerFor} registered an offer for it. A
 * token is single-use: once accepted it is rejected as already used until a later {@code decide}
 * serves it again, because the scripted renderings reuse their tokens where the platform would
 * issue fresh ones.
 *
 * <p>Everything the fake sees is recorded: {@link #snapshots} (registration first, then every
 * update), {@link #served}, {@link #reports}. Events reach subscribers only when a test injects
 * them with {@link #emit(SessionHandle, Offer)} or {@link #emit(SessionHandle, AdInteraction)};
 * delivery is synchronous on the emitting thread.
 *
 * <p>Thread-safe.
 */
public final class InMemoryAdDecisionService implements AdDecisionService {

  private static final Logger LOGGER = Logger.getLogger(InMemoryAdDecisionService.class.getName());

  private final Object lock = new Object();
  private final Map<Placement, Function<AdSessionSnapshot, Optional<Rendering>>> scripts =
      new HashMap<>();
  private final Map<String, Offer> offersByToken = new HashMap<>();
  private final Map<SessionHandle, Session> sessions = new LinkedHashMap<>();
  private final List<AdInteraction> allReports = new ArrayList<>();
  private final List<RuntimeException> swallowedFailures = new ArrayList<>();
  private final AtomicInteger handleSequence = new AtomicInteger();
  private Duration latency = Duration.ZERO;
  private RuntimeException pendingFailure;

  private static final class Session {
    final List<AdSessionSnapshot> snapshots = new ArrayList<>();
    final List<Rendering> served = new ArrayList<>();
    final List<AdInteraction> reports = new ArrayList<>();
    final Map<String, String> creativeByToken = new HashMap<>();
    final Map<String, Action> actionByToken = new HashMap<>();
    final Set<String> usedTokens = new HashSet<>();
    final List<AdEventListener> listeners = new CopyOnWriteArrayList<>();

    AdSessionSnapshot latest() {
      return snapshots.get(snapshots.size() - 1);
    }
  }

  // ── scripting ────────────────────────────────────────────────────────────────────────────────

  /**
   * Serves {@code renderings} in rotation for {@code placement}; no renderings clears the script.
   */
  public InMemoryAdDecisionService onPlacement(Placement placement, Rendering... renderings) {
    Objects.requireNonNull(placement, "placement");
    List<Rendering> rotation =
        Collections.unmodifiableList(new ArrayList<>(Arrays.asList(renderings)));
    if (rotation.isEmpty()) {
      synchronized (lock) {
        scripts.remove(placement);
      }
      return this;
    }
    AtomicInteger next = new AtomicInteger();
    return onPlacement(
        placement,
        snapshot ->
            Optional.of(rotation.get(Math.floorMod(next.getAndIncrement(), rotation.size()))));
  }

  /**
   * Lets {@code rule} pick a rendering for {@code placement} from the session's latest snapshot.
   */
  public InMemoryAdDecisionService onPlacement(
      Placement placement, Function<AdSessionSnapshot, Optional<Rendering>> rule) {
    Objects.requireNonNull(placement, "placement");
    Objects.requireNonNull(rule, "rule");
    synchronized (lock) {
      scripts.put(placement, rule);
    }
    return this;
  }

  /**
   * Registers the offer an {@code APPLY_OFFER} tap carrying {@code token} will be answered with.
   */
  public InMemoryAdDecisionService offerFor(String token, Offer offer) {
    Objects.requireNonNull(token, "token");
    Objects.requireNonNull(offer, "offer");
    synchronized (lock) {
      offersByToken.put(token, offer);
    }
    return this;
  }

  /** Delays every decision by {@code latency}; a latency beyond the caller's timeout is a miss. */
  public InMemoryAdDecisionService latency(Duration latency) {
    Objects.requireNonNull(latency, "latency");
    if (latency.isNegative()) {
      throw new IllegalArgumentException("latency must not be negative");
    }
    synchronized (lock) {
      this.latency = latency;
    }
    return this;
  }

  /** Makes the next call fail as described in the class documentation. */
  public InMemoryAdDecisionService failNext(RuntimeException failure) {
    Objects.requireNonNull(failure, "failure");
    synchronized (lock) {
      pendingFailure = failure;
    }
    return this;
  }

  // ── recordings ───────────────────────────────────────────────────────────────────────────────

  /** Every snapshot seen for {@code handle}: the registration first, then each update, in order. */
  public List<AdSessionSnapshot> snapshots(SessionHandle handle) {
    synchronized (lock) {
      return copyOrEmpty(sessions.get(handle), s -> s.snapshots);
    }
  }

  /** Every rendering served to {@code handle}, in order. */
  public List<Rendering> served(SessionHandle handle) {
    synchronized (lock) {
      return copyOrEmpty(sessions.get(handle), s -> s.served);
    }
  }

  /** Every interaction reported for {@code handle}, in order. */
  public List<AdInteraction> reports(SessionHandle handle) {
    synchronized (lock) {
      return copyOrEmpty(sessions.get(handle), s -> s.reports);
    }
  }

  /** Every interaction reported for any session, in order. */
  public List<AdInteraction> reports() {
    synchronized (lock) {
      return new ArrayList<>(allReports);
    }
  }

  /** Injected failures that {@code decide} or {@code validateAction} absorbed into a miss. */
  public List<RuntimeException> swallowedFailures() {
    synchronized (lock) {
      return new ArrayList<>(swallowedFailures);
    }
  }

  /** Whether {@code handle} was issued and has not been closed. */
  public boolean isOpen(SessionHandle handle) {
    synchronized (lock) {
      return sessions.containsKey(handle);
    }
  }

  private static <T> List<T> copyOrEmpty(Session session, Function<Session, List<T>> field) {
    return session == null ? Collections.emptyList() : new ArrayList<>(field.apply(session));
  }

  // ── event injection ──────────────────────────────────────────────────────────────────────────

  /** Delivers {@code offer} to every subscriber of {@code handle}, on the calling thread. */
  public void emit(SessionHandle handle, Offer offer) {
    Objects.requireNonNull(offer, "offer");
    for (AdEventListener listener : listenersOf(handle)) {
      deliver(listener, () -> listener.onOffer(offer));
    }
  }

  /** Delivers {@code interaction} to every subscriber of {@code handle}, on the calling thread. */
  public void emit(SessionHandle handle, AdInteraction interaction) {
    Objects.requireNonNull(interaction, "interaction");
    for (AdEventListener listener : listenersOf(handle)) {
      deliver(listener, () -> listener.onInteraction(interaction));
    }
  }

  private List<AdEventListener> listenersOf(SessionHandle handle) {
    synchronized (lock) {
      Session session = sessions.get(handle);
      return session == null ? Collections.emptyList() : new ArrayList<>(session.listeners);
    }
  }

  private static void deliver(AdEventListener listener, Runnable callback) {
    try {
      callback.run();
    } catch (RuntimeException e) {
      LOGGER.log(Level.WARNING, "AdEventListener " + listener + " threw; event dropped", e);
    }
  }

  // ── AdDecisionService ────────────────────────────────────────────────────────────────────────

  @Override
  public SessionHandle registerSession(AdSessionSnapshot snapshot) {
    Objects.requireNonNull(snapshot, "snapshot");
    synchronized (lock) {
      throwPendingFailure();
      SessionHandle handle = SessionHandle.of("fake-" + handleSequence.incrementAndGet());
      Session session = new Session();
      session.snapshots.add(snapshot);
      sessions.put(handle, session);
      return handle;
    }
  }

  @Override
  public void updateSession(SessionHandle handle, AdSessionSnapshot snapshot) {
    Objects.requireNonNull(snapshot, "snapshot");
    synchronized (lock) {
      Session session = requireSession(handle);
      throwPendingFailure();
      session.snapshots.add(snapshot);
    }
  }

  @Override
  public Optional<Rendering> decide(
      SessionHandle handle, Placement placement, Capabilities capabilities, Duration timeout) {
    Objects.requireNonNull(placement, "placement");
    Objects.requireNonNull(capabilities, "capabilities");
    Objects.requireNonNull(timeout, "timeout");
    Optional<Rendering> candidate;
    Duration wait;
    boolean timesOut;
    synchronized (lock) {
      Session session = requireSession(handle);
      if (swallowPendingFailure()) {
        return Optional.empty();
      }
      Function<AdSessionSnapshot, Optional<Rendering>> script = scripts.get(placement);
      candidate = script == null ? Optional.empty() : script.apply(session.latest());
      timesOut = latency.compareTo(timeout) > 0;
      wait = timesOut ? timeout : latency;
    }
    if (!sleep(wait) || timesOut || !candidate.isPresent()) {
      return Optional.empty();
    }
    Rendering rendering = candidate.get();
    if (!capabilities.supports(rendering)) {
      return Optional.empty();
    }
    synchronized (lock) {
      Session session = sessions.get(handle);
      if (session == null) {
        return Optional.empty();
      }
      rememberToken(session, rendering, rendering.getCta());
      rememberToken(session, rendering, rendering.getSecondary());
      session.served.add(rendering);
    }
    return Optional.of(rendering);
  }

  @Override
  public ActionOutcome validateAction(SessionHandle handle, Cta cta, String creativeId) {
    Objects.requireNonNull(cta, "cta");
    Objects.requireNonNull(creativeId, "creativeId");
    synchronized (lock) {
      Session session = requireSession(handle);
      RuntimeException failure = takePendingFailure();
      if (failure != null) {
        swallowedFailures.add(failure);
        return ActionOutcome.rejected("injected failure: " + failure.getMessage());
      }
      String issuedFor = session.creativeByToken.get(cta.getToken());
      if (issuedFor == null) {
        return ActionOutcome.rejected("unknown token for this session");
      }
      if (!issuedFor.equals(creativeId)) {
        return ActionOutcome.rejected("token was issued for creative '" + issuedFor + "'");
      }
      Action issuedAction = session.actionByToken.get(cta.getToken());
      if (issuedAction != cta.getAction()) {
        return ActionOutcome.rejected("token was issued for action " + issuedAction);
      }
      if (session.usedTokens.contains(cta.getToken())) {
        return ActionOutcome.rejected("token already used");
      }
      Offer offer = null;
      if (cta.getAction() == Action.APPLY_OFFER) {
        offer = offersByToken.get(cta.getToken());
        if (offer == null) {
          return ActionOutcome.rejected("no offer registered for token");
        }
      }
      session.usedTokens.add(cta.getToken());
      return offer == null ? ActionOutcome.accepted() : ActionOutcome.accepted(offer);
    }
  }

  @Override
  public void report(SessionHandle handle, AdInteraction interaction) {
    Objects.requireNonNull(interaction, "interaction");
    synchronized (lock) {
      Session session = requireSession(handle);
      throwPendingFailure();
      session.reports.add(interaction);
      allReports.add(interaction);
    }
  }

  @Override
  public AdEventSubscription subscribe(SessionHandle handle, AdEventListener listener) {
    Objects.requireNonNull(listener, "listener");
    Session session;
    synchronized (lock) {
      session = requireSession(handle);
      session.listeners.add(listener);
    }
    return () -> session.listeners.remove(listener);
  }

  @Override
  public void closeSession(SessionHandle handle) {
    synchronized (lock) {
      Session session = sessions.remove(handle);
      if (session != null) {
        session.listeners.clear();
      }
    }
  }

  // ── internals ────────────────────────────────────────────────────────────────────────────────

  private Session requireSession(SessionHandle handle) {
    Objects.requireNonNull(handle, "handle");
    Session session = sessions.get(handle);
    if (session == null) {
      throw new IllegalStateException(handle + " was never registered or is closed");
    }
    return session;
  }

  private static void rememberToken(Session session, Rendering rendering, Cta cta) {
    if (cta != null) {
      session.creativeByToken.put(cta.getToken(), rendering.getCreativeId());
      session.actionByToken.put(cta.getToken(), cta.getAction());
      session.usedTokens.remove(cta.getToken());
    }
  }

  private RuntimeException takePendingFailure() {
    RuntimeException failure = pendingFailure;
    pendingFailure = null;
    return failure;
  }

  private void throwPendingFailure() {
    RuntimeException failure = takePendingFailure();
    if (failure != null) {
      throw failure;
    }
  }

  private boolean swallowPendingFailure() {
    RuntimeException failure = takePendingFailure();
    if (failure == null) {
      return false;
    }
    swallowedFailures.add(failure);
    return true;
  }

  /**
   * Sleeps for {@code duration}; returns {@code false} if interrupted (and re-flags the thread).
   */
  private static boolean sleep(Duration duration) {
    if (duration.isZero()) {
      return true;
    }
    try {
      Thread.sleep(duration.toMillis(), duration.getNano() % 1_000_000);
      return true;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }
}
