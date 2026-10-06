package com.bilt.pos.bridge.tray;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * The macOS login item: a per-user LaunchAgent plist in {@code ~/Library/LaunchAgents} that runs
 * the packaged launcher at login. Writing the file is enough; launchd picks it up at the next
 * login, so no {@code launchctl} call is needed and nothing runs with elevated rights.
 *
 * <p>The launcher path comes from the {@code jpackage.app-path} property the jpackage launcher
 * sets, so a development {@code gradle run} reports itself unsupported rather than registering a
 * Gradle-started JVM.
 */
public final class MacLaunchAgent implements LoginItem {

  static final String LABEL = "com.bilt.pos.bridge";

  private final Path plist;
  private final Optional<Path> launcher;

  /** The agent for the current user and the running launcher. */
  public static LoginItem detect() {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (!os.contains("mac")) {
      return LoginItem.unsupported("Start at login is only implemented on macOS so far");
    }
    Path home = Path.of(System.getProperty("user.home"));
    String appPath = System.getProperty("jpackage.app-path");
    return new MacLaunchAgent(
        home.resolve("Library").resolve("LaunchAgents").resolve(LABEL + ".plist"),
        Optional.ofNullable(appPath).map(Path::of));
  }

  MacLaunchAgent(Path plist, Optional<Path> launcher) {
    this.plist = plist;
    this.launcher = launcher;
  }

  @Override
  public boolean supported() {
    return launcher.isPresent();
  }

  @Override
  public Optional<String> unsupportedReason() {
    return launcher.isPresent()
        ? Optional.empty()
        : Optional.of("Start at login needs the packaged app, not a development run");
  }

  @Override
  public boolean enabled() {
    return Files.exists(plist);
  }

  @Override
  public void setEnabled(boolean enabled) throws IOException {
    if (!enabled) {
      Files.deleteIfExists(plist);
      return;
    }
    Files.createDirectories(plist.getParent());
    Files.writeString(plist, plistFor(launcher.orElseThrow()), StandardCharsets.UTF_8);
  }

  /** The plist document; {@code ProcessType Interactive} keeps it a normal user-session agent. */
  static String plistFor(Path launcher) {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
        <plist version="1.0">
        <dict>
          <key>Label</key>
          <string>%s</string>
          <key>ProgramArguments</key>
          <array>
            <string>%s</string>
          </array>
          <key>RunAtLoad</key>
          <true/>
          <key>KeepAlive</key>
          <false/>
          <key>ProcessType</key>
          <string>Interactive</string>
        </dict>
        </plist>
        """
        .formatted(LABEL, escapeXml(launcher.toString()));
  }

  private static String escapeXml(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
