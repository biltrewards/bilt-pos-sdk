package com.bilt.pos.bridge.tray;

import java.io.IOException;
import java.util.Optional;

/**
 * Registers the bridge to start when the user logs in. Each platform has its own mechanism; the
 * tray shows the toggle only where an implementation reports itself {@linkplain #supported()
 * supported}.
 */
public interface LoginItem {

  /** Whether this platform and this launch (packaged, not {@code gradle run}) can be registered. */
  boolean supported();

  /** Why it is not supported, for the tooltip. */
  Optional<String> unsupportedReason();

  /** Whether the bridge is currently registered to start at login. */
  boolean enabled();

  /** Registers or unregisters the bridge. */
  void setEnabled(boolean enabled) throws IOException;

  /** For platforms without an implementation yet. */
  static LoginItem unsupported(String reason) {
    return new LoginItem() {
      @Override
      public boolean supported() {
        return false;
      }

      @Override
      public Optional<String> unsupportedReason() {
        return Optional.of(reason);
      }

      @Override
      public boolean enabled() {
        return false;
      }

      @Override
      public void setEnabled(boolean enabled) {
        throw new UnsupportedOperationException(reason);
      }
    };
  }
}
