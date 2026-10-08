import type { BridgeState } from '@bilt/pos-react/bridge';
import { useEffect, useState, type ReactNode } from 'react';
import type { TerminalInfo } from '@bilt/pos-sdk';
import { DEFAULT_SETTINGS, type Settings } from '../settings';

function describeTerminal(bridge: BridgeState | null, terminal: TerminalInfo | undefined): string {
  if (!bridge?.health) return 'Terminal: unknown until the bridge answers.';
  if (!terminal) return 'The bridge has no terminal configured: only local sessions work.';
  const name = [terminal.label, terminal.model].filter(Boolean).join(' · ') || 'configured';
  const reach =
    terminal.reachable === undefined ? '' : terminal.reachable ? ' · reachable' : ' · unreachable';
  return `Terminal: ${name}${reach}`;
}

export interface SettingsPaneProps {
  readonly settings: Settings;

  /** The bridge as last probed, for its terminal and the status line; `null` before the gate ran. */
  readonly bridge: BridgeState | null;
  readonly onApply: (next: Settings) => void;
}

/**
 * Edits the lane's settings in a draft and applies them in one go, so a keystroke in the POI id
 * does not restart the session; applying remounts the lane with the new options. The bridge
 * drives exactly one terminal, shown as `/health` reports it; the POI id is only passed through
 * to it, so any value, or none, works.
 */
export function SettingsPane({ settings, bridge, onApply }: SettingsPaneProps): ReactNode {
  const [draft, setDraft] = useState<Settings>(settings);
  const terminal = bridge?.health?.terminal;
  useEffect(() => setDraft(settings), [settings]);

  const field = <K extends keyof Settings>(key: K, value: Settings[K]) =>
    setDraft((previous) => ({ ...previous, [key]: value }));

  return (
    <form
      className="panel settings"
      onSubmit={(event) => {
        event.preventDefault();
        onApply({
          ...draft,
          currency: draft.currency.trim().toUpperCase() || DEFAULT_SETTINGS.currency,
          poiId: draft.poiId.trim(),
          saleId: draft.saleId.trim() || DEFAULT_SETTINGS.saleId,
          storeLocation: draft.storeLocation.trim(),
        });
      }}
    >
      <div className="panel-header">
        <h2>Settings</h2>
        <span className="badge" data-testid="bridge-status">
          bridge {bridge?.status ?? 'unknown'}
          {bridge?.baseUrl ? ` at ${bridge.baseUrl}` : ''}
          {bridge?.health
            ? ` · host ${bridge.health.hostVersion} · sdk ${bridge.health.sdkVersion}`
            : ''}
        </span>
      </div>

      <fieldset>
        <legend>Bridge</legend>
        <div className="fields">
          <label>
            Port (direct route; the SDK also tries the ten above it)
            <input
              type="number"
              min={1}
              max={65535}
              value={draft.bridgePort}
              onChange={(event) => field('bridgePort', Number(event.target.value) || 0)}
            />
          </label>
        </div>
        <label>
          <input
            type="radio"
            name="bridge"
            checked={draft.bridge === 'direct'}
            onChange={() => field('bridge', 'direct')}
          />
          Direct: probe <code>http://127.0.0.1:{draft.bridgePort}</code> as a deployed register does
        </label>
        <label>
          <input
            type="radio"
            name="bridge"
            checked={draft.bridge === 'proxy'}
            onChange={() => field('bridge', 'proxy')}
          />
          Through this page&apos;s origin: the Vite dev server proxies to the bridge (needed while
          the development bridge sends no CORS headers; <code>BRIDGE_URL</code> picks the target)
        </label>
      </fieldset>

      <fieldset>
        <legend>Session</legend>
        <label>
          <input
            type="radio"
            name="mode"
            checked={draft.mode === 'terminal'}
            onChange={() => field('mode', 'terminal')}
          />
          Terminal session: bracketed on the bridge&apos;s terminal, with settlement and refunds
        </label>
        <label>
          <input
            type="radio"
            name="mode"
            checked={draft.mode === 'local'}
            onChange={() => field('mode', 'local')}
          />
          Local session: basket, member, context and widgets only, no hardware
        </label>
        <div className="fields">
          <p className="muted small" data-testid="bridge-terminal">
            {describeTerminal(bridge, terminal)}
          </p>
          <label>
            POI id (optional; sent to the terminal as the Nexo <code>POIID</code>, the bridge&apos;s
            default when blank)
            <input
              aria-label="POI id"
              value={draft.poiId}
              onChange={(event) => field('poiId', event.target.value)}
              disabled={draft.mode === 'local'}
            />
          </label>
          <label>
            Sale id (lane)
            <input
              aria-label="Sale id"
              value={draft.saleId}
              onChange={(event) => field('saleId', event.target.value)}
            />
          </label>
          <label>
            Currency
            <input
              aria-label="Currency"
              value={draft.currency}
              maxLength={3}
              onChange={(event) => field('currency', event.target.value)}
            />
          </label>
          <label>
            Store location
            <input
              aria-label="Store location"
              value={draft.storeLocation}
              onChange={(event) => field('storeLocation', event.target.value)}
            />
          </label>
        </div>
      </fieldset>

      <fieldset>
        <legend>Widgets</legend>
        <label>
          <input
            type="checkbox"
            checked={draft.retailMedia}
            onChange={(event) => field('retailMedia', event.target.checked)}
          />
          Retail media on the <code>lane-banner</code> placement (the companion display)
        </label>
      </fieldset>

      <div className="actions">
        <button type="submit">Apply and restart the lane</button>
        <button type="button" className="secondary" onClick={() => setDraft(DEFAULT_SETTINGS)}>
          Reset to defaults
        </button>
      </div>
      <p className="muted small">
        Settings persist in this browser&apos;s <code>localStorage</code>. Tax is the
        emulator&apos;s New Jersey policy: 6.625% on everything but Grocery and Apparel.
      </p>
    </form>
  );
}
