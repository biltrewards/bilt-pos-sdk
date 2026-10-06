package com.bilt.pos.bridge.config;

/** A configuration that could not be read or failed validation. The message names the problem. */
public class BridgeConfigException extends Exception {

  public BridgeConfigException(String message) {
    super(message);
  }

  public BridgeConfigException(String message, Throwable cause) {
    super(message, cause);
  }
}
