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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Session ID to live session. Creation builds the SDK session from the request through the {@link
 * SessionFactory}, registering the host's observer and widgets on the builder; ending hands the
 * SDK's {@code end()} refusals back as 409s. Ended sessions stay listed for a while so a client
 * reconnecting to the event stream can still replay {@code session.ended}; the oldest are evicted
 * once a bounded number have piled up.
 */
public final class SessionRegistry {

  private static final Logger LOGGER = Logger.getLogger(SessionRegistry.class.getName());
  private static final int ENDED_RETENTION = 100;

  private final SessionFactory factory;
  private final TerminalClientProvider terminals;
  private final AdDecisionService adService;
  private final int replayCapacity;
  private final Duration replayWindow;
  private final int idempotencyCapacity;
  private final Executor workers;
  private final IdempotencyCache creationCache;
  private final Map<String, HostedSession> sessions = new LinkedHashMap<>();

  public SessionRegistry(
      SessionFactory factory,
      TerminalClientProvider terminals,
      AdDecisionService adService,
      int replayCapacity,
      Duration replayWindow,
      int idempotencyCapacity,
      Executor workers) {
    this.factory = factory;
    this.terminals = terminals;
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
    String kind = Json.text(body, "kind");
    if (kind == null) {
      kind = Json.has(body, "poiId") ? "terminal" : "local";
    }
    String saleId = Json.requireText(body, "saleId");
    String currency = Json.requireText(body, "currency");
    String storeLocation = Json.text(body, "storeLocation");
    Member member = Parsers.member(body.get("member"));
    ObjectNode context = Json.objectField(body, "context");
    CheckoutPhase phase = Parsers.phase(context);
    Map<String, String> attributes = Json.stringMap(context, "attributes");

    HostedSession hosted =
        new HostedSession(kind, replayCapacity, replayWindow, idempotencyCapacity, workers);
    WidgetBridge widgets =
        WidgetBridge.from(
            Json.array(body, "widgets"),
            Json.objectField(body, "clientCapabilities"),
            adService,
            hosted::publish);

    ShopperSession started;
    switch (kind) {
      case "local":
        {
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
          break;
        }
      case "terminal":
        {
          String poiId = Json.requireText(body, "poiId");
          TerminalClient client = terminals.forPoi(poiId);
          if (client == null) {
            throw HostError.notFound("terminal " + poiId);
          }
          TerminalShopperSession.Builder builder =
              factory
                  .newTerminalSession()
                  .client(client)
                  .saleId(saleId)
                  .poiId(poiId)
                  .currency(currency)
                  .storeLocation(storeLocation)
                  .autoDisplay(Json.bool(body, "autoDisplay", true))
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
          break;
        }
      default:
        throw HostError.badRequest("kind must be local or terminal, not '" + kind + "'");
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

  /** {@code end()} with the SDK's guards; a refusal surfaces as a 409 with the SDK's message. */
  public void end(HostedSession hosted) {
    if (hosted.isEnded()) {
      throw HostError.conflict("the session has already ended");
    }
    hosted.session().end().get();
    hosted.markEnded();
  }

  public void forceEnd(HostedSession hosted, String reason) {
    if (hosted.isEnded()) {
      throw HostError.conflict("the session has already ended");
    }
    TerminalShopperSession terminal = hosted.terminal();
    if (terminal == null) {
      hosted.session().end().get();
    } else {
      terminal.forceEnd(reason).get();
    }
    hosted.markEnded();
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
