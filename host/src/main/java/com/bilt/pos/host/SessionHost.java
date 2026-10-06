/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.host;

import com.bilt.pos.host.internal.Event;
import com.bilt.pos.host.internal.EventBuffer;
import com.bilt.pos.host.internal.HostError;
import com.bilt.pos.host.internal.HostVersion;
import com.bilt.pos.host.internal.HostedOperation;
import com.bilt.pos.host.internal.HostedSession;
import com.bilt.pos.host.internal.IdempotencyCache;
import com.bilt.pos.host.internal.Json;
import com.bilt.pos.host.internal.OperationRunner;
import com.bilt.pos.host.internal.Parsers;
import com.bilt.pos.host.internal.SessionRegistry;
import com.bilt.pos.host.internal.Views;
import com.bilt.pos.internal.SdkVersion;
import com.bilt.pos.media.service.AdDecisionService;
import com.bilt.pos.nexo.client.TerminalClient;
import com.bilt.pos.session.SessionContext;
import com.bilt.pos.session.SessionResult;
import com.bilt.pos.session.ShopperSession;
import com.bilt.pos.session.Terminal;
import com.bilt.pos.session.basket.Basket;
import com.bilt.pos.session.basket.BasketMutation;
import com.bilt.pos.session.identity.Member;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.javalin.Javalin;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import io.javalin.http.sse.SseClient;
import io.javalin.websocket.WsContext;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The Session Host: an HTTP, Server-Sent Events and WebSocket server that exposes the SDK's shopper
 * sessions as the Session Protocol.
 *
 * <p>Embed it, point it at a {@link TerminalClientProvider}, start it:
 *
 * <pre>{@code
 * SessionHost host = SessionHost.builder()
 *     .bindAddress("127.0.0.1")
 *     .port(48333)
 *     .terminalClients(myProvider)
 *     .build();
 * host.start();
 * }</pre>
 *
 * <p>A browser then creates a session with {@code POST /v1/sessions}, edits its basket, member and
 * context, runs terminal operations through {@code POST .../operations} and watches {@code GET
 * .../events}. The host keeps the SDK's rules — one ordered operation in flight per session, end
 * refused while money is unresolved — because it is the SDK; what it adds is the resource shape,
 * idempotent retries, a replayable event log and steps in place of blocking callbacks.
 *
 * <p>This iteration is a development-mode host: {@link HostAuth#permitAll()} is the default, there
 * is no pairing, and the embedding application supplies the terminal configuration.
 */
public final class SessionHost implements AutoCloseable {

  private static final Logger LOGGER = Logger.getLogger(SessionHost.class.getName());
  private static final String PROTOCOL_VERSION = "1";
  private static final Duration SSE_PING = Duration.ofSeconds(15);
  private static final AtomicInteger WORKER_COUNTER = new AtomicInteger();

  private final Builder config;
  private final SessionRegistry registry;
  private final OperationRunner runner;
  private final ExecutorService workers;
  private final ScheduledExecutorService pinger;
  private final Javalin app;
  private volatile boolean started;

  private SessionHost(Builder builder) {
    this.config = builder;
    this.workers =
        Executors.newCachedThreadPool(
            runnable -> {
              Thread thread =
                  new Thread(runnable, "bilt-host-worker-" + WORKER_COUNTER.incrementAndGet());
              thread.setDaemon(true);
              return thread;
            });
    this.pinger =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "bilt-host-sse-ping");
              thread.setDaemon(true);
              return thread;
            });
    this.registry =
        new SessionRegistry(
            builder.sessionFactory,
            builder.terminalClients,
            builder.adDecisionService,
            builder.eventReplayCapacity,
            builder.eventReplayWindow,
            builder.idempotencyCapacity,
            workers);
    this.runner = new OperationRunner(builder.stepDeadlines);
    this.app =
        Javalin.create(
            config -> {
              config.startup.showJavalinBanner = false;
              config.http.defaultContentType = "application/json";
              config.concurrency.useVirtualThreads = false;
              routes(config.routes);
            });
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Binds and starts serving; {@link #port()} is known afterwards. */
  public synchronized void start() {
    if (started) {
      return;
    }
    app.start(config.bindAddress, config.port);
    started = true;
    LOGGER.info("Session Host listening on " + config.bindAddress + ":" + port());
  }

  /** Stops serving and ends every open session. */
  public synchronized void stop() {
    if (!started) {
      return;
    }
    started = false;
    try {
      app.stop();
    } finally {
      registry.closeAll();
      pinger.shutdownNow();
      workers.shutdownNow();
    }
  }

  @Override
  public void close() {
    stop();
  }

  /** The bound port; the one the builder asked for, or the ephemeral one assigned for port 0. */
  public int port() {
    return app.port();
  }

  /** How many sessions are open right now; for a tray icon or a health dashboard. */
  public int activeSessions() {
    return registry.openCount();
  }

  // ─── Routes ───

  private void routes(RoutesConfig routes) {
    routes.before(this::cors);
    routes.options("/*", ctx -> respond(ctx, 204, null));
    routes.before(this::authorize);
    routes.before(this::idempotencyLookup);
    routes.after(this::idempotencyRelease);
    routes.exception(HostError.class, (error, ctx) -> respond(ctx, error.status(), error.toJson()));
    routes.exception(
        Exception.class,
        (failure, ctx) -> {
          HostError error = HostError.from(failure);
          if (error.status() >= 500) {
            LOGGER.log(
                Level.WARNING, "request failed: " + ctx.method() + " " + ctx.path(), failure);
          }
          respond(ctx, error.status(), error.toJson());
        });

    routes.get("/health", ctx -> respond(ctx, 200, health()));

    routes.get("/v1/terminals", ctx -> respond(ctx, 200, terminals()));
    routes.post(
        "/v1/terminals/{poiId}/diagnose", ctx -> device(ctx, Terminal::diagnose, Views::diagnosis));
    routes.post(
        "/v1/terminals/{poiId}/totals",
        ctx -> device(ctx, Terminal::getTotals, Views::reconciliation));
    routes.post(
        "/v1/terminals/{poiId}/reconcile",
        ctx -> device(ctx, Terminal::reconcile, Views::reconciliation));
    routes.post(
        "/v1/terminals/{poiId}/print",
        ctx -> {
          var payload = Parsers.printPayload(Json.body(ctx.body()));
          device(ctx, terminal -> terminal.print(payload), v -> null);
        });
    routes.post(
        "/v1/terminals/{poiId}/sound",
        ctx -> {
          ObjectNode body = Json.body(ctx.body());
          String action = Json.requireText(body, "action");
          if (action.equalsIgnoreCase("STOP")) {
            device(ctx, Terminal::stopSound, v -> null);
            return;
          }
          if (!action.equalsIgnoreCase("PLAY")) {
            throw HostError.badRequest("action must be PLAY or STOP");
          }
          String reference = Json.requireText(body, "soundReferenceId");
          Integer volume = Json.integer(body, "volumePercent");
          device(ctx, terminal -> terminal.playSound(reference, volume), v -> null);
        });

    routes.post(
        "/v1/sessions", ctx -> respond(ctx, 201, registry.create(Json.body(ctx.body())).view()));
    routes.get("/v1/sessions", ctx -> respond(ctx, 200, sessions()));
    routes.get("/v1/sessions/{id}", ctx -> respond(ctx, 200, session(ctx).view()));
    routes.delete(
        "/v1/sessions/{id}",
        ctx -> respond(ctx, 202, registry.end(session(ctx), false, null).toJson()));
    routes.post(
        "/v1/sessions/{id}/force-end",
        ctx -> {
          String reason = Json.text(Json.body(ctx.body()), "reason");
          if (reason == null || reason.isBlank()) {
            throw HostError.badRequest("reason is required");
          }
          respond(ctx, 202, registry.end(session(ctx), true, reason.strip()).toJson());
        });
    routes.post(
        "/v1/sessions/{id}/abort",
        ctx -> {
          HostedSession hosted = session(ctx);
          HostedOperation running = hosted.running();
          ObjectNode body = Json.object();
          if (running != null && running.isAbortable()) {
            runner.abort(hosted, running);
            body.set("operation", running.toJson());
          }
          respond(ctx, 202, body);
        });

    routes.get("/v1/sessions/{id}/basket", ctx -> basket(ctx, s -> s.basket().snapshot()));
    routes.put(
        "/v1/sessions/{id}/basket",
        ctx -> {
          ObjectNode body = Json.body(ctx.body());
          boolean hasSnapshot = Json.has(body, "snapshot");
          ArrayNode items = Json.array(body, "items");
          if (hasSnapshot == (items != null)) {
            throw HostError.badRequest("exactly one of snapshot or items is required");
          }
          if (hasSnapshot) {
            Basket snapshot = Parsers.basket(body.get("snapshot"));
            basket(ctx, s -> s.basket().replace(snapshot));
          } else {
            var parsed = Parsers.basketItems(items);
            basket(ctx, s -> s.basket().replace(parsed));
          }
        });
    routes.post(
        "/v1/sessions/{id}/basket/items",
        ctx -> {
          ObjectNode body = Json.body(ctx.body());
          var item = Parsers.basketItem(body);
          String itemId = Json.text(body, "itemId");
          if (itemId != null && !itemId.matches("[0-9]+")) {
            throw HostError.badRequest("itemId must be numeric");
          }
          basket(
              ctx,
              s -> itemId == null ? s.basket().addItem(item) : s.basket().addItem(item, itemId));
        });
    routes.patch(
        "/v1/sessions/{id}/basket/items/{itemId}",
        ctx -> {
          String itemId = ctx.pathParam("itemId");
          Consumer<BasketMutation> patch = Parsers.itemPatch(itemId, Json.body(ctx.body()));
          basket(
              ctx,
              s -> {
                requireItem(s, itemId);
                return s.basket().mutate(patch);
              });
        });
    routes.delete(
        "/v1/sessions/{id}/basket/items/{itemId}",
        ctx -> {
          String itemId = ctx.pathParam("itemId");
          basket(
              ctx,
              s -> {
                requireItem(s, itemId);
                return s.basket().removeItem(itemId);
              });
        });
    routes.post(
        "/v1/sessions/{id}/basket/mutations",
        ctx -> {
          ArrayNode mutations = Json.array(Json.body(ctx.body()), "mutations");
          if (mutations == null || mutations.isEmpty()) {
            throw HostError.badRequest("mutations must not be empty");
          }
          List<Consumer<BasketMutation>> steps = new ArrayList<>();
          for (JsonNode node : mutations) {
            steps.add(Parsers.mutation(node));
          }
          basket(ctx, s -> s.basket().mutate(m -> steps.forEach(step -> step.accept(m))));
        });
    routes.post(
        "/v1/sessions/{id}/basket/tax-total",
        ctx -> {
          ObjectNode body = Json.body(ctx.body());
          if (!body.has("amount")) {
            throw HostError.badRequest("amount is required (null restores item-level tax)");
          }
          var amount = Json.decimal(body, "amount");
          basket(ctx, s -> s.basket().setTaxTotal(amount));
        });
    routes.post("/v1/sessions/{id}/basket/clear", ctx -> basket(ctx, s -> s.basket().clear()));

    routes.get("/v1/sessions/{id}/member", ctx -> member(ctx, session(ctx).session().member()));
    routes.put(
        "/v1/sessions/{id}/member",
        ctx -> {
          Member member = Parsers.member(Json.body(ctx.body()));
          if (member == null) {
            throw HostError.badRequest("member requires id or resolver; use DELETE to clear");
          }
          HostedSession hosted = session(ctx);
          hosted.session().member(member);
          respond(ctx, 200, Views.member(hosted.session().member()));
        });
    routes.delete(
        "/v1/sessions/{id}/member",
        ctx -> {
          session(ctx).session().member(null);
          respond(ctx, 204, null);
        });

    routes.get(
        "/v1/sessions/{id}/context",
        ctx -> respond(ctx, 200, Views.context(session(ctx).session().context().snapshot())));
    routes.patch(
        "/v1/sessions/{id}/context",
        ctx -> {
          ObjectNode body = Json.body(ctx.body());
          var phase = Parsers.phase(body);
          Map<String, String> attributes = Json.stringMap(body, "attributes");
          if (phase == null && attributes == null) {
            throw HostError.badRequest("the patch must set phase or attributes");
          }
          SessionContext context = session(ctx).session().context();
          if (phase != null) {
            context.phase(phase);
          }
          if (attributes != null) {
            attributes.forEach(context::attribute);
          }
          respond(ctx, 200, Views.context(context.snapshot()));
        });

    routes.post(
        "/v1/sessions/{id}/operations",
        ctx -> respond(ctx, 202, runner.submit(session(ctx), Json.body(ctx.body())).toJson()));
    routes.get(
        "/v1/sessions/{id}/operations",
        ctx -> respond(ctx, 200, operations(session(ctx), ctx.queryParam("status"))));
    routes.get(
        "/v1/sessions/{id}/operations/{operationId}",
        ctx -> respond(ctx, 200, operation(ctx).toJson()));
    routes.post(
        "/v1/sessions/{id}/operations/{operationId}/reply",
        ctx -> {
          HostedOperation operation = operation(ctx);
          runner.reply(operation, Json.body(ctx.body()));
          respond(ctx, 200, operation.toJson());
        });
    routes.post(
        "/v1/sessions/{id}/operations/{operationId}/abort",
        ctx -> {
          HostedSession hosted = session(ctx);
          HostedOperation operation = hosted.operation(ctx.pathParam("operationId"));
          boolean issued = runner.abort(hosted, operation);
          respond(ctx, issued ? 202 : 200, operation.toJson());
        });

    routes.get(
        "/v1/sessions/{id}/widgets", ctx -> respond(ctx, 200, session(ctx).widgets().view()));
    routes.post(
        "/v1/sessions/{id}/widgets/{type}/pause",
        ctx -> {
          HostedSession hosted = session(ctx);
          String type = ctx.pathParam("type");
          hosted.widgets().widget(type).pause();
          respond(ctx, 200, hosted.widgets().state(type));
        });
    routes.post(
        "/v1/sessions/{id}/widgets/{type}/resume",
        ctx -> {
          HostedSession hosted = session(ctx);
          String type = ctx.pathParam("type");
          hosted.widgets().widget(type).resume();
          respond(ctx, 200, hosted.widgets().state(type));
        });
    routes.post(
        "/v1/sessions/{id}/widgets/retail-media/actions",
        ctx -> {
          session(ctx).widgets().action(Json.body(ctx.body()));
          respond(ctx, 202, null);
        });

    routes.sse("/v1/sessions/{id}/events", this::sse);
    routes.ws(
        "/v1/sessions/{id}/events",
        ws -> {
          Map<String, AutoCloseable> subscriptions = new ConcurrentHashMap<>();
          ws.onConnect(
              ctx -> {
                if (!config.auth.permits(request(ctx))) {
                  ctx.closeSession(4401, "unauthorized");
                  return;
                }
                HostedSession hosted = registry.find(ctx.pathParam("id"));
                if (hosted == null) {
                  ctx.closeSession(4404, "session not found");
                  return;
                }
                long since;
                try {
                  since = since(ctx.queryParam("since"));
                } catch (HostError e) {
                  ctx.closeSession(4400, e.getMessage());
                  return;
                }
                if (!hosted.events().canReplayFrom(since)) {
                  ctx.closeSession(4410, "since is no longer buffered");
                  return;
                }
                ctx.enableAutomaticPings();
                var subscription =
                    hosted
                        .events()
                        .subscribe(
                            since,
                            new EventBuffer.Subscriber() {
                              @Override
                              public void onEvent(Event event) {
                                if (ctx.session.isOpen()) {
                                  ctx.send(event.json());
                                }
                              }

                              @Override
                              public void onClosed() {
                                if (ctx.session.isOpen()) {
                                  ctx.closeSession(1000, "session ended");
                                }
                              }
                            });
                subscriptions.put(ctx.sessionId(), subscription);
              });
          ws.onClose(ctx -> unsubscribe(subscriptions, ctx));
          ws.onError(ctx -> unsubscribe(subscriptions, ctx));
          ws.onMessage(ctx -> {});
        });
  }

  private static void unsubscribe(Map<String, AutoCloseable> subscriptions, WsContext ctx) {
    AutoCloseable subscription = subscriptions.remove(ctx.sessionId());
    if (subscription != null) {
      try {
        subscription.close();
      } catch (Exception e) {
        LOGGER.log(Level.FINE, "closing a WebSocket subscription failed", e);
      }
    }
  }

  private void sse(SseClient client) {
    HostedSession hosted = registry.find(client.ctx().pathParam("id"));
    if (hosted == null) {
      client.sendEvent("error", Json.write(HostError.notFound("session").toJson()), null);
      client.close();
      return;
    }
    long since = since(client.ctx().queryParam("since"));
    if (!hosted.events().canReplayFrom(since)) {
      throw HostError.gone(hosted.events().oldestSeq());
    }
    client.keepAlive();
    ScheduledFuture<?> ping =
        pinger.scheduleAtFixedRate(
            () -> {
              try {
                client.sendComment("ping");
              } catch (RuntimeException e) {
                client.close();
              }
            },
            SSE_PING.toMillis(),
            SSE_PING.toMillis(),
            TimeUnit.MILLISECONDS);
    var subscription =
        hosted
            .events()
            .subscribe(
                since,
                new EventBuffer.Subscriber() {
                  @Override
                  public void onEvent(Event event) {
                    client.sendEvent(event.type(), event.json(), Long.toString(event.seq()));
                  }

                  @Override
                  public void onClosed() {
                    client.close();
                  }
                });
    client.onClose(
        () -> {
          ping.cancel(false);
          subscription.close();
        });
  }

  private static long since(String raw) {
    if (raw == null || raw.isEmpty()) {
      return 0;
    }
    try {
      long since = Long.parseLong(raw);
      if (since < 0) {
        throw HostError.badRequest("since must not be negative");
      }
      return since;
    } catch (NumberFormatException e) {
      throw HostError.badRequest("since must be an event sequence number");
    }
  }

  // ─── Handlers' shared pieces ───

  private ObjectNode health() {
    ObjectNode node = Json.object();
    node.put("host", config.hostKind);
    node.put("hostVersion", HostVersion.current());
    node.put("sdkVersion", SdkVersion.current());
    node.putArray("protocolVersions").add(PROTOCOL_VERSION);
    node.set("terminals", terminals());
    return node;
  }

  private ArrayNode terminals() {
    ArrayNode array = Json.array();
    for (TerminalInfo terminal : config.terminalClients.terminals()) {
      array.add(Views.terminal(terminal));
    }
    return array;
  }

  private ArrayNode sessions() {
    ArrayNode array = Json.array();
    for (HostedSession hosted : registry.all()) {
      array.add(hosted.view());
    }
    return array;
  }

  private static ArrayNode operations(HostedSession hosted, String statusFilter) {
    Set<HostedOperation.Status> statuses = null;
    if (statusFilter != null && !statusFilter.isEmpty()) {
      statuses = java.util.EnumSet.noneOf(HostedOperation.Status.class);
      for (String wire : statusFilter.split(",")) {
        statuses.add(HostedOperation.Status.fromWire(wire.trim()));
      }
    }
    ArrayNode array = Json.array();
    for (HostedOperation operation : hosted.operations()) {
      if (statuses == null || statuses.contains(operation.status())) {
        array.add(operation.toJson());
      }
    }
    return array;
  }

  /** A session-less device operation, run synchronously against a short-lived {@link Terminal}. */
  private <T> void device(
      Context ctx, Function<Terminal, SessionResult<T>> call, Function<T, JsonNode> view) {
    String poiId = ctx.pathParam("poiId");
    TerminalClient client = config.terminalClients.forPoi(poiId);
    if (client == null) {
      throw HostError.notFound("terminal " + poiId);
    }
    String saleId = ctx.queryParam("saleId");
    try (Terminal terminal =
        Terminal.builder()
            .client(client)
            .poiId(poiId)
            .saleId(saleId == null || saleId.isEmpty() ? config.deviceSaleId : saleId)
            .storeLocation(ctx.queryParam("storeLocation"))
            .build()) {
      JsonNode body = view.apply(call.apply(terminal).get());
      respond(ctx, body == null ? 204 : 200, body);
    }
  }

  private HostedSession session(Context ctx) {
    return registry.require(ctx.pathParam("id"));
  }

  private HostedOperation operation(Context ctx) {
    return session(ctx).operation(ctx.pathParam("operationId"));
  }

  private void basket(Context ctx, Function<ShopperSession, Basket> change) {
    respond(ctx, 200, Views.basket(change.apply(session(ctx).session())));
  }

  private static void member(Context ctx, Member member) {
    if (member == null) {
      respond(ctx, 204, null);
    } else {
      respond(ctx, 200, Views.member(member));
    }
  }

  private static void requireItem(ShopperSession session, String itemId) {
    if (session.basket().snapshot().getItem(itemId) == null) {
      throw HostError.notFound("basket item " + itemId);
    }
  }

  // ─── Cross-cutting ───

  /**
   * Answers CORS for the configured origins so a page served from elsewhere can call the host: the
   * preflight gets the allowed methods and headers, every response the allow-origin, and Chrome's
   * local-network-access preflight its private-network consent.
   */
  private void cors(Context ctx) {
    String origin = ctx.header("Origin");
    if (origin == null || !originAllowed(origin)) {
      if (ctx.method() == io.javalin.http.HandlerType.OPTIONS) {
        ctx.status(204).result("");
        ctx.skipRemainingHandlers();
      }
      return;
    }
    ctx.header("Access-Control-Allow-Origin", origin);
    ctx.header("Vary", "Origin");
    ctx.header("Access-Control-Expose-Headers", "Idempotent-Replayed");
    if ("true".equalsIgnoreCase(ctx.header("Access-Control-Request-Private-Network"))) {
      ctx.header("Access-Control-Allow-Private-Network", "true");
    }
    if (ctx.method() == io.javalin.http.HandlerType.OPTIONS) {
      ctx.header("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
      ctx.header("Access-Control-Allow-Headers", "Content-Type, Authorization, Idempotency-Key");
      ctx.header("Access-Control-Max-Age", "600");
      ctx.status(204).result("");
      ctx.skipRemainingHandlers();
    }
  }

  private boolean originAllowed(String origin) {
    return config.allowedOrigins.contains("*") || config.allowedOrigins.contains(origin);
  }

  private void authorize(Context ctx) {
    if (ctx.path().equals("/health")) {
      return;
    }
    if (!config.auth.permits(request(ctx))) {
      throw HostError.unauthorized();
    }
  }

  private static HostRequest request(Context ctx) {
    return new HostRequest() {
      @Override
      public String method() {
        return ctx.method().name();
      }

      @Override
      public String path() {
        return ctx.path();
      }

      @Override
      public String header(String name) {
        return ctx.header(name);
      }

      @Override
      public String remoteAddress() {
        return ctx.ip();
      }
    };
  }

  private static HostRequest request(WsContext ctx) {
    return new HostRequest() {
      @Override
      public String method() {
        return "GET";
      }

      @Override
      public String path() {
        return ctx.session.getUpgradeRequest().getRequestURI().getPath();
      }

      @Override
      public String header(String name) {
        return ctx.header(name);
      }

      @Override
      public String remoteAddress() {
        // the peer's socket address, as ctx.ip() is for HTTP; host() would be the Host header
        SocketAddress peer = ctx.session.getRemoteSocketAddress();
        return peer instanceof InetSocketAddress
            ? ((InetSocketAddress) peer).getAddress().getHostAddress()
            : String.valueOf(peer);
      }
    };
  }

  private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
  private static final String IDEMPOTENCY_STORE = "bilt.idempotency.store";
  private static final String IDEMPOTENCY_RELEASE = "bilt.idempotency.release";

  /**
   * Every state-changing request under {@code /v1/sessions} must carry an {@code Idempotency-Key};
   * a key seen before replays the stored response and skips the handler.
   */
  private void idempotencyLookup(Context ctx) {
    if (!ctx.path().startsWith("/v1/sessions")) {
      return;
    }
    String method = ctx.method().name();
    if (!List.of("POST", "PUT", "PATCH", "DELETE").contains(method)) {
      return;
    }
    String key = ctx.header(IDEMPOTENCY_HEADER);
    if (key == null || key.isBlank()) {
      throw HostError.badRequest("the " + IDEMPOTENCY_HEADER + " header is required");
    }
    if (key.length() > 128) {
      throw HostError.badRequest(IDEMPOTENCY_HEADER + " must be at most 128 characters");
    }
    String fingerprint = IdempotencyCache.fingerprint(ctx.method().name(), ctx.path(), ctx.body());
    IdempotencyCache cache = cacheFor(ctx);
    if (cache == null) {
      return;
    }
    var stored = cache.lookup(key, fingerprint);
    if (stored != null) {
      ctx.status(stored.status()).header("Idempotent-Replayed", "true");
      if (stored.body() != null) {
        ctx.result(stored.body()).contentType("application/json");
      }
      ctx.skipRemainingHandlers();
      return;
    }
    ctx.attribute(
        IDEMPOTENCY_STORE,
        (BiConsumer<Integer, String>)
            (status, body) -> cache.store(key, fingerprint, status, body));
    ctx.attribute(IDEMPOTENCY_RELEASE, (Runnable) () -> cache.release(key));
  }

  /**
   * Frees the key of a request that ended without {@code respond}, so a retry is not stuck on it.
   */
  private void idempotencyRelease(Context ctx) {
    Runnable release = ctx.attribute(IDEMPOTENCY_RELEASE);
    if (release != null) {
      ctx.attribute(IDEMPOTENCY_RELEASE, null);
      release.run();
    }
  }

  private IdempotencyCache cacheFor(Context ctx) {
    if (ctx.path().equals("/v1/sessions")) {
      return registry.creationCache();
    }
    // before-handlers run without the matched route's path params, so the id comes from the path
    String rest = ctx.path().substring("/v1/sessions/".length());
    int slash = rest.indexOf('/');
    String id = slash < 0 ? rest : rest.substring(0, slash);
    HostedSession hosted = id.isEmpty() ? null : registry.find(id);
    return hosted == null ? null : hosted.idempotency();
  }

  @SuppressWarnings("unchecked")
  private static void respond(Context ctx, int status, JsonNode body) {
    String json = body == null ? null : Json.write(body);
    ctx.status(status);
    if (json != null) {
      ctx.result(json).contentType("application/json");
    } else {
      ctx.result("");
    }
    Object store = ctx.attribute(IDEMPOTENCY_STORE);
    if (store != null) {
      ((BiConsumer<Integer, String>) store).accept(status, json);
      ctx.attribute(IDEMPOTENCY_STORE, null);
      ctx.attribute(IDEMPOTENCY_RELEASE, null);
    }
  }

  // ─── Builder ───

  /** Configuration for a host; everything has a development-mode default except the terminals. */
  public static final class Builder {
    private int port;
    private String bindAddress = "127.0.0.1";
    private TerminalClientProvider terminalClients = TerminalClientProvider.none();
    private SessionFactory sessionFactory = SessionFactory.defaults();
    private StepDeadlines stepDeadlines = StepDeadlines.defaults();
    private HostAuth auth = HostAuth.permitAll();
    private AdDecisionService adDecisionService;
    private String hostKind = "bridge";
    private String deviceSaleId = "bilt-session-host";
    private int eventReplayCapacity = 1000;
    private Duration eventReplayWindow = Duration.ofMinutes(10);
    private int idempotencyCapacity = 256;
    private Set<String> allowedOrigins = Set.of();

    private Builder() {}

    /**
     * Browser origins the host answers CORS for, for example {@code https://pos.example.com};
     * {@code "*"} allows any origin, which is fine for a development host on loopback. Without any,
     * cross-origin pages are blocked by their own browser's preflight even though the host would
     * permit the request.
     */
    public Builder allowedOrigins(java.util.Collection<String> origins) {
      this.allowedOrigins = Set.copyOf(Objects.requireNonNull(origins, "origins"));
      return this;
    }

    /**
     * The port to listen on; 0 picks an ephemeral one, which tests read back with {@code port()}.
     */
    public Builder port(int port) {
      if (port < 0 || port > 65535) {
        throw new IllegalArgumentException("port must be between 0 and 65535");
      }
      this.port = port;
      return this;
    }

    /** The interface to bind; loopback by default, and the bridge never binds anything else. */
    public Builder bindAddress(String bindAddress) {
      this.bindAddress = Objects.requireNonNull(bindAddress, "bindAddress");
      return this;
    }

    public Builder terminalClients(TerminalClientProvider terminalClients) {
      this.terminalClients = Objects.requireNonNull(terminalClients, "terminalClients");
      return this;
    }

    public Builder sessionFactory(SessionFactory sessionFactory) {
      this.sessionFactory = Objects.requireNonNull(sessionFactory, "sessionFactory");
      return this;
    }

    public Builder stepDeadlines(StepDeadlines stepDeadlines) {
      this.stepDeadlines = Objects.requireNonNull(stepDeadlines, "stepDeadlines");
      return this;
    }

    public Builder auth(HostAuth auth) {
      this.auth = Objects.requireNonNull(auth, "auth");
      return this;
    }

    /**
     * The ad decision service behind {@code retail-media} widgets. Without one, a session that asks
     * for the widget is refused, since the SDK ships no platform-backed default yet.
     */
    public Builder adDecisionService(AdDecisionService adDecisionService) {
      this.adDecisionService = adDecisionService;
      return this;
    }

    /**
     * What {@code /health} reports as {@code host}: {@code bridge} by default, {@code cloud} later.
     */
    public Builder hostKind(String hostKind) {
      this.hostKind = Objects.requireNonNull(hostKind, "hostKind");
      return this;
    }

    /** The Nexo {@code SaleID} for session-less device operations that name none. */
    public Builder deviceSaleId(String deviceSaleId) {
      this.deviceSaleId = Objects.requireNonNull(deviceSaleId, "deviceSaleId");
      return this;
    }

    /** How many events, and how old, a session keeps for {@code ?since} replay. */
    public Builder eventReplay(int capacity, Duration window) {
      if (capacity < 1) {
        throw new IllegalArgumentException("capacity must be positive");
      }
      this.eventReplayCapacity = capacity;
      this.eventReplayWindow = Objects.requireNonNull(window, "window");
      return this;
    }

    public SessionHost build() {
      return new SessionHost(this);
    }
  }
}
