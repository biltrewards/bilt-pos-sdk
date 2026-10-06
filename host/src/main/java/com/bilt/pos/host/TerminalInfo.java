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
 * What a client is told about a terminal: its {@code poiId}, optionally the device model and the
 * host's last known reachability. Deliberately not the address, certificate or passphrase — the
 * page that drives a checkout never sees how the terminal is reached.
 */
public final class TerminalInfo {

  private final String poiId;
  private final String model;
  private final Boolean reachable;

  private TerminalInfo(String poiId, String model, Boolean reachable) {
    this.poiId = Objects.requireNonNull(poiId, "poiId");
    if (poiId.isEmpty()) {
      throw new IllegalArgumentException("poiId must not be empty");
    }
    this.model = model;
    this.reachable = reachable;
  }

  /** A terminal known only by its {@code poiId}. */
  public static TerminalInfo of(String poiId) {
    return new TerminalInfo(poiId, null, null);
  }

  /** A terminal with its model for the register's terminal picker. */
  public static TerminalInfo of(String poiId, String model) {
    return new TerminalInfo(poiId, model, null);
  }

  /** The same terminal with a cached reachability verdict; {@code null} means unknown. */
  public TerminalInfo withReachable(Boolean reachable) {
    return new TerminalInfo(poiId, model, reachable);
  }

  public String poiId() {
    return poiId;
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
    return poiId.equals(that.poiId)
        && Objects.equals(model, that.model)
        && Objects.equals(reachable, that.reachable);
  }

  @Override
  public int hashCode() {
    return Objects.hash(poiId, model, reachable);
  }

  @Override
  public String toString() {
    return "TerminalInfo{" + poiId + (model == null ? "" : ", " + model) + "}";
  }
}
