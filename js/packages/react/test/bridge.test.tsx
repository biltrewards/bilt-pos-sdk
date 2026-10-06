import type { Health } from '@bilt/pos-protocol';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { BiltPosProvider, RetailMediaSurface, useTerminalSession } from '../src/index';
import {
  BridgeGate,
  InstallBridgePrompt,
  bridgeDetector,
  detectPlatform,
  parseBridgeManifest,
  useBridge,
  type BridgeDetection,
  type BridgeDetector,
} from '../src/bridge';
import { LANE, MockBiltPos } from './harness';

const HEALTH: Health = {
  host: 'bridge',
  hostVersion: '1.2.0',
  sdkVersion: '0.25.0',
  protocolVersions: ['1'],
  terminals: [{ poiId: 'VictaLane-275839164' }],
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

function Probe() {
  const bridge = useBridge({ port: 48333, pollIntervalMs: 2000 });
  return (
    <div>
      <span data-testid="status">{bridge.status}</span>
      <span data-testid="attempts">{bridge.attempts}</span>
      <span data-testid="error">{bridge.error?.name ?? ''}</span>
      <span data-testid="host">{bridge.health?.hostVersion ?? ''}</span>
      <button onClick={bridge.retry}>retry</button>
    </div>
  );
}

describe('useBridge', () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => {
    vi.useFakeTimers();
    vi.stubGlobal('fetch', fetchMock);
    fetchMock.mockReset();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('goes detecting → missing → ready, polling /health every two seconds', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'));
    render(<Probe />);
    expect(screen.getByTestId('status').textContent).toBe('detecting');

    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(screen.getByTestId('status').textContent).toBe('missing');
    expect(screen.getByTestId('error').textContent).toBe('EngineUnavailableError');
    expect(screen.getByTestId('attempts').textContent).toBe('1');
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(String(fetchMock.mock.calls[0]![0])).toBe('http://127.0.0.1:48333/health');

    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'));
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000);
    });
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(screen.getByTestId('status').textContent).toBe('missing');

    fetchMock.mockResolvedValueOnce(json(HEALTH));
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000);
    });
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(screen.getByTestId('status').textContent).toBe('ready');
    expect(screen.getByTestId('host').textContent).toBe('1.2.0');

    await act(async () => {
      await vi.advanceTimersByTimeAsync(10_000);
    });
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it('reports outdated when the bridge does not speak this protocol version', async () => {
    fetchMock.mockResolvedValueOnce(json({ ...HEALTH, protocolVersions: ['0'] }));
    render(<Probe />);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(screen.getByTestId('status').textContent).toBe('outdated');
    expect(screen.getByTestId('error').textContent).toBe('EngineOutdatedError');
    expect(screen.getByTestId('host').textContent).toBe('1.2.0');
  });

  it('probes again at once on retry()', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'));
    render(<Probe />);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    fetchMock.mockResolvedValueOnce(json(HEALTH));
    await act(async () => {
      fireEvent.click(screen.getByText('retry'));
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(screen.getByTestId('status').textContent).toBe('ready');
  });

  it('treats a probe that outlives the timeout as missing', async () => {
    fetchMock.mockImplementationOnce(
      (_input, init) =>
        new Promise<Response>((_resolve, reject) => {
          init?.signal?.addEventListener('abort', () => reject(new Error('aborted')));
        }),
    );
    const detector = bridgeDetector({ timeoutMs: 400 });
    const probe = detector.detect();
    await vi.advanceTimersByTimeAsync(400);
    expect((await probe).status).toBe('missing');
  });
});

describe('detectPlatform and parseBridgeManifest', () => {
  it('reads the platform off the user agent', () => {
    expect(detectPlatform('Mozilla/5.0 (Windows NT 10.0; Win64; x64)')).toBe('windows');
    expect(detectPlatform('Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5)')).toBe('macos');
    expect(detectPlatform('Mozilla/5.0 (X11; Linux x86_64)')).toBe('linux');
    expect(detectPlatform('Mozilla/5.0 (Linux; Android 14)')).toBe('unknown');
    expect(detectPlatform('Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X)')).toBe('unknown');
  });

  it('accepts the manifest shape with optional fields', () => {
    const manifest = parseBridgeManifest({
      version: '1.2.0',
      minimumProtocolVersion: '1',
      releaseNotesUrl: 'https://example.com/notes',
      downloads: {
        windows: { url: 'https://example.com/bridge.msi', sha256: 'abc' },
        macos: 'https://example.com/bridge.pkg',
      },
    });
    expect(manifest.downloads.windows?.url).toBe('https://example.com/bridge.msi');
    expect(manifest.downloads.macos?.url).toBe('https://example.com/bridge.pkg');
    expect(manifest.downloads.linux).toBeUndefined();
    expect(() => parseBridgeManifest({ downloads: {} })).toThrow(/version/);
  });
});

function scripted(...outcomes: BridgeDetection['status'][]): BridgeDetector & { calls: number } {
  const baseUrl = 'http://127.0.0.1:48333';
  const detector = {
    baseUrl,
    calls: 0,
    async detect(): Promise<BridgeDetection> {
      const status = outcomes[Math.min(detector.calls, outcomes.length - 1)] ?? 'missing';
      detector.calls += 1;
      switch (status) {
        case 'ready':
          return { status, baseUrl, health: HEALTH };
        case 'outdated':
          return {
            status,
            baseUrl,
            health: { ...HEALTH, protocolVersions: ['0'] },
            error: new (await import('@bilt/pos-sdk')).EngineOutdatedError('1', ['0']),
          };
        default:
          return {
            status: 'missing',
            baseUrl,
            error: new (await import('@bilt/pos-sdk')).EngineUnavailableError(
              'nothing on loopback',
            ),
          };
      }
    },
  };
  return detector;
}

describe('InstallBridgePrompt', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'navigator',
      Object.assign({}, navigator, { userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)' }),
    );
  });
  afterEach(() => vi.unstubAllGlobals());

  it('links the installer from the manifest for this platform and falls back to downloadUrl', async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(
      json({
        version: '1.2.0',
        downloads: { windows: { url: 'https://dl.example/bridge.msi' } },
      }),
    );
    const view = render(
      <InstallBridgePrompt
        detector={scripted('missing')}
        pollIntervalMs={60_000}
        manifestUrl="https://dl.example/manifest.json"
        fetch={fetchMock}
        downloadUrl="https://dl.example/fallback"
      />,
    );
    await waitFor(() =>
      expect(screen.getByRole('alert').getAttribute('data-status')).toBe('missing'),
    );
    await waitFor(() =>
      expect(screen.getByText('Download for Windows').getAttribute('href')).toBe(
        'https://dl.example/bridge.msi',
      ),
    );
    expect(fetchMock).toHaveBeenCalledWith('https://dl.example/manifest.json', expect.anything());
    view.unmount();

    render(
      <InstallBridgePrompt
        detector={scripted('missing')}
        pollIntervalMs={60_000}
        downloadUrl="https://dl.example/fallback"
      />,
    );
    await waitFor(() =>
      expect(screen.getByText('Download for Windows').getAttribute('href')).toBe(
        'https://dl.example/fallback',
      ),
    );
  });

  it('explains the loopback permission and waits for the cashier with autoDetect off', async () => {
    const detector = scripted('ready');
    render(
      <InstallBridgePrompt detector={detector} autoDetect={false}>
        <span>lane</span>
      </InstallBridgePrompt>,
    );
    expect(screen.getByRole('status').getAttribute('data-status')).toBe('detecting');
    expect(screen.getByText(/Choose Allow/)).toBeTruthy();
    expect(detector.calls).toBe(0);
    fireEvent.click(screen.getByText('Continue'));
    await waitFor(() => expect(screen.getByText('lane')).toBeTruthy());
    expect(detector.calls).toBe(1);
  });

  it('shows the update copy with the protocol versions when outdated', async () => {
    render(<InstallBridgePrompt detector={scripted('outdated')} pollIntervalMs={60_000} />);
    await waitFor(() =>
      expect(screen.getByRole('alert').getAttribute('data-status')).toBe('outdated'),
    );
    expect(screen.getByText(/Update the Bilt Terminal Bridge/)).toBeTruthy();
    expect(screen.getByText('0 → 1')).toBeTruthy();
  });
});

// The design page's `Register()` sketch, minus the pairing branch this iteration leaves out.
function Lane() {
  const { session, status } = useTerminalSession(LANE);
  return (
    <div>
      <span data-testid="lane">{status}</span>
      <RetailMediaSurface session={session} placement="lane-banner" />
    </div>
  );
}

function Register({ detector }: { detector: BridgeDetector }) {
  const bridge = useBridge({ detector, pollIntervalMs: 60_000 });
  if (bridge.status === 'missing' || bridge.status === 'outdated') {
    return <InstallBridgePrompt bridge={bridge} downloadUrl="https://dl.example/bridge" />;
  }
  if (bridge.status === 'detecting') return <p>Connecting…</p>;
  return (
    <BiltPosProvider pos={new MockBiltPos()}>
      <Lane />
    </BiltPosProvider>
  );
}

describe('the Register() sketch', () => {
  it('renders the prompt for missing and outdated bridges and the lane once ready', async () => {
    const missing = render(<Register detector={scripted('missing')} />);
    expect(screen.getByText('Connecting…')).toBeTruthy();
    await waitFor(() =>
      expect(screen.getByRole('alert').getAttribute('data-status')).toBe('missing'),
    );
    expect(screen.getByText(/Install the Bilt Terminal Bridge/)).toBeTruthy();
    missing.unmount();

    const outdated = render(<Register detector={scripted('outdated')} />);
    await waitFor(() =>
      expect(screen.getByRole('alert').getAttribute('data-status')).toBe('outdated'),
    );
    outdated.unmount();

    render(<Register detector={scripted('ready')} />);
    await waitFor(() => expect(screen.getByTestId('lane').textContent).toBe('open'));
  });

  it('lets the lane through once a missing bridge appears, without a reload', async () => {
    const detector = scripted('missing', 'ready');
    function Polling() {
      const bridge = useBridge({ detector, pollIntervalMs: 10 });
      return bridge.status === 'ready' ? <span>lane</span> : <span>{bridge.status}</span>;
    }
    render(<Polling />);
    await waitFor(() => expect(screen.getByText('missing')).toBeTruthy());
    await waitFor(() => expect(screen.getByText('lane')).toBeTruthy());
    expect(detector.calls).toBe(2);
  });

  it('BridgeGate renders the prompt, then its children', async () => {
    render(
      <BridgeGate detector={scripted('missing', 'ready')} pollIntervalMs={10}>
        {(bridge) => <span>lane on {bridge.health?.hostVersion}</span>}
      </BridgeGate>,
    );
    await waitFor(() => expect(screen.getByRole('alert')).toBeTruthy());
    await waitFor(() => expect(screen.getByText('lane on 1.2.0')).toBeTruthy());
  });
});
