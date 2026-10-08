import type { LocalBridgeOptions } from '@bilt/pos-sdk/bridge';
import { useCallback, useState } from 'react';

/** `terminal` brackets the session on a Bilt terminal; `local` runs basket, member and widgets only. */
export type SessionMode = 'terminal' | 'local';

/**
 * How the page reaches the bridge. `direct` is what a deployed register does: the SDK probes
 * `http://127.0.0.1:48333`. `proxy` goes through the page's own origin, where the Vite dev
 * server forwards to the bridge (see `vite.config.ts`); it exists because the development
 * bridge sends no CORS headers yet.
 */
export type BridgeRoute = 'direct' | 'proxy';

export interface Settings {
  readonly mode: SessionMode;

  /** The Nexo `POIID` passed through to the bridge's one terminal; blank uses the bridge's default. */
  readonly poiId: string;
  readonly saleId: string;
  readonly currency: string;
  readonly storeLocation: string;
  readonly bridge: BridgeRoute;

  /** Whether the session carries a retail-media widget for the `lane-banner` placement. */
  readonly retailMedia: boolean;
}

export const STORAGE_KEY = 'browser-pos.settings';

export const DEFAULT_SETTINGS: Settings = {
  mode: 'terminal',
  poiId: '',
  saleId: 'LANE-3',
  currency: 'USD',
  storeLocation: 'STR-0142',
  bridge: import.meta.env.DEV ? 'proxy' : 'direct',
  retailMedia: true,
};

function text(value: unknown, fallback: string): string {
  return typeof value === 'string' && value.trim().length > 0 ? value : fallback;
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
      retailMedia: typeof parsed.retailMedia === 'boolean' ? parsed.retailMedia : true,
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
 * The `localBridge()` options for a route. `direct` takes every default (port 48333 and its
 * fallback range on 127.0.0.1). `proxy` probes the page's own origin, one port, so the Vite
 * proxy carries the traffic. The SDK builds `http://` and `ws://` URLs only, so the proxy route
 * is for the plain-HTTP dev server; a page served over HTTPS takes the direct route, which
 * Chrome allows to loopback after its one-time permission prompt.
 */
export function bridgeOptions(route: BridgeRoute): LocalBridgeOptions {
  if (route === 'direct' || typeof window === 'undefined') return {};
  const { hostname, port, protocol } = window.location;
  if (protocol !== 'http:') return {};
  return { host: hostname, port: Number(port || 80), fallbackPorts: 0 };
}

/** How status lines name the session, with the passed-through `POIID` when one is set. */
export function describeSession(settings: Pick<Settings, 'mode' | 'poiId'>): string {
  if (settings.mode === 'local') return 'local session';
  return settings.poiId ? `terminal session (POIID ${settings.poiId})` : 'terminal session';
}
