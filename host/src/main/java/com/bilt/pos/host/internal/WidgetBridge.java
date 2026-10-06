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

import com.bilt.pos.media.Placement;
import com.bilt.pos.media.RetailMedia;
import com.bilt.pos.media.SurfaceKind;
import com.bilt.pos.media.service.AdDecisionService;
import com.bilt.pos.session.CheckoutPhase;
import com.bilt.pos.session.SessionError;
import com.bilt.pos.session.SessionErrorCode;
import com.bilt.pos.widget.ActionSink;
import com.bilt.pos.widget.Cta;
import com.bilt.pos.widget.MediaSpec;
import com.bilt.pos.widget.Rendering;
import com.bilt.pos.widget.Surface;
import com.bilt.pos.widget.Widget;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Widgets over the wire. The host runs the SDK's widget engine; the browser only renders. Each
 * placement the client configured gets an {@link EventSurface} that turns {@code show(rendering)}
 * into a {@code widget.rendering} event and {@code clear()} into {@code widget.clear}, and
 * remembers the rendering's {@link ActionSink} so {@code POST .../widgets/retail-media/actions} can
 * report what the shopper did. Token validation stays in the SDK; a report the surface cannot
 * honour (a creative no longer on display, a foreign token) becomes a {@code background.error}
 * rather than an HTTP error, so a renderer cannot break the checkout by sending the wrong thing.
 */
public final class WidgetBridge {

  public static final String RETAIL_MEDIA = "retail-media";

  private final Map<String, Widget> widgets = new LinkedHashMap<>();
  private final Map<Placement, EventSurface> surfaces = new LinkedHashMap<>();
  private final BiConsumer<String, JsonNode> publish;

  private WidgetBridge(BiConsumer<String, JsonNode> publish) {
    this.publish = publish;
  }

  /**
   * Builds the widgets a session creation asked for from its {@code widgets} array and {@code
   * clientCapabilities}; the returned bridge's {@link #widgets()} go on the SDK builder.
   */
  public static WidgetBridge from(
      ArrayNode widgets,
      JsonNode clientCapabilities,
      AdDecisionService adService,
      BiConsumer<String, JsonNode> publish) {
    WidgetBridge bridge = new WidgetBridge(publish);
    if (widgets == null) {
      return bridge;
    }
    Set<MediaSpec.MediaType> defaultFormats = formats(clientCapabilities, "clientCapabilities");
    SurfaceKind defaultKind =
        clientCapabilities == null
            ? SurfaceKind.WEB
            : Json.enumValue(clientCapabilities, "surfaceKind", SurfaceKind.class);
    if (defaultKind == null) {
      defaultKind = SurfaceKind.WEB;
    }
    for (JsonNode spec : widgets) {
      String type = Json.requireText(spec, "type");
      if (!RETAIL_MEDIA.equals(type)) {
        throw HostError.badRequest("unknown widget type '" + type + "'");
      }
      if (bridge.widgets.containsKey(type)) {
        throw HostError.badRequest("widget '" + type + "' is listed twice");
      }
      if (adService == null) {
        throw HostError.unsupported(
            "this host has no ad decision service, so retail-media widgets are unavailable");
      }
      ArrayNode placements = Json.array(spec, "placements");
      if (placements == null || placements.isEmpty()) {
        throw HostError.badRequest("retail-media needs at least one placement");
      }
      RetailMedia.Builder builder =
          RetailMedia.builder()
              .adService(adService)
              .onOffer(
                  offer -> {
                    ObjectNode payload = Json.object();
                    payload.put("widget", RETAIL_MEDIA);
                    payload.set("offer", Views.offer(offer));
                    publish.accept("widget.offer", payload);
                  })
              .onInteraction(
                  interaction -> {
                    ObjectNode payload = Json.object();
                    payload.put("widget", RETAIL_MEDIA);
                    payload.set("interaction", Views.interaction(interaction));
                    publish.accept("widget.interaction", payload);
                  });
      for (JsonNode placementSpec : placements) {
        Placement placement = Placement.of(Json.requireText(placementSpec, "id"));
        Set<MediaSpec.MediaType> formats =
            placementSpec.has("formats") ? formats(placementSpec, "placements") : defaultFormats;
        SurfaceKind kind = Json.enumValue(placementSpec, "surfaceKind", SurfaceKind.class);
        EventSurface surface =
            new EventSurface(placement, formats, kind == null ? defaultKind : kind, publish);
        if (bridge.surfaces.put(placement, surface) != null) {
          throw HostError.badRequest("placement '" + placement.getId() + "' is listed twice");
        }
        builder.surface(placement, surface);
      }
      List<String> phases = Json.strings(spec, "eligiblePhases");
      if (phases != null) {
        EnumSet<CheckoutPhase> eligible = EnumSet.noneOf(CheckoutPhase.class);
        for (String phase : phases) {
          eligible.add(Json.enumValue(phase, "eligiblePhases", CheckoutPhase.class));
        }
        if (eligible.isEmpty()) {
          throw HostError.badRequest("eligiblePhases must not be empty");
        }
        builder.eligiblePhases(eligible);
      }
      Duration decisionTimeout = Parsers.duration(spec, "decisionTimeout");
      if (decisionTimeout != null) {
        builder.decisionTimeout(decisionTimeout);
      }
      Duration renderingTtl = Parsers.duration(spec, "renderingTtl");
      if (renderingTtl != null) {
        builder.renderingTtl(renderingTtl);
      }
      try {
        bridge.widgets.put(type, builder.build());
      } catch (IllegalArgumentException e) {
        throw HostError.badRequest("invalid retail-media configuration: " + e.getMessage());
      }
    }
    return bridge;
  }

  private static Set<MediaSpec.MediaType> formats(JsonNode node, String field) {
    List<String> names = node == null ? null : Json.strings(node, "formats");
    if (names == null) {
      return Collections.unmodifiableSet(EnumSet.allOf(MediaSpec.MediaType.class));
    }
    EnumSet<MediaSpec.MediaType> formats = EnumSet.noneOf(MediaSpec.MediaType.class);
    for (String name : names) {
      formats.add(Json.enumValue(name, field + ".formats", MediaSpec.MediaType.class));
    }
    if (formats.isEmpty()) {
      throw HostError.badRequest(field + ".formats must not be empty");
    }
    return Collections.unmodifiableSet(formats);
  }

  /** The SDK widgets to register on the session builder. */
  public List<Widget> widgets() {
    return new ArrayList<>(widgets.values());
  }

  /** The {@code WidgetState} list. */
  public ArrayNode view() {
    ArrayNode array = Json.array();
    widgets.forEach((type, widget) -> array.add(state(type, widget)));
    return array;
  }

  public ObjectNode state(String type) {
    return state(type, widget(type));
  }

  private ObjectNode state(String type, Widget widget) {
    ObjectNode node = Json.object();
    node.put("type", type);
    node.put("paused", widget.isPaused());
    ArrayNode placements = node.putArray("placements");
    surfaces.forEach(
        (placement, surface) -> {
          ObjectNode p = placements.addObject();
          p.put("id", placement.getId());
          p.put("surfaceKind", surface.kind().name());
          ArrayNode formats = p.putArray("formats");
          surface.supportedFormats().forEach(format -> formats.add(format.name()));
        });
    return node;
  }

  public Widget widget(String type) {
    Widget widget = widgets.get(type);
    if (widget == null) {
      throw HostError.notFound("widget " + type);
    }
    return widget;
  }

  /** A {@code WidgetAction}: {@code { kind, creativeId, placement, action?, token? }}. */
  public void action(JsonNode body) {
    widget(RETAIL_MEDIA);
    String kind = Json.requireText(body, "kind");
    String creativeId = Json.requireText(body, "creativeId");
    Placement placement = Placement.of(Json.requireText(body, "placement"));
    EventSurface surface = surfaces.get(placement);
    if (surface == null) {
      throw HostError.notFound("placement " + placement.getId());
    }
    switch (kind.toLowerCase(Locale.ROOT)) {
      case "perform":
      case "viewed":
      case "dismissed":
      case "completed":
        break;
      default:
        throw HostError.badRequest(
            "kind must be perform, viewed, dismissed or completed, not '" + kind + "'");
    }
    surface.action(kind.toLowerCase(Locale.ROOT), creativeId, Json.text(body, "token"));
  }

  /** A rendering surface whose display is a browser on the other end of the event stream. */
  static final class EventSurface implements Surface {
    private final Placement placement;
    private final Set<MediaSpec.MediaType> formats;
    private final SurfaceKind kind;
    private final BiConsumer<String, JsonNode> publish;
    private Rendering current;
    private ActionSink sink;

    EventSurface(
        Placement placement,
        Set<MediaSpec.MediaType> formats,
        SurfaceKind kind,
        BiConsumer<String, JsonNode> publish) {
      this.placement = placement;
      this.formats = formats;
      this.kind = kind;
      this.publish = publish;
    }

    @Override
    public Set<MediaSpec.MediaType> supportedFormats() {
      return formats;
    }

    @Override
    public SurfaceKind kind() {
      return kind;
    }

    @Override
    public synchronized void show(Rendering rendering, ActionSink actions) {
      current = rendering;
      sink = actions;
      ObjectNode payload = Json.object();
      payload.put("widget", RETAIL_MEDIA);
      payload.put("placement", placement.getId());
      payload.set("rendering", Views.rendering(rendering));
      publish.accept("widget.rendering", payload);
    }

    @Override
    public synchronized void clear() {
      if (current == null) {
        return;
      }
      current = null;
      sink = null;
      ObjectNode payload = Json.object();
      payload.put("widget", RETAIL_MEDIA);
      payload.put("placement", placement.getId());
      publish.accept("widget.clear", payload);
    }

    void action(String kind, String creativeId, String token) {
      Rendering rendering;
      ActionSink target;
      synchronized (this) {
        rendering = current;
        target = sink;
      }
      if (rendering == null || !rendering.getCreativeId().equals(creativeId)) {
        reject("creative '" + creativeId + "' is not on display at " + placement.getId());
        return;
      }
      switch (kind) {
        case "perform":
          Cta cta = rendering.ctaForToken(token);
          if (cta == null) {
            reject("token does not belong to creative '" + creativeId + "'");
            return;
          }
          target.perform(cta);
          return;
        case "viewed":
          target.viewed(rendering);
          return;
        case "dismissed":
          target.dismissed(rendering);
          return;
        default:
          target.completed(rendering);
      }
    }

    private void reject(String reason) {
      publish.accept(
          "background.error",
          Views.error(
              new SessionError(
                  SessionErrorCode.INVALID_STATE,
                  "RetailMedia ignored an action on " + placement.getId() + ": " + reason)));
    }
  }
}
