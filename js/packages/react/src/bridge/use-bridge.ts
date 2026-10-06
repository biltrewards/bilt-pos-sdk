import type { Health } from '@bilt/pos-protocol';
import {
  detectBridge,
  type BridgeDetection,
  type BridgeMissingError,
  type BridgeOutdatedError,
} from '@bilt/pos-sdk/bridge';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { detectionError, type BridgeDetect, type BridgeProbeOptions } from './detect';
import {
  downloadFor,
  fetchBridgeManifest,
  type BridgeDownload,
  type BridgeManifest,
} from './manifest';
import { detectPlatform, type BridgePlatform } from './platform';

/**
 * Where the bridge stands: `detecting` until the first probe answered, `missing` when nothing
 * answered on loopback, `outdated` when a bridge answered but speaks an older protocol, `ready`
 * when the register can connect. There is no pairing state in this iteration.
 */
export type BridgeStatus = 'detecting' | 'missing' | 'outdated' | 'ready';

/** Options for `useBridge`: the probe options of `localBridge()` plus the hook's own. */
export interface UseBridgeOptions extends BridgeProbeOptions {
  /**
   * Where the Terminal Bridge update manifest is. Fetched once the bridge turns out `missing`
   * or `outdated`, to link the installer for this platform; `InstallBridgePrompt` falls back
   * to its `downloadUrl` prop without one.
   */
  readonly manifestUrl?: string;

  /** How often to probe again while `missing` or `outdated`; default 2000 ms. */
  readonly pollIntervalMs?: number;

  /**
   * Whether to probe on mount; default `true`. With `false` the hook stays `detecting` until
   * `retry()`, so the page can explain Chrome's loopback permission prompt before the first
   * probe triggers it.
   */
  readonly autoDetect?: boolean;

  /** The probe to run; default `detectBridge` from `@bilt/pos-sdk/bridge`. */
  readonly detect?: BridgeDetect;
}

/** What `useBridge` returns. */
export interface BridgeState {
  readonly status: BridgeStatus;

  /**
   * Why the bridge is `missing` or `outdated`: the `BridgeMissingError` or `BridgeOutdatedError`
   * that `BiltPos.connect(localBridge())` would reject with; `undefined` otherwise.
   */
  readonly error?: BridgeMissingError | BridgeOutdatedError;

  /** The bridge's health once one answered, `ready` or `outdated`. */
  readonly health?: Health;

  /** The loopback origin a bridge answered on, e.g. `http://127.0.0.1:48333`; `undefined` while `detecting` or `missing`. */
  readonly baseUrl?: string;

  /** The health URLs the last probe tried, when it found nothing. */
  readonly probed?: readonly string[];

  /** How many probes have run; `0` before the first. */
  readonly attempts: number;

  /** This browser's platform, from the user agent. */
  readonly platform: BridgePlatform;

  /** The update manifest, once loaded. */
  readonly manifest?: BridgeManifest;

  /** The installer for `platform` from the manifest, when it lists one. */
  readonly download?: BridgeDownload;

  /** Probes again now, also while `detecting` with `autoDetect: false`. */
  retry(): void;
}

interface Probe {
  detection: BridgeDetection | null;
  attempts: number;
}

const MANIFEST_RETRY_MS = 5000;

/**
 * Detects the Terminal Bridge on loopback and keeps watching for it: one `detectBridge()` probe
 * on mount (port 48333 and its fallback range, 400 ms each), then one every `pollIntervalMs`
 * while it is `missing` or `outdated`, so a cashier who installs or updates the bridge is let
 * through without a page reload. `retry()` probes at once.
 *
 * ```tsx
 * const bridge = useBridge();
 * if (bridge.status !== 'ready') return <InstallBridgePrompt bridge={bridge} />;
 * ```
 */
export function useBridge(options: UseBridgeOptions = {}): BridgeState {
  const {
    host,
    port,
    fallbackPorts,
    healthTimeoutMs,
    fetch: fetchImpl,
    manifestUrl,
    pollIntervalMs = 2000,
    autoDetect = true,
    detect = detectBridge,
  } = options;
  const probeOptions = useMemo<BridgeProbeOptions>(
    () => ({
      ...(host === undefined ? {} : { host }),
      ...(port === undefined ? {} : { port }),
      ...(fallbackPorts === undefined ? {} : { fallbackPorts }),
      ...(healthTimeoutMs === undefined ? {} : { healthTimeoutMs }),
      ...(fetchImpl === undefined ? {} : { fetch: fetchImpl }),
    }),
    [host, port, fallbackPorts, healthTimeoutMs, fetchImpl],
  );
  // The probe restarts when where it looks changes, not when an inline `detect`, `fetch` or
  // `fallbackPorts` array gets a new identity on every render; those are read through a ref.
  const probeKey = JSON.stringify([host, port, fallbackPorts, healthTimeoutMs]);
  const latest = useRef({ detect, probeOptions });
  latest.current = { detect, probeOptions };

  const [probe, setProbe] = useState<Probe>({ detection: null, attempts: 0 });
  const [manifest, setManifest] = useState<BridgeManifest | null>(null);
  const [manifestFailures, setManifestFailures] = useState(0);
  const [requested, setRequested] = useState(autoDetect ? 1 : 0);
  const latestProbe = useRef(0);
  const platform = useMemo(() => detectPlatform(), []);

  const run = useCallback(async () => {
    const token = ++latestProbe.current;
    // A custom detector that throws or rejects counts as a probe that found nothing, so polling
    // goes on.
    let detection: BridgeDetection;
    try {
      detection = await latest.current.detect(latest.current.probeOptions);
    } catch {
      detection = { status: 'missing', probed: [] };
    }
    if (token !== latestProbe.current) return;
    setProbe((previous) => ({ detection, attempts: previous.attempts + 1 }));
  }, []);

  useEffect(() => {
    setProbe({ detection: null, attempts: 0 });
  }, [probeKey]);

  useEffect(() => {
    if (requested === 0) return;
    void run();
  }, [requested, run, probeKey]);

  const status: BridgeStatus = probe.detection?.status ?? 'detecting';

  useEffect(() => {
    if (status !== 'missing' && status !== 'outdated') return;
    const timer = setTimeout(() => void run(), pollIntervalMs);
    return () => clearTimeout(timer);
  }, [status, probe.attempts, requested, pollIntervalMs, run]);

  useEffect(() => {
    if (!manifestUrl || manifest || (status !== 'missing' && status !== 'outdated')) return;
    const controller = new AbortController();
    const fetchOptions = fetchImpl
      ? { fetch: fetchImpl, signal: controller.signal }
      : { signal: controller.signal };
    let retryTimer: ReturnType<typeof setTimeout> | null = null;
    fetchBridgeManifest(manifestUrl, fetchOptions).then(
      (loaded) => {
        if (!controller.signal.aborted) setManifest(loaded);
      },
      () => {
        // A transient failure should not leave the prompt without an installer link for good.
        if (controller.signal.aborted) return;
        retryTimer = setTimeout(() => setManifestFailures((n) => n + 1), MANIFEST_RETRY_MS);
      },
    );
    return () => {
      controller.abort();
      if (retryTimer !== null) clearTimeout(retryTimer);
    };
  }, [manifestUrl, manifest, status, fetchImpl, manifestFailures]);

  useEffect(
    () => () => {
      latestProbe.current += 1;
    },
    [],
  );

  const retry = useCallback(() => setRequested((n) => n + 1), []);

  return useMemo<BridgeState>(() => {
    const detection = probe.detection;
    const error = detection ? detectionError(detection) : undefined;
    const download = downloadFor(manifest, platform);
    return {
      status,
      attempts: probe.attempts,
      platform,
      retry,
      ...(error ? { error } : {}),
      ...(detection && detection.status !== 'missing'
        ? { health: detection.health, baseUrl: detection.baseUrl }
        : {}),
      ...(detection && detection.status === 'missing' ? { probed: detection.probed } : {}),
      ...(manifest ? { manifest } : {}),
      ...(download ? { download } : {}),
    };
  }, [status, probe, platform, retry, manifest]);
}
