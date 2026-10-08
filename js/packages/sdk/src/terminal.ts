import type {
  DiagnosisResult,
  PrintPayload,
  ReconciliationResult,
  TerminalInfo,
} from '@bilt/pos-protocol';

export type { TerminalInfo };

/**
 * The host's terminal's device and admin operations outside any session — the Java `Terminal`
 * facade. A host drives exactly one terminal; `poiId` is only the Nexo `POIID` these requests
 * carry, the host's default when absent.
 * There is no bracket: nothing is sent until an operation is called and the terminal holds no
 * state on this object's behalf. For a connectivity ping before the first checkout, end-of-day
 * reconciliation, or a receipt reprint after the session that took the payment has ended.
 * Operations are synchronous round trips to the terminal and reject with a `SessionError`
 * (`NETWORK` when it cannot be reached).
 */
export interface Terminal {
  readonly poiId: string | undefined;

  /** Terminal health and host reachability. */
  diagnose(): Promise<DiagnosisResult>;

  /** Running totals since the last reconciliation; lighter than `reconcile()`, which closes the period. */
  totals(): Promise<ReconciliationResult>;

  /** End-of-period totals; closes the period. */
  reconcile(): Promise<ReconciliationResult>;

  /** Prints a document on the terminal printer. */
  print(payload: PrintPayload): Promise<void>;

  /** Plays a pre-provisioned sound by its reference; `volumePercent` 0–100, terminal default when omitted. */
  playSound(soundReferenceId: string, volumePercent?: number): Promise<void>;

  /** Stops any sound playing. */
  stopSound(): Promise<void>;
}
