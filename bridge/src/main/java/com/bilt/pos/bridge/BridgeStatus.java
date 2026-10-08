package com.bilt.pos.bridge;

/** A point-in-time snapshot of what the bridge is doing, for the tray menu and {@code /health}. */
public record BridgeStatus(
    String bridgeVersion,
    String sdkVersion,
    String bindAddress,
    int port,
    boolean terminalConfigured,
    int sessionCount,
    boolean sessionHostEmbedded) {

  /** The one-line listening status shown at the top of the tray menu. */
  public String listeningLine() {
    return port > 0 ? "Listening on " + bindAddress + ":" + port : "Not listening";
  }
}
