import type { Health } from '@bilt/pos-protocol';
import { BiltPosImpl } from './core/pos';
import type { Engine } from './internal';
import type { ShopperSession, ShopperSessionOptions, TerminalSessionOptions } from './session';
import type { Terminal, TerminalInfo } from './terminal';
import type { TerminalShopperSession } from './terminal-session';

/**
 * What differs between engines, reported as data rather than as different method signatures.
 * The public API is the same over the Terminal Bridge, the Cloud Session Service and a future
 * in-browser engine; a register that cares whether a settlement outlives a page reload asks
 * here.
 */
export interface EngineCapabilities {
  /** A short name for diagnostics, e.g. `"bridge"`. */
  readonly name: string;

  /** Sessions and in-flight operations survive the page being reloaded or the tab closed. */
  readonly survivesPageReload: boolean;

  /** Checkouts keep working through an internet outage (the terminal is reached over the LAN). */
  readonly worksOffline: boolean;

  /** The engine runs widgets and delivers `widget.*` events. */
  readonly supportsWidgets: boolean;

  /** `startTerminalSession` is available. */
  readonly supportsTerminalSessions: boolean;

  /** `startShopperSession` (no terminal) is available. */
  readonly supportsLocalSessions: boolean;
}

/**
 * What the SDK tells an engine factory about itself when connecting, so the engine can check
 * protocol compatibility and identify the client to the host.
 */
export interface EngineContext {
  readonly sdkVersion: string;
  readonly protocolVersion: string;
}

/**
 * Produces the engine `BiltPos.connect` runs on: `localBridge()` and `cloud(..)` are the two
 * factories the SDK will ship, each in its own entry point. The `Engine` interface itself is in
 * `@bilt/pos-sdk/internal`.
 */
export type EngineFactory = (context: EngineContext) => Engine | Promise<Engine>;

/**
 * The entry point: a connection to one host, from which sessions are started and terminals
 * reached.
 *
 * ```ts
 * const pos = await BiltPos.connect(localBridge());
 * await using session = await pos.startTerminalSession({
 *   saleId: 'LANE-3', poiId: 'VictaLane-275839164', currency: 'USD', storeLocation: 'STR-0142',
 *   widgets: [{ type: 'retail-media', placements: ['lane-banner'] }],
 * });
 * ```
 */
export interface BiltPos {
  readonly capabilities: EngineCapabilities;

  /** The host's health, as last fetched: versions and the terminals it knows. */
  health(): Promise<Health>;

  /**
   * Starts a session with no terminal: the basket model, member state, context and widgets,
   * for a lane without a terminal or a register that only wants the session's bookkeeping.
   */
  startShopperSession(options: ShopperSessionOptions): Promise<ShopperSession>;

  /**
   * Starts a session bracketed on a terminal. Resolves once the terminal acknowledged the
   * start; a refused start rejects with a `SessionError` and creates no session.
   */
  startTerminalSession(options: TerminalSessionOptions): Promise<TerminalShopperSession>;

  /** The terminals the host can reach. */
  terminals(): Promise<readonly TerminalInfo[]>;

  /** Session-less device operations on one terminal. */
  terminal(poiId: string): Terminal;

  /**
   * Releases the connection. Open sessions are left to the host, which keeps them alive for a
   * client that reconnects on an engine that `survivesPageReload`; call `end()` on each session
   * first to close them for good.
   */
  close(): Promise<void>;

  [Symbol.asyncDispose](): Promise<void>;
}

/**
 * The static side of `BiltPos`. `connect` runs the factory, checks the host's protocol version
 * against the SDK's and rejects with an `EngineUnavailableError` or `EngineOutdatedError` (or a
 * bridge-specific subclass) before any session exists. The SDK core that provides this value is
 * the follow-up to this interface.
 */
export interface BiltPosStatic {
  connect(engine: EngineFactory): Promise<BiltPos>;
}

/**
 * The SDK's `BiltPos`: `BiltPos.connect(localBridge())` runs the core over the engine the
 * factory builds. Shares its name with the interface, as a class would.
 */
export const BiltPos: BiltPosStatic = {
  connect: (engine) => BiltPosImpl.connect(engine),
};
