import { describe, expect, it, vi } from 'vitest';
import { SessionEmitter } from '../src/core/emitter';

const payload = { basket: {} } as never;

describe('the session emitter', () => {
  it('removes a once handler when off is given the original function', () => {
    const emitter = new SessionEmitter(() => undefined);
    const handler = vi.fn();
    emitter.once('basket.changed', handler);
    emitter.off('basket.changed', handler);
    emitter.emit('basket.changed', payload);
    expect(handler).not.toHaveBeenCalled();
  });

  it('runs a once handler a single time and leaves other handlers alone', () => {
    const emitter = new SessionEmitter(() => undefined);
    const once = vi.fn();
    const always = vi.fn();
    emitter.once('basket.changed', once);
    emitter.on('basket.changed', always);
    emitter.emit('basket.changed', payload);
    emitter.emit('basket.changed', payload);
    expect(once).toHaveBeenCalledTimes(1);
    expect(always).toHaveBeenCalledTimes(2);
  });

  it('keeps the unsubscribe a once call returns working', () => {
    const emitter = new SessionEmitter(() => undefined);
    const handler = vi.fn();
    const off = emitter.once('basket.changed', handler);
    off();
    emitter.emit('basket.changed', payload);
    expect(handler).not.toHaveBeenCalled();
  });
});
