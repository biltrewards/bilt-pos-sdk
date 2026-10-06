import type { CheckoutPhase } from '@bilt/pos-sdk';
import type { ReactNode } from 'react';
import { useLane } from '../lane/LaneProvider';
import { describeError } from '../log';

const PHASES: readonly CheckoutPhase[] = ['SCANNING', 'MEMBER_IDENTIFIED', 'TENDERING', 'COMPLETE'];

/**
 * The strip above every pane: where the connection and the session stand, the phase (settable
 * by hand on a local session), and the session's end and restart controls.
 */
export function LaneBar(): ReactNode {
  const { connection, lane, session, settings, context, run, report, settlement } = useLane();

  if (connection.status === 'connecting') {
    return <p className="notice">Connecting to the Terminal Bridge…</p>;
  }
  if (connection.status === 'error' || !connection.pos) {
    return (
      <div className="notice error">
        <p>Could not connect: {connection.error ? describeError(connection.error) : 'closed'}</p>
        <button type="button" onClick={connection.reconnect}>
          Try again
        </button>
      </div>
    );
  }
  if (lane.status === 'idle' || lane.status === 'starting') {
    return (
      <p className="notice">
        Starting{' '}
        {settings.mode === 'terminal' ? `a session on ${settings.poiId}` : 'a local session'}…
      </p>
    );
  }
  // A refused `end()` also lands in `error` but leaves the session open (a settlement may still be
  // moving money), so only a lane without a session is a start failure.
  if (lane.status === 'error' && !session) {
    return (
      <div className="notice error">
        <p>
          The session could not be started: {lane.error ? describeError(lane.error) : 'unknown'}
        </p>
        <button type="button" onClick={lane.restart}>
          Try again
        </button>
      </div>
    );
  }
  if (!session || lane.status === 'ended') {
    return (
      <div className="notice">
        <p>The session has ended.</p>
        <button type="button" onClick={lane.restart}>
          Start the next session
        </button>
      </div>
    );
  }

  const c = connection.pos.capabilities;
  const busy = settlement.status === 'running' || settlement.status === 'awaitingReply';

  return (
    <div className="lane-bar">
      <span>
        {session.kind === 'terminal' ? `Terminal ${settings.poiId}` : 'Local session'} · lane{' '}
        {session.saleId} · session <code data-testid="session-id">{session.id}</code> · engine{' '}
        {c.name}
        {c.survivesPageReload ? ' (survives reload)' : ''}
      </span>
      <span className="lane-phase">
        Phase: <strong>{context.phase ?? '—'}</strong>
        {session.kind === 'local'
          ? PHASES.map((candidate) => (
              <button
                key={candidate}
                type="button"
                className="link"
                disabled={candidate === context.phase}
                onClick={() => run(context.setPhase(candidate))}
              >
                {candidate}
              </button>
            ))
          : null}
      </span>
      <span className="lane-actions">
        {lane.status === 'error' && lane.error ? (
          <span className="error small">{describeError(lane.error)}</span>
        ) : null}
        <button
          type="button"
          className="secondary"
          onClick={() => lane.end().catch(report)}
          disabled={busy || (lane.status !== 'open' && lane.status !== 'error')}
          title={busy ? 'Refused while money is moving' : undefined}
        >
          End session
        </button>
      </span>
    </div>
  );
}
