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
 * What a client is told about a terminal: its {@code poiId}, and optionally a human label and the
 * device model. Deliberately not the address, certificate or passphrase — the page that drives a
 * checkout never sees how the terminal is reached.
 */
public final class TerminalInfo {

  private final String poiId;
  private final String label;
  private final String model;

  private TerminalInfo(String poiId, String label, String model) {
    this.poiId = Objects.requireNonNull(poiId, "poiId");
    if (poiId.isEmpty()) {
      throw new IllegalArgumentException("poiId must not be empty");
    }
    this.label = label;
    this.model = model;
  }

  /** A terminal known only by its {@code poiId}. */
  public static TerminalInfo of(String poiId) {
    return new TerminalInfo(poiId, null, null);
  }

  /** A terminal with a label and model for display in the register's terminal picker. */
  public static TerminalInfo of(String poiId, String label, String model) {
    return new TerminalInfo(poiId, label, model);
  }

  public String poiId() {
    return poiId;
  }

  public String label() {
    return label;
  }

  public String model() {
    return model;
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
        && Objects.equals(label, that.label)
        && Objects.equals(model, that.model);
  }

  @Override
  public int hashCode() {
    return Objects.hash(poiId, label, model);
  }

  @Override
  public String toString() {
    return "TerminalInfo{" + poiId + (label == null ? "" : ", " + label) + "}";
  }
}
