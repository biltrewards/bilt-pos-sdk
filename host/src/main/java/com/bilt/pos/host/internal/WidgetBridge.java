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
import com.bilt.pos.widget.ActionSink;
import com.bilt.pos.widget.Cta;
import com.bilt.pos.widget.MediaSpec;
import com.bilt.pos.widget.Rendering;
import com.bilt.pos.widget.Surface;
import com.bilt.pos.widget.Widget;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
 * placement the client asked for gets an {@link EventSurface} that turns {@code show(rendering)}
 * into a {@code widget.rendering} event and {@code clear()} into {@code widget.clear}, and
 * remembers the rendering's {@link ActionSink} so {@code POST .../widgets/retail-media/actions}
 * can report what the shopper did. Token validation stays in the SDK: an action names a creative
 * and a token, and the sink refuses anything that was not issued.
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
   * Builds the widgets a session creation asked for. {@code widgets} is the request's array, {@code
   * clientCapabilities} its declared rendering formats; the returned bridge's {@link #widgets()}
   * go on the SDK builder.
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
    Set<MediaSpec.MediaType> formats = formats(clientCapabilities);
    for (JsonNode spec : widgets) {
      String type = Json.requireText(spec, "type");
      if (!RETAIL_MEDIA.equals(type)) {
        throw HostError.unsupported("unknown widget type '" + type + "'");
      }
      if (bridge.widgets.containsKey(type)) {
        throw HostError.badRequest("widget '" + type + "' is listed twice");
      }
      if (adService == null) {
        throw HostError.unsupported(
            "this host has no ad decision service, so retail-media widgets are unavailable");
      }
      List<String> placements = Json.strings(spec, "placements");
      if (placements == null || placements.isEmpty()) {
        throw HostError.badRequest("retail-media needs at least one placement");
      }
      RetailMedia.Builder builder =
          RetailMedia.builder()
              .adService(adService)
              .onOffer(offer -> publish.accept("widget.offer", Views.offer(offer)))
              .onInteraction(
                  interaction ->
                      publish.accept("widget.interaction", Views.interaction(interaction)));
      for (String id : placements) {
        Placement placement = Placement.of(id);
        EventSurface surface = new EventSurface(placement, formats, publish);
        bridge.surfaces.put(placement, surface);
        builder.surface(placement, surface);
      }
      bridge.widgets.put(type, builder.build());
    }
    return bridge;
  }

  private static Set<MediaSpec.MediaType> formats(JsonNode clientCapabilities) {
    List<String> names =
        clientCapabilities == null ? null : Json.strings(clientCapabilities, "formats");
    if (names == null) {
      return Collections.unmodifiableSet(EnumSet.allOf(MediaSpec.MediaType.class));
    }
    EnumSet<MediaSpec.MediaType> formats = EnumSet.noneOf(MediaSpec.MediaType.class);
    for (String name : names) {
      formats.add(Json.enumValue(name, "clientCapabilities.formats", MediaSpec.MediaType.class));
    }
    if (formats.isEmpty()) {
      throw HostError.badRequest("clientCapabilities.formats must not be empty");
    }
    return Collections.unmodifiableSet(formats);
  }

  /** The SDK widgets to register on the session builder. */
  public List<Widget> widgets() {
    return new ArrayList<>(widgets.values());
  }

  public ArrayNode view() {
    ArrayNode array = Json.array();
    widgets.forEach(
        (type, widget) -> {
          ObjectNode node = array.addObject();
          node.put("type", type);
          node.put("paused", widget.isPaused());
          ArrayNode placements = node.putArray("placements");
          surfaces.keySet().forEach(placement -> placements.add(placement.getId()));
        });
    return array;
  }

  public Widget widget(String type) {
    Widget widget = widgets.get(type);
    if (widget == null) {
      throw HostError.notFound("widget " + type);
    }
    return widget;
  }

  /**
   * {@code { placement, creativeId, action, token? }} where {@code action} is {@code perform},
   * {@code viewed}, {@code dismissed} or {@code completed}; {@code perform} needs the CTA token.
   */
  public void action(JsonNode body) {
    widget(RETAIL_MEDIA);
    Placement placement = Placement.of(Json.requireText(body, "placement"));
    EventSurface surface = surfaces.get(placement);
    if (surface == null) {
      throw HostError.notFound("placement " + placement.getId());
    }
    surface.action(
        Json.requireText(body, "creativeId"),
        Json.requireText(body, "action"),
        Json.text(body, "token"));
  }

  /** A rendering surface whose display is a browser on the other end of the event stream. */
  static final class EventSurface implements Surface {
    private final Placement placement;
    private final Set<MediaSpec.MediaType> formats;
    private final BiConsumer<String, JsonNode> publish;
    private Rendering current;
    private ActionSink sink;

    EventSurface(
        Placement placement, Set<MediaSpec.MediaType> formats, BiConsumer<String, JsonNode> publish) {
      this.placement = placement;
      this.formats = formats;
      this.publish = publish;
    }

    @Override
    public Set<MediaSpec.MediaType> supportedFormats() {
      return formats;
    }

    @Override
    public SurfaceKind kind() {
      return SurfaceKind.WEB;
    }

    @Override
    public synchronized void show(Rendering rendering, ActionSink actions) {
      current = rendering;
      sink = actions;
      ObjectNode payload = Json.object();
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
      payload.put("placement", placement.getId());
      publish.accept("widget.clear", payload);
    }

    void action(String creativeId, String action, String token) {
      Rendering rendering;
      ActionSink target;
      synchronized (this) {
        rendering = current;
        target = sink;
      }
      if (rendering == null || !rendering.getCreativeId().equals(creativeId)) {
        throw HostError.conflict(
            "creative '" + creativeId + "' is not on display at " + placement.getId());
      }
      switch (action.toLowerCase(Locale.ROOT)) {
        case "perform":
          Cta cta = rendering.ctaForToken(token);
          if (cta == null) {
            throw HostError.unprocessable("token does not belong to creative '" + creativeId + "'");
          }
          target.perform(cta);
          return;
        case "viewed":
          target.viewed(rendering);
          return;
        case "dismissed":
          target.dismissed(rendering);
          return;
        case "completed":
          target.completed(rendering);
          return;
        default:
          throw HostError.badRequest(
              "action must be perform, viewed, dismissed or completed, not '" + action + "'");
      }
    }
  }
}
