import type { Member, MemberInput, SessionEventType } from '@bilt/pos-protocol';
import type { ShopperSession } from '@bilt/pos-sdk';
import { useCallback, useMemo } from 'react';
import { useSessionValue } from './internal/use-session-value';

/** What `useMember` returns: the member as last announced and the ways to change it. */
export interface UseMemberResult {
  /** The member as of the last `member.changed`: resolved, pending, or `null` for a guest. */
  readonly member: Member | null;

  /** Attaches a member, as `session.member.set` does. */
  set(member: MemberInput): Promise<Member>;

  /** Signs the member out, as `session.member.clear` does. */
  clear(): Promise<void>;

  /** Re-reads the member from the engine. */
  refresh(): Promise<Member | null>;
}

const EVENTS: readonly SessionEventType[] = ['member.changed'];

function noSession(what: string): Promise<never> {
  return Promise.reject(new Error(`member.${what}() called without an open session`));
}

/**
 * The member attached to the visit as React state: re-renders on `member.changed`, whether the
 * change came from the register, a terminal prompt or a completed lookup.
 *
 * ```tsx
 * const { member, set } = useMember(session);
 * <button onClick={() => set({ resolver: { type: 'PHONE', value: phone } })}>Sign in</button>
 * ```
 */
export function useMember(session: ShopperSession | null | undefined): UseMemberResult {
  const member = useSessionValue<Member | null>(session, EVENTS, (s) => s.member.current, null);
  const set = useCallback(
    (input: MemberInput) => (session ? session.member.set(input) : noSession('set')),
    [session],
  );
  const clear = useCallback(
    () => (session ? session.member.clear() : noSession('clear')),
    [session],
  );
  const refresh = useCallback(() => (session ? session.member.get() : noSession('get')), [session]);
  return useMemo(() => ({ member, set, clear, refresh }), [member, set, clear, refresh]);
}
