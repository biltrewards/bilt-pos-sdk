import { InstallBridgePrompt, type BridgeState } from '@bilt/pos-react/bridge';
import type { TerminalInfo } from '@bilt/pos-sdk';
import { useState, type ReactNode } from 'react';
import { useController, useEmulatorState } from '../emulator/context';
import { canOperateTerminal, sessionOperationInProgress } from '../emulator/state';
import type { Settings } from '../settings';
import { LabeledCheckbox } from './common';

function describeTerminal(bridge: BridgeState, terminal: TerminalInfo | undefined): string {
  if (!bridge.health) return 'Terminal: unknown until the bridge answers';
  if (!terminal) return 'No terminal configured: local sessions only';
  const name = [terminal.label, terminal.model].filter(Boolean).join(' · ') || 'configured';
  const reach =
    terminal.reachable === undefined ? '' : terminal.reachable ? ' · reachable' : ' · unreachable';
  return `Terminal: ${name}${reach}`;
}

function bridgeLabel(bridge: BridgeState): string {
  switch (bridge.status) {
    case 'detecting':
      return 'Bridge: detecting…';
    case 'missing':
      return 'Bridge: not found';
    case 'outdated':
      return `Bridge: outdated (${bridge.health?.hostVersion ?? '?'})`;
    case 'ready':
      return `Bridge: ready at ${bridge.baseUrl ?? '?'} (${bridge.health?.hostVersion ?? '?'})`;
  }
}

/**
 * The desktop's `ConnectionPanel`: reaching the terminal on the first row, driving the checkout
 * on the second. Terminal IP, adb tunnel, Encrypt and the passphrase are the bridge's own
 * configuration in the browser, so the first row carries the bridge status, the terminal picked
 * from the bridge's `/health`, the bridge port and the Local session toggle instead.
 */
export function ConnectionPanel({
  settings,
  onSettings,
  bridge,
  guideUrl,
  manifestUrl,
}: {
  settings: Settings;
  onSettings: (next: Settings) => void;
  bridge: BridgeState;
  guideUrl: string;
  manifestUrl?: string;
}): ReactNode {
  const controller = useController();
  const state = useEmulatorState();
  const [identifyOnStart, setIdentifyOnStart] = useState(false);
  const connected = state.connection.phase !== 'DISCONNECTED';
  const sessionActive = state.sessionId !== null;
  const terminal = bridge.health?.terminal;
  const canConnect = bridge.status === 'ready';
  const set = <K extends keyof Settings>(key: K, value: Settings[K]) =>
    onSettings({ ...settings, [key]: value });

  return (
    <section className="card connection" aria-label="Connection">
      <div className="flow-row">
        <span className={`bridge-status ${bridge.status}`} data-testid="bridge-status">
          {bridgeLabel(bridge)}
        </span>
        <span className="muted small" data-testid="bridge-terminal">
          {describeTerminal(bridge, terminal)}
        </span>
        <input
          className="compact"
          aria-label="POI id"
          placeholder="POI id (optional)"
          value={settings.poiId}
          disabled={connected || settings.mode === 'local'}
          onChange={(event) => set('poiId', event.target.value.trim())}
        />
        <label className="field">
          Bridge port
          <input
            className="compact port"
            aria-label="Bridge port"
            inputMode="numeric"
            value={settings.bridgePort}
            disabled={connected}
            onChange={(event) => {
              const port = Number(event.target.value);
              if (Number.isInteger(port) && port > 0 && port < 65536) set('bridgePort', port);
            }}
          />
        </label>
        <LabeledCheckbox
          label="Via page origin"
          checked={settings.bridge === 'proxy'}
          disabled={connected}
          onChange={(checked) => set('bridge', checked ? 'proxy' : 'direct')}
        />
        <LabeledCheckbox
          label="Local session"
          checked={settings.mode === 'local'}
          disabled={connected}
          onChange={(checked) => set('mode', checked ? 'local' : 'terminal')}
        />
        <button
          type="button"
          disabled={!connected && !canConnect}
          onClick={() => (connected ? controller.disconnect() : controller.connect())}
        >
          {connected ? 'Disconnect' : 'Connect'}
        </button>
      </div>
      <div className="flow-row">
        <button
          type="button"
          disabled={!connected}
          onClick={() =>
            sessionActive ? controller.endSession() : controller.startSession(identifyOnStart)
          }
        >
          {sessionActive ? 'End Checkout' : 'Start Checkout'}
        </button>
        <button
          type="button"
          className="tonal"
          disabled={!canOperateTerminal(state)}
          onClick={() => controller.identifyMember()}
        >
          {state.identifyInProgress ? 'Signing in…' : 'Loyalty Sign-In'}
        </button>
        <LabeledCheckbox
          label="Identify"
          checked={identifyOnStart}
          disabled={sessionActive || settings.mode === 'local'}
          onChange={setIdentifyOnStart}
        />
        <span className="gap" />
        <button
          type="button"
          className="tonal"
          disabled={
            state.sessionId === null ||
            sessionOperationInProgress(state) ||
            state.basket.length === 0
          }
          onClick={() => controller.clearBasket()}
        >
          Clear basket
        </button>
        <button
          type="button"
          className="danger"
          disabled={!sessionOperationInProgress(state)}
          onClick={() => controller.abort()}
        >
          Abort operation
        </button>
      </div>
      {bridge.status === 'missing' || bridge.status === 'outdated' ? (
        <InstallBridgePrompt
          bridge={bridge}
          downloadUrl={guideUrl}
          {...(manifestUrl ? { manifestUrl } : {})}
          className="install-prompt"
        />
      ) : null}
    </section>
  );
}

/** Connection and session indicators for the top bar: the desktop's `StatusIndicators`. */
export function StatusIndicators({ settings }: { settings: Settings }): ReactNode {
  const state = useEmulatorState();
  const [dot, label] = {
    CONNECTED: ['ok', 'Connected'],
    CONNECTING: ['pending', 'Connecting…'],
    ERROR: ['error', 'Unreachable'],
    DISCONNECTED: ['idle', 'Disconnected'],
  }[state.connection.phase];
  return (
    <div className="status" data-testid="status">
      <div className="status-line">
        <span className={`dot ${dot}`} aria-hidden="true" />
        <span>{[label, state.connection.detail].filter(Boolean).join(' — ')}</span>
      </div>
      <div className="small">
        TLS: bridge-managed · Mode: {settings.mode === 'local' ? 'local session' : 'terminal'} ·
        Session: <span data-testid="session-id">{state.sessionId?.slice(0, 12) ?? 'none'}</span>
      </div>
    </div>
  );
}
