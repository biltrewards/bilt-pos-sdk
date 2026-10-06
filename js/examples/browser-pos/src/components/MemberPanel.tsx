import { useOperation, useMember } from '@bilt/pos-react';
import type {
  IdentifyResult,
  Operation,
  ShopperSession,
  TerminalShopperSession,
} from '@bilt/pos-sdk';
import { useState, type ReactNode } from 'react';
import { describeError } from './Toasts';

export interface MemberPanelProps {
  readonly session: ShopperSession | null;

  /** The same session when it has a terminal, for the on-terminal prompt; `null` otherwise. */
  readonly terminalSession: TerminalShopperSession | null;
  readonly onError: (error: unknown) => void;
}

/**
 * Who the shopper is. A phone number typed by the cashier attaches a pending member that the
 * host resolves in the background; on a terminal session the shopper can also identify on the
 * device. Either way the panel follows `member.changed`.
 */
export function MemberPanel({ session, terminalSession, onError }: MemberPanelProps): ReactNode {
  const { member, set, clear } = useMember(session);
  const [phone, setPhone] = useState('');
  const [identifyOp, setIdentifyOp] = useState<Operation<IdentifyResult> | null>(null);
  const identify = useOperation(identifyOp);

  const signIn = () => {
    const value = phone.trim();
    if (!value) return;
    set({ resolver: { type: 'PHONE', value, keyedByCashier: true } }).catch(onError);
  };

  return (
    <section className="panel">
      <h2>Member</h2>
      {member === null ? (
        <p className="muted">Guest checkout.</p>
      ) : member.resolved ? (
        <dl className="facts">
          <dt>Member</dt>
          <dd>{member.memberId}</dd>
          {member.loyaltyBrand ? (
            <>
              <dt>Program</dt>
              <dd>{member.loyaltyBrand}</dd>
            </>
          ) : null}
          <dt>Points</dt>
          <dd>{member.pointBalance}</dd>
          <dt>Rewards</dt>
          <dd>{member.rewards.length}</dd>
        </dl>
      ) : (
        <p>
          Looking up {member.resolver?.type.toLowerCase()} {member.resolver?.value}…
        </p>
      )}
      <form
        className="inline"
        onSubmit={(event) => {
          event.preventDefault();
          signIn();
        }}
      >
        <input
          type="tel"
          placeholder="+1 201 555 0123"
          aria-label="Phone number"
          value={phone}
          onChange={(event) => setPhone(event.target.value)}
          disabled={!session}
        />
        <button type="submit" disabled={!session || phone.trim().length === 0}>
          Sign in by phone
        </button>
      </form>
      <div className="actions">
        {terminalSession ? (
          <button
            type="button"
            className="secondary"
            disabled={identify.pending}
            onClick={() => setIdentifyOp(terminalSession.identifyMember())}
          >
            {identify.pending ? 'Waiting for the terminal…' : 'Identify on terminal'}
          </button>
        ) : null}
        <button
          type="button"
          className="secondary"
          disabled={!session || member === null}
          onClick={() => clear().catch(onError)}
        >
          Sign out
        </button>
      </div>
      {identify.status === 'succeeded' && identify.result ? (
        <p className="small">Terminal says: {identify.result.status}</p>
      ) : null}
      {identify.error ? <p className="small error">{describeError(identify.error)}</p> : null}
    </section>
  );
}
