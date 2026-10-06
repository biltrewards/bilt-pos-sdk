// Builds and starts the real Session Host (`:host`, the Java module) for the contract suite.
//
// The host is looked for in the repository this package lives in, or in the checkout that
// `BILT_HOST_REPO` names (the host module may live on another branch than the SDK). It is built
// with `./gradlew :host:installDist` unless the installed distribution already exists; set
// `BILT_HOST_REBUILD=1` to force a build, or `BILT_HOST_BIN` to point at a start script directly.
// When no host can be found the suites skip and say why.
import { execFileSync, spawn, type ChildProcess } from 'node:child_process';
import { existsSync, mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const packageDir = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
const defaultRepo = resolve(packageDir, '..', '..', '..');

export interface HostLocation {
  readonly repo: string;
  readonly bin: string;
  readonly installed: boolean;
}

/** Where the host is, or the reason there is none. */
export function locateHost(): { location: HostLocation } | { reason: string } {
  if (process.env.BILT_HOST_BIN) {
    const bin = process.env.BILT_HOST_BIN;
    if (!existsSync(bin)) return { reason: `BILT_HOST_BIN=${bin} does not exist` };
    return { location: { repo: defaultRepo, bin, installed: true } };
  }
  const repo = process.env.BILT_HOST_REPO ?? defaultRepo;
  if (!existsSync(join(repo, 'host', 'build.gradle.kts'))) {
    return {
      reason: `no host/ module at ${repo} (set BILT_HOST_REPO to a checkout of feature/session-host)`,
    };
  }
  const bin = join(repo, 'host', 'build', 'install', 'host', 'bin', 'host');
  return { location: { repo, bin, installed: existsSync(bin) } };
}

/** Runs `./gradlew :host:installDist` when the distribution is missing or a rebuild was asked for. */
export function buildHost(location: HostLocation): void {
  if (location.installed && !process.env.BILT_HOST_REBUILD) return;
  const gradlew = join(location.repo, process.platform === 'win32' ? 'gradlew.bat' : 'gradlew');
  execFileSync(gradlew, [':host:installDist', '-q', '--console=plain'], {
    cwd: location.repo,
    stdio: 'inherit',
    timeout: 15 * 60 * 1000,
  });
}

export interface TerminalSpec {
  readonly poiId: string;
  readonly host: string;
  readonly port: number;
  readonly model?: string;
}

export interface RunningHost {
  readonly port: number;
  readonly baseUrl: string;
  readonly log: () => string;
  stop(): Promise<void>;
}

/** Starts the host on an ephemeral port with the given plaintext terminals. */
export async function startHost(
  location: HostLocation,
  terminals: readonly TerminalSpec[] = [],
): Promise<RunningHost> {
  const dir = mkdtempSync(join(tmpdir(), 'bilt-host-'));
  const config = join(dir, 'host.json');
  writeFileSync(
    config,
    JSON.stringify({
      port: 0,
      terminals: terminals.map((t) => ({
        poiId: t.poiId,
        host: t.host,
        port: t.port,
        tls: false,
        encryption: false,
        ...(t.model ? { model: t.model } : {}),
      })),
    }),
  );
  const child: ChildProcess = spawn(location.bin, [config], {
    stdio: ['ignore', 'pipe', 'pipe'],
    env: { ...process.env, JAVA_OPTS: process.env.JAVA_OPTS ?? '-Xmx512m' },
  });
  let output = '';
  const port = await new Promise<number>((resolvePort, reject) => {
    const timer = setTimeout(
      () => reject(new Error(`the host did not start in time:\n${output}`)),
      60_000,
    );
    const onData = (chunk: Buffer) => {
      output += chunk.toString('utf8');
      const match = /listening on http:\/\/[^:]+:(\d+)/.exec(output);
      if (match) {
        clearTimeout(timer);
        resolvePort(Number(match[1]));
      }
    };
    child.stdout?.on('data', onData);
    child.stderr?.on('data', (chunk: Buffer) => {
      output += chunk.toString('utf8');
    });
    child.once('exit', (code) => {
      clearTimeout(timer);
      reject(new Error(`the host exited with ${code} before listening:\n${output}`));
    });
  });
  return {
    port,
    baseUrl: `http://127.0.0.1:${port}`,
    log: () => output,
    stop: () =>
      new Promise<void>((done) => {
        if (child.exitCode !== null) return done();
        child.once('exit', () => done());
        child.kill('SIGTERM');
        setTimeout(() => child.kill('SIGKILL'), 10_000).unref();
      }),
  };
}

/** The suites' shared decision: a located host, or the reason to skip. */
export const hostProbe = locateHost();
export const hostAvailable = 'location' in hostProbe;
export function skipReason(): string {
  return 'reason' in hostProbe ? hostProbe.reason : '';
}
