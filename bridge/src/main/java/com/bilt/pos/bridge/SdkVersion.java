package com.bilt.pos.bridge;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** The embedded Java SDK's version, from the resource {@code :java} stamps at build time. */
final class SdkVersion {

  private static final String VERSION = load();

  private SdkVersion() {}

  static String get() {
    return VERSION;
  }

  private static String load() {
    try (InputStream in =
        SdkVersion.class.getResourceAsStream("/bilt-pos-sdk-version.properties")) {
      if (in == null) {
        return "unknown";
      }
      Properties props = new Properties();
      props.load(in);
      return props.getProperty("version", "unknown");
    } catch (IOException e) {
      return "unknown";
    }
  }
}
