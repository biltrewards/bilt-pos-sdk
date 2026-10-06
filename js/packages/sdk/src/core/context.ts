import type { CheckoutPhase, SessionContext, SessionContextPatch } from '@bilt/pos-protocol';
import type { Engine } from '../internal';
import type { SessionContextApi } from '../session';
import { newIdempotencyKey } from './ids';

/** The context facade: reads are from the mirror, writes are `PATCH .../context`. */
export class SessionContextImpl implements SessionContextApi {
  state: SessionContext;

  constructor(
    private readonly engine: Engine,
    private readonly sessionId: string,
    initial: SessionContext,
  ) {
    this.state = initial;
  }

  private async patch(patch: SessionContextPatch): Promise<void> {
    this.state = await this.engine.context(this.sessionId, patch, {
      idempotencyKey: newIdempotencyKey(),
    });
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
