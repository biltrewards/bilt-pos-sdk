import { PROTOCOL_VERSION, type Health } from '@bilt/pos-protocol';
import { EngineOutdatedError, EngineUnavailableError } from '@bilt/pos-sdk';

/** The default loopback port of the Terminal Bridge. */
export const DEFAULT_BRIDGE_PORT = 48333;

/** The default time a `/health` probe may take before the bridge counts as missing. */
export const DEFAULT_DETECT_TIMEOUT_MS = 400;

/**
 * The outcome of one probe. `ready` carries the bridge's health; `outdated` the health and an
 * `EngineOutdatedError` saying which protocol version is needed; `missing` the error the probe
 * failed with, an `EngineUnavailableError`.
 */
export type BridgeDetection =
  | { readonly status: 'ready'; readonly baseUrl: string; readonly health: Health }
  | {
      readonly status: 'outdated';
      readonly baseUrl: string;
      readonly health: Health;
      readonly error: EngineOutdatedError;
    }
  | {
      readonly status: 'missing';
      readonly baseUrl: string;
      readonly error: EngineUnavailableError;
    };

/**
 * Probes for the Terminal Bridge. `useBridge` runs one of these; `bridgeDetector()` is the
 * default.
 *
 * TODO(RET-6900): `@bilt/pos-sdk/bridge` will ship `localBridge()` with `BridgeMissingError`
 * (an `EngineUnavailableError`) and `BridgeOutdatedError` (an `EngineOutdatedError`) plus the
 * detection behind them. This interface maps 1:1 onto that: `missing` is what
 * `BiltPos.connect(localBridge())` rejects with as `BridgeMissingError`, `outdated` as
 * `BridgeOutdatedError`, `ready` is a connect that would succeed. Once the runtime lands the
 * default detector delegates to it and the errors become the bridge subclasses; the shape of
 * `BridgeDetection` and `useBridge` does not change.
 */
export interface BridgeDetector {
  /** The loopback origin probed, e.g. `http://127.0.0.1:48333`. */
  readonly baseUrl: string;

  /** One probe; never rejects, a failure is a `missing` detection. */
  detect(signal?: AbortSignal): Promise<BridgeDetection>;
}

/** Options for `bridgeDetector()`. */
export interface BridgeDetectorOptions {
  /** The loopback port; default `DEFAULT_BRIDGE_PORT` (48333). */
  readonly port?: number;

  /** The loopback host; default `127.0.0.1`. */
  readonly host?: string;

  /** How long one `/health` probe may take; default 400 ms. */
  readonly timeoutMs?: number;

  /** The protocol major version this SDK needs; default `PROTOCOL_VERSION` from `@bilt/pos-protocol`. */
  readonly protocolVersion?: string;

  /** The `fetch` to probe with; default the global one. */
  readonly fetch?: typeof fetch;
}

function isHealth(value: unknown): value is Health {
  if (typeof value !== 'object' || value === null) return false;
  const health = value as Partial<Health>;
  return (
    (health.host === 'bridge' || health.host === 'cloud') &&
    typeof health.hostVersion === 'string' &&
    Array.isArray(health.protocolVersions)
  );
}

function withTimeout(timeoutMs: number, outer?: AbortSignal): AbortSignal {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(new Error('timeout')), timeoutMs);
  const clear = () => clearTimeout(timer);
  controller.signal.addEventListener('abort', clear, { once: true });
  if (outer) {
    if (outer.aborted) controller.abort(outer.reason);
    else outer.addEventListener('abort', () => controller.abort(outer.reason), { once: true });
  }
  return controller.signal;
}

/**
 * The default `BridgeDetector`: `GET {baseUrl}/health` with a short timeout, checked against
 * the protocol version this SDK speaks. The first probe from a public page is the one Chrome
 * gates behind its one-time loopback permission prompt; a denied prompt looks like `missing`,
 * which is why `InstallBridgePrompt` explains it before retrying.
 */
export function bridgeDetector(options: BridgeDetectorOptions = {}): BridgeDetector {
  const {
    port = DEFAULT_BRIDGE_PORT,
    host = '127.0.0.1',
    timeoutMs = DEFAULT_DETECT_TIMEOUT_MS,
    protocolVersion = PROTOCOL_VERSION,
  } = options;
  const baseUrl = `http://${host}:${port}`;
  const fetchImpl = options.fetch ?? ((input, init) => fetch(input, init));
  return {
    baseUrl,
    async detect(signal?: AbortSignal): Promise<BridgeDetection> {
      try {
        const response = await fetchImpl(`${baseUrl}/health`, {
          method: 'GET',
          headers: { Accept: 'application/json' },
          signal: withTimeout(timeoutMs, signal),
        });
        if (!response.ok) {
          return {
            status: 'missing',
            baseUrl,
            error: new EngineUnavailableError(`${baseUrl}/health answered ${response.status}`),
          };
        }
        const body: unknown = await response.json();
        if (!isHealth(body)) {
          return {
            status: 'missing',
            baseUrl,
            error: new EngineUnavailableError(`${baseUrl}/health did not answer with a Health`),
          };
        }
        if (!body.protocolVersions.includes(protocolVersion)) {
          return {
            status: 'outdated',
            baseUrl,
            health: body,
            error: new EngineOutdatedError(protocolVersion, body.protocolVersions),
          };
        }
        return { status: 'ready', baseUrl, health: body };
      } catch (cause: unknown) {
        const message = cause instanceof Error ? cause.message : String(cause);
        return {
          status: 'missing',
          baseUrl,
          error: new EngineUnavailableError(`no Terminal Bridge at ${baseUrl}: ${message}`, {
            cause,
          }),
        };
      }
    },
  };
}
