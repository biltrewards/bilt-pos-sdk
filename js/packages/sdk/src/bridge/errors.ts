import { EngineOutdatedError, EngineUnavailableError } from '../errors';

/**
 * No Terminal Bridge answered on loopback: nothing listened on the well-known port or its
 * fallback range within the health timeout. The register shows the install prompt and polls
 * `detectBridge()` until the bridge is up. Extends `EngineUnavailableError`, so a register that
 * only distinguishes "no engine" from "engine too old" can catch the base classes.
 */
export class BridgeMissingError extends EngineUnavailableError {
  override readonly name = 'BridgeMissingError';

  constructor(
    /** The health URLs that were probed, in order. */
    readonly probed: readonly string[],
    message = `no Terminal Bridge answered on ${probed.length === 1 ? probed[0] : `${probed.length} loopback ports`}`,
  ) {
    super(message);
  }
}

/**
 * A Terminal Bridge answered but does not list the protocol version this SDK speaks. The
 * register shows the update prompt; the bridge also self-updates. Extends `EngineOutdatedError`.
 */
export class BridgeOutdatedError extends EngineOutdatedError {
  override readonly name = 'BridgeOutdatedError';

  constructor(
    /** Where the outdated bridge answered. */
    readonly baseUrl: string,
    required: string,
    available: readonly string[],
  ) {
    super(
      required,
      available,
      `the Terminal Bridge at ${baseUrl} speaks protocol version(s) ${available.join(', ') || 'none'}; this SDK needs ${required}`,
    );
  }
}
