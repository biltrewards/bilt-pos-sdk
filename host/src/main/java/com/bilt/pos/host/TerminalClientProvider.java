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
import java.util.List;

/**
 * Where the host gets its terminals from.
 *
 * <p>The host knows nothing about how a terminal is reached — addresses, certificates and the
 * payload passphrase belong to the embedding application: the Terminal Bridge pulls them from its
 * paired configuration, the cloud service from its relay, a test from a scripted double. The host
 * asks this provider for the {@link TerminalClient} behind a {@code poiId} when a {@code terminal}
 * session is created or a session-less device operation is requested, and lists {@link
 * #terminals()} on {@code GET /v1/terminals} and in the health report.
 *
 * <p>{@link #forPoi(String)} returns {@code null} for a terminal the provider does not know, which
 * the host turns into a 404. Implementations decide whether to share one client across sessions or
 * hand out a fresh one per call; the host never closes what it is given.
 */
public interface TerminalClientProvider {

  /** The client that reaches the terminal with this {@code poiId}, or {@code null} if unknown. */
  TerminalClient forPoi(String poiId);

  /** The terminals this host can reach, as advertised to clients. */
  List<TerminalInfo> terminals();

  /** A provider with no terminals: {@code local} sessions still work, {@code terminal} ones 404. */
  static TerminalClientProvider none() {
    return new TerminalClientProvider() {
      @Override
      public TerminalClient forPoi(String poiId) {
        return null;
      }

      @Override
      public List<TerminalInfo> terminals() {
        return List.of();
      }
    };
  }
}
