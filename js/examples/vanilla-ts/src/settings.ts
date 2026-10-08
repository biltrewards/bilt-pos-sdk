/** The lane settings, kept in `localStorage`. `local` runs basket and member without a terminal. */
export interface Settings {
  readonly mode: 'terminal' | 'local';
  readonly poiId: string;
  readonly saleId: string;
  readonly currency: string;
  readonly storeLocation: string;
}

export const STORAGE_KEY = 'vanilla-ts.settings';

export const DEFAULT_SETTINGS: Settings = {
  mode: 'terminal',
  poiId: 'VictaLane-275839164',
  saleId: 'LANE-3',
  currency: 'USD',
  storeLocation: 'STR-0142',
};

export function loadSettings(): Settings {
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '{}') as Partial<Settings>;
    return { ...DEFAULT_SETTINGS, ...saved, mode: saved.mode === 'local' ? 'local' : 'terminal' };
  } catch {
    return DEFAULT_SETTINGS;
  }
}

export function saveSettings(settings: Settings): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
  } catch {
    // A private window or a full quota: the settings simply do not persist.
  }
}
