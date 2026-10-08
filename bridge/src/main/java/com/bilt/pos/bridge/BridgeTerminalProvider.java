package com.bilt.pos.bridge;

import com.bilt.pos.host.TerminalClientProvider;
import com.bilt.pos.host.TerminalInfo;
import com.bilt.pos.nexo.client.TerminalClient;

/**
 * Hands the Session Host the terminal from the bridge's configuration. It reads through to the
 * {@link Bridge} on every call, so a config reload is visible to the next session without the host
 * being restarted; sessions already holding a client keep the one they started with.
 */
public final class BridgeTerminalProvider implements TerminalClientProvider {

  private final Bridge bridge;

  public BridgeTerminalProvider(Bridge bridge) {
    this.bridge = bridge;
  }

  @Override
  public TerminalClient client() {
    return bridge.terminalClient().orElse(null);
  }

  @Override
  public TerminalInfo info() {
    return bridge
        .config()
        .terminal()
        .map(t -> TerminalInfo.of(t.label().orElse(null), t.model().orElse(null)))
        .orElseGet(() -> TerminalInfo.of(null, null));
  }
}
