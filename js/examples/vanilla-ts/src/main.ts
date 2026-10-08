import { BiltPos } from '@bilt/pos-sdk';
import { detectBridge, localBridge, type LocalBridgeOptions } from '@bilt/pos-sdk/bridge';
import { mountRegister } from './register';
import './styles.css';

// A deployed register probes the bridge on 127.0.0.1 with the defaults. Under the Vite dev
// server the page goes through its own origin instead, which proxies to the bridge (see
// `vite.config.ts`), because the development bridge sends no CORS headers yet.
const bridge: LocalBridgeOptions = import.meta.env.DEV
  ? { host: location.hostname, port: Number(location.port || 80), fallbackPorts: 0 }
  : {};

void mountRegister(document.getElementById('app')!, {
  detect: () => detectBridge(bridge),
  connect: () => BiltPos.connect(localBridge(bridge)),
});
