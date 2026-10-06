/** The bridge's well-known loopback port; `fallbackPorts` more are probed above it. */
export const BRIDGE_DEFAULT_PORT = 48333;

/** How many ports above the primary the bridge may fall back to when the primary is taken. */
export const BRIDGE_DEFAULT_FALLBACK_PORTS = 10;

/** How long one `GET /health` probe may take before the port counts as silent. */
export const BRIDGE_DEFAULT_HEALTH_TIMEOUT_MS = 400;

/**
 * Options for `localBridge()` and `detectBridge()`. Everything is optional; the defaults find a
 * bridge the installer put on the register machine.
 */
export interface LocalBridgeOptions {
  /** The loopback address the bridge binds; default `127.0.0.1`. */
  readonly host?: string;

  /** The first port to probe; default 48333. */
  readonly port?: number;

  /** How many ports above `port` to probe as well; default 10, `0` probes `port` alone. */
  readonly fallbackPorts?: number;

  /** The per-port health probe timeout; default 400 ms. */
  readonly healthTimeoutMs?: number;

  /**
   * Sent as `Authorization: Bearer ...` on every HTTP request. Unused by default: the bridge
   * of this iteration has no pairing and accepts loopback callers as they are.
   */
  readonly bearerToken?: string;

  /** Retries for a request that failed before reaching the bridge (a dropped socket); default 2. */
  readonly retries?: number;

  /** The `fetch` to use; default `globalThis.fetch`. */
  readonly fetch?: typeof globalThis.fetch;

  /** The `WebSocket` constructor to use for the event stream; default `globalThis.WebSocket`. */
  readonly webSocket?: typeof globalThis.WebSocket;

  /** The `EventSource` constructor for the SSE fallback; default `globalThis.EventSource`. */
  readonly eventSource?: typeof globalThis.EventSource;
}

/** The options with every default applied. */
export interface ResolvedBridgeOptions {
  readonly host: string;
  readonly port: number;
  readonly fallbackPorts: number;
  readonly healthTimeoutMs: number;
  readonly bearerToken: string | undefined;
  readonly retries: number;
  readonly fetch: typeof globalThis.fetch;
  readonly webSocket: typeof globalThis.WebSocket | undefined;
  readonly eventSource: typeof globalThis.EventSource | undefined;
}

// Called as a free function: `window.fetch` invoked as a method of another object throws
// "Illegal invocation" in browsers, and that is exactly how a stored reference would be called.
export function resolveOptions(options: LocalBridgeOptions = {}): ResolvedBridgeOptions {
  const fetchImpl = options.fetch ?? globalThis.fetch;
  return {
    host: options.host ?? '127.0.0.1',
    port: options.port ?? BRIDGE_DEFAULT_PORT,
    fallbackPorts: options.fallbackPorts ?? BRIDGE_DEFAULT_FALLBACK_PORTS,
    healthTimeoutMs: options.healthTimeoutMs ?? BRIDGE_DEFAULT_HEALTH_TIMEOUT_MS,
    bearerToken: options.bearerToken,
    retries: options.retries ?? 2,
    fetch: (input, init) => fetchImpl(input, init),
    webSocket:
      options.webSocket ?? (typeof WebSocket === 'undefined' ? undefined : globalThis.WebSocket),
    eventSource:
      options.eventSource ??
      (typeof EventSource === 'undefined' ? undefined : globalThis.EventSource),
  };
}

/** `127.0.0.1`, `::1` and `localhost` are the addresses Chrome's loopback-network permission covers. */
export function isLoopback(host: string): boolean {
  return host === '127.0.0.1' || host === 'localhost' || host === '::1' || host === '[::1]';
}
