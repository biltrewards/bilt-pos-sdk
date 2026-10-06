/**
 * `@bilt/pos-sdk/bridge`: the Terminal Bridge engine. `localBridge()` finds the bridge on
 * loopback and hands `BiltPos.connect` an engine for it; `detectBridge()` is the probe an
 * install prompt polls. Everything bridge-specific — ports, detection states, the install and
 * update errors — lives here and nowhere in the core entry point, so a register written against
 * `@bilt/pos-sdk` runs unchanged over another engine.
 *
 * ```ts
 * import { BiltPos } from '@bilt/pos-sdk';
 * import { BridgeMissingError, BridgeOutdatedError, localBridge } from '@bilt/pos-sdk/bridge';
 *
 * try {
 *   const pos = await BiltPos.connect(localBridge());
 * } catch (error) {
 *   if (error instanceof BridgeMissingError) showInstallPrompt();
 *   else if (error instanceof BridgeOutdatedError) showUpdatePrompt();
 *   else throw error;
 * }
 * ```
 *
 * @packageDocumentation
 */
import type { EngineFactory } from '../pos';
import { detectBridge } from './detect';
import { BridgeEngine } from './engine';
import { BridgeMissingError, BridgeOutdatedError } from './errors';
import { resolveOptions, type LocalBridgeOptions } from './options';

export { detectBridge, type BridgeDetection } from './detect';
export { BridgeEngine, BRIDGE_CAPABILITIES } from './engine';
export { BridgeMissingError, BridgeOutdatedError } from './errors';
export {
  BRIDGE_DEFAULT_FALLBACK_PORTS,
  BRIDGE_DEFAULT_HEALTH_TIMEOUT_MS,
  BRIDGE_DEFAULT_PORT,
  type LocalBridgeOptions,
} from './options';

/**
 * The engine factory for a Terminal Bridge on the register machine. When `BiltPos.connect`
 * runs it, it probes `GET /health` on port 48333 and the ten ports above it (400 ms each, all at
 * once), then rejects with `BridgeMissingError` when nothing answered or `BridgeOutdatedError`
 * when the bridge does not list protocol version `1`. There is no pairing in this iteration;
 * `bearerToken` is accepted for the day there is.
 */
export function localBridge(options: LocalBridgeOptions = {}): EngineFactory {
  return async (context) => {
    const detection = await detectBridge(options);
    switch (detection.status) {
      case 'ready':
        return new BridgeEngine(detection.baseUrl, resolveOptions(options));
      case 'outdated':
        throw new BridgeOutdatedError(
          detection.baseUrl,
          context.protocolVersion,
          detection.health.protocolVersions,
        );
      default:
        throw new BridgeMissingError(detection.probed);
    }
  };
}
