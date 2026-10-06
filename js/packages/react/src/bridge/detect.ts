import { PROTOCOL_VERSION } from '@bilt/pos-protocol';
import {
  BridgeMissingError,
  BridgeOutdatedError,
  type BridgeDetection,
  type LocalBridgeOptions,
} from '@bilt/pos-sdk/bridge';

export type { BridgeDetection, LocalBridgeOptions };

/**
 * One probe for the Terminal Bridge: the signature of `detectBridge` from `@bilt/pos-sdk/bridge`,
 * which `useBridge` runs by default. A test or a register with its own discovery passes another.
 */
export type BridgeDetect = (options: LocalBridgeOptions) => Promise<BridgeDetection>;

/** The probe options `useBridge` forwards to `detectBridge`. */
export type BridgeProbeOptions = Pick<
  LocalBridgeOptions,
  'host' | 'port' | 'fallbackPorts' | 'healthTimeoutMs' | 'fetch'
>;

/**
 * The error `BiltPos.connect(localBridge())` would reject with for a detection, so a prompt
 * shows the same failure whether it polled or connected: `BridgeMissingError` for `missing`,
 * `BridgeOutdatedError` for `outdated`, nothing for `ready`.
 */
export function detectionError(
  detection: BridgeDetection,
): BridgeMissingError | BridgeOutdatedError | undefined {
  switch (detection.status) {
    case 'missing':
      return new BridgeMissingError(detection.probed);
    case 'outdated':
      return new BridgeOutdatedError(
        detection.baseUrl,
        PROTOCOL_VERSION,
        detection.health.protocolVersions,
      );
    default:
      return undefined;
  }
}
