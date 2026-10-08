/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.host;

import com.bilt.pos.nexo.client.TerminalClient;
import java.util.Objects;

/**
 * Where the host gets its terminal from.
 *
 * <p>A host is a bridge between one register and one terminal. It knows nothing about how that
 * terminal is reached — address, certificate and payload passphrase belong to the embedding
 * application: the Terminal Bridge pulls them from its configuration, a test from a scripted
 * double. The host asks this provider for the {@link TerminalClient} when a {@code terminal}
 * session is created or a session-less device operation is requested, and reports {@link #info()}
 * on {@code GET /v1/terminal} and in the health report.
 *
 * <p>Whatever {@code poiId} a request names is passed through as the Nexo {@code POIID} of the
 * messages sent to this one terminal; it never selects a terminal. {@link #client()} returns {@code
 * null} when no terminal is configured, and the host then serves {@code local} sessions only.
 * Implementations decide whether to share one client across sessions or hand out a fresh one per
 * call; the host never closes what it is given.
 */
public interface TerminalClientProvider {

  /** The client that reaches the terminal, or {@code null} when none is configured. */
  TerminalClient client();

  /** What clients are told about the terminal; asked only while {@link #client()} has one. */
  default TerminalInfo info() {
    return TerminalInfo.of(null, null);
  }

  /**
   * A provider with no terminal: {@code local} sessions still work, {@code terminal} ones do not.
   */
  static TerminalClientProvider none() {
    return () -> null;
  }

  /** A provider that always hands out {@code client}, described to clients as {@code info}. */
  static TerminalClientProvider of(TerminalClient client, TerminalInfo info) {
    Objects.requireNonNull(client, "client");
    Objects.requireNonNull(info, "info");
    return new TerminalClientProvider() {
      @Override
      public TerminalClient client() {
        return client;
      }

      @Override
      public TerminalInfo info() {
        return info;
      }
    };
  }
}
