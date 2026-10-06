package com.bilt.pos.bridge;

import com.bilt.pos.bridge.config.FileBridgeConfigSource;
import com.bilt.pos.bridge.tray.BridgeTray;
import com.bilt.pos.bridge.tray.MacLaunchAgent;
import java.awt.AWTException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Entry point. Starts the bridge, then either sits in the menu bar or, where no tray exists (SSH
 * sessions, CI, servers), runs as a plain foreground process that logs until it is signalled.
 *
 * <p>Accepts {@code --config <file>} to read a specific file; otherwise the platform location from
 * {@link AppDirs} is used.
 */
public final class BridgeMain {

  private static final Logger LOG = Logger.getLogger(BridgeMain.class.getName());

  private BridgeMain() {}

  public static void main(String[] args) throws Exception {
    AppDirs dirs = AppDirs.detect();
    BridgeLogging logging = BridgeLogging.configure(dirs.logDir());
    LOG.info(AppDirs.APP_NAME + " " + BridgeVersion.get() + " (SDK " + SdkVersion.get() + ")");
    LOG.info(dirs.toString());

    Path configFile = configFileFrom(args).orElse(dirs.configFile());
    FileBridgeConfigSource source = new FileBridgeConfigSource(configFile);
    Bridge bridge = new Bridge(source);
    try {
      bridge.start();
    } catch (IOException e) {
      LOG.log(Level.SEVERE, "Cannot start the listener: " + e.getMessage());
      System.exit(2);
    }

    CountDownLatch stop = new CountDownLatch(1);
    AtomicBoolean closed = new AtomicBoolean();
    Runnable shutdown =
        () -> {
          if (closed.compareAndSet(false, true)) {
            LOG.info("Shutting down");
            bridge.close();
          }
          stop.countDown();
        };
    Runtime.getRuntime().addShutdownHook(new Thread(shutdown, "bridge-shutdown"));

    BridgeTray tray = null;
    if (BridgeTray.available()) {
      tray =
          new BridgeTray(
              bridge,
              dirs,
              () ->
                  Diagnostics.render(
                      bridge.status(),
                      Optional.ofNullable(bridge.config()),
                      source.location(),
                      bridge.lastConfigError(),
                      dirs,
                      logging.recentLines()),
              MacLaunchAgent.detect(),
              shutdown);
      try {
        tray.install();
        LOG.info("Tray icon installed");
      } catch (AWTException | RuntimeException e) {
        LOG.log(Level.WARNING, "No tray icon; running in the foreground: " + e.getMessage());
        tray = null;
      }
    } else {
      LOG.info("No system tray in this session; running in the foreground (Ctrl-C to stop)");
    }

    stop.await();
    if (tray != null) {
      tray.remove();
    }
    System.exit(0);
  }

  private static Optional<Path> configFileFrom(String[] args) {
    for (int i = 0; i < args.length - 1; i++) {
      if (args[i].equals("--config")) {
        return Optional.of(Path.of(args[i + 1]).toAbsolutePath());
      }
    }
    return Optional.empty();
  }
}
