import { useState, type ReactNode } from 'react';
import { DEFAULT_SETTINGS, type Settings } from '../settings';

export interface SettingsPanelProps {
  readonly settings: Settings;
  readonly onApply: (next: Settings) => void;
}

/**
 * Edits the lane's settings in a draft and applies them in one go, so a keystroke in the POI id
 * does not restart the session; applying remounts the register with the new options.
 */
export function SettingsPanel({ settings, onApply }: SettingsPanelProps): ReactNode {
  const [draft, setDraft] = useState<Settings>(settings);
  const field = <K extends keyof Settings>(key: K, value: Settings[K]) =>
    setDraft((previous) => ({ ...previous, [key]: value }));

  return (
    <form
      className="panel settings"
      onSubmit={(event) => {
        event.preventDefault();
        onApply({ ...draft, currency: draft.currency.trim().toUpperCase() });
      }}
    >
      <h2>Settings</h2>
      <fieldset>
        <legend>Session</legend>
        <label>
          <input
            type="radio"
            name="mode"
            checked={draft.mode === 'terminal'}
            onChange={() => field('mode', 'terminal')}
          />
          Terminal session: bracketed on the terminal below, with settlement
        </label>
        <label>
          <input
            type="radio"
            name="mode"
            checked={draft.mode === 'local'}
            onChange={() => field('mode', 'local')}
          />
          Local session: basket, member and widgets only, no hardware
        </label>
      </fieldset>
      <div className="fields">
        <label>
          POI id (optional; passed through to the terminal as its Nexo POIID)
          <input
            value={draft.poiId}
            onChange={(event) => field('poiId', event.target.value)}
            disabled={draft.mode === 'local'}
          />
        </label>
        <label>
          Sale id (lane)
          <input value={draft.saleId} onChange={(event) => field('saleId', event.target.value)} />
        </label>
        <label>
          Currency
          <input
            value={draft.currency}
            maxLength={3}
            onChange={(event) => field('currency', event.target.value)}
          />
        </label>
        <label>
          Store location
          <input
            value={draft.storeLocation}
            onChange={(event) => field('storeLocation', event.target.value)}
          />
        </label>
      </div>
      <fieldset>
        <legend>Widgets</legend>
        <label>
          <input
            type="checkbox"
            checked={draft.retailMedia}
            onChange={(event) => field('retailMedia', event.target.checked)}
          />
          Retail media on the <code>lane-banner</code> placement (turn off for a host without an ad
          decision service)
        </label>
      </fieldset>
      <fieldset>
        <legend>Bridge route</legend>
        <label>
          <input
            type="radio"
            name="bridge"
            checked={draft.bridge === 'direct'}
            onChange={() => field('bridge', 'direct')}
          />
          Direct: probe <code>http://127.0.0.1:48333</code> as a deployed register does
        </label>
        <label>
          <input
            type="radio"
            name="bridge"
            checked={draft.bridge === 'proxy'}
            onChange={() => field('bridge', 'proxy')}
          />
          Through this page&apos;s origin: the Vite dev server proxies to the bridge (needed while
          the development bridge sends no CORS headers)
        </label>
      </fieldset>
      <div className="actions">
        <button type="submit">Apply and restart the lane</button>
        <button type="button" className="secondary" onClick={() => setDraft(DEFAULT_SETTINGS)}>
          Reset to defaults
        </button>
      </div>
    </form>
  );
}
