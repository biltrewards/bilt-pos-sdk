/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.widget;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.logging.Logger;

/**
 * A {@link Surface} that draws through a browser control the register already embeds.
 *
 * <p>The SDK has no dependency on any platform's WebView. A register subclasses this class and
 * wires three things to its own control: {@link #loadUrl} navigates it, {@link #evaluateJavascript}
 * runs a script in the loaded page, and the platform's JavaScript interface (an {@code
 * addJavascriptInterface} object on Android, a {@code WKScriptMessageHandler} on iOS, a CEF query
 * handler on desktop) forwards each string the page posts to {@link #onBridgeMessage}. Everything
 * else — the bridge protocol, ready-gating, token checks — is implemented here once.
 *
 * <h2>Bridge protocol</h2>
 *
 * <p>Outbound, the SDK calls {@code window.BiltMedia.receive(message)} on the page with one of:
 *
 * <pre>{@code
 * { "type": "rendering", "rendering": { "creativeId": "crt_91ad", "placement": "lane-banner",
 *     "media": { "type": "video", "url": "https://...", "durationMs": 15000, "poster": "https://..." },
 *     "headline": "...", "body": "...",
 *     "cta": { "label": "Apply offer", "action": "APPLY_OFFER", "token": "act_..." },
 *     "secondary": { "label": "Text me this", "action": "SEND_TO_PHONE", "token": "act_..." },
 *     "ttlMs": 30000, "tracking": { "impression": "https://...", "viewability": "https://..." } } }
 * { "type": "clear" }
 * }</pre>
 *
 * <p>Inbound, the page posts one of:
 *
 * <pre>{@code
 * { "type": "ready" }
 * { "type": "action", "creativeId": "crt_91ad", "action": "APPLY_OFFER", "token": "act_..." }
 * { "type": "viewed",    "creativeId": "crt_91ad" }
 * { "type": "dismissed", "creativeId": "crt_91ad" }
 * { "type": "completed", "creativeId": "crt_91ad" }
 * }</pre>
 *
 * <p>Both directions are specified by {@code /com/bilt/pos/widget/bridge-messages.schema.json} on
 * the SDK's classpath. Inbound messages are allow-listed by type; an {@code action} must name the
 * creative currently on display and carry one of its own CTA tokens, and {@code viewed}, {@code
 * dismissed} and {@code completed} must name the current creative. Only the first {@code viewed}
 * for each shown rendering counts, so a page that reloads and sees the rendering again cannot
 * report a second view. A rendering's TTL runs from the {@link #show} that delivered it; once it
 * has passed, the rendering's CTAs are no longer acted on even if the widget's replacement or clear
 * has not yet arrived. Anything else — malformed JSON, an unknown type, a foreign or stale token,
 * an expired CTA, a repeated view — is dropped and reported through {@link #onBridgeError}, never
 * thrown into the platform callback and never passed to the {@link ActionSink}.
 *
 * <h2>Lifecycle</h2>
 *
 * <p>The renderer page is loaded lazily on the first {@link #show}. Until the page posts {@code
 * ready}, the latest rendering (or a clear) is held and pushed once the page announces itself;
 * after that, shows and clears are evaluated directly. A page that reloads and posts {@code ready}
 * again receives the current rendering again, so a browser crash or navigation heals itself, unless
 * that rendering's TTL has passed, in which case the page is left empty for the widget's next show.
 * A host that preloads the page before the first show simply forwards the early {@code ready}.
 *
 * <h2>Threading</h2>
 *
 * <p>{@link #show} and {@link #clear} arrive on the widget's executor, {@link #onBridgeMessage} on
 * whatever thread the platform delivers JavaScript messages on. The class serializes its own state
 * and calls {@link #loadUrl}, {@link #evaluateJavascript} and the sink with no lock held. Outbound
 * calls are issued one at a time in the order of the state changes that produced them, even when a
 * {@code ready} races a show or clear; a subclass is responsible for marshaling them, in that
 * order, to the thread its browser control requires and should return without waiting for the
 * result.
 */
public abstract class WebSurface implements Surface {

  private static final Logger LOGGER = Logger.getLogger(WebSurface.class.getName());

  private final String rendererPageUrl;
  private final LongSupplier nanoClock;
  private final Object lock = new Object();

  private boolean pageRequested;
  private boolean pageReady;
  private Rendering current;
  private ActionSink sink;
  private boolean viewedReported;
  private long shownAtNanos;

  // Browser calls are queued under the lock alongside the state change they reflect and drained by
  // one thread at a time outside it, so a script chosen earlier can never reach the page later.
  private final ArrayDeque<Runnable> outbox = new ArrayDeque<>();
  private boolean draining;

  /** Creates a surface that will show {@code rendererPageUrl} in the host's browser control. */
  protected WebSurface(String rendererPageUrl) {
    this(rendererPageUrl, System::nanoTime);
  }

  WebSurface(String rendererPageUrl, LongSupplier nanoClock) {
    this.rendererPageUrl = Objects.requireNonNull(rendererPageUrl, "rendererPageUrl");
    this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
  }

  /**
   * Navigates the browser control to {@code url}. Called with no lock held; may be asynchronous. If
   * it throws, the exception propagates out of the {@link #show} that triggered it and the next
   * show tries the load again.
   */
  protected abstract void loadUrl(String url);

  /** Runs {@code script} in the loaded page. Called with no lock held; may be asynchronous. */
  protected abstract void evaluateJavascript(String script);

  /**
   * Called for every inbound message the bridge drops: malformed JSON, an unknown type, a message
   * for a creative that is not on display, a foreign token, a CTA past its TTL, a repeated view, or
   * a sink that threw. {@code rawJson} is the message as received (possibly {@code null}). Called
   * with no lock held, on the thread that delivered the message, so a subclass may hand the error
   * to the widget synchronously. The default logs a {@code java.util.logging} warning; a subclass
   * may route it to the register's own error channel. Must not throw.
   */
  protected void onBridgeError(String reason, String rawJson) {
    LOGGER.warning("WebSurface dropped a bridge message: " + reason + " -- " + rawJson);
  }

  @Override
  public final void show(Rendering rendering, ActionSink actions) {
    Objects.requireNonNull(rendering, "rendering");
    Objects.requireNonNull(actions, "actions");
    synchronized (lock) {
      current = rendering;
      sink = actions;
      viewedReported = false;
      shownAtNanos = nanoClock.getAsLong();
      if (!pageRequested) {
        pageRequested = true;
        outbox.add(this::loadRendererPage);
      } else if (pageReady) {
        enqueueScript(BridgeMessages.renderingScript(rendering));
      }
    }
    drainOutbox();
  }

  @Override
  public final void clear() {
    synchronized (lock) {
      if (current == null) {
        return;
      }
      current = null;
      sink = null;
      if (pageReady) {
        enqueueScript(BridgeMessages.clearScript());
      }
    }
    drainOutbox();
  }

  private void loadRendererPage() {
    try {
      loadUrl(rendererPageUrl);
    } catch (RuntimeException | Error e) {
      synchronized (lock) {
        if (!pageReady) {
          pageRequested = false;
        }
      }
      throw e;
    }
  }

  private void enqueueScript(String script) {
    outbox.add(() -> evaluateJavascript(script));
  }

  private void drainOutbox() {
    synchronized (lock) {
      if (draining) {
        return;
      }
      draining = true;
    }
    while (true) {
      Runnable call;
      synchronized (lock) {
        call = outbox.poll();
        if (call == null) {
          draining = false;
          return;
        }
      }
      try {
        call.run();
      } catch (RuntimeException | Error e) {
        // Release the drain so the calls still queued go out on the next state change.
        synchronized (lock) {
          draining = false;
        }
        throw e;
      }
    }
  }

  /**
   * Entry point for the platform's JavaScript interface: the page posted {@code json}. Parses,
   * validates and dispatches the message as described in the class documentation. Never throws.
   */
  protected final void onBridgeMessage(String json) {
    try {
      dispatch(json);
    } catch (RuntimeException e) {
      onBridgeError(e.getClass().getSimpleName() + ": " + e.getMessage(), json);
    }
  }

  private void dispatch(String json) {
    JsonNode message;
    try {
      message = BridgeMessages.parse(json);
    } catch (IllegalArgumentException e) {
      onBridgeError(e.getMessage(), json);
      return;
    }
    String type = BridgeMessages.text(message, "type");
    if (type == null) {
      onBridgeError("missing type", json);
      return;
    }
    switch (type) {
      case BridgeMessages.TYPE_READY:
        onPageReady();
        return;
      case BridgeMessages.TYPE_ACTION:
        onAction(message, json);
        return;
      case BridgeMessages.TYPE_VIEWED:
      case BridgeMessages.TYPE_DISMISSED:
      case BridgeMessages.TYPE_COMPLETED:
        onLifecycleEvent(type, message, json);
        return;
      default:
        onBridgeError("unknown message type '" + type + "'", json);
    }
  }

  private void onPageReady() {
    synchronized (lock) {
      pageRequested = true;
      pageReady = true;
      if (current != null && !currentExpired()) {
        enqueueScript(BridgeMessages.renderingScript(current));
      }
    }
    drainOutbox();
  }

  private void onAction(JsonNode message, String json) {
    String creativeId = BridgeMessages.text(message, "creativeId");
    String actionName = BridgeMessages.text(message, "action");
    String token = BridgeMessages.text(message, "token");
    if (creativeId == null || actionName == null || token == null) {
      onBridgeError("action requires creativeId, action and token", json);
      return;
    }
    Cta cta = null;
    ActionSink target = null;
    String rejection;
    synchronized (lock) {
      rejection = notOnDisplay(creativeId);
      if (rejection == null && currentExpired()) {
        rejection = "creative '" + creativeId + "' is past its TTL";
      }
      if (rejection == null) {
        cta = current.ctaForToken(token);
        target = sink;
      }
    }
    if (rejection != null) {
      onBridgeError(rejection, json);
      return;
    }
    if (cta == null) {
      onBridgeError("token does not belong to creative '" + creativeId + "'", json);
      return;
    }
    if (!cta.getAction().name().equals(actionName)) {
      onBridgeError(
          "action '" + actionName + "' does not match the token's " + cta.getAction(), json);
      return;
    }
    target.perform(cta);
  }

  /** Must be called with the lock held. Returns the rejection reason, or null if on display. */
  private String notOnDisplay(String creativeId) {
    return current != null && current.getCreativeId().equals(creativeId)
        ? null
        : "creative '" + creativeId + "' is not on display";
  }

  /** Must be called with the lock held and {@code current} non-null. */
  private boolean currentExpired() {
    Duration shownFor = Duration.ofNanos(nanoClock.getAsLong() - shownAtNanos);
    return shownFor.compareTo(current.getTtl()) >= 0;
  }

  private void onLifecycleEvent(String type, JsonNode message, String json) {
    String creativeId = BridgeMessages.text(message, "creativeId");
    if (creativeId == null) {
      onBridgeError(type + " requires creativeId", json);
      return;
    }
    Rendering rendering = null;
    ActionSink target = null;
    String rejection;
    synchronized (lock) {
      rejection = notOnDisplay(creativeId);
      if (rejection == null && type.equals(BridgeMessages.TYPE_VIEWED)) {
        if (viewedReported) {
          rejection = "creative '" + creativeId + "' was already reported viewed";
        } else {
          viewedReported = true;
        }
      }
      if (rejection == null) {
        rendering = current;
        target = sink;
      }
    }
    if (rejection != null) {
      onBridgeError(rejection, json);
      return;
    }
    switch (type) {
      case BridgeMessages.TYPE_VIEWED:
        target.viewed(rendering);
        return;
      case BridgeMessages.TYPE_DISMISSED:
        target.dismissed(rendering);
        return;
      default:
        target.completed(rendering);
    }
  }
}
