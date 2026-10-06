import { useState, type ReactNode } from 'react';
import { useLane } from '../lane/LaneProvider';
import { diagnostics, useLogEntries, type LogKind } from '../log';

const KINDS: readonly LogKind[] = ['event', 'operation', 'error', 'info'];

/**
 * Every session event (the log's own sequence, the event type, a payload summary and the raw
 * payload on demand), the lifecycle of every operation the page started, and every SDK error,
 * newest last. "Copy diagnostics" puts the settings, the engine and the whole log on the
 * clipboard as JSON.
 */
export function LogPane(): ReactNode {
  const { log, settings, connection, session } = useLane();
  const entries = useLogEntries(log);
  const [kinds, setKinds] = useState<ReadonlySet<LogKind>>(new Set(KINDS));
  const [filter, setFilter] = useState('');
  const [copied, setCopied] = useState<string | null>(null);
  const shown = entries.filter(
    (entry) =>
      kinds.has(entry.kind) &&
      (filter.trim() === '' ||
        `${entry.type} ${entry.summary}`.toLowerCase().includes(filter.trim().toLowerCase())),
  );

  const copy = async () => {
    const text = diagnostics(log, {
      settings,
      connection: connection.status,
      engine: connection.pos?.capabilities ?? null,
      session: session ? { id: session.id, kind: session.kind, state: session.state } : null,
      userAgent: typeof navigator === 'undefined' ? undefined : navigator.userAgent,
    });
    try {
      await navigator.clipboard.writeText(text);
      setCopied(`Copied ${entries.length} entries`);
    } catch (error) {
      setCopied(`Clipboard refused: ${error instanceof Error ? error.message : String(error)}`);
    }
    setTimeout(() => setCopied(null), 4000);
  };

  return (
    <section className="panel">
      <div className="log-toolbar">
        <h2>Log</h2>
        {KINDS.map((kind) => (
          <label key={kind} className="check">
            <input
              type="checkbox"
              checked={kinds.has(kind)}
              onChange={(event) => {
                const next = new Set(kinds);
                if (event.target.checked) next.add(kind);
                else next.delete(kind);
                setKinds(next);
              }}
            />
            {kind}
          </label>
        ))}
        <input
          aria-label="Filter log"
          placeholder="Filter by type or text"
          value={filter}
          onChange={(event) => setFilter(event.target.value)}
        />
        <button type="button" className="secondary" onClick={() => void copy()}>
          Copy diagnostics
        </button>
        <button type="button" className="secondary" onClick={() => log.clear()}>
          Clear
        </button>
        {copied ? <span className="small muted">{copied}</span> : null}
        <span className="badge">
          {shown.length} / {entries.length}
        </span>
      </div>
      {shown.length === 0 ? (
        <p className="muted">Nothing logged yet.</p>
      ) : (
        <table className="log-table">
          <thead>
            <tr>
              <th>#</th>
              <th>Time</th>
              <th>Kind</th>
              <th>Type</th>
              <th>Summary</th>
            </tr>
          </thead>
          <tbody>
            {shown.map((entry) => (
              <tr key={entry.seq} className={`kind-${entry.kind}`} data-testid="log-entry">
                <td className="seq">{entry.seq}</td>
                <td className="seq">{entry.at.slice(11, 23)}</td>
                <td>{entry.kind}</td>
                <td>
                  <code>{entry.type}</code>
                </td>
                <td>
                  {entry.summary}
                  {entry.payload !== undefined ? (
                    <details>
                      <summary className="small muted">payload</summary>
                      <pre>{JSON.stringify(entry.payload, null, 2)}</pre>
                    </details>
                  ) : null}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
