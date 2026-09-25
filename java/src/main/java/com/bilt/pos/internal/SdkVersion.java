/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.internal;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * The release of this SDK build, read once from {@code bilt-pos-sdk-version.properties} on the
 * classpath. Gradle's {@code processResources} expands that resource from the {@code VERSION}
 * property in {@code gradle.properties}, so the value tracks the published artifact without a
 * hand-maintained constant. Outside a Gradle build (an IDE running straight from {@code src}) the
 * placeholder is still in the file and {@link #current()} answers {@value #UNKNOWN} instead.
 */
public final class SdkVersion {

  /** What {@link #current()} returns when the build did not expand the version resource. */
  public static final String UNKNOWN = "unknown";

  private static final String RESOURCE = "/bilt-pos-sdk-version.properties";
  private static final String VERSION = load();

  private SdkVersion() {}

  /** The SDK version, e.g. {@code "0.24.1"}, or {@link #UNKNOWN}. Never {@code null}. */
  public static String current() {
    return VERSION;
  }

  private static String load() {
    try (InputStream in = SdkVersion.class.getResourceAsStream(RESOURCE)) {
      if (in == null) {
        return UNKNOWN;
      }
      Properties properties = new Properties();
      properties.load(in);
      String version = properties.getProperty("version", "").trim();
      return version.isEmpty() || version.contains("${") ? UNKNOWN : version;
    } catch (IOException e) {
      return UNKNOWN;
    }
  }
}
