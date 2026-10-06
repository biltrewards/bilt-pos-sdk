import type { components } from './generated/openapi';

/**
 * One event on a session's stream: the envelope `{ seq, at, type, payload }` as a
 * discriminated union over `type`. The members come straight from the spec's `Event` schema;
 * this file only names the pieces a client reaches for.
 */
export type SessionEvent = components['schemas']['Event'];

/** Every event `type` the protocol defines, e.g. `"basket.changed"`. */
export type SessionEventType = SessionEvent['type'];

/** The envelope for one event type. */
export type SessionEventOf<T extends SessionEventType> = Extract<SessionEvent, { type: T }>;

/** The `payload` an event type carries. */
export type SessionEventPayload<T extends SessionEventType> = SessionEventOf<T>['payload'];

/** `type` to `payload`, the shape a typed emitter keys its handlers on. */
export type SessionEventMap = {
  [T in SessionEventType]: SessionEventPayload<T>;
};
