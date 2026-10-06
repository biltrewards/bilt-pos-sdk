import type {
  AbandonedSettlementRecord,
  ReversedMovement,
  SessionError as SessionErrorData,
  SessionErrorCode,
} from '@bilt/pos-protocol';

/**
 * Why a session operation failed, as an `Error` the register can `catch` or `instanceof`.
 * Mirrors the Java `SessionError`: a `code` from `SessionErrorCode`, a message, the raw Nexo
 * error condition when the failure came from the terminal, and the movements a stopped void had
 * already reversed. A settlement the register abandoned also carries its manual-takeover record,
 * as `SessionException.getAbandonedSettlement()` does in Java.
 *
 * Every rejected `Operation` rejects with one of these; the engine-level failures below are the
 * only other errors the SDK throws.
 */
export class SessionError extends Error {
  override readonly name = 'SessionError';
  readonly code: SessionErrorCode;
  readonly nexoErrorCondition: string | undefined;
  readonly reversedMovements: readonly ReversedMovement[];
  readonly details: Readonly<Record<string, unknown>> | undefined;
  readonly abandonedSettlement: AbandonedSettlementRecord | undefined;

  constructor(
    data: SessionErrorData,
    options: { abandonedSettlement?: AbandonedSettlementRecord; cause?: unknown } = {},
  ) {
    super(data.message, options.cause === undefined ? undefined : { cause: options.cause });
    this.code = data.code;
    this.nexoErrorCondition = data.nexoErrorCondition;
    this.reversedMovements = data.reversedMovements ?? [];
    this.details = data.details;
    this.abandonedSettlement = options.abandonedSettlement;
  }

  /** The wire form, for logging or for handing the error to a backend. */
  toJSON(): SessionErrorData {
    const json: SessionErrorData = { code: this.code, message: this.message };
    if (this.nexoErrorCondition !== undefined) {
      json.nexoErrorCondition = this.nexoErrorCondition;
    }
    if (this.reversedMovements.length > 0) {
      json.reversedMovements = [...this.reversedMovements];
    }
    if (this.details !== undefined) {
      json.details = { ...this.details };
    }
    return json;
  }
}

/**
 * The engine behind `BiltPos` could not be used at all; the base of the failures
 * `BiltPos.connect` rejects with. Bridge-specific subclasses (bridge missing, origin not
 * paired) live in `@bilt/pos-sdk/bridge` and extend these, so a register that only needs to know
 * "show the install prompt" catches the base.
 */
export class EngineError extends Error {
  override readonly name: string = 'EngineError';
}

/** No engine answered: nothing is listening on loopback, the cloud service is unreachable, and so on. */
export class EngineUnavailableError extends EngineError {
  override readonly name: string = 'EngineUnavailableError';
}

/**
 * The engine answered but does not speak the protocol version this SDK was generated from. The
 * register shows an update prompt; `required` is the version the SDK needs and `available` what
 * the host listed in its health response.
 */
export class EngineOutdatedError extends EngineError {
  override readonly name: string = 'EngineOutdatedError';
  readonly required: string;
  readonly available: readonly string[];

  constructor(required: string, available: readonly string[], message?: string) {
    super(
      message ??
        `the host speaks protocol version(s) ${available.join(', ') || 'none'}; this SDK needs ${required}`,
    );
    this.required = required;
    this.available = available;
  }
}
