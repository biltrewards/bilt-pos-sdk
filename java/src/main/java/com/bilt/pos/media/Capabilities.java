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
import com.bilt.pos.widget.MediaSpec;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * What this SDK build and this register can render.
 *
 * <p>Capabilities travel with every decision request so the ad platform only serves what can be
 * shown: the media formats the surfaces play, the {@link Action}s the register is prepared to
 * handle, the {@link Placement}s it has surfaces for and the {@link SurfaceKind} behind each. The
 * platform filters its candidates against this before deciding. Should an unsupported creative
 * still arrive — a video for an image-only banner, a {@code SEND_TO_PHONE} button the register did
 * not declare — the SDK does not render it and reports nothing for it; it is treated as a miss.
 *
 * <p>{@link #getSdkVersion()} lets the platform key behaviour to the SDK release rather than
 * probing for features one by one.
 */
public final class Capabilities {

  private final String sdkVersion;
  private final Set<MediaSpec.MediaType> formats;
  private final Set<Action> actions;
  private final Map<Placement, SurfaceKind> surfaces;

  private Capabilities(Builder builder) {
    this.sdkVersion = Objects.requireNonNull(builder.sdkVersion, "sdkVersion");
    EnumSet<MediaSpec.MediaType> formats = EnumSet.noneOf(MediaSpec.MediaType.class);
    formats.addAll(builder.formats);
    EnumSet<Action> actions = EnumSet.noneOf(Action.class);
    actions.addAll(builder.actions);
    this.formats = Collections.unmodifiableSet(formats);
    this.actions = Collections.unmodifiableSet(actions);
    this.surfaces = Collections.unmodifiableMap(new LinkedHashMap<>(builder.surfaces));
  }

  public static Builder builder() {
    return new Builder();
  }

  /** The SDK release these capabilities describe, e.g. {@code "0.24.0"}. */
  public String getSdkVersion() {
    return sdkVersion;
  }

  /** Media formats at least one declared surface can play. Never {@code null}. */
  public Set<MediaSpec.MediaType> getFormats() {
    return formats;
  }

  /** Calls to action the register handles. Never {@code null}. */
  public Set<Action> getActions() {
    return actions;
  }

  /** Placements the register has a surface for. Never {@code null}. */
  public Set<Placement> getPlacements() {
    return surfaces.keySet();
  }

  /** The kind of surface behind each declared placement. Never {@code null}. */
  public Map<Placement, SurfaceKind> getSurfaces() {
    return surfaces;
  }

  /**
   * Whether a rendering's placement is declared here, along with every format and action it needs.
   */
  public boolean supports(com.bilt.pos.widget.Rendering rendering) {
    if (!surfaces.containsKey(Placement.of(rendering.getPlacement()))) {
      return false;
    }
    if (!formats.contains(rendering.getMedia().getType())) {
      return false;
    }
    if (rendering.getCta() != null && !actions.contains(rendering.getCta().getAction())) {
      return false;
    }
    return rendering.getSecondary() == null
        || actions.contains(rendering.getSecondary().getAction());
  }

  public Builder toBuilder() {
    Builder builder = builder().sdkVersion(sdkVersion).formats(formats).actions(actions);
    surfaces.forEach(builder::placement);
    return builder;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Capabilities)) {
      return false;
    }
    Capabilities other = (Capabilities) o;
    return sdkVersion.equals(other.sdkVersion)
        && formats.equals(other.formats)
        && actions.equals(other.actions)
        && surfaces.equals(other.surfaces);
  }

  @Override
  public int hashCode() {
    return Objects.hash(sdkVersion, formats, actions, surfaces);
  }

  @Override
  public String toString() {
    return "Capabilities{sdkVersion='"
        + sdkVersion
        + "', formats="
        + formats
        + ", actions="
        + actions
        + ", surfaces="
        + surfaces
        + "}";
  }

  /** Builder for {@link Capabilities}. */
  public static final class Builder {

    private String sdkVersion;
    private final Set<MediaSpec.MediaType> formats = new LinkedHashSet<>();
    private final Set<Action> actions = new LinkedHashSet<>();
    private final Map<Placement, SurfaceKind> surfaces = new LinkedHashMap<>();

    private Builder() {}

    public Builder sdkVersion(String sdkVersion) {
      this.sdkVersion = sdkVersion;
      return this;
    }

    public Builder format(MediaSpec.MediaType format) {
      formats.add(Objects.requireNonNull(format, "format"));
      return this;
    }

    public Builder formats(Set<MediaSpec.MediaType> formats) {
      this.formats.clear();
      if (formats != null) {
        formats.forEach(this::format);
      }
      return this;
    }

    public Builder action(Action action) {
      actions.add(Objects.requireNonNull(action, "action"));
      return this;
    }

    public Builder actions(Set<Action> actions) {
      this.actions.clear();
      if (actions != null) {
        actions.forEach(this::action);
      }
      return this;
    }

    /** Declares a placement and the kind of surface the register draws it with. */
    public Builder placement(Placement placement, SurfaceKind kind) {
      surfaces.put(
          Objects.requireNonNull(placement, "placement"), Objects.requireNonNull(kind, "kind"));
      return this;
    }

    public Capabilities build() {
      return new Capabilities(this);
    }
  }
}
