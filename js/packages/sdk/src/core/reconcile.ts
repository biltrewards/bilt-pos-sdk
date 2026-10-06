/** How many times a mirror is re-read while events keep landing during the read. */
const RECONCILE_ATTEMPTS = 3;

/**
 * Reads the host's current state for a mirror, repeating while events are applied during the
 * read, since such an event may be newer than the snapshot. Resolves `undefined` when the state
 * never held still or a read failed; the mirror then keeps what the events gave it.
 */
export async function readStable<T>(
  read: () => Promise<T>,
  version: () => number,
): Promise<{ readonly value: T } | undefined> {
  for (let attempt = 0; attempt < RECONCILE_ATTEMPTS; attempt++) {
    const seen = version();
    try {
      const value = await read();
      if (version() === seen) return { value };
    } catch {
      return undefined;
    }
  }
  return undefined;
}
