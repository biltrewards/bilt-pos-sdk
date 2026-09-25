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
import java.util.Objects;

/**
 * The media asset of a {@link Rendering}: what kind it is, where to fetch it, and for video how
 * long it runs and what to show before it starts.
 *
 * <p>URLs are signed, short-lived CDN links issued with the creative; a surface fetches them
 * directly and does not cache across renderings. {@link #getDuration()} and {@link #getPoster()}
 * are meaningful for {@link MediaType#VIDEO} and {@code null} otherwise.
 */
public final class MediaSpec {

  /** The kinds of media a surface may be asked to draw. */
  public enum MediaType {
    /** A still image (PNG, JPEG, WebP). */
    IMAGE,
    /** A video clip; {@link MediaSpec#getDuration()} says how long. */
    VIDEO,
    /** A self-contained HTML document to embed; only web-capable surfaces accept it. */
    HTML
  }

  private final MediaType type;
  private final URI url;
  private final Duration duration;
  private final URI poster;

  private MediaSpec(Builder builder) {
    this.type = Objects.requireNonNull(builder.type, "type");
    this.url = Objects.requireNonNull(builder.url, "url");
    if (builder.duration != null && (builder.duration.isNegative() || builder.duration.isZero())) {
      throw new IllegalArgumentException("duration must be positive");
    }
    this.duration = builder.duration;
    this.poster = builder.poster;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Shorthand for a still image. */
  public static MediaSpec image(URI url) {
    return builder().type(MediaType.IMAGE).url(url).build();
  }

  /** Shorthand for a video clip with a known running time. */
  public static MediaSpec video(URI url, Duration duration) {
    return builder().type(MediaType.VIDEO).url(url).duration(duration).build();
  }

  /** Shorthand for an embeddable HTML document. */
  public static MediaSpec html(URI url) {
    return builder().type(MediaType.HTML).url(url).build();
  }

  public MediaType getType() {
    return type;
  }

  public URI getUrl() {
    return url;
  }

  /** Running time of a video, or {@code null}. */
  public Duration getDuration() {
    return duration;
  }

  /** Still frame to show before a video starts, or {@code null}. */
  public URI getPoster() {
    return poster;
  }

  public Builder toBuilder() {
    return builder().type(type).url(url).duration(duration).poster(poster);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof MediaSpec)) {
      return false;
    }
    MediaSpec other = (MediaSpec) o;
    return type == other.type
        && url.equals(other.url)
        && Objects.equals(duration, other.duration)
        && Objects.equals(poster, other.poster);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, url, duration, poster);
  }

  @Override
  public String toString() {
    return "MediaSpec{type=" + type + ", url=" + url + ", duration=" + duration + "}";
  }

  /** Builder for {@link MediaSpec}. */
  public static final class Builder {

    private MediaType type;
    private URI url;
    private Duration duration;
    private URI poster;

    private Builder() {}

    public Builder type(MediaType type) {
      this.type = type;
      return this;
    }

    public Builder url(URI url) {
      this.url = url;
      return this;
    }

    /** Running time of a video; must be positive when set. */
    public Builder duration(Duration duration) {
      this.duration = duration;
      return this;
    }

    public Builder poster(URI poster) {
      this.poster = poster;
      return this;
    }

    public MediaSpec build() {
      return new MediaSpec(this);
    }
  }
}
