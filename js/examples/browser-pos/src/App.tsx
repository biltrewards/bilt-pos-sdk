import { BiltPosProvider, useBiltPos } from '@bilt/pos-react';
import { BridgeGate, type BridgeDetect } from '@bilt/pos-react/bridge';
import { BiltPos } from '@bilt/pos-sdk';
import { localBridge, type LocalBridgeOptions } from '@bilt/pos-sdk/bridge';
import { useMemo, useState, type ReactNode } from 'react';
import { Register } from './Register';
import { SettingsPanel } from './components/SettingsPanel';
import { bridgeOptions, describeSession, useSettings, type Settings } from './settings';

/** Where to send a cashier who has no bridge: the setup guide says where the installer is. */
const BRIDGE_GUIDE_URL = 'https://biltrewards.github.io/bilt-pos-sdk/terminal-bridge.html';

/** The update manifest, once the feed exists; the prompt falls back to the guide link without it. */
const MANIFEST_URL = import.meta.env.VITE_BRIDGE_MANIFEST_URL;

export interface AppProps {
  /** Connects over the bridge; tests pass a factory that returns a `BiltPos` double. */
  readonly connect?: (options: LocalBridgeOptions) => Promise<BiltPos>;

  /** The bridge probe; tests script it. */
  readonly detect?: BridgeDetect;
}

function defaultConnect(options: LocalBridgeOptions): Promise<BiltPos> {
  return BiltPos.connect(localBridge(options));
}

function registerKey(settings: Settings): string {
  return [
    settings.mode,
    settings.poiId,
    settings.saleId,
    settings.currency,
    settings.storeLocation,
    settings.retailMedia ? 'rm' : 'no-rm',
  ].join('|');
}

/** The engine behind the provider, as data: what a register can rely on from this host. */
function Capabilities(): ReactNode {
  const { pos, status } = useBiltPos();
  if (!pos || status !== 'connected') return null;
  const c = pos.capabilities;
  const flags = [
    c.survivesPageReload ? 'survives page reload' : 'does not survive page reload',
    c.worksOffline ? 'works offline' : 'needs the internet',
    c.supportsWidgets ? 'widgets' : 'no widgets',
  ];
  return (
    <p className="capabilities muted small">
      engine {c.name}: {flags.join(' · ')}
    </p>
  );
}

/**
 * The register page: settings, the bridge gate, the connection and the lane. The gate renders
 * the install prompt until a compatible bridge answers on loopback; the provider connects to it
 * and the lane starts its session. Changing a setting remounts the lane, which ends the old
 * session and starts a new one with the new options.
 */
export function App(props: AppProps): ReactNode {
  const connect = props.connect ?? defaultConnect;
  const [settings, updateSettings] = useSettings();
  const [showSettings, setShowSettings] = useState(false);
  const probe = useMemo(() => bridgeOptions(settings.bridge), [settings.bridge]);

  return (
    <div className="app">
      <header className="app-header">
        <div>
          <h1>Browser POS</h1>
          <p className="muted">
            Bilt POS SDK example register · {describeSession(settings)} · lane {settings.saleId} ·{' '}
            {settings.currency}
          </p>
        </div>
        <button
          type="button"
          className="secondary"
          onClick={() => setShowSettings((open) => !open)}
        >
          {showSettings ? 'Close settings' : 'Settings'}
        </button>
      </header>

      {showSettings ? (
        <SettingsPanel
          settings={settings}
          onApply={(next) => {
            updateSettings(next);
            setShowSettings(false);
          }}
        />
      ) : null}

      <BridgeGate
        {...probe}
        {...(props.detect ? { detect: props.detect } : {})}
        {...(MANIFEST_URL ? { manifestUrl: MANIFEST_URL } : {})}
        downloadUrl={BRIDGE_GUIDE_URL}
        className="panel"
      >
        <BiltPosProvider key={settings.bridge} connect={() => connect(probe)}>
          <Capabilities />
          <Register key={registerKey(settings)} settings={settings} />
        </BiltPosProvider>
      </BridgeGate>
    </div>
  );
}
