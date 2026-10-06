package com.bilt.pos.bridge;

import com.bilt.pos.host.TerminalClientProvider;
import com.bilt.pos.host.TerminalInfo;
import com.bilt.pos.nexo.client.TerminalClient;
import java.util.List;

/**
 * Hands the Session Host the terminals from the bridge's configuration. It reads through to the
 * {@link Bridge} on every call, so a config reload is visible to the next session without the host
 * being restarted; sessions already holding a client keep the one they started with.
 */
public final class BridgeTerminalProvider implements TerminalClientProvider {

  private final Bridge bridge;

  public BridgeTerminalProvider(Bridge bridge) {
    this.bridge = bridge;
  }

  @Override
  public TerminalClient forPoi(String poiId) {
    return bridge.terminalClient(poiId).orElse(null);
  }

  @Override
  public List<TerminalInfo> terminals() {
    return bridge.terminalIds().stream().map(TerminalInfo::of).toList();
  }
}
