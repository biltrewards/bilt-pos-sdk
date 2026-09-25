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

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A structured creative ready to be drawn by a {@link Surface}.
 *
 * <p>A rendering is what the ad platform decided to show for one placement in one session: the
 * media asset, the copy, up to two calls to action, how long it stays valid, and the tracking URLs
 * the surface pings when the creative is seen. It is the unit that crosses the widget-to-surface
 * seam and the unit the web bridge serializes, so its shape is stable and fully described by the
 * {@code bridge-messages.schema.json} resource next to this package.
 *
 * <p>A rendering never carries PII. Personalization happened on the platform; what arrives here is
 * an anonymous creative plus opaque CTA tokens, and it may be logged, cached and shipped to a web
 * page without further care.
 *
 * <p>Instances are immutable. {@link #getTtl()} is the platform's freshness bound: a surface still
 * showing a rendering past its TTL should expect a replacement or a {@link Surface#clear()} from
 * the widget and must not act on its CTAs afterwards.
 */
public final class Rendering {

  private final String creativeId;
  private final String placement;
  private final MediaSpec media;
  private final String headline;
  private final String body;
  private final Cta cta;
  private final Cta secondary;
  private final Duration ttl;
  private final Map<String, URI> tracking;

  private Rendering(Builder builder) {
    this.creativeId = Objects.requireNonNull(builder.creativeId, "creativeId");
    this.placement = Objects.requireNonNull(builder.placement, "placement");
    this.media = Objects.requireNonNull(builder.media, "media");
    this.headline = Objects.requireNonNull(builder.headline, "headline");
    this.body = builder.body;
    this.cta = builder.cta;
    this.secondary = builder.secondary;
    Objects.requireNonNull(builder.ttl, "ttl");
    if (builder.ttl.isNegative() || builder.ttl.isZero()) {
      throw new IllegalArgumentException("ttl must be positive");
    }
    if (builder.cta != null
        && builder.secondary != null
        && builder.cta.getToken().equals(builder.secondary.getToken())) {
      throw new IllegalArgumentException("cta and secondary must carry distinct tokens");
    }
    this.ttl = builder.ttl;
    this.tracking =
        builder.tracking == null
            ? Collections.emptyMap()
            : Collections.unmodifiableMap(new LinkedHashMap<>(builder.tracking));
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Platform identifier of the creative; the key every interaction is reported against. */
  public String getCreativeId() {
    return creativeId;
  }

  /** Identifier of the placement this rendering was decided for, e.g. {@code "lane-banner"}. */
  public String getPlacement() {
    return placement;
  }

  public MediaSpec getMedia() {
    return media;
  }

  public String getHeadline() {
    return headline;
  }

  /** Supporting copy, or {@code null}. */
  public String getBody() {
    return body;
  }

  /** The primary call to action, or {@code null} when the creative is display-only. */
  public Cta getCta() {
    return cta;
  }

  /** A secondary call to action, or {@code null}. */
  public Cta getSecondary() {
    return secondary;
  }

  /** How long the platform considers this rendering fresh. Always positive. */
  public Duration getTtl() {
    return ttl;
  }

  /**
   * Tracking beacons keyed by event name, e.g. {@code "impression"} and {@code "viewability"}.
   * Never {@code null}; may be empty.
   */
  public Map<String, URI> getTracking() {
    return tracking;
  }

  /**
   * Returns the CTA carrying {@code token}, or {@code null} if neither call to action on this
   * rendering does. This is the lookup a surface or sink performs to validate an inbound tap.
   */
  public Cta ctaForToken(String token) {
    if (token == null) {
      return null;
    }
    if (cta != null && cta.getToken().equals(token)) {
      return cta;
    }
    if (secondary != null && secondary.getToken().equals(token)) {
      return secondary;
    }
    return null;
  }

  public Builder toBuilder() {
    return builder()
        .creativeId(creativeId)
        .placement(placement)
        .media(media)
        .headline(headline)
        .body(body)
        .cta(cta)
        .secondary(secondary)
        .ttl(ttl)
        .tracking(tracking);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Rendering)) {
      return false;
    }
    Rendering other = (Rendering) o;
    return creativeId.equals(other.creativeId)
        && placement.equals(other.placement)
        && media.equals(other.media)
        && headline.equals(other.headline)
        && Objects.equals(body, other.body)
        && Objects.equals(cta, other.cta)
        && Objects.equals(secondary, other.secondary)
        && ttl.equals(other.ttl)
        && tracking.equals(other.tracking);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        creativeId, placement, media, headline, body, cta, secondary, ttl, tracking);
  }

  @Override
  public String toString() {
    return "Rendering{creativeId='"
        + creativeId
        + "', placement='"
        + placement
        + "', media="
        + media
        + ", headline='"
        + headline
        + "'}";
  }

  /** Builder for {@link Rendering}. */
  public static final class Builder {

    private String creativeId;
    private String placement;
    private MediaSpec media;
    private String headline;
    private String body;
    private Cta cta;
    private Cta secondary;
    private Duration ttl;
    private Map<String, URI> tracking;

    private Builder() {}

    public Builder creativeId(String creativeId) {
      this.creativeId = creativeId;
      return this;
    }

    public Builder placement(String placement) {
      this.placement = placement;
      return this;
    }

    public Builder media(MediaSpec media) {
      this.media = media;
      return this;
    }

    public Builder headline(String headline) {
      this.headline = headline;
      return this;
    }

    public Builder body(String body) {
      this.body = body;
      return this;
    }

    /** The primary call to action. */
    public Builder cta(Cta cta) {
      this.cta = cta;
      return this;
    }

    public Builder secondary(Cta secondary) {
      this.secondary = secondary;
      return this;
    }

    /** Freshness bound; must be positive. */
    public Builder ttl(Duration ttl) {
      this.ttl = ttl;
      return this;
    }

    public Builder tracking(Map<String, URI> tracking) {
      this.tracking = tracking == null ? null : new LinkedHashMap<>(tracking);
      return this;
    }

    public Builder addTracking(String event, URI url) {
      if (tracking == null) {
        tracking = new LinkedHashMap<>();
      }
      tracking.put(Objects.requireNonNull(event, "event"), Objects.requireNonNull(url, "url"));
      return this;
    }

    public Rendering build() {
      return new Rendering(this);
    }
  }
}
