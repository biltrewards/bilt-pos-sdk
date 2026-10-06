/**
 * Mints the idempotency key for one intended mutation: a UUID v4 from the platform's
 * `crypto.randomUUID`, so the SDK carries no `uuid` dependency. The core mints one key per public
 * call and hands it to the engine, which reuses it on every retry of that request.
 */
export function newIdempotencyKey(): string {
  return crypto.randomUUID();
}
