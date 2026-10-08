/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.host;

import java.util.Objects;

/**
 * What a client is told about the host's terminal: optionally a label, the device model and the
 * host's last known reachability. Deliberately not the address, certificate or passphrase — the
 * page that drives a checkout never sees how the terminal is reached — and no {@code poiId}, which
 * is whatever each session or request names.
 */
public final class TerminalInfo {

  private final String label;
  private final String model;
  private final Boolean reachable;

  private TerminalInfo(String label, String model, Boolean reachable) {
    this.label = label;
    this.model = model;
    this.reachable = reachable;
  }

  /** A terminal with a human-readable label and model, either of which may be {@code null}. */
  public static TerminalInfo of(String label, String model) {
    return new TerminalInfo(label, model, null);
  }

  /** The same terminal with a cached reachability verdict; {@code null} means unknown. */
  public TerminalInfo withReachable(Boolean reachable) {
    return new TerminalInfo(label, model, reachable);
  }

  public String label() {
    return label;
  }

  public String model() {
    return model;
  }

  /**
   * The host's last known reachability, a cached view rather than a probe; {@code null} if unknown.
   */
  public Boolean reachable() {
    return reachable;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof TerminalInfo)) {
      return false;
    }
    TerminalInfo that = (TerminalInfo) other;
    return Objects.equals(label, that.label)
        && Objects.equals(model, that.model)
        && Objects.equals(reachable, that.reachable);
  }

  @Override
  public int hashCode() {
    return Objects.hash(label, model, reachable);
  }

  @Override
  public String toString() {
    return "TerminalInfo{label=" + label + ", model=" + model + ", reachable=" + reachable + "}";
  }
}
