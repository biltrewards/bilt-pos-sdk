/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.host.internal;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** The host's release, stamped into a resource by the build so {@code /health} can report it. */
public final class HostVersion {

  public static final String UNKNOWN = "unknown";
  private static final String RESOURCE = "/bilt-pos-host-version.properties";
  private static final String VERSION = load();

  private HostVersion() {}

  public static String current() {
    return VERSION;
  }

  private static String load() {
    try (InputStream in = HostVersion.class.getResourceAsStream(RESOURCE)) {
      if (in == null) {
        return UNKNOWN;
      }
      Properties properties = new Properties();
      properties.load(in);
      String version = properties.getProperty("version");
      return version == null || version.isBlank() || version.contains("${") ? UNKNOWN : version;
    } catch (IOException e) {
      return UNKNOWN;
    }
  }
}
