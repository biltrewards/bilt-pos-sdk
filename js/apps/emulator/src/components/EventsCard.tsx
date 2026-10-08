import { useState, type ReactNode } from 'react';
import { useLines, type LineLog } from '../log';
import { TabRow } from './common';

type LogTab = 'events' | 'detailed' | 'protocol';

const TABS = [
  { id: 'events', label: 'Events' },
  { id: 'detailed', label: 'Detailed' },
  { id: 'protocol', label: 'Protocol' },
] as const;

export interface LogFeeds {
  readonly events: LineLog;
  readonly detailed: LineLog;
  readonly protocol: LineLog;
}

/**
 * The desktop's `EventsCard`: the curated event feed, the detailed log, and in place of the Nexo
 * envelopes (which never reach the browser) the HTTP requests to the bridge and the session
 * events it streams back. Newest first.
 */
export function EventsCard({ feeds }: { feeds: LogFeeds }): ReactNode {
  const [tab, setTab] = useState<LogTab>('events');
  const events = useLines(feeds.events);
  const detailed = useLines(feeds.detailed);
  const protocol = useLines(feeds.protocol);
  const lines = tab === 'events' ? events : tab === 'detailed' ? detailed : protocol;
  return (
    <section className="card events" aria-label="Log">
      <TabRow tabs={TABS} selected={tab} onSelect={setTab} label="Log feeds" />
      <hr />
      <ol className="log scroll grow" data-testid={`log-${tab}`}>
        {[...lines].reverse().map((line, index) => (
          <li key={lines.length - index}>{line}</li>
        ))}
      </ol>
    </section>
  );
}
