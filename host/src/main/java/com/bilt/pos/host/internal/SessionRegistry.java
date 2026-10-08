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

import com.bilt.pos.host.SessionFactory;
import com.bilt.pos.host.TerminalClientProvider;
import com.bilt.pos.media.service.AdDecisionService;
import com.bilt.pos.nexo.client.TerminalClient;
import com.bilt.pos.session.CheckoutPhase;
import com.bilt.pos.session.SessionErrorCode;
import com.bilt.pos.session.SessionException;
import com.bilt.pos.session.SessionResult;
import com.bilt.pos.session.ShopperSession;
import com.bilt.pos.session.TerminalShopperSession;
import com.bilt.pos.session.identity.Member;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Session ID to live session. Creation builds the SDK session from the request through the {@link
 * SessionFactory}, registering the host's observer and widgets on the builder. Ending is itself an
 * operation on the lane: when the lane is idle it runs on the calling thread so the SDK's refusals
 * come back as the 409 the protocol promises; otherwise it queues and a refusal fails the
 * operation. Ended sessions stay listed for a while so a client reconnecting to the event stream
 * can still replay {@code session.ended}; the oldest are evicted once a bounded number pile up.
 */
public final class SessionRegistry {

  private static final Logger LOGGER = Logger.getLogger(SessionRegistry.class.getName());
  private static final int ENDED_RETENTION = 100;

  private final SessionFactory factory;
  private final TerminalClientProvider terminal;
  private final String defaultPoiId;
  private final AdDecisionService adService;
  private final int replayCapacity;
  private final Duration replayWindow;
  private final int idempotencyCapacity;
  private final Executor workers;
  private final IdempotencyCache creationCache;
  private final Map<String, HostedSession> sessions = new LinkedHashMap<>();

  public SessionRegistry(
      SessionFactory factory,
      TerminalClientProvider terminal,
      String defaultPoiId,
      AdDecisionService adService,
      int replayCapacity,
      Duration replayWindow,
      int idempotencyCapacity,
      Executor workers) {
    this.factory = factory;
    this.terminal = terminal;
    this.defaultPoiId = defaultPoiId;
    this.adService = adService;
    this.replayCapacity = replayCapacity;
    this.replayWindow = replayWindow;
    this.idempotencyCapacity = idempotencyCapacity;
    this.workers = workers;
    this.creationCache = new IdempotencyCache(idempotencyCapacity);
  }

  /** The idempotency cache for {@code POST /v1/sessions} itself. */
  public IdempotencyCache creationCache() {
    return creationCache;
  }

  public HostedSession create(ObjectNode body) {
    String kind = Json.requireText(body, "kind");
    if (!kind.equals("local") && !kind.equals("terminal")) {
      throw HostError.badRequest("kind must be local or terminal, not '" + kind + "'");
    }
    String saleId = Json.requireText(body, "saleId");
    String currency = Json.requireText(body, "currency");
    if (!currency.matches("[A-Z]{3}")) {
      throw HostError.badRequest("currency must be an ISO 4217 code");
    }
    String storeLocation = Json.text(body, "storeLocation");
    TerminalClient client = null;
    String poiId = null;
    if (kind.equals("terminal")) {
      client = terminal.client();
      if (client == null) {
        throw HostError.unsupported(
            "no terminal is configured on this host; only local sessions are available");
      }
      // the host has one terminal; poiId only fills the Nexo header and never selects one
      String requested = Json.text(body, "poiId");
      if (requested != null && requested.isEmpty()) {
        throw HostError.badRequest("poiId must not be empty");
      }
      poiId = requested == null ? defaultPoiId : requested;
    }
    boolean autoDisplay = Json.bool(body, "autoDisplay", true);
    Member member = Parsers.member(body.get("member"));
    ObjectNode context = Json.objectField(body, "context");
    CheckoutPhase phase = Parsers.phase(context);
    Map<String, String> attributes = Json.stringMap(context, "attributes");

    HostedSession hosted =
        new HostedSession(
            new HostedSession.Spec(kind, saleId, poiId, currency, storeLocation, autoDisplay),
            replayCapacity,
            replayWindow,
            idempotencyCapacity,
            workers);
    WidgetBridge widgets =
        WidgetBridge.from(
            Json.array(body, "widgets"),
            Json.objectField(body, "clientCapabilities"),
            adService,
            hosted::publish);

    ShopperSession started;
    if (kind.equals("local")) {
      ShopperSession.Builder builder =
          factory
              .newLocalSession()
              .saleId(saleId)
              .currency(currency)
              .storeLocation(storeLocation)
              .onBackgroundError(hosted::backgroundError)
              .widget(hosted.observer())
              .widgets(widgets.widgets());
      if (member != null) {
        builder.member(member);
      }
      if (phase != null) {
        builder.phase(phase);
      }
      if (attributes != null) {
        attributes.forEach(builder::attribute);
      }
      started = builder.start();
    } else {
      TerminalShopperSession.Builder builder =
          factory
              .newTerminalSession()
              .client(client)
              .saleId(saleId)
              .poiId(poiId)
              .currency(currency)
              .storeLocation(storeLocation)
              .autoDisplay(autoDisplay)
              .onBackgroundError(hosted::backgroundError)
              .widget(hosted.observer())
              .widgets(widgets.widgets());
      if (member != null) {
        builder.member(member);
      }
      if (phase != null) {
        builder.phase(phase);
      }
      if (attributes != null) {
        attributes.forEach(builder::attribute);
      }
      started = builder.start().get();
    }
    hosted.attach(started, widgets);
    synchronized (sessions) {
      sessions.put(hosted.id(), hosted);
      evictEnded();
    }
    return hosted;
  }

  public HostedSession find(String id) {
    synchronized (sessions) {
      return sessions.get(id);
    }
  }

  public HostedSession require(String id) {
    HostedSession hosted = find(id);
    if (hosted == null) {
      throw HostError.notFound("session " + id);
    }
    return hosted;
  }

  public List<HostedSession> all() {
    synchronized (sessions) {
      return new ArrayList<>(sessions.values());
    }
  }

  public int openCount() {
    int open = 0;
    for (HostedSession hosted : all()) {
      if (!hosted.isEnded()) {
        open++;
      }
    }
    return open;
  }

  /**
   * {@code end()} or {@code forceEnd(reason)} as the {@code end}/{@code forceEnd} operation. With
   * an idle lane it runs now, so an SDK guard refusal is a 409 and nothing is recorded; with a busy
   * lane it queues behind the operation in flight and a refusal fails the operation.
   */
  public HostedOperation end(HostedSession hosted, boolean forced, String reason) {
    if (hosted.isEndingOrEnded()) {
      throw HostError.conflict("the session has already ended");
    }
    HostedOperation operation = new HostedOperation(forced ? "forceEnd" : "end", true);
    TerminalShopperSession terminal = hosted.terminal();
    Supplier<SessionResult<Void>> call =
        forced && terminal != null ? () -> terminal.forceEnd(reason) : () -> hosted.session().end();
    Runnable queuedLaunch =
        () -> {
          try {
            SessionResult<Void> end = call.get();
            end.execute();
            end.get();
            operation.succeeded(null);
          } catch (Throwable failure) {
            hosted.endFailed();
            operation.failed(HostError.from(failure), false);
          } finally {
            hosted.completed(operation);
          }
        };
    if (!hosted.beginEnd(forced, reason, operation, queuedLaunch)) {
      return operation;
    }
    // the lane is idle and now this end's: it runs here, so an SDK guard refusal is a 409
    operation.started();
    try {
      call.get().get();
      operation.succeeded(null);
    } catch (SessionException e) {
      if (e.getError().getCode() == SessionErrorCode.INVALID_STATE) {
        hosted.endRefused(operation);
        throw HostError.of(e.getError());
      }
      hosted.endFailed();
      operation.failed(HostError.of(e.getError()), false);
    } catch (RuntimeException e) {
      HostError error = HostError.from(e);
      if (error.status() == 409) {
        hosted.endRefused(operation);
        throw error;
      }
      hosted.endFailed();
      operation.failed(error, false);
    }
    hosted.completed(operation);
    return operation;
  }

  /** Best-effort teardown on {@code stop()}: ends what it can, closes every event stream. */
  public void closeAll() {
    for (HostedSession hosted : all()) {
      if (!hosted.isEnded()) {
        try {
          hosted.session().close();
        } catch (RuntimeException e) {
          LOGGER.log(Level.FINE, "closing session " + hosted.id() + " on stop failed", e);
        }
      }
      hosted.events().close();
    }
  }

  private void evictEnded() {
    int ended = 0;
    for (HostedSession hosted : sessions.values()) {
      if (hosted.isEnded()) {
        ended++;
      }
    }
    Iterator<HostedSession> iterator = sessions.values().iterator();
    while (ended > ENDED_RETENTION && iterator.hasNext()) {
      if (iterator.next().isEnded()) {
        iterator.remove();
        ended--;
      }
    }
  }
}
