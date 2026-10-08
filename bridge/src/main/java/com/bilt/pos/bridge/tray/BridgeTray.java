package com.bilt.pos.bridge.tray;

import com.bilt.pos.bridge.AppDirs;
import com.bilt.pos.bridge.Bridge;
import com.bilt.pos.bridge.BridgeStatus;
import com.bilt.pos.bridge.config.BridgeConfigException;
import java.awt.AWTException;
import java.awt.CheckboxMenuItem;
import java.awt.Desktop;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The menu-bar presence: status lines, the config and log shortcuts, diagnostics, the login item
 * toggle and Quit. AWT's {@link PopupMenu} has no "about to open" event, so the status lines are
 * refreshed on a short timer as well as after every reload.
 */
public final class BridgeTray {

  private static final Logger LOG = Logger.getLogger(BridgeTray.class.getName());

  private final Bridge bridge;
  private final AppDirs dirs;
  private final Supplier<String> diagnostics;
  private final LoginItem loginItem;
  private final Runnable quit;

  private final MenuItem listening = new MenuItem();
  private final MenuItem terminal = new MenuItem();
  private final MenuItem sessions = new MenuItem();
  private final MenuItem configError = new MenuItem();
  private final CheckboxMenuItem startAtLogin = new CheckboxMenuItem("Start at login");
  private final ScheduledExecutorService refresher =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "bridge-tray-refresh");
            t.setDaemon(true);
            return t;
          });
  private TrayIcon icon;

  public BridgeTray(
      Bridge bridge,
      AppDirs dirs,
      Supplier<String> diagnostics,
      LoginItem loginItem,
      Runnable quit) {
    this.bridge = bridge;
    this.dirs = dirs;
    this.diagnostics = diagnostics;
    this.loginItem = loginItem;
    this.quit = quit;
  }

  /** Whether a tray can be shown at all in this session. */
  public static boolean available() {
    try {
      return !java.awt.GraphicsEnvironment.isHeadless() && SystemTray.isSupported();
    } catch (RuntimeException | LinkageError e) {
      return false;
    }
  }

  /** Adds the icon to the system tray. */
  public void install() throws AWTException {
    PopupMenu menu = new PopupMenu();
    for (MenuItem status : new MenuItem[] {listening, terminal, sessions, configError}) {
      status.setEnabled(false);
      menu.add(status);
    }
    menu.addSeparator();
    menu.add(action("Open config file", this::openConfig));
    menu.add(action("Reload config", bridge::reload));
    menu.add(action("Open logs folder", this::openLogs));
    menu.add(action("Copy diagnostics", this::copyDiagnostics));
    menu.addSeparator();
    startAtLogin.setEnabled(loginItem.supported());
    startAtLogin.setState(loginItem.supported() && loginItem.enabled());
    if (!loginItem.supported()) {
      startAtLogin.setLabel(
          "Start at login (" + loginItem.unsupportedReason().orElse("unavailable") + ")");
    }
    startAtLogin.addItemListener(e -> toggleLoginItem());
    menu.add(startAtLogin);
    menu.addSeparator();
    menu.add(action("Quit " + AppDirs.APP_NAME, quit));

    icon = new TrayIcon(TrayGlyph.image(), AppDirs.APP_NAME, menu);
    icon.setImageAutoSize(true);
    refresh();
    SystemTray.getSystemTray().add(icon);
    bridge.addListener((config, error) -> refresh());
    refresher.scheduleWithFixedDelay(this::refresh, 2, 2, TimeUnit.SECONDS);
  }

  /** Removes the icon; safe to call more than once. */
  public void remove() {
    refresher.shutdownNow();
    if (icon != null) {
      SystemTray.getSystemTray().remove(icon);
      icon = null;
    }
  }

  private void refresh() {
    BridgeStatus status = bridge.status();
    listening.setLabel(status.listeningLine());
    terminal.setLabel(
        status.terminalConfigured() ? "Terminal configured" : "No terminal configured");
    sessions.setLabel("Sessions active: " + status.sessionCount());
    Optional<String> error = bridge.lastConfigError();
    configError.setLabel(error.map(e -> "Config error: " + truncate(e)).orElse(""));
    if (icon != null) {
      icon.setToolTip(
          AppDirs.APP_NAME + " " + status.bridgeVersion() + "\n" + status.listeningLine());
    }
  }

  private void openConfig() {
    Optional<String> location = bridge.source().location();
    if (location.isEmpty()) {
      return;
    }
    Path file = Path.of(location.get());
    try {
      if (!Files.exists(file)) {
        // Reload refuses to invent a config, so the user's explicit ask is what recreates the
        // starter; the file watcher then reloads it like any other change to the file.
        bridge.source().load();
      }
      if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
        Desktop.getDesktop().open(file.toFile());
      } else {
        LOG.info("Config file: " + file);
      }
    } catch (BridgeConfigException | IOException | RuntimeException e) {
      LOG.log(Level.WARNING, "Cannot open " + file, e);
    }
  }

  private void openLogs() {
    Path dir = dirs.logDir();
    try {
      Files.createDirectories(dir);
      if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
        Desktop.getDesktop().open(dir.toFile());
      } else {
        LOG.info("Log folder: " + dir);
      }
    } catch (IOException | RuntimeException e) {
      LOG.log(Level.WARNING, "Cannot open " + dir, e);
    }
  }

  private void copyDiagnostics() {
    try {
      String text = diagnostics.get();
      Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
      LOG.info("Diagnostics copied to the clipboard (" + text.length() + " chars)");
    } catch (RuntimeException e) {
      LOG.log(Level.WARNING, "Cannot copy diagnostics", e);
    }
  }

  private void toggleLoginItem() {
    boolean wanted = startAtLogin.getState();
    try {
      loginItem.setEnabled(wanted);
      LOG.info(wanted ? "Registered to start at login" : "Removed the start-at-login registration");
    } catch (IOException | RuntimeException e) {
      LOG.log(Level.WARNING, "Cannot change the start-at-login registration", e);
      startAtLogin.setState(loginItem.enabled());
    }
  }

  private static MenuItem action(String label, Runnable run) {
    MenuItem item = new MenuItem(label);
    item.addActionListener(e -> run.run());
    return item;
  }

  private static String truncate(String s) {
    return s.length() <= 60 ? s : s.substring(0, 57) + "...";
  }
}
