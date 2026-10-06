package com.bilt.pos.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AppDirsTest {

  private static final Path HOME = Path.of("/Users/cashier");

  @Test
  void macOsUsesLibraryFolders() {
    AppDirs dirs = AppDirs.detect("Mac OS X", HOME, Map.of(), null);

    assertEquals(
        HOME.resolve("Library/Application Support/Bilt Terminal Bridge"), dirs.configDir());
    assertEquals(
        HOME.resolve("Library/Application Support/Bilt Terminal Bridge/config.json"),
        dirs.configFile());
    assertEquals(HOME.resolve("Library/Logs/Bilt Terminal Bridge"), dirs.logDir());
  }

  @Test
  void windowsUsesAppDataFolders() {
    Path home = Path.of("C:\\Users\\cashier");
    AppDirs dirs =
        AppDirs.detect(
            "Windows 11",
            home,
            Map.of(
                "APPDATA", "C:\\Users\\cashier\\AppData\\Roaming",
                "LOCALAPPDATA", "C:\\Users\\cashier\\AppData\\Local"),
            null);

    assertEquals(
        Path.of("C:\\Users\\cashier\\AppData\\Roaming", "Bilt Terminal Bridge"), dirs.configDir());
    assertEquals(
        Path.of("C:\\Users\\cashier\\AppData\\Local", "Bilt Terminal Bridge", "Logs"),
        dirs.logDir());
  }

  @Test
  void windowsFallsBackWhenEnvIsMissing() {
    AppDirs dirs = AppDirs.detect("Windows 10", HOME, Map.of(), null);

    assertEquals(HOME.resolve("AppData/Roaming/Bilt Terminal Bridge"), dirs.configDir());
    assertEquals(HOME.resolve("AppData/Local/Bilt Terminal Bridge/Logs"), dirs.logDir());
  }

  @Test
  void linuxFollowsXdgWithDefaults() {
    AppDirs defaults = AppDirs.detect("Linux", HOME, Map.of(), null);
    assertEquals(HOME.resolve(".config/bilt-terminal-bridge"), defaults.configDir());
    assertEquals(HOME.resolve(".local/state/bilt-terminal-bridge/logs"), defaults.logDir());

    AppDirs xdg =
        AppDirs.detect(
            "Linux",
            HOME,
            Map.of("XDG_CONFIG_HOME", "/etc/xdg-user", "XDG_STATE_HOME", "/var/state-user"),
            null);
    assertEquals(Path.of("/etc/xdg-user/bilt-terminal-bridge"), xdg.configDir());
    assertEquals(Path.of("/var/state-user/bilt-terminal-bridge/logs"), xdg.logDir());
  }

  @Test
  void overridePinsBothDirectories() {
    AppDirs dirs = AppDirs.detect("Mac OS X", HOME, Map.of(), "/tmp/bridge-dev");

    assertEquals(Path.of("/tmp/bridge-dev"), dirs.configDir());
    assertEquals(Path.of("/tmp/bridge-dev/logs"), dirs.logDir());
  }
}
