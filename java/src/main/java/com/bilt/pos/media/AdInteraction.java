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

import com.bilt.pos.widget.Action;
import java.time.Instant;
import java.util.Objects;

/**
 * One thing the shopper did with a creative, reported to the ad platform for measurement and
 * frequency capping.
 *
 * <p>An interaction names the creative and the placement it was shown in, what happened ({@link
 * Kind}), when, and — for tap-driven kinds — which {@link Action} was requested. It carries no PII
 * and no basket data; the platform correlates it with the session it already knows about.
 */
public final class AdInteraction {

  /** What happened. */
  public enum Kind {
    /** The rendering was handed to a surface. */
    SHOWN,
    /** The surface reported the creative as seen under its viewability rule. */
    VIEWED,
    /** The shopper tapped a call to action; {@link AdInteraction#getAction()} says which. */
    TAPPED,
    /** The platform accepted the tapped CTA; for {@code APPLY_OFFER} an offer was issued. */
    CTA_ACCEPTED,
    /** A send-to-phone was requested and handed to the platform. */
    SEND_TO_PHONE_REQUESTED,
    /** The shopper closed the creative, or the widget cleared it on a dismiss CTA. */
    DISMISSED,
    /** The creative's video played to the end. */
    COMPLETED
  }

  private final String creativeId;
  private final Placement placement;
  private final Kind kind;
  private final Instant timestamp;
  private final Action action;

  private AdInteraction(Builder builder) {
    this.creativeId = Objects.requireNonNull(builder.creativeId, "creativeId");
    this.placement = Objects.requireNonNull(builder.placement, "placement");
    this.kind = Objects.requireNonNull(builder.kind, "kind");
    this.timestamp = builder.timestamp == null ? Instant.now() : builder.timestamp;
    this.action = builder.action;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Shorthand for an interaction stamped now with no action. */
  public static AdInteraction of(String creativeId, Placement placement, Kind kind) {
    return builder().creativeId(creativeId).placement(placement).kind(kind).build();
  }

  public String getCreativeId() {
    return creativeId;
  }

  public Placement getPlacement() {
    return placement;
  }

  public Kind getKind() {
    return kind;
  }

  public Instant getTimestamp() {
    return timestamp;
  }

  /** The call to action involved, for tap-driven kinds; {@code null} otherwise. */
  public Action getAction() {
    return action;
  }

  public Builder toBuilder() {
    return builder()
        .creativeId(creativeId)
        .placement(placement)
        .kind(kind)
        .timestamp(timestamp)
        .action(action);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof AdInteraction)) {
      return false;
    }
    AdInteraction other = (AdInteraction) o;
    return creativeId.equals(other.creativeId)
        && placement.equals(other.placement)
        && kind == other.kind
        && timestamp.equals(other.timestamp)
        && action == other.action;
  }

  @Override
  public int hashCode() {
    return Objects.hash(creativeId, placement, kind, timestamp, action);
  }

  @Override
  public String toString() {
    return "AdInteraction{"
        + kind
        + " creativeId='"
        + creativeId
        + "', placement="
        + placement.getId()
        + (action == null ? "" : ", action=" + action)
        + ", at="
        + timestamp
        + "}";
  }

  /** Builder for {@link AdInteraction}. */
  public static final class Builder {

    private String creativeId;
    private Placement placement;
    private Kind kind;
    private Instant timestamp;
    private Action action;

    private Builder() {}

    public Builder creativeId(String creativeId) {
      this.creativeId = creativeId;
      return this;
    }

    public Builder placement(Placement placement) {
      this.placement = placement;
      return this;
    }

    public Builder kind(Kind kind) {
      this.kind = kind;
      return this;
    }

    /** Defaults to the build time. */
    public Builder timestamp(Instant timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    public Builder action(Action action) {
      this.action = action;
      return this;
    }

    public AdInteraction build() {
      return new AdInteraction(this);
    }
  }
}
