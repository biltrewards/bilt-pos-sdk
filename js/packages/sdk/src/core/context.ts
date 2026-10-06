import type { CheckoutPhase, SessionContext, SessionContextPatch } from '@bilt/pos-protocol';
import type { Engine } from '../internal';
import type { SessionContextApi } from '../session';
import { newIdempotencyKey } from './ids';
import { readStable } from './reconcile';

/**
 * The context facade: reads are from the mirror, writes are `PATCH .../context`. The protocol
 * carries no context version to order a response against an event, so when a context event was
 * applied while a write was in flight the response may be the older of the two: the mirror is
 * then reconciled with a fresh read instead of trusting either.
 */
export class SessionContextImpl implements SessionContextApi {
  state: SessionContext;

  constructor(
    private readonly engine: Engine,
    private readonly sessionId: string,
    initial: SessionContext,
    /** Counts the context events the session has applied. */
    private readonly version: () => number,
  ) {
    this.state = initial;
  }

  private async patch(patch: SessionContextPatch): Promise<void> {
    const seen = this.version();
    const response = await this.engine.context(this.sessionId, patch, {
      idempotencyKey: newIdempotencyKey(),
    });
    if (this.version() === seen) {
      this.state = response;
      return;
    }
    const fresh = await readStable(() => this.engine.context(this.sessionId), this.version);
    if (fresh) this.state = fresh.value;
  }

  phase(): CheckoutPhase {
    return this.state.phase;
  }

  setPhase(phase: CheckoutPhase): Promise<void> {
    return this.patch({ phase });
  }

  attributes(): Readonly<Record<string, string>> {
    return { ...this.state.attributes };
  }

  setAttribute(key: string, value: string): Promise<void> {
    return this.patch({ attributes: { [key]: value } });
  }

  removeAttribute(key: string): Promise<void> {
    return this.patch({ attributes: { [key]: null } });
  }

  snapshot(): SessionContext {
    return { ...this.state, attributes: { ...this.state.attributes } };
  }
}
