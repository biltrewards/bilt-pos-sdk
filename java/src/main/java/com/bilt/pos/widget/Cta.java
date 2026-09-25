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

import java.util.Objects;

/**
 * A call to action on a {@link Rendering}: the button label the shopper sees, the {@link Action} it
 * requests, and an opaque token.
 *
 * <p>The token is minted by the ad platform for this creative and this session. It carries no
 * meaning to the SDK beyond identity: when the shopper taps, the token is what the widget presents
 * to the platform to prove which button on which creative was tapped, and the platform answers with
 * the offer (or refuses). A surface must hand the token back exactly as received and never
 * fabricate one.
 */
public final class Cta {

  private final String label;
  private final Action action;
  private final String token;

  private Cta(String label, Action action, String token) {
    this.label = Objects.requireNonNull(label, "label");
    this.action = Objects.requireNonNull(action, "action");
    this.token = Objects.requireNonNull(token, "token");
  }

  public static Cta of(String label, Action action, String token) {
    return new Cta(label, action, token);
  }

  /** Button text, already localized by the platform. */
  public String getLabel() {
    return label;
  }

  public Action getAction() {
    return action;
  }

  /** Opaque platform token identifying this button on this creative in this session. */
  public String getToken() {
    return token;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Cta)) {
      return false;
    }
    Cta other = (Cta) o;
    return label.equals(other.label) && action == other.action && token.equals(other.token);
  }

  @Override
  public int hashCode() {
    return Objects.hash(label, action, token);
  }

  @Override
  public String toString() {
    return "Cta{label='" + label + "', action=" + action + ", token='" + token + "'}";
  }
}
