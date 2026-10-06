import type { Member, MemberInput } from '@bilt/pos-protocol';
import { SessionError } from '../errors';
import type { Engine } from '../internal';
import type { SessionMember } from '../session';
import { newIdempotencyKey } from './ids';

/**
 * The member facade: `current` follows `member.changed`; writes go to the engine and install
 * their response, so a register that awaits a write sees it reflected. The protocol carries no
 * member version to order a response against an event, so when a `member.changed` was applied
 * while a request was in flight the response may be the older of the two: the mirror is then
 * reconciled with a fresh read instead of trusting either.
 */
export class SessionMemberImpl implements SessionMember {
  current: Member | null;

  constructor(
    private readonly engine: Engine,
    private readonly sessionId: string,
    initial: Member | null,
    /** Counts the `member.changed` events the session has applied. */
    private readonly version: () => number,
  ) {
    this.current = initial;
  }

  async get(): Promise<Member | null> {
    const seen = this.version();
    const member = await this.engine.member(this.sessionId, { kind: 'get' });
    // A re-read is only worth returning if it is the freshest view, so hand back what the mirror
    // holds once any interleaved event has been reconciled.
    await this.install(member, seen);
    return this.current;
  }

  async set(member: MemberInput): Promise<Member> {
    const seen = this.version();
    const attached = await this.engine.member(
      this.sessionId,
      { kind: 'set', member },
      { idempotencyKey: newIdempotencyKey() },
    );
    if (attached === null) {
      throw new SessionError({ code: 'UNKNOWN', message: 'the engine attached no member' });
    }
    await this.install(attached, seen);
    return attached;
  }

  async clear(): Promise<void> {
    const seen = this.version();
    await this.engine.member(
      this.sessionId,
      { kind: 'clear' },
      { idempotencyKey: newIdempotencyKey() },
    );
    await this.install(null, seen);
  }

  private async install(response: Member | null, seen: number): Promise<void> {
    if (this.version() === seen) {
      this.current = response;
      return;
    }
    try {
      this.current = await this.engine.member(this.sessionId, { kind: 'get' });
    } catch {
      // The event already applied is the best the mirror has; the write itself succeeded.
    }
  }
}
