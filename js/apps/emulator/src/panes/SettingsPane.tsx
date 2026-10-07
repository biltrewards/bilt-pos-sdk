import type { BridgeState } from '@bilt/pos-react/bridge';
import { useEffect, useState, type ReactNode } from 'react';
import { DEFAULT_SETTINGS, type Settings } from '../settings';

export interface SettingsPaneProps {
  readonly settings: Settings;

  /** The bridge as last probed, for the terminal list and the status line; `null` before the gate ran. */
  readonly bridge: BridgeState | null;
  readonly onApply: (next: Settings) => void;
}

const OTHER = '__other__';

/**
 * Edits the lane's settings in a draft and applies them in one go, so a keystroke in the POI id
 * does not restart the session; applying remounts the lane with the new options. The terminal
 * is picked from the ones the bridge lists in `/health`, with a free-text fallback for one the
 * bridge does not know yet.
 */
export function SettingsPane({ settings, bridge, onApply }: SettingsPaneProps): ReactNode {
  const [draft, setDraft] = useState<Settings>(settings);
  const terminals = bridge?.health?.terminals ?? [];
  const listed = terminals.some((terminal) => terminal.poiId === draft.poiId);
  const [freeText, setFreeText] = useState(() => !listed && terminals.length > 0);
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
          poiId: draft.poiId.trim() || DEFAULT_SETTINGS.poiId,
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
          Terminal session: bracketed on the terminal below, with settlement and refunds
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
          <label>
            Terminal (from the bridge&apos;s <code>/health</code>)
            <select
              aria-label="Terminal"
              disabled={draft.mode === 'local'}
              value={freeText || !listed ? OTHER : draft.poiId}
              onChange={(event) => {
                if (event.target.value === OTHER) {
                  setFreeText(true);
                } else {
                  setFreeText(false);
                  field('poiId', event.target.value);
                }
              }}
            >
              {terminals.map((terminal) => (
                <option key={terminal.poiId} value={terminal.poiId}>
                  {terminal.poiId}
                  {terminal.model ? ` (${terminal.model})` : ''}
                  {terminal.reachable === false ? ' · unreachable' : ''}
                </option>
              ))}
              <option value={OTHER}>
                {terminals.length === 0 ? 'No terminals listed: type a POI id' : 'Other…'}
              </option>
            </select>
          </label>
          {freeText || !listed ? (
            <label>
              POI id
              <input
                aria-label="POI id"
                value={draft.poiId}
                onChange={(event) => field('poiId', event.target.value)}
                disabled={draft.mode === 'local'}
              />
            </label>
          ) : null}
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
