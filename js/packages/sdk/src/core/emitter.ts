import type { SessionEventPayload, SessionEventType } from '@bilt/pos-protocol';
import type { SessionEventHandler, Unsubscribe } from '../session';
import type { Reporter } from './report';

type AnyHandler = SessionEventHandler<SessionEventType>;

/**
 * The typed emitter behind `ShopperSession.on/once/off`. Handlers of one event run in
 * registration order; a throwing handler is reported and the rest still run. Subscribing or
 * unsubscribing from inside a handler takes effect from the next event.
 */
export class SessionEmitter {
  private readonly handlers = new Map<SessionEventType, Set<AnyHandler>>();
  /** The handler a `once` wrapper stands for, so `off(type, handler)` finds it. */
  private readonly originals = new WeakMap<AnyHandler, AnyHandler>();

  constructor(private readonly report: Reporter) {}

  on<T extends SessionEventType>(type: T, handler: SessionEventHandler<T>): Unsubscribe {
    let set = this.handlers.get(type);
    if (!set) {
      set = new Set();
      this.handlers.set(type, set);
    }
    set.add(handler as AnyHandler);
    return () => this.off(type, handler);
  }

  once<T extends SessionEventType>(type: T, handler: SessionEventHandler<T>): Unsubscribe {
    const wrapper = ((payload) => {
      this.off(type, wrapper as SessionEventHandler<T>);
      handler(payload);
    }) as SessionEventHandler<T>;
    this.originals.set(wrapper as AnyHandler, handler as AnyHandler);
    return this.on(type, wrapper);
  }

  off<T extends SessionEventType>(type: T, handler: SessionEventHandler<T>): void {
    const set = this.handlers.get(type);
    if (!set) return;
    set.delete(handler as AnyHandler);
    for (const registered of set) {
      if (this.originals.get(registered) === (handler as AnyHandler)) set.delete(registered);
    }
  }

  emit<T extends SessionEventType>(type: T, payload: SessionEventPayload<T>): void {
    const set = this.handlers.get(type);
    if (!set || set.size === 0) return;
    for (const handler of [...set]) {
      try {
        (handler as SessionEventHandler<T>)(payload);
      } catch (error) {
        this.report(`a ${type} handler threw`, error);
      }
    }
  }

  clear(): void {
    this.handlers.clear();
  }
}
