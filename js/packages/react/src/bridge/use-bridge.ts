import type { Health } from '@bilt/pos-protocol';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { bridgeDetector, type BridgeDetection, type BridgeDetector } from './detector';
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

/** Options for `useBridge`. */
export interface UseBridgeOptions {
  /** The loopback port to probe; default 48333. */
  readonly port?: number;

  /**
   * Where the Terminal Bridge update manifest is. Fetched once the bridge turns out `missing`
   * or `outdated`, to link the installer for this platform; `InstallBridgePrompt` falls back
   * to its `downloadUrl` prop without one.
   */
  readonly manifestUrl?: string;

  /** How often to probe again while `missing` or `outdated`; default 2000 ms. */
  readonly pollIntervalMs?: number;

  /** How long one probe may take; default 400 ms. */
  readonly timeoutMs?: number;

  /**
   * Whether to probe on mount; default `true`. With `false` the hook stays `detecting` until
   * `retry()`, so the page can explain Chrome's loopback permission prompt before the first
   * probe triggers it.
   */
  readonly autoDetect?: boolean;

  /** The probe to run; default `bridgeDetector({ port, timeoutMs })`. */
  readonly detector?: BridgeDetector;

  /** The `fetch` the manifest is loaded with; default the global one. */
  readonly fetch?: typeof fetch;
}

/** What `useBridge` returns. */
export interface BridgeState {
  readonly status: BridgeStatus;

  /** Why the bridge is `missing` or `outdated`; `undefined` otherwise. */
  readonly error?: Error;

  /** The bridge's health once one answered, `ready` or `outdated`. */
  readonly health?: Health;

  /** The loopback origin probed, e.g. `http://127.0.0.1:48333`. */
  readonly baseUrl: string;

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

/**
 * Detects the Terminal Bridge on loopback and keeps watching for it: one `/health` probe on
 * mount, then one every `pollIntervalMs` while it is `missing` or `outdated`, so a cashier who
 * installs or updates the bridge is let through without a page reload. `retry()` probes at
 * once.
 *
 * ```tsx
 * const bridge = useBridge();
 * if (bridge.status !== 'ready') return <InstallBridgePrompt bridge={bridge} />;
 * ```
 */
export function useBridge(options: UseBridgeOptions = {}): BridgeState {
  const { port, manifestUrl, pollIntervalMs = 2000, timeoutMs, autoDetect = true } = options;
  const detector = useMemo(
    () =>
      options.detector ??
      bridgeDetector({
        ...(port === undefined ? {} : { port }),
        ...(timeoutMs === undefined ? {} : { timeoutMs }),
      }),
    [options.detector, port, timeoutMs],
  );
  const fetchRef = useRef(options.fetch);
  fetchRef.current = options.fetch;

  const [probe, setProbe] = useState<Probe>({ detection: null, attempts: 0 });
  const [manifest, setManifest] = useState<BridgeManifest | null>(null);
  const [requested, setRequested] = useState(autoDetect ? 1 : 0);
  const inFlight = useRef<AbortController | null>(null);
  const platform = useMemo(() => detectPlatform(), []);

  const run = useCallback(async () => {
    inFlight.current?.abort();
    const controller = new AbortController();
    inFlight.current = controller;
    const detection = await detector.detect(controller.signal);
    if (controller.signal.aborted) return;
    inFlight.current = null;
    setProbe((previous) => ({ detection, attempts: previous.attempts + 1 }));
  }, [detector]);

  useEffect(() => {
    setProbe({ detection: null, attempts: 0 });
  }, [detector]);

  useEffect(() => {
    if (requested === 0) return;
    void run();
  }, [requested, run]);

  const status: BridgeStatus = probe.detection?.status ?? 'detecting';

  useEffect(() => {
    if (status !== 'missing' && status !== 'outdated') return;
    const timer = setTimeout(() => void run(), pollIntervalMs);
    return () => clearTimeout(timer);
  }, [status, probe.attempts, requested, pollIntervalMs, run]);

  useEffect(() => {
    if (!manifestUrl || manifest || (status !== 'missing' && status !== 'outdated')) return;
    const controller = new AbortController();
    const fetchOptions = fetchRef.current
      ? { fetch: fetchRef.current, signal: controller.signal }
      : { signal: controller.signal };
    fetchBridgeManifest(manifestUrl, fetchOptions).then(
      (loaded) => {
        if (!controller.signal.aborted) setManifest(loaded);
      },
      () => undefined,
    );
    return () => controller.abort();
  }, [manifestUrl, manifest, status]);

  useEffect(() => () => inFlight.current?.abort(), []);

  const retry = useCallback(() => setRequested((n) => n + 1), []);

  return useMemo<BridgeState>(() => {
    const detection = probe.detection;
    const download = downloadFor(manifest, platform);
    return {
      status,
      baseUrl: detector.baseUrl,
      attempts: probe.attempts,
      platform,
      retry,
      ...(detection && detection.status !== 'ready' ? { error: detection.error } : {}),
      ...(detection && detection.status !== 'missing' ? { health: detection.health } : {}),
      ...(manifest ? { manifest } : {}),
      ...(download ? { download } : {}),
    };
  }, [status, probe, detector, platform, retry, manifest]);
}
