import { useBridge, type BridgeDetect } from '@bilt/pos-react/bridge';
import { BiltPos } from '@bilt/pos-sdk';
import { localBridge, type LocalBridgeOptions } from '@bilt/pos-sdk/bridge';
import { useEffect, useMemo, useRef, useState, useSyncExternalStore, type ReactNode } from 'react';
import { BasketCard } from './components/BasketCard';
import { TabRow } from './components/common';
import { ConnectionPanel, StatusIndicators } from './components/ConnectionPanel';
import { Dialogs } from './components/Dialogs';
import { EventsCard, type LogFeeds } from './components/EventsCard';
import { MemberCard } from './components/MemberCard';
import { RefundTab } from './components/RefundTab';
import { KeypadEntry, SaleTab, useKeypad, type SaleTabPane } from './components/SaleTab';
import { StoredValueTab } from './components/StoredValueTab';
import { BrowserEmulatorController } from './emulator/controller';
import { EmulatorProvider, useEmulatorState } from './emulator/context';
import { LineLog } from './log';
import { bridgeOptions, useSettings, type Settings } from './settings';
import { IndexedDbSaleStore, type SaleStore } from './store/sales-store';

/** Where to send a cashier who has no bridge: the setup guide says where the installer is. */
const BRIDGE_GUIDE_URL = 'https://biltrewards.github.io/bilt-pos-sdk/terminal-bridge.html';

/** The update manifest, once the feed exists; the prompt falls back to the guide link without it. */
const MANIFEST_URL = import.meta.env.VITE_BRIDGE_MANIFEST_URL;

/** The bottom tabs, as the desktop's `EmulatorTab`. */
export type EmulatorTab = 'sale' | 'storedValue' | 'refund';

const TABS = [
  { id: 'sale', label: 'Sale' },
  { id: 'storedValue', label: 'Stored Value' },
  { id: 'refund', label: 'Refund' },
] as const;

export interface AppProps {
  /** Connects over the bridge; tests pass a factory that returns a `BiltPos` double. */
  readonly connect?: (options: LocalBridgeOptions) => Promise<BiltPos>;

  /** The bridge probe; tests script it. */
  readonly detect?: BridgeDetect;

  /** Where completed sales are kept; IndexedDB unless a test says otherwise. */
  readonly sales?: SaleStore;

  /** The tab and Sale pane to open on; the screenshot run and the tests pick them. */
  readonly initialTab?: EmulatorTab;
  readonly initialSalePane?: SaleTabPane;
}

function defaultConnect(options: LocalBridgeOptions): Promise<BiltPos> {
  return BiltPos.connect(localBridge(options));
}

function laneConfig(settings: Settings) {
  const { mode, poiId, saleId, currency, storeLocation } = settings;
  return { mode, poiId: poiId.trim(), saleId, currency, storeLocation };
}

/**
 * The page under the top bar, the desktop's `EmulatorApp` body: the connection card, the
 * loyalty card once a sign-in ran, then the basket over the selected tab's content beside the
 * log, and the tab row at the bottom.
 */
function Workspace({
  settings,
  onSettings,
  bridge,
  feeds,
  initialTab,
  initialSalePane,
}: {
  settings: Settings;
  onSettings: (next: Settings) => void;
  bridge: ReturnType<typeof useBridge>;
  feeds: LogFeeds;
  initialTab: EmulatorTab;
  initialSalePane: SaleTabPane;
}): ReactNode {
  const state = useEmulatorState();
  const [tab, setTab] = useState<EmulatorTab>(initialTab);
  const [salePane, setSalePane] = useState<SaleTabPane>(initialSalePane);
  const entryRef = useRef<KeypadEntry | null>(null);
  entryRef.current ??= new KeypadEntry();
  const keypad = useKeypad(entryRef.current);

  // Release the edit when the line it adopted is gone or no longer re-priceable.
  useEffect(() => {
    const sku = keypad.editingSku;
    if (
      sku !== null &&
      !state.basket.some((line) => line.sku === sku && line.editablePriceMinor !== null)
    ) {
      keypad.reset();
    }
  }, [state.basket, keypad]);

  return (
    <div className="app">
      <Dialogs />
      <header className="top-bar">
        <h1>Bilt POS Emulator</h1>
        <StatusIndicators settings={settings} />
      </header>
      <main className="main">
        <ConnectionPanel
          settings={settings}
          onSettings={onSettings}
          bridge={bridge}
          guideUrl={BRIDGE_GUIDE_URL}
          {...(MANIFEST_URL ? { manifestUrl: MANIFEST_URL } : {})}
        />
        {state.member ? <MemberCard member={state.member} /> : null}
        <div className="workspace">
          <div className="left">
            <BasketCard
              editingSku={keypad.editingSku}
              onEditCustomLine={(sku, priceMinor) => {
                keypad.edit(sku, priceMinor);
                setSalePane('keypad');
                setTab('sale');
              }}
            />
            {tab === 'sale' ? (
              <SaleTab entry={keypad} pane={salePane} onSelectPane={setSalePane} />
            ) : tab === 'storedValue' ? (
              <StoredValueTab />
            ) : (
              <RefundTab />
            )}
          </div>
          <EventsCard feeds={feeds} />
        </div>
      </main>
      <nav className="bottom-bar">
        <TabRow tabs={TABS} selected={tab} onSelect={setTab} label="Screens" />
      </nav>
    </div>
  );
}

/**
 * The browser register emulator: a port of the Compose desktop emulator onto `@bilt/pos-sdk`
 * and the Terminal Bridge. The controller holds the connection and the checkout, the components
 * render its state exactly as the desktop's panels do. The page probes the bridge on load and
 * connects once it answers, as the desktop connects to an autodetected terminal.
 */
export function App(props: AppProps): ReactNode {
  const [settings, updateSettings] = useSettings();
  const givenSales = props.sales;
  const sales = useMemo(() => givenSales ?? new IndexedDbSaleStore(), [givenSales]);
  const feeds = useMemo<LogFeeds>(
    () => ({ events: new LineLog(), detailed: new LineLog(), protocol: new LineLog() }),
    [],
  );
  const connect = props.connect ?? defaultConnect;
  const connectRef = useRef(connect);
  connectRef.current = connect;
  const [controller] = useState(
    () =>
      new BrowserEmulatorController(
        { connect: (options) => connectRef.current(options), sales, ...feeds },
        laneConfig(settings),
      ),
  );
  const { bridge: route, bridgePort } = settings;
  const probe = useMemo(() => bridgeOptions({ bridge: route, bridgePort }), [route, bridgePort]);
  const bridge = useBridge({
    ...probe,
    ...(props.detect ? { detect: props.detect } : {}),
    ...(MANIFEST_URL ? { manifestUrl: MANIFEST_URL } : {}),
  });

  useEffect(() => {
    controller.configure(laneConfig(settings));
    controller.setBridge(probe);
  }, [controller, settings, probe]);

  useEffect(() => {
    controller.start();
    return () => controller.dispose();
  }, [controller]);

  // Connect once the bridge answers, unless the operator disconnected by hand: the desktop
  // connects to an autodetected terminal the same way.
  const phase = useSyncExternalStore(
    controller.subscribe,
    () => controller.getState().connection.phase,
    () => controller.getState().connection.phase,
  );
  useEffect(() => {
    if (
      bridge.status === 'ready' &&
      phase === 'DISCONNECTED' &&
      !controller.disconnectedByOperator
    ) {
      controller.connect();
    }
  }, [bridge.status, phase, controller]);

  return (
    <EmulatorProvider controller={controller}>
      <Workspace
        settings={settings}
        onSettings={updateSettings}
        bridge={bridge}
        feeds={feeds}
        initialTab={props.initialTab ?? 'sale'}
        initialSalePane={props.initialSalePane ?? 'products'}
      />
    </EmulatorProvider>
  );
}
