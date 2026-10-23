import { PROTOCOL_VERSION, type Health, type Session } from '@biltorg/pos-protocol';
import { EngineOutdatedError, SessionError } from '../errors';
import type { Engine } from '../internal';
import type { BiltPos as BiltPosInterface, EngineCapabilities, EngineFactory } from '../pos';
import type {
  AttachSessionOptions,
  ShopperSession,
  ShopperSessionOptions,
  TerminalSessionOptions,
} from '../session';
import type { Terminal, TerminalInfo } from '../terminal';
import type { TerminalShopperSession } from '../terminal-session';
import { consoleReporter, type Reporter } from './report';
import { ShopperSessionImpl, toCreateSessionRequest, type SessionRuntime } from './session';
import { TerminalShopperSessionImpl } from './terminal-session';
import { TerminalImpl } from './terminal';
import { SDK_VERSION } from './version';
import { newIdempotencyKey } from './ids';

/**
 * The `BiltPos` the SDK ships: one engine, the sessions started on it, and the terminal it
 * reaches. Construct one with `BiltPos.connect(localBridge())`.
 */
export class BiltPosImpl implements BiltPosInterface {
  private readonly runtime: SessionRuntime;
  private readonly sessions = new Set<ShopperSessionImpl>();

  private constructor(engine: Engine, report: Reporter) {
    this.runtime = { engine, report };
  }

  /**
   * Runs the factory, reads the host's health and checks it speaks this SDK's protocol version.
   * Rejects with an `EngineUnavailableError` (or the factory's own subclass, such as
   * `BridgeMissingError`) when nothing answers and with an `EngineOutdatedError` when the host
   * lists other protocol versions; no session exists in either case.
   */
  static async connect(
    factory: EngineFactory,
    report: Reporter = consoleReporter,
  ): Promise<BiltPosInterface> {
    const engine = await factory({ sdkVersion: SDK_VERSION, protocolVersion: PROTOCOL_VERSION });
    let health: Health;
    try {
      health = await engine.health();
    } catch (error) {
      await engine.close().catch(() => undefined);
      throw error;
    }
    if (!health.protocolVersions.includes(PROTOCOL_VERSION)) {
      await engine.close().catch(() => undefined);
      throw new EngineOutdatedError(PROTOCOL_VERSION, health.protocolVersions);
    }
    return new BiltPosImpl(engine, report);
  }

  get capabilities(): EngineCapabilities {
    return this.runtime.engine.capabilities;
  }

  health(): Promise<Health> {
    return this.runtime.engine.health();
  }

  async startShopperSession(options: ShopperSessionOptions): Promise<ShopperSession> {
    const session = await this.runtime.engine.createSession(
      toCreateSessionRequest('local', options),
      { idempotencyKey: newIdempotencyKey() },
    );
    return this.track(await new ShopperSessionImpl(this.runtime, session, options).initialize());
  }

  async startTerminalSession(options: TerminalSessionOptions): Promise<TerminalShopperSession> {
    const session = await this.runtime.engine.createSession(
      toCreateSessionRequest('terminal', options),
      { idempotencyKey: newIdempotencyKey() },
    );
    return this.track(
      await new TerminalShopperSessionImpl(this.runtime, session, options).initialize(),
    );
  }

  async attachShopperSession(
    sessionId: string,
    options: AttachSessionOptions = {},
  ): Promise<ShopperSession> {
    const session = await this.attachable(sessionId, 'local');
    return this.track(
      await new ShopperSessionImpl(this.runtime, session, options).initialize(true),
    );
  }

  async attachTerminalSession(
    sessionId: string,
    options: AttachSessionOptions = {},
  ): Promise<TerminalShopperSession> {
    const session = await this.attachable(sessionId, 'terminal');
    return this.track(
      await new TerminalShopperSessionImpl(this.runtime, session, options).initialize(true),
    );
  }

  /** Reads the session off the engine; rejects when the id is unknown or the kind does not match. */
  private async attachable(sessionId: string, kind: 'local' | 'terminal'): Promise<Session> {
    const session = await this.runtime.engine.session(sessionId);
    if (session.kind !== kind) {
      throw new SessionError({
        code: 'INVALID_STATE',
        message: `session ${sessionId} is a ${session.kind} session, not a ${kind} session`,
      });
    }
    return session;
  }

  private track<S extends ShopperSessionImpl>(session: S): S {
    this.sessions.add(session);
    void session.ended.then(() => this.sessions.delete(session));
    return session;
  }

  terminalInfo(): Promise<TerminalInfo | null> {
    return this.runtime.engine.terminalInfo();
  }

  terminal(poiId?: string): Terminal {
    return new TerminalImpl(this.runtime.engine, poiId);
  }

  async close(): Promise<void> {
    await Promise.all([...this.sessions].map((session) => session.detach()));
    this.sessions.clear();
    await this.runtime.engine.close();
  }

  [Symbol.asyncDispose](): Promise<void> {
    return this.close();
  }
}
