import type { Health } from '@bilt/pos-protocol';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { BiltPosProvider, RetailMediaSurface, useTerminalSession } from '../src/index';
import {
  BridgeGate,
  InstallBridgePrompt,
  detectPlatform,
  parseBridgeManifest,
  useBridge,
  type BridgeDetect,
  type BridgeDetection,
} from '../src/bridge';
import { LANE, MockBiltPos } from './harness';

const HEALTH: Health = {
  host: 'bridge',
  hostVersion: '1.2.0',
  sdkVersion: '0.25.0',
  protocolVersions: ['1'],
  terminal: { model: 'VictaLane' },
};

const BASE_URL = 'http://127.0.0.1:48333';

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

function Probe() {
  // One port only, so every probe is exactly one fetch.
  const bridge = useBridge({ port: 48333, fallbackPorts: 0, pollIntervalMs: 2000 });
  return (
    <div>
      <span data-testid="status">{bridge.status}</span>
      <span data-testid="attempts">{bridge.attempts}</span>
      <span data-testid="error">{bridge.error?.name ?? ''}</span>
      <span data-testid="host">{bridge.health?.hostVersion ?? ''}</span>
      <span data-testid="base">{bridge.baseUrl ?? ''}</span>
      <span data-testid="probed">{bridge.probed?.join(',') ?? ''}</span>
      <button onClick={bridge.retry}>retry</button>
    </div>
  );
}

describe('useBridge over detectBridge', () => {
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
    expect(screen.getByTestId('error').textContent).toBe('BridgeMissingError');
    expect(screen.getByTestId('probed').textContent).toBe(`${BASE_URL}/health`);
    expect(screen.getByTestId('attempts').textContent).toBe('1');
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(String(fetchMock.mock.calls[0]![0])).toBe(`${BASE_URL}/health`);

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
    expect(screen.getByTestId('base').textContent).toBe(BASE_URL);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(10_000);
    });
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it('reports outdated with a BridgeOutdatedError when the protocol version is missing', async () => {
    fetchMock.mockResolvedValueOnce(json({ ...HEALTH, protocolVersions: ['0'] }));
    render(<Probe />);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(screen.getByTestId('status').textContent).toBe('outdated');
    expect(screen.getByTestId('error').textContent).toBe('BridgeOutdatedError');
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

/** A probe that answers the scripted outcomes in order, repeating the last; the shape `detectBridge` returns. */
function scripted(...outcomes: BridgeDetection['status'][]): BridgeDetect & { calls: number } {
  const detect = Object.assign(
    async (): Promise<BridgeDetection> => {
      const status = outcomes[Math.min(detect.calls, outcomes.length - 1)] ?? 'missing';
      detect.calls += 1;
      switch (status) {
        case 'ready':
          return { status, baseUrl: BASE_URL, health: HEALTH };
        case 'outdated':
          return { status, baseUrl: BASE_URL, health: { ...HEALTH, protocolVersions: ['0'] } };
        default:
          return { status: 'missing', probed: [`${BASE_URL}/health`] };
      }
    },
    { calls: 0 },
  );
  return detect;
}

describe('InstallBridgePrompt', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'navigator',
      Object.assign({}, navigator, { userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)' }),
    );
  });
  afterEach(() => vi.unstubAllGlobals());

  it('retries a failed manifest fetch while the bridge stays missing', async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockRejectedValueOnce(new Error('download service down'))
      .mockResolvedValue(
        json({
          version: '1.2.0',
          downloads: { windows: { url: 'https://dl.example/bridge.msi' } },
        }),
      );
    render(
      <InstallBridgePrompt
        detect={scripted('missing')}
        pollIntervalMs={60_000}
        manifestUrl="https://dl.example/manifest.json"
        fetch={fetchMock}
      />,
    );
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
    await waitFor(
      () =>
        expect(screen.getByText('Download for Windows').getAttribute('href')).toBe(
          'https://dl.example/bridge.msi',
        ),
      { timeout: 8000 },
    );
    expect(fetchMock).toHaveBeenCalledTimes(2);
  }, 10_000);

  it('links the installer from the manifest for this platform and falls back to downloadUrl', async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(
      json({
        version: '1.2.0',
        downloads: { windows: { url: 'https://dl.example/bridge.msi' } },
      }),
    );
    const view = render(
      <InstallBridgePrompt
        detect={scripted('missing')}
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
        detect={scripted('missing')}
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
    const detect = scripted('ready');
    render(
      <InstallBridgePrompt detect={detect} autoDetect={false}>
        <span>lane</span>
      </InstallBridgePrompt>,
    );
    expect(screen.getByRole('status').getAttribute('data-status')).toBe('detecting');
    expect(screen.getByText(/Choose Allow/)).toBeTruthy();
    expect(detect.calls).toBe(0);
    fireEvent.click(screen.getByText('Continue'));
    await waitFor(() => expect(screen.getByText('lane')).toBeTruthy());
    expect(detect.calls).toBe(1);
  });

  it('shows the update copy with the protocol versions when outdated', async () => {
    render(<InstallBridgePrompt detect={scripted('outdated')} pollIntervalMs={60_000} />);
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

function Register({ detect }: { detect: BridgeDetect }) {
  const bridge = useBridge({ detect, pollIntervalMs: 60_000 });
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
    const missing = render(<Register detect={scripted('missing')} />);
    expect(screen.getByText('Connecting…')).toBeTruthy();
    await waitFor(() =>
      expect(screen.getByRole('alert').getAttribute('data-status')).toBe('missing'),
    );
    expect(screen.getByText(/Install the Bilt Terminal Bridge/)).toBeTruthy();
    missing.unmount();

    const outdated = render(<Register detect={scripted('outdated')} />);
    await waitFor(() =>
      expect(screen.getByRole('alert').getAttribute('data-status')).toBe('outdated'),
    );
    outdated.unmount();

    render(<Register detect={scripted('ready')} />);
    await waitFor(() => expect(screen.getByTestId('lane').textContent).toBe('open'));
  });

  it('lets the lane through once a missing bridge appears, without a reload', async () => {
    const detect = scripted('missing', 'ready');
    function Polling() {
      const bridge = useBridge({ detect, pollIntervalMs: 10 });
      return bridge.status === 'ready' ? <span>lane</span> : <span>{bridge.status}</span>;
    }
    render(<Polling />);
    await waitFor(() => expect(screen.getByText('missing')).toBeTruthy());
    await waitFor(() => expect(screen.getByText('lane')).toBeTruthy());
    expect(detect.calls).toBe(2);
  });

  it('keeps polling when a custom detector throws', async () => {
    const ready = scripted('ready');
    let calls = 0;
    const detect: BridgeDetect = (options) => {
      calls += 1;
      if (calls === 1) throw new Error('detector blew up');
      return ready(options);
    };
    function Polling() {
      const bridge = useBridge({ detect, pollIntervalMs: 10 });
      return <span>{bridge.status}</span>;
    }
    render(<Polling />);
    await waitFor(() => expect(screen.getByText('missing')).toBeTruthy());
    await waitFor(() => expect(screen.getByText('ready')).toBeTruthy());
    expect(calls).toBe(2);
  });

  it('stays ready when detect and fetch are new on every render', async () => {
    const probe = scripted('ready');
    function Inline() {
      const bridge = useBridge({
        detect: (options) => probe(options),
        fetch: async () => json({}),
      });
      return <span>{bridge.status}</span>;
    }
    const view = render(<Inline />);
    await waitFor(() => expect(screen.getByText('ready')).toBeTruthy());
    view.rerender(<Inline />);
    view.rerender(<Inline />);
    expect(screen.getByText('ready')).toBeTruthy();
    expect(probe.calls).toBe(1);
  });

  it('BridgeGate renders the prompt, then its children', async () => {
    render(
      <BridgeGate detect={scripted('missing', 'ready')} pollIntervalMs={10}>
        {(bridge) => <span>lane on {bridge.health?.hostVersion}</span>}
      </BridgeGate>,
    );
    await waitFor(() => expect(screen.getByRole('alert')).toBeTruthy());
    await waitFor(() => expect(screen.getByText('lane on 1.2.0')).toBeTruthy());
  });
});
