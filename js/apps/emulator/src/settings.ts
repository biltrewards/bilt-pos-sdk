import type { LocalBridgeOptions } from '@bilt/pos-sdk/bridge';
import { useCallback, useState } from 'react';

/** `terminal` brackets the session on a Bilt terminal; `local` runs basket, member and widgets only. */
export type SessionMode = 'terminal' | 'local';

/**
 * How the page reaches the bridge. `direct` is what a deployed register does: the SDK probes
 * `http://127.0.0.1:<bridgePort>`. `proxy` goes through the page's own origin, where the Vite
 * dev server forwards to the bridge (see `vite.config.ts`); it exists because the development
 * bridge sends no CORS headers yet.
 */
export type BridgeRoute = 'direct' | 'proxy';

export interface Settings {
  readonly mode: SessionMode;

  /**
   * The Nexo `POIID` a terminal session's messages carry. It does not pick a terminal: the
   * bridge drives exactly one and passes this through. Blank leaves it to the bridge's default.
   */
  readonly poiId: string;
  readonly saleId: string;
  readonly currency: string;
  readonly storeLocation: string;
  readonly bridge: BridgeRoute;

  /** The first port the direct route probes; the SDK tries the ten above it as well. */
  readonly bridgePort: number;
}

export const STORAGE_KEY = 'bilt-pos-emulator.settings';

export const DEFAULT_SETTINGS: Settings = {
  mode: 'terminal',
  poiId: '',
  saleId: 'LANE-3',
  currency: 'USD',
  storeLocation: 'STR-0142',
  bridge: import.meta.env.DEV ? 'proxy' : 'direct',
  bridgePort: 48333,
};

function text(value: unknown, fallback: string): string {
  return typeof value === 'string' && value.trim().length > 0 ? value : fallback;
}

function port(value: unknown, fallback: number): number {
  const n = Number(value);
  return Number.isInteger(n) && n > 0 && n < 65536 ? n : fallback;
}

function safeStorage(): Storage | undefined {
  try {
    return typeof localStorage === 'undefined' ? undefined : localStorage;
  } catch {
    return undefined;
  }
}

/** The settings as last saved, each field falling back to its default when missing or malformed. */
export function loadSettings(storage: Storage | undefined = safeStorage()): Settings {
  try {
    const raw = storage?.getItem(STORAGE_KEY);
    if (!raw) return DEFAULT_SETTINGS;
    const parsed = JSON.parse(raw) as Partial<Record<keyof Settings, unknown>>;
    return {
      mode: parsed.mode === 'local' ? 'local' : 'terminal',
      poiId: typeof parsed.poiId === 'string' ? parsed.poiId.trim() : DEFAULT_SETTINGS.poiId,
      saleId: text(parsed.saleId, DEFAULT_SETTINGS.saleId),
      currency: text(parsed.currency, DEFAULT_SETTINGS.currency).toUpperCase(),
      storeLocation: text(parsed.storeLocation, DEFAULT_SETTINGS.storeLocation),
      bridge:
        parsed.bridge === 'proxy' || parsed.bridge === 'direct'
          ? parsed.bridge
          : DEFAULT_SETTINGS.bridge,
      bridgePort: port(parsed.bridgePort, DEFAULT_SETTINGS.bridgePort),
    };
  } catch {
    return DEFAULT_SETTINGS;
  }
}

export function saveSettings(
  settings: Settings,
  storage: Storage | undefined = safeStorage(),
): void {
  try {
    storage?.setItem(STORAGE_KEY, JSON.stringify(settings));
  } catch {
    // A private window or a full quota: the settings simply do not persist.
  }
}

/** How status lines name the lane's session, with the passed-through `POIID` when one is set. */
export function describeSession(settings: Pick<Settings, 'mode' | 'poiId'>): string {
  if (settings.mode === 'local') return 'local session';
  return settings.poiId ? `terminal session (POIID ${settings.poiId})` : 'terminal session';
}

/** The persisted settings as state; `update` saves and re-renders. */
export function useSettings(): readonly [Settings, (next: Settings) => void] {
  const [settings, setSettings] = useState<Settings>(() => loadSettings());
  const update = useCallback((next: Settings) => {
    saveSettings(next);
    setSettings(next);
  }, []);
  return [settings, update];
}

/**
 * The `localBridge()` options for the settings. `direct` probes `127.0.0.1:<bridgePort>` and the
 * fallback range above it. `proxy` probes the page's own origin, one port, so the Vite proxy
 * carries the traffic; a page not served over plain HTTP takes the direct route, since the SDK
 * builds `http://` and `ws://` URLs only.
 */
export function bridgeOptions(
  settings: Pick<Settings, 'bridge' | 'bridgePort'>,
): LocalBridgeOptions {
  if (settings.bridge === 'direct' || typeof window === 'undefined') {
    return { port: settings.bridgePort };
  }
  const { hostname, port: pagePort, protocol } = window.location;
  if (protocol !== 'http:') return { port: settings.bridgePort };
  return { host: hostname, port: Number(pagePort || 80), fallbackPorts: 0 };
}

/** What identifies a lane: changing any of it restarts the session. */
export function laneKey(settings: Settings): string {
  return [
    settings.mode,
    settings.poiId,
    settings.saleId,
    settings.currency,
    settings.storeLocation,
  ].join('|');
}
