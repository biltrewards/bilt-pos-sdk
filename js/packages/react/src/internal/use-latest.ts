import { useEffect, useRef, type MutableRefObject } from 'react';

/**
 * A ref that always holds the latest value, so an effect subscribed once can call the newest
 * callback without re-subscribing on every render.
 */
export function useLatest<T>(value: T): MutableRefObject<T> {
  const ref = useRef(value);
  useEffect(() => {
    ref.current = value;
  });
  return ref;
}
