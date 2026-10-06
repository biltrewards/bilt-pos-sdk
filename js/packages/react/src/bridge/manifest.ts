import type { BridgePlatform } from './platform';

/** One installer in the update manifest. */
export interface BridgeDownload {
  readonly url: string;
  readonly sha256?: string;
  readonly signature?: string;
}

/**
 * The Terminal Bridge update manifest, as the install prompt reads it: the latest version, the
 * installer per platform, the oldest protocol version that build speaks and where the release
 * notes are. The bridge's own updater reads the same document. Provisional until the update
 * feed ships; the reader below accepts a superset so a richer manifest keeps working.
 */
export interface BridgeManifest {
  readonly version: string;
  readonly minimumProtocolVersion?: string;
  readonly releaseNotesUrl?: string;
  readonly downloads: Partial<Record<Exclude<BridgePlatform, 'unknown'>, BridgeDownload>>;
}

const PLATFORM_KEYS = ['windows', 'macos', 'linux'] as const;

function readDownload(value: unknown): BridgeDownload | null {
  if (typeof value === 'string') return { url: value };
  if (typeof value !== 'object' || value === null) return null;
  const record = value as Record<string, unknown>;
  if (typeof record.url !== 'string') return null;
  const download: { url: string; sha256?: string; signature?: string } = { url: record.url };
  if (typeof record.sha256 === 'string') download.sha256 = record.sha256;
  if (typeof record.signature === 'string') download.signature = record.signature;
  return download;
}

/**
 * Validates a parsed manifest document. Throws on a document without a `version` or a
 * per-platform download map (`downloads`, or `platforms` as the feed's older drafts called it).
 */
export function parseBridgeManifest(document: unknown): BridgeManifest {
  if (typeof document !== 'object' || document === null) {
    throw new Error('the bridge manifest is not an object');
  }
  const record = document as Record<string, unknown>;
  if (typeof record.version !== 'string') {
    throw new Error('the bridge manifest has no version');
  }
  const source = record.downloads ?? record.platforms;
  if (typeof source !== 'object' || source === null) {
    throw new Error('the bridge manifest has no downloads');
  }
  const downloads: { -readonly [K in keyof BridgeManifest['downloads']]: BridgeDownload } = {};
  for (const key of PLATFORM_KEYS) {
    const download = readDownload((source as Record<string, unknown>)[key]);
    if (download) downloads[key] = download;
  }
  const manifest: {
    version: string;
    minimumProtocolVersion?: string;
    releaseNotesUrl?: string;
    downloads: BridgeManifest['downloads'];
  } = { version: record.version, downloads };
  if (typeof record.minimumProtocolVersion === 'string') {
    manifest.minimumProtocolVersion = record.minimumProtocolVersion;
  }
  if (typeof record.releaseNotesUrl === 'string') {
    manifest.releaseNotesUrl = record.releaseNotesUrl;
  }
  return manifest;
}

/** Fetches and validates the manifest at `url`. */
export async function fetchBridgeManifest(
  url: string,
  options: { readonly fetch?: typeof fetch; readonly signal?: AbortSignal } = {},
): Promise<BridgeManifest> {
  const fetchImpl = options.fetch ?? ((input, init) => fetch(input, init));
  const init: RequestInit = { headers: { Accept: 'application/json' } };
  if (options.signal) init.signal = options.signal;
  const response = await fetchImpl(url, init);
  if (!response.ok) throw new Error(`the bridge manifest at ${url} answered ${response.status}`);
  return parseBridgeManifest(await response.json());
}

/** The installer for a platform, or `null` when the manifest has none (and for `unknown`). */
export function downloadFor(
  manifest: BridgeManifest | null | undefined,
  platform: BridgePlatform,
): BridgeDownload | null {
  if (!manifest || platform === 'unknown') return null;
  return manifest.downloads[platform] ?? null;
}
