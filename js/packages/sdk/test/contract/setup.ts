// Shared pieces of the contract suites: the WebSocket to inject on Node 20 and the connect helper.
import WebSocketPolyfill from 'ws';
import { BiltPos } from '../../src/index';
import { localBridge, type LocalBridgeOptions } from '../../src/bridge/index';

/** Node 22+ has a global WebSocket; on Node 20 the `ws` package stands in. */
export const webSocket: typeof globalThis.WebSocket =
  typeof globalThis.WebSocket === 'function'
    ? globalThis.WebSocket
    : (WebSocketPolyfill as unknown as typeof globalThis.WebSocket);

export function bridgeOptions(port: number, extra: LocalBridgeOptions = {}): LocalBridgeOptions {
  return { port, fallbackPorts: 0, healthTimeoutMs: 5_000, webSocket, ...extra };
}

export function connectTo(port: number, extra: LocalBridgeOptions = {}) {
  return BiltPos.connect(localBridge(bridgeOptions(port, extra)));
}
