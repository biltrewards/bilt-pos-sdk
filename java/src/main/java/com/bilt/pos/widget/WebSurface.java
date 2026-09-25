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
import java.util.Objects;
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
 * dismissed} and {@code completed} must name the current creative. Anything else — malformed JSON,
 * an unknown type, a foreign or stale token — is dropped and reported through {@link
 * #onBridgeError}, never thrown into the platform callback and never passed to the {@link
 * ActionSink}.
 *
 * <h2>Lifecycle</h2>
 *
 * <p>The renderer page is loaded lazily on the first {@link #show}. Until the page posts {@code
 * ready}, the latest rendering (or a clear) is held and pushed once the page announces itself;
 * after that, shows and clears are evaluated directly. A page that reloads and posts {@code ready}
 * again receives the current rendering again, so a browser crash or navigation heals itself. A host
 * that preloads the page before the first show simply forwards the early {@code ready}.
 *
 * <h2>Threading</h2>
 *
 * <p>{@link #show} and {@link #clear} arrive on the widget's executor, {@link #onBridgeMessage} on
 * whatever thread the platform delivers JavaScript messages on. The class serializes its own state
 * and calls {@link #loadUrl}, {@link #evaluateJavascript} and the sink with no lock held; a
 * subclass is responsible for marshaling the two outbound calls to the thread its browser control
 * requires and should return without waiting for the result.
 */
public abstract class WebSurface implements Surface {

  private static final Logger LOGGER = Logger.getLogger(WebSurface.class.getName());

  private final String rendererPageUrl;
  private final Object lock = new Object();

  private boolean pageRequested;
  private boolean pageReady;
  private Rendering current;
  private ActionSink sink;

  /** Creates a surface that will show {@code rendererPageUrl} in the host's browser control. */
  protected WebSurface(String rendererPageUrl) {
    this.rendererPageUrl = Objects.requireNonNull(rendererPageUrl, "rendererPageUrl");
  }

  /**
   * Navigates the browser control to {@code url}. Called with no lock held; may be asynchronous.
   */
  protected abstract void loadUrl(String url);

  /** Runs {@code script} in the loaded page. Called with no lock held; may be asynchronous. */
  protected abstract void evaluateJavascript(String script);

  /**
   * Called for every inbound message the bridge drops: malformed JSON, an unknown type, a message
   * for a creative that is not on display, a foreign token, or a sink that threw. {@code rawJson}
   * is the message as received (possibly {@code null}). The default logs a {@code
   * java.util.logging} warning; a subclass may route it to the register's own error channel. Must
   * not throw.
   */
  protected void onBridgeError(String reason, String rawJson) {
    LOGGER.warning("WebSurface dropped a bridge message: " + reason + " -- " + rawJson);
  }

  @Override
  public final void show(Rendering rendering, ActionSink actions) {
    Objects.requireNonNull(rendering, "rendering");
    Objects.requireNonNull(actions, "actions");
    boolean load = false;
    String script = null;
    synchronized (lock) {
      current = rendering;
      sink = actions;
      if (!pageRequested) {
        pageRequested = true;
        load = true;
      } else if (pageReady) {
        script = BridgeMessages.renderingScript(rendering);
      }
    }
    if (load) {
      loadUrl(rendererPageUrl);
    }
    if (script != null) {
      evaluateJavascript(script);
    }
  }

  @Override
  public final void clear() {
    String script = null;
    synchronized (lock) {
      if (current == null) {
        return;
      }
      current = null;
      sink = null;
      if (pageReady) {
        script = BridgeMessages.clearScript();
      }
    }
    if (script != null) {
      evaluateJavascript(script);
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
    String script;
    synchronized (lock) {
      pageRequested = true;
      pageReady = true;
      script = current == null ? null : BridgeMessages.renderingScript(current);
    }
    if (script != null) {
      evaluateJavascript(script);
    }
  }

  private void onAction(JsonNode message, String json) {
    String creativeId = BridgeMessages.text(message, "creativeId");
    String actionName = BridgeMessages.text(message, "action");
    String token = BridgeMessages.text(message, "token");
    if (creativeId == null || actionName == null || token == null) {
      onBridgeError("action requires creativeId, action and token", json);
      return;
    }
    Cta cta;
    ActionSink target;
    synchronized (lock) {
      if (current == null || !current.getCreativeId().equals(creativeId)) {
        onBridgeError("creative '" + creativeId + "' is not on display", json);
        return;
      }
      cta = current.ctaForToken(token);
      target = sink;
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

  private void onLifecycleEvent(String type, JsonNode message, String json) {
    String creativeId = BridgeMessages.text(message, "creativeId");
    if (creativeId == null) {
      onBridgeError(type + " requires creativeId", json);
      return;
    }
    Rendering rendering;
    ActionSink target;
    synchronized (lock) {
      if (current == null || !current.getCreativeId().equals(creativeId)) {
        onBridgeError("creative '" + creativeId + "' is not on display", json);
        return;
      }
      rendering = current;
      target = sink;
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
