/** The desktop platforms the Terminal Bridge installs on; `unknown` covers phones, tablets and anything else. */
export type BridgePlatform = 'windows' | 'macos' | 'linux' | 'unknown';

/** Human names for the download link, e.g. "Download for Windows". */
export const PLATFORM_NAMES: Readonly<Record<BridgePlatform, string>> = {
  windows: 'Windows',
  macos: 'macOS',
  linux: 'Linux',
  unknown: 'your computer',
};

/**
 * Reads the platform off a user agent string; the current browser's when none is given.
 * Android and iOS report `unknown`, since the bridge is a desktop application and tablets go
 * through the in-process SDK or the cloud service.
 */
export function detectPlatform(userAgent?: string): BridgePlatform {
  const ua = userAgent ?? (typeof navigator === 'undefined' ? '' : navigator.userAgent);
  if (/Android|iPhone|iPad|iPod/i.test(ua)) return 'unknown';
  if (/Windows/i.test(ua)) return 'windows';
  if (/Mac OS X|Macintosh/i.test(ua)) return 'macos';
  if (/Linux|X11|CrOS/i.test(ua)) return 'linux';
  return 'unknown';
}
