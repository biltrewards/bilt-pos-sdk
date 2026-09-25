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

import java.util.Objects;

/**
 * A named zone on the register where an ad may be shown.
 *
 * <p>Placements come from the ad platform's placement map for a device model: the platform knows
 * that a given register has, say, a {@code "lane-banner"} beside the cart and a {@code
 * "pinpad-interstitial"} between tender and receipt, and it targets creatives at those names. The
 * SDK does not interpret the id; it passes it through on decision requests and interaction reports,
 * and it is the string {@code Rendering.getPlacement()} echoes back.
 *
 * <p>A banner beside the cart is the expected common case. A full-screen interstitial is one
 * placement among others rather than a different mode: it is decided, rendered and reported like
 * any other zone, with the register's {@code Surface} for that placement deciding how much screen
 * it takes.
 *
 * <p>Instances have value semantics: two placements with the same id are equal.
 */
public final class Placement {

  private final String id;

  private Placement(String id) {
    this.id = Objects.requireNonNull(id, "id");
    if (id.isEmpty()) {
      throw new IllegalArgumentException("placement id must not be empty");
    }
  }

  public static Placement of(String id) {
    return new Placement(id);
  }

  /** The platform's identifier for this zone, e.g. {@code "lane-banner"}. */
  public String getId() {
    return id;
  }

  @Override
  public boolean equals(Object o) {
    return this == o || (o instanceof Placement && id.equals(((Placement) o).id));
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return "Placement{" + id + "}";
  }
}
