/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.media;

import com.bilt.pos.internal.SdkVersion;
import com.bilt.pos.media.service.AdDecisionService;
import com.bilt.pos.media.service.AdSessionSnapshot;
import com.bilt.pos.media.service.SessionHandle;
import com.bilt.pos.session.CheckoutPhase;
import com.bilt.pos.session.SessionContextSnapshot;
import com.bilt.pos.session.SessionError;
import com.bilt.pos.session.SessionErrorCode;
import com.bilt.pos.session.basket.Basket;
import com.bilt.pos.session.basket.BasketChange;
import com.bilt.pos.session.basket.BasketLineItem;
import com.bilt.pos.session.identity.Member;
import com.bilt.pos.widget.Action;
import com.bilt.pos.widget.MediaSpec;
import com.bilt.pos.widget.Rendering;
import com.bilt.pos.widget.Surface;
import com.bilt.pos.widget.Widget;
import com.bilt.pos.widget.WidgetHost;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Sponsored media on a shopper-facing display, decided by the Bilt ad platform and rendered by the
 * SDK on {@link Surface}s the register hands over. The register never sees an ad request, a
 * creative or a rendering decision: it keeps the session's basket, member and context current, and
 * what comes back is {@linkplain Builder#onOffer(Consumer) offers to apply} and an informational
 * {@linkplain Builder#onInteraction(Consumer) stream of interactions}.
 *
 * <p>A {@code RetailMedia} is a {@link Widget}: it is registered on a session builder, follows the
 * session as a {@link com.bilt.pos.widget.SessionObserver}, and is reached at runtime through
 * {@code session.widget(RetailMedia.class)}. It works the same on a local {@code ShopperSession}
 * and on a {@code TerminalShopperSession}.
 *
 * <pre>{@code
 * ShopperSession session = ShopperSession.builder()
 *     .saleId("LANE-3")
 *     .currency("USD")
 *     .storeLocation("STR-0142")
 *     .credentials(BiltCredentials.clientCredentials(clientId, clientSecret))
 *     .widget(RetailMedia.builder()
 *         .surface(Placement.of("lane-banner"), new AndroidViewSurface(bannerContainer))
 *         .surface(Placement.of("lane-interstitial"), new AndroidWebViewSurface(webView))
 *         .onOffer(offer -> pos.applyOffer(offer))
 *         .onInteraction(event -> pos.recordAdInteraction(event))
 *         .build())
 *     .start();
 *
 * session.basket().replace(toBasket(pos.cart()));          // on every cart change
 * session.member(Member.idResolver().phone("+12015550123")); // when the shopper signs in
 * session.widget(RetailMedia.class).pause();                 // while the screen shows PIN entry
 * session.widget(RetailMedia.class).resume();
 * }</pre>
 *
 * <h2>What the register must do</h2>
 *
 * <p>{@code onOffer} is the only callback the register must act on. It fires after the shopper
 * accepted an offer call to action <em>and</em> the ad platform validated the action token, and it
 * carries an {@link Offer} — id, {@linkplain Offer.Scope scope}, the SKU for a line-item offer,
 * amount or percentage, expiry and the creative it came from. The register turns it into a basket
 * discount or line discount in its own pricing; the SDK never touches prices in this mode, and the
 * creative's copy is never trusted for pricing — only the validated offer reaches the register.
 *
 * <p>{@code onInteraction} is informational. Every shopper-visible ad event arrives as an {@link
 * AdInteraction} — shown, viewed, tapped, accepted, sent to phone, dismissed, completed — and the
 * register may log it, adjust its own screen, or ignore it. Both callbacks fire the same way
 * whichever surface tier is in use, and both are delivered on the session's callback executor (the
 * builder's {@code callbackExecutor}, or the calling thread when none was configured). A handler
 * that throws is reported through {@code onBackgroundError} and never affects the widget.
 *
 * <h2>Surfaces and placements</h2>
 *
 * <p>Each {@link Placement} is a named zone from the platform's placement map for the device — a
 * banner strip beside the cart is the common case, a full-screen interstitial one placement among
 * others — and is bound to exactly one {@link Surface}. What the surface can draw is read from the
 * surface itself ({@link Surface#supportedFormats()}, {@link Surface#kind()}) and sent to the
 * platform as {@link #capabilities()} with every decision, so the platform only serves what this
 * register can render. The five ways to get a rendering on screen, from a native container to the
 * register drawing the creative itself, are described in the {@link com.bilt.pos.widget} package
 * documentation.
 *
 * <h2>Pause and resume</h2>
 *
 * <p>{@link #pause()} clears every surface immediately and suppresses decision requests until
 * {@link #resume()}; the session's callbacks keep arriving so the widget stays current and picks up
 * where the session is when resumed. Both may be called from any thread at any time and are
 * idempotent. Media is requested and shown only in the {@linkplain Builder#eligiblePhases(Set)
 * eligible phases}; leaving an eligible phase clears the surfaces as {@code pause()} does.
 *
 * <h2>Never in the way of the checkout</h2>
 *
 * <p>The widget never blocks a session call. Basket, member and context updates are handed to the
 * ad platform without waiting on it, decision requests run on the widget's own thread under {@link
 * Builder#decisionTimeout(Duration)}, and a miss is a blank placement, never an error the register
 * sees. Every failure — a platform request that failed, a surface that could not draw, a tap with a
 * token the platform refused — goes to the session's {@code onBackgroundError} handler through
 * {@link WidgetHost#reportBackgroundError(SessionError)}. Nothing is thrown into the register's
 * basket call.
 *
 * <h2>Ad decision service</h2>
 *
 * <p>Decisions come from an {@link AdDecisionService}. This release ships no platform-backed
 * implementation, so a widget built without {@link Builder#adService(AdDecisionService)} reports a
 * clear {@link SessionErrorCode#UNSUPPORTED} error at attach and stays inert for the session — the
 * session itself is unaffected. {@link com.bilt.pos.media.service.InMemoryAdDecisionService} is the
 * intended choice for tests and the emulator today.
 *
 * <p>This release defines the widget's public surface and the session bookkeeping behind it:
 * registering the session with the service, keeping its {@link AdSessionSnapshot} current on every
 * basket, member and context change, closing it at the end, and the action-validation contract
 * behind the surfaces' {@link com.bilt.pos.widget.ActionSink}. Decisioning, caching, rendering and
 * measurement reporting arrive in a later release behind the same API.
 */
public final class RetailMedia implements Widget {

  private static final Set<CheckoutPhase> DEFAULT_ELIGIBLE_PHASES =
      Collections.unmodifiableSet(EnumSet.complementOf(EnumSet.of(CheckoutPhase.COMPLETE)));
  private static final Duration DEFAULT_DECISION_TIMEOUT = Duration.ofMillis(500);

  private final Map<Placement, Surface> surfaces;
  private final Consumer<Offer> onOffer;
  private final Consumer<AdInteraction> onInteraction;
  private final AdDecisionService adService;
  private final Set<CheckoutPhase> eligiblePhases;
  private final Duration decisionTimeout;
  private final Duration renderingTtl;
  private final String sdkVersion;
  private final Capabilities capabilities;

  private final Object lock = new Object();
  private final Map<Placement, Rendering> current = new HashMap<>();
  private final AtomicBoolean paused = new AtomicBoolean();
  private volatile WidgetHost host;
  private volatile boolean inert;
  private volatile boolean detached;
  private volatile ExecutorService widgetThread;
  private volatile SessionHandle handle;
  private volatile AdSessionSnapshot snapshot;
  private volatile CheckoutPhase phase;

  private RetailMedia(Builder builder) {
    if (builder.surfaces.isEmpty()) {
      throw new IllegalArgumentException(
          "RetailMedia needs at least one surface(placement, surface)");
    }
    this.surfaces = Collections.unmodifiableMap(new LinkedHashMap<>(builder.surfaces));
    this.onOffer = builder.onOffer;
    this.onInteraction = builder.onInteraction;
    this.adService = builder.adService;
    this.eligiblePhases = Collections.unmodifiableSet(EnumSet.copyOf(builder.eligiblePhases));
    this.decisionTimeout = builder.decisionTimeout;
    this.renderingTtl = builder.renderingTtl;
    this.sdkVersion = builder.sdkVersion == null ? SdkVersion.current() : builder.sdkVersion;
    this.capabilities = deriveCapabilities();
  }

  public static Builder builder() {
    return new Builder();
  }

  private Capabilities deriveCapabilities() {
    Capabilities.Builder capabilities =
        Capabilities.builder().sdkVersion(sdkVersion).actions(EnumSet.allOf(Action.class));
    Set<MediaSpec.MediaType> formats = new LinkedHashSet<>();
    for (Map.Entry<Placement, Surface> entry : surfaces.entrySet()) {
      Surface surface = entry.getValue();
      formats.addAll(surface.supportedFormats());
      capabilities.placement(entry.getKey(), surface.kind());
    }
    return capabilities.formats(formats).build();
  }

  // ─── Register-facing surface ───

  /** The surfaces by placement, in registration order. Never empty. */
  public Map<Placement, Surface> surfaces() {
    return surfaces;
  }

  /**
   * What this widget tells the platform it can render, derived from its surfaces: the union of
   * their {@link Surface#supportedFormats()}, every {@link Action}, and each placement with its
   * surface's {@link Surface#kind()}, stamped with the SDK version. Sent with every decision.
   */
  public Capabilities capabilities() {
    return capabilities;
  }

  /** The phases in which media is requested and shown. */
  public Set<CheckoutPhase> eligiblePhases() {
    return eligiblePhases;
  }

  /** How long a decision request may take before the placement stays blank. */
  public Duration decisionTimeout() {
    return decisionTimeout;
  }

  /** The configured rendering TTL override, or {@code null} to honour each rendering's own TTL. */
  public Duration renderingTtl() {
    return renderingTtl;
  }

  @Override
  public void pause() {
    if (paused.compareAndSet(false, true)) {
      clearSurfaces();
    }
  }

  @Override
  public void resume() {
    if (paused.compareAndSet(true, false)) {
      evaluateAll();
    }
  }

  @Override
  public boolean isPaused() {
    return paused.get();
  }

  // ─── Widget lifecycle ───

  @Override
  public void attach(WidgetHost host) {
    Objects.requireNonNull(host, "host");
    synchronized (lock) {
      if (this.host != null) {
        throw new IllegalStateException(
            "this RetailMedia already belongs to session "
                + this.host.sessionId()
                + "; one widget instance belongs to one session");
      }
      this.host = host;
    }
    if (adService == null) {
      inert = true;
      host.reportBackgroundError(
          new SessionError(
              SessionErrorCode.UNSUPPORTED,
              "RetailMedia has no ad decision service: this release ships no platform-backed"
                  + " default, so configure RetailMedia.builder().adService(..) —"
                  + " InMemoryAdDecisionService for tests and the emulator. The widget stays"
                  + " inert for this session."));
      return;
    }
    SessionContextSnapshot context = host.context();
    if (context.storeLocation() == null || context.currency() == null) {
      inert = true;
      host.reportBackgroundError(
          new SessionError(
              SessionErrorCode.INVALID_STATE,
              "RetailMedia needs the session's storeLocation and currency to register with the"
                  + " ad platform; set both on the session builder. The widget stays inert for"
                  + " this session."));
      return;
    }
    widgetThread =
        Executors.newSingleThreadExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "bilt-retail-media-" + host.sessionId());
              thread.setDaemon(true);
              return thread;
            });
  }

  @Override
  public void detach() {
    detached = true;
    clearSurfaces();
    closeSession();
    ExecutorService thread = widgetThread;
    if (thread != null) {
      thread.shutdown();
    }
  }

  // ─── Session observer ───

  @Override
  public void started(SessionContextSnapshot context) {
    if (inert) {
      return;
    }
    phase = context.phase();
    AdSessionSnapshot first = withContext(AdSessionSnapshot.builder(), context).build();
    try {
      handle = adService.registerSession(first);
      snapshot = first;
    } catch (RuntimeException e) {
      inert = true;
      report("registering the session with the ad platform", e);
      return;
    }
    evaluateAll();
  }

  @Override
  public void contextChanged(SessionContextSnapshot context) {
    if (!isRegistered()) {
      return;
    }
    phase = context.phase();
    push(withContext(snapshot.toBuilder(), context).build());
    if (!eligiblePhases.contains(context.phase())) {
      clearSurfaces();
    }
  }

  @Override
  public void basketChanged(BasketChange change) {
    if (!isRegistered()) {
      return;
    }
    push(snapshot.toBuilder().lines(summarize(change.current())).build());
  }

  @Override
  public void memberChanged(Member member) {
    if (!isRegistered()) {
      return;
    }
    String memberId = member != null && member.isResolved() ? member.memberId() : null;
    push(snapshot.toBuilder().memberId(memberId).build());
  }

  @Override
  public void ended() {
    clearSurfaces();
    closeSession();
  }

  private boolean isRegistered() {
    return !inert && handle != null;
  }

  private static AdSessionSnapshot.Builder withContext(
      AdSessionSnapshot.Builder builder, SessionContextSnapshot context) {
    return builder
        .saleId(context.saleId())
        .storeLocation(context.storeLocation())
        .laneId(context.poiId())
        .currency(context.currency())
        .phase(context.phase().name())
        .attributes(context.attributes());
  }

  /**
   * The basket's sale lines as the ad platform sees them. Return and credit lines describe money
   * going back to the shopper, not what is being bought, so they are left out.
   */
  private static List<AdSessionSnapshot.LineSummary> summarize(Basket basket) {
    List<AdSessionSnapshot.LineSummary> lines = new ArrayList<>();
    for (BasketLineItem item : basket.getItems()) {
      if (!item.isSale() || item.getSku() == null || item.getQuantity() <= 0) {
        continue;
      }
      lines.add(
          AdSessionSnapshot.LineSummary.builder()
              .sku(item.getSku())
              .description(item.getDescription())
              .quantity(item.getQuantity())
              .unitPrice(item.getUnitPrice())
              .category(item.getCategory())
              .metadata(item.getMetadata())
              .build());
    }
    return lines;
  }

  private void push(AdSessionSnapshot next) {
    SessionHandle registered = handle;
    if (registered == null) {
      return;
    }
    try {
      adService.updateSession(registered, next);
      snapshot = next;
    } catch (RuntimeException e) {
      report("updating the ad platform's view of the session", e);
      return;
    }
    evaluateAll();
  }

  private void closeSession() {
    SessionHandle registered;
    synchronized (lock) {
      registered = handle;
      handle = null;
    }
    if (registered == null) {
      return;
    }
    try {
      adService.closeSession(registered);
    } catch (RuntimeException e) {
      report("closing the session with the ad platform", e);
    }
  }

  // ─── Engine seam ───

  /**
   * Considers whether {@code placement} should show something now, given the current snapshot,
   * phase and pause state, and if so decides and {@linkplain #present(Placement, Rendering)
   * presents} a rendering. Called after every snapshot change in an eligible phase and on {@link
   * #resume()}, on the session's operation lane, so anything slow belongs on {@link
   * #onWidgetThread(Runnable)}. Deliberately empty in this release: RET-6776 fills it with
   * decisioning against {@link #decisionTimeout()}, the rendering cache and fallback creative, TTL
   * handling and measurement reporting.
   */
  void evaluate(Placement placement) {}

  private void evaluateAll() {
    if (inert || detached || paused.get()) {
      return;
    }
    CheckoutPhase now = phase;
    if (now == null || !eligiblePhases.contains(now)) {
      return;
    }
    for (Placement placement : surfaces.keySet()) {
      evaluate(placement);
    }
  }

  /**
   * Puts {@code rendering} on {@code placement}'s surface, replacing whatever it shows, with an
   * action sink bound to that rendering. Runs on the widget thread; a no-op while paused, inert or
   * detached. This is the one way a rendering reaches a surface, for the engine and for tests.
   */
  void present(Placement placement, Rendering rendering) {
    Surface surface = surfaces.get(Objects.requireNonNull(placement, "placement"));
    if (surface == null) {
      throw new IllegalArgumentException(placement + " has no surface on this widget");
    }
    Objects.requireNonNull(rendering, "rendering");
    onWidgetThread(
        () -> {
          if (paused.get() || detached) {
            return;
          }
          synchronized (lock) {
            current.put(placement, rendering);
          }
          surface.show(rendering, new RetailMediaActionSink(this, placement, rendering));
        });
  }

  /** Clears {@code placement} if it still shows {@code rendering}; a no-op otherwise. */
  void clear(Placement placement, Rendering rendering) {
    onWidgetThread(
        () -> {
          synchronized (lock) {
            if (current.get(placement) != rendering) {
              return;
            }
            current.remove(placement);
          }
          surfaces.get(placement).clear();
        });
  }

  private void clearSurfaces() {
    synchronized (lock) {
      current.clear();
    }
    ExecutorService thread = widgetThread;
    if (thread == null) {
      return;
    }
    onWidgetThread(
        () -> {
          for (Surface surface : surfaces.values()) {
            try {
              surface.clear();
            } catch (RuntimeException e) {
              report("clearing a surface", e);
            }
          }
        });
  }

  /** The rendering {@code placement} shows right now, or {@code null}. */
  Rendering current(Placement placement) {
    synchronized (lock) {
      return current.get(placement);
    }
  }

  /** The service handle for the current session, or {@code null} before registration or after. */
  SessionHandle handle() {
    return handle;
  }

  /** The service's view of the session as last pushed, or {@code null} before registration. */
  AdSessionSnapshot snapshot() {
    return snapshot;
  }

  AdDecisionService adService() {
    return adService;
  }

  /**
   * Runs {@code work} on the widget's own single thread — where surfaces are driven and platform
   * calls that may wait belong — and reports, rather than propagates, anything it throws. Dropped
   * after {@link #detach()}.
   */
  void onWidgetThread(Runnable work) {
    ExecutorService thread = widgetThread;
    if (thread == null || detached) {
      return;
    }
    try {
      thread.execute(
          () -> {
            try {
              work.run();
            } catch (RuntimeException e) {
              report("running background work", e);
            }
          });
    } catch (RejectedExecutionException e) {
      // the session ended between the check and the hand-off; nothing left to show it on
    }
  }

  void deliverOffer(Offer offer) {
    deliver(onOffer, offer, "onOffer");
  }

  void deliverInteraction(AdInteraction interaction) {
    deliver(onInteraction, interaction, "onInteraction");
  }

  private <T> void deliver(Consumer<T> handler, T value, String name) {
    WidgetHost owner = host;
    if (handler == null || owner == null) {
      return;
    }
    owner
        .callbackExecutor()
        .execute(
            () -> {
              try {
                handler.accept(value);
              } catch (RuntimeException e) {
                report("delivering " + name, e);
              }
            });
  }

  void report(String what, RuntimeException failure) {
    report(
        new SessionError(
            SessionErrorCode.UNKNOWN,
            "RetailMedia failed " + what + ": " + failure.getMessage(),
            null,
            failure));
  }

  void report(SessionError error) {
    WidgetHost owner = host;
    if (owner != null) {
      owner.reportBackgroundError(error);
    }
  }

  @Override
  public String toString() {
    return "RetailMedia{placements=" + surfaces.keySet() + (paused.get() ? ", paused" : "") + "}";
  }

  /** Builder for {@link RetailMedia}; obtained from {@link RetailMedia#builder()}. */
  public static final class Builder {

    private final Map<Placement, Surface> surfaces = new LinkedHashMap<>();
    private Consumer<Offer> onOffer;
    private Consumer<AdInteraction> onInteraction;
    private AdDecisionService adService;
    private Set<CheckoutPhase> eligiblePhases = DEFAULT_ELIGIBLE_PHASES;
    private Duration decisionTimeout = DEFAULT_DECISION_TIMEOUT;
    private Duration renderingTtl;
    private String sdkVersion;

    private Builder() {}

    /**
     * Binds {@code placement} to {@code surface}. Repeatable, one call per placement, in the order
     * the placements should be considered; at least one is required. A placement bound twice is
     * refused with {@link IllegalArgumentException} — a zone has exactly one place to draw.
     */
    public Builder surface(Placement placement, Surface surface) {
      Objects.requireNonNull(placement, "placement");
      Objects.requireNonNull(surface, "surface");
      if (surfaces.containsKey(placement)) {
        throw new IllegalArgumentException(placement + " already has a surface");
      }
      surfaces.put(placement, surface);
      return this;
    }

    /**
     * Receives every validated {@link Offer} the shopper accepted; the register applies it to its
     * own pricing. The only callback the register must act on. Delivered on the session's callback
     * executor. Optional, but a widget without it can show ads and not honour them.
     */
    public Builder onOffer(Consumer<Offer> onOffer) {
      this.onOffer = onOffer;
      return this;
    }

    /**
     * Receives every shopper-visible ad event as an {@link AdInteraction}, for logging or for the
     * register's own screen. Informational; the SDK measures and reports the same events to the
     * platform whether or not the register listens. Delivered on the session's callback executor.
     */
    public Builder onInteraction(Consumer<AdInteraction> onInteraction) {
      this.onInteraction = onInteraction;
      return this;
    }

    /**
     * The service that registers the session, decides what to show and validates taps. Required in
     * practice in this release: there is no platform-backed default yet, and a widget built without
     * one reports {@link SessionErrorCode#UNSUPPORTED} through {@code onBackgroundError} at attach
     * and stays inert. Use {@link com.bilt.pos.media.service.InMemoryAdDecisionService} for tests
     * and the emulator.
     */
    public Builder adService(AdDecisionService adService) {
      this.adService = adService;
      return this;
    }

    /**
     * The checkout phases in which media is requested and shown; outside them the surfaces stay
     * clear. Default: every phase except {@link CheckoutPhase#COMPLETE}. Must not be empty.
     */
    public Builder eligiblePhases(Set<CheckoutPhase> eligiblePhases) {
      Objects.requireNonNull(eligiblePhases, "eligiblePhases");
      if (eligiblePhases.isEmpty()) {
        throw new IllegalArgumentException("eligiblePhases must not be empty");
      }
      this.eligiblePhases = EnumSet.copyOf(eligiblePhases);
      return this;
    }

    /**
     * How long a decision request may take; a slower answer is a miss and the placement stays blank
     * or falls back. Default 500 ms. Must be positive.
     */
    public Builder decisionTimeout(Duration decisionTimeout) {
      this.decisionTimeout = requirePositive(decisionTimeout, "decisionTimeout");
      return this;
    }

    /**
     * Overrides how long a rendering stays on a surface before it is refreshed, regardless of the
     * TTL the platform put on it. Default: honour each rendering's own {@link Rendering#getTtl()}.
     * Must be positive when set.
     */
    public Builder renderingTtl(Duration renderingTtl) {
      this.renderingTtl =
          renderingTtl == null ? null : requirePositive(renderingTtl, "renderingTtl");
      return this;
    }

    /**
     * The SDK version reported in {@link Capabilities}. Default: this build's version from the
     * {@code bilt-pos-sdk-version.properties} resource Gradle generates, e.g. {@code "0.24.1"}. For
     * a wrapper SDK that wants to report its own release.
     */
    public Builder sdkVersion(String sdkVersion) {
      this.sdkVersion = sdkVersion;
      return this;
    }

    /** Builds the widget. Fails with {@link IllegalArgumentException} when no surface was bound. */
    public RetailMedia build() {
      return new RetailMedia(this);
    }

    private static Duration requirePositive(Duration duration, String name) {
      Objects.requireNonNull(duration, name);
      if (duration.isNegative() || duration.isZero()) {
        throw new IllegalArgumentException(name + " must be positive");
      }
      return duration;
    }
  }
}
