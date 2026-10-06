package com.bilt.pos.bridge.tray;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MacLaunchAgentTest {

  @Test
  void writesAndRemovesPlistForPackagedLauncher(@TempDir Path dir) throws Exception {
    Path plist = dir.resolve("LaunchAgents").resolve("com.bilt.pos.bridge.plist");
    Path launcher =
        Path.of("/Applications/Bilt Terminal Bridge.app/Contents/MacOS/Bilt Terminal Bridge");
    MacLaunchAgent agent = new MacLaunchAgent(plist, Optional.of(launcher));

    assertTrue(agent.supported());
    assertFalse(agent.enabled());

    agent.setEnabled(true);
    assertTrue(agent.enabled());
    String xml = Files.readString(plist);
    assertTrue(xml.contains("<string>com.bilt.pos.bridge</string>"), xml);
    assertTrue(xml.contains("<string>" + launcher + "</string>"), xml);
    assertTrue(xml.contains("<key>RunAtLoad</key>\n  <true/>"), xml);

    agent.setEnabled(false);
    assertFalse(agent.enabled());
    assertFalse(Files.exists(plist));
  }

  @Test
  void developmentRunIsUnsupported(@TempDir Path dir) {
    MacLaunchAgent agent = new MacLaunchAgent(dir.resolve("x.plist"), Optional.empty());

    assertFalse(agent.supported());
    assertTrue(agent.unsupportedReason().orElseThrow().contains("packaged app"));
  }

  @Test
  void escapesXmlInLauncherPath() {
    String xml = MacLaunchAgent.plistFor(Path.of("/Apps/A&B/launcher"));
    assertTrue(xml.contains("/Apps/A&amp;B/launcher"), xml);
    assertEquals(-1, xml.indexOf("A&B"));
  }
}
