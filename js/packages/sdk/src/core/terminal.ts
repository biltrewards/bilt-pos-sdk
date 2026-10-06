import type { DiagnosisResult, PrintPayload, ReconciliationResult } from '@bilt/pos-protocol';
import type { Engine } from '../internal';
import type { Terminal } from '../terminal';
import { newIdempotencyKey } from './ids';

/** The session-less terminal facade: each call is one synchronous round trip through the engine. */
export class TerminalImpl implements Terminal {
  constructor(
    private readonly engine: Engine,
    readonly poiId: string,
    private readonly storeLocation?: string,
  ) {}

  diagnose(): Promise<DiagnosisResult> {
    return this.engine.terminal(this.poiId, { kind: 'diagnose' });
  }

  totals(): Promise<ReconciliationResult> {
    return this.engine.terminal(
      this.poiId,
      this.storeLocation === undefined
        ? { kind: 'totals' }
        : { kind: 'totals', storeLocation: this.storeLocation },
    );
  }

  reconcile(): Promise<ReconciliationResult> {
    return this.engine.terminal(
      this.poiId,
      this.storeLocation === undefined
        ? { kind: 'reconcile' }
        : { kind: 'reconcile', storeLocation: this.storeLocation },
      { idempotencyKey: newIdempotencyKey() },
    );
  }

  print(payload: PrintPayload): Promise<void> {
    return this.engine.terminal(
      this.poiId,
      { kind: 'print', payload },
      { idempotencyKey: newIdempotencyKey() },
    );
  }

  playSound(soundReferenceId: string, volumePercent?: number): Promise<void> {
    return this.engine.terminal(
      this.poiId,
      {
        kind: 'sound',
        request:
          volumePercent === undefined
            ? { action: 'PLAY', soundReferenceId }
            : { action: 'PLAY', soundReferenceId, volumePercent },
      },
      { idempotencyKey: newIdempotencyKey() },
    );
  }

  stopSound(): Promise<void> {
    return this.engine.terminal(
      this.poiId,
      { kind: 'sound', request: { action: 'STOP' } },
      { idempotencyKey: newIdempotencyKey() },
    );
  }
}
