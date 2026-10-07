import { BiltPosProvider } from '@bilt/pos-react';
import {
  BridgeGate,
  InstallBridgePrompt,
  type BridgeDetect,
  type BridgeState,
} from '@bilt/pos-react/bridge';
import { BiltPos } from '@bilt/pos-sdk';
import { localBridge, type LocalBridgeOptions } from '@bilt/pos-sdk/bridge';
import { useMemo, useState, type ReactNode } from 'react';
import { LaneBar } from './components/LaneBar';
import { LaneProvider } from './lane/LaneProvider';
import { LogStore } from './log';
import { DisplayPane } from './panes/DisplayPane';
import { LogPane } from './panes/LogPane';
import { RefundsPane } from './panes/RefundsPane';
import { SalePane } from './panes/SalePane';
import { SettingsPane } from './panes/SettingsPane';
import { bridgeOptions, laneKey, useSettings, type Settings } from './settings';
import { IndexedDbSaleStore, type SaleStore } from './store/sales-store';

/** Where to send a cashier who has no bridge: the setup guide says where the installer is. */
const BRIDGE_GUIDE_URL = 'https://biltrewards.github.io/bilt-pos-sdk/terminal-bridge.html';

/** The update manifest, once the feed exists; the prompt falls back to the guide link without it. */
const MANIFEST_URL = import.meta.env.VITE_BRIDGE_MANIFEST_URL;

export type Tab = 'sale' | 'refunds' | 'display' | 'log' | 'settings';

const TABS: readonly { id: Tab; label: string }[] = [
  { id: 'sale', label: 'Sale' },
  { id: 'refunds', label: 'Refunds' },
  { id: 'display', label: 'Companion display' },
  { id: 'log', label: 'Log' },
  { id: 'settings', label: 'Settings' },
];

export interface AppProps {
  /** Connects over the bridge; tests pass a factory that returns a `BiltPos` double. */
  readonly connect?: (options: LocalBridgeOptions) => Promise<BiltPos>;

  /** The bridge probe; tests script it. */
  readonly detect?: BridgeDetect;

  /** Where completed sales are kept; IndexedDB unless a test says otherwise. */
  readonly sales?: SaleStore;

  /** The tab to open on; `sale` by default. */
  readonly initialTab?: Tab;
}

function defaultConnect(options: LocalBridgeOptions): Promise<BiltPos> {
  return BiltPos.connect(localBridge(options));
}

function TabBar({ tab, onSelect }: { tab: Tab; onSelect: (tab: Tab) => void }): ReactNode {
  return (
    <nav className="tabs" role="tablist" aria-label="Panes">
      {TABS.map((candidate) => (
        <button
          key={candidate.id}
          type="button"
          role="tab"
          aria-selected={candidate.id === tab}
          className={candidate.id === tab ? 'tab active' : 'tab'}
          onClick={() => onSelect(candidate.id)}
        >
          {candidate.label}
        </button>
      ))}
    </nav>
  );
}

/**
 * The emulator page: a tab bar, the bridge gate and, behind it, the connection and the lane
 * shared by every pane. Every pane stays mounted and the inactive ones are hidden, so the
 * companion display keeps its rendering and the log its scroll position across tab switches.
 * The Settings pane is reachable in front of the gate too, since the bridge port lives there.
 */
export function App(props: AppProps): ReactNode {
  const connect = props.connect ?? defaultConnect;
  const [settings, updateSettings] = useSettings();
  const [tab, setTab] = useState<Tab>(props.initialTab ?? 'sale');
  const log = useMemo(() => new LogStore(), []);
  const givenSales = props.sales;
  const sales = useMemo(() => givenSales ?? new IndexedDbSaleStore(), [givenSales]);
  const probe = useMemo(
    () => bridgeOptions(settings),
    // The probe only depends on the route and the port.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [settings.bridge, settings.bridgePort],
  );
  const probeKey = `${settings.bridge}:${settings.bridgePort}`;

  const apply = (next: Settings) => {
    log.info('settings', `applied: ${laneKey(next)} via ${next.bridge}:${next.bridgePort}`);
    updateSettings(next);
    if (tab === 'settings') setTab('sale');
  };

  const settingsPane = (bridge: BridgeState | null) => (
    <SettingsPane settings={settings} bridge={bridge} onApply={apply} />
  );

  return (
    <div className="app">
      <header className="app-header">
        <div>
          <h1>Register emulator</h1>
          <p className="muted small">
            Bilt POS SDK ·{' '}
            {settings.mode === 'terminal' ? `terminal ${settings.poiId}` : 'local session'} · lane{' '}
            {settings.saleId} · {settings.currency} · store {settings.storeLocation}
          </p>
        </div>
        <TabBar tab={tab} onSelect={setTab} />
      </header>

      <BridgeGate
        key={probeKey}
        {...probe}
        {...(props.detect ? { detect: props.detect } : {})}
        {...(MANIFEST_URL ? { manifestUrl: MANIFEST_URL } : {})}
        downloadUrl={BRIDGE_GUIDE_URL}
        className="panel"
        fallback={(bridge) =>
          tab === 'settings' ? (
            settingsPane(bridge)
          ) : (
            <>
              <InstallBridgePrompt
                bridge={bridge}
                downloadUrl={BRIDGE_GUIDE_URL}
                {...(MANIFEST_URL ? { manifestUrl: MANIFEST_URL } : {})}
                className="panel"
              />
              <p className="small muted">
                Probing {bridge.probed?.join(', ') ?? 'the bridge'}. The port is in the Settings
                tab.
              </p>
            </>
          )
        }
      >
        {(bridge) => (
          <BiltPosProvider key={probeKey} connect={() => connect(probe)}>
            <LaneProvider key={laneKey(settings)} settings={settings} log={log} sales={sales}>
              <LaneBar onDisableRetailMedia={() => apply({ ...settings, retailMedia: false })} />
              <section role="tabpanel" hidden={tab !== 'sale'} aria-label="Sale">
                <SalePane />
              </section>
              <section role="tabpanel" hidden={tab !== 'refunds'} aria-label="Refunds">
                <RefundsPane />
              </section>
              <section role="tabpanel" hidden={tab !== 'display'} aria-label="Companion display">
                <DisplayPane />
              </section>
              <section role="tabpanel" hidden={tab !== 'log'} aria-label="Log">
                <LogPane />
              </section>
              <section role="tabpanel" hidden={tab !== 'settings'} aria-label="Settings">
                {settingsPane(bridge)}
              </section>
            </LaneProvider>
          </BiltPosProvider>
        )}
      </BridgeGate>
    </div>
  );
}
