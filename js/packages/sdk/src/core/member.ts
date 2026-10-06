import type { Member, MemberInput } from '@bilt/pos-protocol';
import { SessionError } from '../errors';
import type { Engine } from '../internal';
import type { SessionMember } from '../session';
import { newIdempotencyKey } from './ids';

/** The member facade: `current` follows `member.changed`; writes go to the engine. */
export class SessionMemberImpl implements SessionMember {
  current: Member | null;

  constructor(
    private readonly engine: Engine,
    private readonly sessionId: string,
    initial: Member | null,
  ) {
    this.current = initial;
  }

  async get(): Promise<Member | null> {
    const member = await this.engine.member(this.sessionId, { kind: 'get' });
    this.current = member;
    return member;
  }

  async set(member: MemberInput): Promise<Member> {
    const attached = await this.engine.member(
      this.sessionId,
      { kind: 'set', member },
      { idempotencyKey: newIdempotencyKey() },
    );
    if (attached === null) {
      throw new SessionError({ code: 'UNKNOWN', message: 'the engine attached no member' });
    }
    this.current = attached;
    return attached;
  }

  async clear(): Promise<void> {
    await this.engine.member(
      this.sessionId,
      { kind: 'clear' },
      { idempotencyKey: newIdempotencyKey() },
    );
    this.current = null;
  }
}
