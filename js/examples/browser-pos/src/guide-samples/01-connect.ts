// Samples for "Connect" and "Engines and capabilities" in docs/javascript-sdk-integration.md.
import { BiltPos, EngineError } from '@bilt/pos-sdk';
import { BridgeMissingError, BridgeOutdatedError, localBridge } from '@bilt/pos-sdk/bridge';

declare function showInstallPrompt(probed: readonly string[]): void;
declare function showUpdatePrompt(required: string, available: readonly string[]): void;
declare function showHostUnavailable(message: string): void;

export async function connect(): Promise<BiltPos> {
  try {
    return await BiltPos.connect(localBridge());
  } catch (error) {
    if (error instanceof BridgeMissingError) {
      showInstallPrompt(error.probed); // the health URLs that stayed silent
    } else if (error instanceof BridgeOutdatedError) {
      showUpdatePrompt(error.required, error.available); // protocol versions
    } else if (error instanceof EngineError) {
      showHostUnavailable(error.message); // any other engine failure
    }
    throw error;
  }
}

export function describeEngine(pos: BiltPos): string {
  const { name, survivesPageReload, worksOffline, supportsWidgets } = pos.capabilities;
  return [
    `engine ${name}`,
    survivesPageReload ? 'a reload keeps the session' : 'a reload loses the session',
    worksOffline ? 'checkouts run through an internet outage' : 'checkouts need the internet',
    supportsWidgets ? 'widgets available' : 'no widgets',
  ].join(', ');
}
