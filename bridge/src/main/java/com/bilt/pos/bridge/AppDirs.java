package com.bilt.pos.bridge;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * Where the bridge keeps its configuration and logs on each desktop platform.
 *
 * <p>macOS uses {@code ~/Library/Application Support} and {@code ~/Library/Logs}, Windows {@code
 * %APPDATA%} and {@code %LOCALAPPDATA%}, and everything else the XDG base directories. The {@code
 * bilt.bridge.dir} system property overrides both with a single directory, which is handy for
 * development runs and tests.
 */
public final class AppDirs {

  /** The product name as it appears in user-visible directory names. */
  public static final String APP_NAME = "Bilt Terminal Bridge";

  /** The lowercase, hyphenated form used on Linux. */
  public static final String APP_SLUG = "bilt-terminal-bridge";

  /** System property that pins both directories to one location. */
  public static final String OVERRIDE_PROPERTY = "bilt.bridge.dir";

  private final Path configDir;
  private final Path logDir;

  private AppDirs(Path configDir, Path logDir) {
    this.configDir = configDir;
    this.logDir = logDir;
  }

  /** The directories for the current platform and user. */
  public static AppDirs detect() {
    return detect(
        System.getProperty("os.name", ""),
        Path.of(System.getProperty("user.home")),
        System.getenv(),
        System.getProperty(OVERRIDE_PROPERTY));
  }

  /** Resolves the directories for the given platform; exposed for tests. */
  static AppDirs detect(String osName, Path home, Map<String, String> env, String override) {
    if (override != null && !override.isBlank()) {
      Path base = Path.of(override).toAbsolutePath();
      return new AppDirs(base, base.resolve("logs"));
    }
    String os = osName.toLowerCase(Locale.ROOT);
    if (os.contains("mac") || os.contains("darwin")) {
      Path library = home.resolve("Library");
      return new AppDirs(
          library.resolve("Application Support").resolve(APP_NAME),
          library.resolve("Logs").resolve(APP_NAME));
    }
    if (os.contains("win")) {
      Path roaming = envPath(env, "APPDATA", home.resolve("AppData").resolve("Roaming"));
      Path local = envPath(env, "LOCALAPPDATA", home.resolve("AppData").resolve("Local"));
      return new AppDirs(roaming.resolve(APP_NAME), local.resolve(APP_NAME).resolve("Logs"));
    }
    Path config = envPath(env, "XDG_CONFIG_HOME", home.resolve(".config"));
    Path state = envPath(env, "XDG_STATE_HOME", home.resolve(".local").resolve("state"));
    return new AppDirs(config.resolve(APP_SLUG), state.resolve(APP_SLUG).resolve("logs"));
  }

  private static Path envPath(Map<String, String> env, String name, Path fallback) {
    String value = env.get(name);
    return value == null || value.isBlank() ? fallback : Path.of(value);
  }

  /** The directory holding {@code config.json}. */
  public Path configDir() {
    return configDir;
  }

  /** The configuration file itself. */
  public Path configFile() {
    return configDir.resolve("config.json");
  }

  /** The directory the rotating log files are written to. */
  public Path logDir() {
    return logDir;
  }

  @Override
  public String toString() {
    return "AppDirs{config=" + configDir + ", logs=" + logDir + "}";
  }
}
