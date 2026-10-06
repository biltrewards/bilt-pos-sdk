import { PROTOCOL_VERSION, type Health } from '@bilt/pos-protocol';
import { isLoopback, resolveOptions, type LocalBridgeOptions } from './options';

/**
 * What `detectBridge()` found. `ready` carries the base URL to talk to; `outdated` means a
 * bridge answered without this SDK's protocol version; `missing` lists the health URLs that
 * stayed silent.
 */
export type BridgeDetection =
  | { readonly status: 'ready'; readonly baseUrl: string; readonly health: Health }
  | { readonly status: 'outdated'; readonly baseUrl: string; readonly health: Health }
  | { readonly status: 'missing'; readonly probed: readonly string[] };

/**
 * Probes `GET /health` on the bridge's well-known port and its fallback range, each with the
 * health timeout, all at once. The lowest port with a compatible bridge wins; a bridge that
 * answered without protocol version `1` is reported as `outdated` only when no compatible one
 * answered. This is what an install prompt polls every couple of seconds.
 *
 * On Chrome a page served from a public origin triggers the one-time loopback-network
 * permission prompt here, so a register should explain it before the first call.
 */
export async function detectBridge(options: LocalBridgeOptions = {}): Promise<BridgeDetection> {
  const resolved = resolveOptions(options);
  const ports = Array.from({ length: resolved.fallbackPorts + 1 }, (_, i) => resolved.port + i);
  const probed = ports.map((port) => `http://${resolved.host}:${port}/health`);
  const init: Record<string, unknown> = isLoopback(resolved.host)
    ? { targetAddressSpace: 'loopback' }
    : {};

  const results = await Promise.all(
    probed.map(async (url): Promise<Health | undefined> => {
      try {
        const response = await resolved.fetch(url, {
          ...init,
          method: 'GET',
          headers: { Accept: 'application/json' },
          signal: AbortSignal.timeout(resolved.healthTimeoutMs),
        });
        if (!response.ok) return undefined;
        const body = (await response.json()) as Partial<Health>;
        return Array.isArray(body.protocolVersions) ? (body as Health) : undefined;
      } catch {
        return undefined;
      }
    }),
  );

  let outdated: BridgeDetection | undefined;
  for (let i = 0; i < results.length; i++) {
    const health = results[i];
    if (!health) continue;
    const baseUrl = `http://${resolved.host}:${ports[i]}`;
    if (health.protocolVersions.includes(PROTOCOL_VERSION)) {
      return { status: 'ready', baseUrl, health };
    }
    outdated ??= { status: 'outdated', baseUrl, health };
  }
  return outdated ?? { status: 'missing', probed };
}
