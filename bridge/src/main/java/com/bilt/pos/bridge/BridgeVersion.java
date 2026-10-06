package com.bilt.pos.bridge;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** The bridge's own version, stamped from the Gradle {@code VERSION} property at build time. */
public final class BridgeVersion {

  private static final String VERSION = load();

  private BridgeVersion() {}

  /** The release version, or {@code "unknown"} when running from an unstamped classpath. */
  public static String get() {
    return VERSION;
  }

  private static String load() {
    try (InputStream in = BridgeVersion.class.getResourceAsStream("/bridge-version.properties")) {
      if (in == null) {
        return "unknown";
      }
      Properties props = new Properties();
      props.load(in);
      String version = props.getProperty("version", "unknown");
      return version.startsWith("${") ? "unknown" : version;
    } catch (IOException e) {
      return "unknown";
    }
  }
}
