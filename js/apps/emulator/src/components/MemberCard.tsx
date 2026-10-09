import type { ReactNode } from 'react';
import {
  memberHeadline,
  vasLines,
  type MemberIdentity,
  type MemberRewardUi,
} from '../emulator/state';

function rewardLine(reward: MemberRewardUi): string {
  return [
    `${reward.kind} ${reward.rewardRef ?? '(no rewardRef)'}`,
    reward.description || null,
    reward.expiresAtLabel ? `expires ${reward.expiresAtLabel}` : null,
  ]
    .filter(Boolean)
    .join(' — ');
}

/**
 * The terminal's answer to the last loyalty sign-in: who signed in, what they are worth, and the
 * rewards the payment may redeem, or why nobody is attached.
 */
export function MemberCard({ member }: { member: MemberIdentity }): ReactNode {
  return (
    <section className="card member" aria-label="Loyalty sign-in">
      <div className="member-header">
        <h2>Loyalty sign-in</h2>
        <span className={member.kind === 'found' ? 'success' : 'error'} data-testid="member">
          {memberHeadline(member)}
        </span>
      </div>
      {member.kind === 'found' ? (
        <>
          <p className="small">
            {member.pointBalance !== null ? `${member.pointBalance} pts` : 'points not reported'} ·{' '}
            {member.rewards.length} reward(s)
          </p>
          <ul className="rewards small">
            {member.rewards.map((reward, index) => (
              <li key={reward.rewardRef ?? index}>{rewardLine(reward)}</li>
            ))}
          </ul>
          {member.vas ? (
            <>
              <h3>Wallet pass (VAS)</h3>
              <ul className="rewards small">
                {vasLines(member.vas).map((line, index) => (
                  <li key={index}>{line}</li>
                ))}
              </ul>
            </>
          ) : null}
        </>
      ) : null}
      {member.kind === 'failed' && member.detail ? (
        <p className="small error">{member.detail}</p>
      ) : null}
    </section>
  );
}
