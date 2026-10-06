package com.bilt.pos.bridge;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Routes {@code java.util.logging} to stderr, to a rotating file under the app's log directory, and
 * to an in-memory ring of the most recent lines that the diagnostics summary includes.
 */
public final class BridgeLogging {

  private static final int FILE_LIMIT_BYTES = 1_000_000;
  private static final int FILE_COUNT = 5;
  private static final int RECENT_LINES = 200;

  private final Path logDir;
  private final RingHandler recent = new RingHandler(RECENT_LINES);

  private BridgeLogging(Path logDir) {
    this.logDir = logDir;
  }

  /**
   * Installs the handlers on the root logger. The file handler is skipped if the dir is unusable.
   */
  public static BridgeLogging configure(Path logDir) {
    LogManager.getLogManager().reset();
    Logger root = Logger.getLogger("");
    root.setLevel(Level.INFO);
    Formatter formatter = new LineFormatter();

    ConsoleHandler console = new ConsoleHandler();
    console.setFormatter(formatter);
    console.setLevel(Level.ALL);
    root.addHandler(console);

    BridgeLogging logging = new BridgeLogging(logDir);
    logging.recent.setFormatter(formatter);
    root.addHandler(logging.recent);

    try {
      Files.createDirectories(logDir);
      FileHandler file =
          new FileHandler(
              logDir.resolve("bridge-%g.log").toString(), FILE_LIMIT_BYTES, FILE_COUNT, true);
      file.setFormatter(formatter);
      file.setLevel(Level.ALL);
      root.addHandler(file);
    } catch (IOException e) {
      Logger.getLogger(BridgeLogging.class.getName())
          .log(Level.WARNING, "Cannot write logs under " + logDir + ": " + e.getMessage());
    }
    return logging;
  }

  /** The directory the rotating files live in. */
  public Path logDir() {
    return logDir;
  }

  /** The most recent log lines, oldest first. */
  public List<String> recentLines() {
    return recent.lines();
  }

  /** One line per record: timestamp, level, logger, message, then any stack trace. */
  static final class LineFormatter extends Formatter {
    private static final DateTimeFormatter TIME =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    @Override
    public String format(LogRecord record) {
      StringBuilder sb = new StringBuilder();
      sb.append(TIME.format(Instant.ofEpochMilli(record.getMillis())))
          .append(' ')
          .append(record.getLevel().getName())
          .append(' ')
          .append(shortName(record.getLoggerName()))
          .append(" - ")
          .append(formatMessage(record))
          .append(System.lineSeparator());
      if (record.getThrown() != null) {
        StringWriter sw = new StringWriter();
        record.getThrown().printStackTrace(new PrintWriter(sw));
        sb.append(sw);
      }
      return sb.toString();
    }

    private static String shortName(String logger) {
      if (logger == null) {
        return "-";
      }
      int dot = logger.lastIndexOf('.');
      return dot < 0 ? logger : logger.substring(dot + 1);
    }
  }

  /** Keeps the last {@code capacity} formatted records. */
  static final class RingHandler extends Handler {
    private final Deque<String> lines = new ArrayDeque<>();
    private final int capacity;

    RingHandler(int capacity) {
      this.capacity = capacity;
      setLevel(Level.ALL);
    }

    @Override
    public synchronized void publish(LogRecord record) {
      if (!isLoggable(record)) {
        return;
      }
      String text = getFormatter() == null ? record.getMessage() : getFormatter().format(record);
      lines.addLast(text.stripTrailing());
      while (lines.size() > capacity) {
        lines.removeFirst();
      }
    }

    synchronized List<String> lines() {
      return new ArrayList<>(lines);
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}
  }
}
