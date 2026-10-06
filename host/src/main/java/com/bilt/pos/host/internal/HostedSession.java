/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.host.internal;

import com.bilt.pos.session.SessionContextSnapshot;
import com.bilt.pos.session.SessionError;
import com.bilt.pos.session.ShopperSession;
import com.bilt.pos.session.TerminalShopperSession;
import com.bilt.pos.session.basket.BasketChange;
import com.bilt.pos.session.identity.Member;
import com.bilt.pos.widget.Widget;
import com.bilt.pos.widget.WidgetHost;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One live session as the host sees it: the SDK session, its event stream, its idempotency cache,
 * its operations and the ordered lane they take turns on.
 *
 * <p>The lane mirrors the SDK's rule of one in-flight ordered operation per session, but the host
 * holds queued operations back rather than handing them to the SDK's executor straight away: that
 * way a queued operation can still be aborted before it starts, and {@code running} means exactly
 * one thing. Unordered operations ({@code updateInputDisplay}) bypass the lane, as they do in the
 * SDK.
 *
 * <p>The session's own observer seam is used for state events: {@link Observer} is registered as a
 * widget on the builder (a {@code Widget} is a {@code SessionObserver} with lifecycle hooks) and
 * turns the SDK's callbacks into {@code session.started}, {@code basket.changed}, {@code
 * member.changed}, {@code context.changed} and {@code session.ended}.
 */
public final class HostedSession {

  private static final Logger LOGGER = Logger.getLogger(HostedSession.class.getName());

  /** The creation parameters the session view reports back. */
  public static final class Spec {
    final String kind;
    final String saleId;
    final String poiId;
    final String currency;
    final String storeLocation;
    final boolean autoDisplay;

    public Spec(
        String kind,
        String saleId,
        String poiId,
        String currency,
        String storeLocation,
        boolean autoDisplay) {
      this.kind = kind;
      this.saleId = saleId;
      this.poiId = poiId;
      this.currency = currency;
      this.storeLocation = storeLocation;
      this.autoDisplay = autoDisplay;
    }
  }

  private final Spec spec;
  private final EventBuffer events;
  private final IdempotencyCache idempotency;
  private final Observer observer = new Observer();
  private final Executor workers;
  private final Map<String, HostedOperation> operations = new ConcurrentHashMap<>();
  private final ArrayDeque<QueuedOperation> lane = new ArrayDeque<>();
  private final Instant createdAt = Instant.now();
  private volatile String sessionId;
  private volatile ShopperSession session;
  private volatile TerminalShopperSession terminal;
  private volatile WidgetBridge widgets;
  private volatile HostedOperation running;
  private volatile String state = "open";
  private volatile Instant endedAt;
  private volatile boolean endForced;
  private volatile String endReason;
  private volatile HostedOperation endOperation;

  private static final class QueuedOperation {
    final HostedOperation operation;
    final Runnable launch;

    QueuedOperation(HostedOperation operation, Runnable launch) {
      this.operation = operation;
      this.launch = launch;
    }
  }

  HostedSession(
      Spec spec,
      int replayCapacity,
      Duration replayWindow,
      int idempotencyCapacity,
      Executor workers) {
    this.spec = spec;
    this.events = new EventBuffer(replayCapacity, replayWindow);
    this.idempotency = new IdempotencyCache(idempotencyCapacity);
    this.workers = workers;
  }

  /** The widget to register on the SDK builder before {@link #attach} is called. */
  Widget observer() {
    return observer;
  }

  /** Binds the started SDK session; the observer has already published {@code session.started}. */
  void attach(ShopperSession started, WidgetBridge widgetBridge) {
    this.sessionId = started.getSessionId();
    this.session = started;
    this.terminal =
        started instanceof TerminalShopperSession ? (TerminalShopperSession) started : null;
    this.widgets = widgetBridge;
  }

  public String id() {
    return sessionId;
  }

  public String kind() {
    return spec.kind;
  }

  public ShopperSession session() {
    return session;
  }

  /** The terminal session, or {@code null} for a {@code local} one. */
  public TerminalShopperSession terminal() {
    return terminal;
  }

  public WidgetBridge widgets() {
    return widgets;
  }

  public EventBuffer events() {
    return events;
  }

  public IdempotencyCache idempotency() {
    return idempotency;
  }

  public boolean isEnded() {
    return "ended".equals(state);
  }

  public boolean isEndingOrEnded() {
    return !"open".equals(state);
  }

  public Event publish(String type, JsonNode payload) {
    return events.publish(type, payload);
  }

  // ─── Views ───

  /** The {@code Session} resource. */
  public ObjectNode view() {
    ObjectNode node = Json.object();
    node.put("id", sessionId);
    node.put("kind", spec.kind);
    node.put("saleId", spec.saleId);
    Json.putText(node, "poiId", spec.poiId);
    node.put("currency", spec.currency);
    Json.putText(node, "storeLocation", spec.storeLocation);
    node.put("autoDisplay", spec.autoDisplay);
    node.put("state", state);
    node.put("createdAt", createdAt.toString());
    Json.putInstant(node, "endedAt", endedAt);
    node.put("eventsUrl", "/v1/sessions/" + sessionId + "/events");
    return node;
  }

  // ─── Operations and the lane ───

  public HostedOperation operation(String operationId) {
    HostedOperation operation = operations.get(operationId);
    if (operation == null) {
      throw HostError.notFound("operation " + operationId);
    }
    return operation;
  }

  /** Every operation, newest first. */
  public List<HostedOperation> operations() {
    List<HostedOperation> all = new ArrayList<>(operations.values());
    all.sort((a, b) -> b.createdAt().compareTo(a.createdAt()));
    return all;
  }

  /** The ordered operation currently in flight, or {@code null}. */
  public HostedOperation running() {
    return running;
  }

  /** Drops an operation registered with {@link #register} whose request was refused outright. */
  void unregister(HostedOperation operation) {
    operations.remove(operation.id());
  }

  /** Publishes {@code operation.completed} once, however many paths reach the end. */
  void publishCompletion(HostedOperation operation) {
    // claiming and publishing are one step: the thread that loses the claim must not be able
    // to publish a later event before the winner's operation.completed has its seq
    synchronized (operation) {
      if (operation.markCompletionPublished()) {
        publish("operation.completed", operation.toJson());
      }
    }
  }

  /**
   * Registers an operation and either starts it now (unordered, or the lane is idle) or queues it
   * behind the one in flight. {@code launch} does the SDK work and must end by calling {@link
   * #completed}.
   */
  void submit(HostedOperation operation, Runnable launch) {
    synchronized (lane) {
      // decided under the same lock that end() claims the lane under, so an operation is either
      // ahead of the end or refused, never accepted behind it to fail when it later executes
      if (!"open".equals(state) && operation != endOperation) {
        throw HostError.conflict("the session is ending or has ended; no further operations");
      }
      operations.put(operation.id(), operation);
      if (operation.ordered()) {
        if (running != null) {
          lane.addLast(new QueuedOperation(operation, launch));
          return;
        }
        running = operation;
      }
    }
    start(operation, launch);
  }

  /**
   * Accepts an {@code end}/{@code forceEnd}: marks the session ending and either claims the idle
   * lane for the caller to run it (true) or queues {@code queuedLaunch} behind the operation in
   * flight (false). The ending state and the lane are settled in one step, so nothing can start
   * while an idle end tears the session down and nothing is accepted after it.
   */
  boolean beginEnd(
      boolean forced, String reason, HostedOperation operation, Runnable queuedLaunch) {
    synchronized (lane) {
      if (!"open".equals(state)) {
        throw HostError.conflict("the session has already ended");
      }
      ending(forced, reason, operation);
      operations.put(operation.id(), operation);
      if (running == null && lane.isEmpty()) {
        running = operation;
        return true;
      }
      lane.addLast(new QueuedOperation(operation, queuedLaunch));
      return false;
    }
  }

  /** Frees the lane an idle end claimed when the SDK refused it and the session stays open. */
  void releaseLane(HostedOperation operation) {
    synchronized (lane) {
      if (running == operation) {
        running = null;
      }
    }
  }

  private void start(HostedOperation operation, Runnable launch) {
    operation.started();
    workers.execute(launch);
  }

  /** Called by every launch when its operation reaches a terminal status. */
  void completed(HostedOperation operation) {
    publishCompletion(operation);
    if (!operation.ordered()) {
      return;
    }
    QueuedOperation next;
    synchronized (lane) {
      if (running != operation) {
        return;
      }
      next = lane.pollFirst();
      running = next == null ? null : next.operation;
    }
    if (next != null) {
      start(next.operation, next.launch);
    }
  }

  /** Removes a not-yet-started operation from the lane; false if it had already started. */
  boolean dequeue(HostedOperation operation) {
    synchronized (lane) {
      return lane.removeIf(queued -> queued.operation == operation);
    }
  }

  // ─── Lifecycle ───

  /**
   * Marks the session as ending on behalf of an {@code end} or {@code forceEnd} operation; the
   * observer completes that operation and flips the state to ended when the SDK confirms.
   */
  void ending(boolean forced, String reason, HostedOperation operation) {
    endForced = forced;
    endReason = reason;
    endOperation = operation;
    state = "ending";
  }

  /** The end did not go through; the session stays open. */
  void endFailed() {
    if (!"ended".equals(state)) {
      state = "open";
    }
  }

  // ─── Observer ───

  /**
   * The SDK observer behind the state events. Registered through the builder's widget slot, it is
   * invisible to clients: {@code GET .../widgets} filters it out.
   */
  final class Observer implements Widget {

    @Override
    public void attach(WidgetHost host) {
      sessionId = host.sessionId();
    }

    @Override
    public void detach() {}

    @Override
    public void pause() {}

    @Override
    public void resume() {}

    @Override
    public boolean isPaused() {
      return false;
    }

    @Override
    public void started(SessionContextSnapshot context) {
      ObjectNode payload = Json.object();
      payload.set("session", view());
      payload.set("context", Views.context(context));
      publish("session.started", payload);
    }

    @Override
    public void memberChanged(Member member) {
      ObjectNode payload = Json.object();
      payload.set("member", Views.member(member));
      publish("member.changed", payload);
    }

    @Override
    public void contextChanged(SessionContextSnapshot context) {
      publish("context.changed", Views.context(context));
    }

    @Override
    public void basketChanged(BasketChange change) {
      publish("basket.changed", Views.basketChange(change));
    }

    @Override
    public void ended() {
      endedAt = Instant.now();
      state = "ended";
      // the end operation completes before the session does, so a client that watches the
      // stream sees operation.completed and then session.ended, as the protocol promises
      HostedOperation end = endOperation;
      if (end != null) {
        if (!end.isTerminal()) {
          end.succeeded(null);
        }
        publishCompletion(end);
      }
      ObjectNode payload = Json.object();
      payload.put("sessionId", sessionId);
      payload.put("forced", endForced);
      Json.putText(payload, "reason", endForced ? endReason : null);
      publish("session.ended", payload);
      events.close();
    }
  }

  /** The {@code onBackgroundError} hook: the SDK's background failures as events. */
  void backgroundError(SessionError error) {
    LOGGER.log(Level.FINE, "background error on session {0}: {1}", new Object[] {sessionId, error});
    publish("background.error", Views.error(error));
  }
}
