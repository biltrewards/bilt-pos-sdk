import { useOperation } from '@bilt/pos-react';
import type { IdentifyResult, Operation, Reward } from '@bilt/pos-sdk';
import { useState, type ReactNode } from 'react';
import { useLane } from '../lane/LaneProvider';
import { describeError, track } from '../log';

function RewardLine({ reward }: { reward: Reward }): ReactNode {
  return (
    <li>
      <code>{reward.rewardRef}</code>
      {reward.type ? ` · ${reward.type.toLowerCase()}` : ''}
      {reward.description ? ` · ${reward.description}` : ''}
      {reward.expirationDate
        ? ` · expires ${new Date(reward.expirationDate).toLocaleDateString()}`
        : ''}
    </li>
  );
}

/**
 * Who the shopper is. A phone number typed by the cashier attaches a pending member that the
 * host resolves in the background; on a terminal session the shopper can also identify on the
 * device. Either way the card follows `member.changed`: program, points and the rewards the
 * settlement may redeem.
 */
export function MemberPanel({ locked }: { readonly locked: boolean }): ReactNode {
  const { member, terminalSession, session, report, log } = useLane();
  const [phone, setPhone] = useState('');
  const [memberId, setMemberId] = useState('');
  const [identifyOp, setIdentifyOp] = useState<Operation<IdentifyResult> | null>(null);
  const identify = useOperation(identifyOp);
  const current = member.member;

  const signInByPhone = () => {
    const value = phone.trim();
    if (!value) return;
    member.set({ resolver: { type: 'PHONE', value, keyedByCashier: true } }).catch(report);
  };
  const signInById = () => {
    const value = memberId.trim();
    if (!value) return;
    member.set({ id: value }).catch(report);
  };
  const prompt = () => {
    if (!terminalSession) return;
    setIdentifyOp(
      track(log, 'identifyMember', terminalSession.identifyMember()) as Operation<IdentifyResult>,
    );
  };

  return (
    <section className="panel">
      <h2>Loyalty</h2>
      {current === null ? (
        <p className="muted">Guest checkout.</p>
      ) : current.resolved ? (
        <dl className="facts" data-testid="member-card">
          <dt>Member</dt>
          <dd>{current.memberId}</dd>
          <dt>Program</dt>
          <dd>{current.loyaltyBrand ?? '—'}</dd>
          <dt>Points</dt>
          <dd>{current.pointBalance}</dd>
          <dt>Rewards</dt>
          <dd>
            {current.rewards.length === 0 ? (
              'none'
            ) : (
              <ul className="ledger">
                {current.rewards.map((reward) => (
                  <RewardLine key={reward.rewardRef} reward={reward} />
                ))}
              </ul>
            )}
          </dd>
        </dl>
      ) : (
        <p>
          Looking up {current.resolver?.type.toLowerCase()} {current.resolver?.value}…
        </p>
      )}
      <form
        className="inline"
        onSubmit={(event) => {
          event.preventDefault();
          signInByPhone();
        }}
      >
        <input
          type="tel"
          placeholder="+1 201 555 0123"
          aria-label="Phone number"
          value={phone}
          onChange={(event) => setPhone(event.target.value)}
          disabled={!session || locked}
        />
        <button type="submit" disabled={!session || locked || phone.trim().length === 0}>
          Sign in by phone
        </button>
      </form>
      <form
        className="inline"
        onSubmit={(event) => {
          event.preventDefault();
          signInById();
        }}
      >
        <input
          placeholder="Bilt member id"
          aria-label="Member id"
          value={memberId}
          onChange={(event) => setMemberId(event.target.value)}
          disabled={!session || locked}
        />
        <button
          type="submit"
          className="secondary"
          disabled={!session || locked || memberId.trim().length === 0}
        >
          Attach by id
        </button>
      </form>
      <div className="actions">
        {terminalSession ? (
          <button
            type="button"
            className="secondary"
            disabled={identify.pending || locked}
            onClick={prompt}
          >
            {identify.pending ? 'Waiting for the terminal…' : 'Identify on terminal'}
          </button>
        ) : null}
        {identify.pending ? (
          <button type="button" className="link" onClick={() => identify.abort().catch(report)}>
            Cancel prompt
          </button>
        ) : null}
        <button
          type="button"
          className="secondary"
          disabled={!session || current === null || locked}
          onClick={() => member.clear().catch(report)}
        >
          Sign out
        </button>
      </div>
      {identify.status === 'succeeded' && identify.result ? (
        <p className="small">
          Terminal says: {identify.result.status}
          {identify.result.memberId ? ` · ${identify.result.memberId}` : ''}
        </p>
      ) : null}
      {identify.error ? <p className="small error">{describeError(identify.error)}</p> : null}
    </section>
  );
}
