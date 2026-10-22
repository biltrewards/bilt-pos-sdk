## Executive summary

**What this does**
- A browser register can now get its checkout session back after the page reloads or the register crashes: it asks the SDK for the session by the id it saved, and picks up where it left off — reading the result of a payment that was in flight, answering a prompt that is still waiting, cancelling an operation, or ending the session.
- The register can also list a session's operations (the commands running on the terminal, newest first, optionally narrowed by status) and read the outcome of a finished one.

**Why**
- Sessions live on the terminal bridge and deliberately outlive the page, but the SDK had no way to get a handle on one again — after a reload the register could not see or act on its own session. Until now the documentation told integrators to call the bridge's raw HTTP endpoints themselves.

**What it costs / risks**
- Nothing changes on the terminal bridge or on the wire: this is entirely a JavaScript SDK change, so existing sessions and registers are unaffected.
- Attaching to a session that already ended still opens, so its final state stays readable; asking for a session that does not exist, or for the wrong kind, fails with a clear error.

**Status / decision**
- Open for review. Deliberately left out: the proposed per-operation service id (undecided, tracked separately), any bridge changes (none were needed), and the recovery documentation (covered by a separate PR).

## Summary

A `BiltPos` could only ever start sessions: after a page reload, a session still running on the bridge was unreachable through the SDK. This adds `attachTerminalSession`/`attachShopperSession`, `session.operations(filter)`, and `session.operation(id)`.

Attach reads the session via `engine.session(id)`, rejects on an unknown id or a kind mismatch, and returns the same impl a start returns, `initialize(attach = true)`: mirrors are rebuilt by the normal pump replaying the event buffer from `since` 0 plus the existing `resync`, and operations still in flight (`queued`/`running`/`awaitingReply`) are adopted — each becomes a `PendingOperation` fed by `accept(resource)` and the held events the replay gathered before it was named. Adopted step events the host already resolved are marked answered via `noteStep` so replay does not re-reply them (the host would answer `notPending`), while the live `pendingStep` answers through the handlers `AttachTerminalSessionOptions` carries (`settlement`, `reversal`), delivered through the new `stepHandlersFor(type)` hook. A failed attach detaches only — the session may belong to a page still using it, so unlike a failed start it is not ended.

Two design choices worth a look:

- `session.operation(id)` is **synchronous** (`Operation | undefined` over tracked handles). An `Operation` is itself a promise, so `Promise<Operation>` would await to the operation's *result*, never the handle; there is no way to hand an async lookup a thenable. `operations()` is the fetch path — it adopts every listed op — and `operation(id)` is the dedupe/lookup over what the session already tracks (in-flight plus a `finished` map of settled handles).
- `TerminalShopperSessionImpl`'s private helper was renamed `operation` → `request`; the name now belongs to the public accessor. Mechanical rename, no behaviour change.

Everything goes through the `Engine` interface: `operations(sessionId, filter?)` gained the status filter (`BridgeEngine` maps it to `?status=`); `session(id)`/`operation(id, opId)`/`events(id, since)` already existed. No host or schema changes, no `serviceId`, no recovery-doc edits (those are in #168).

## Verification

`pnpm lint`, `lint:spec`, `generate:check`, `build`, `typecheck`, `test`, `api:check` all green in `js/` locally. New `test/attach.test.ts` (11 tests) covers: attach to a running session with mirrors rebuilt from replay, attach to an ended session (`ended` settled, `end()` a no-op), unknown id (`NOT_FOUND`), kind mismatch (`INVALID_STATE`), `operations()` unfiltered and `{ status: [...] }`, a finished handle resolving with its stored result and rejecting with its stored error, a running handle settling on completion, `abort()` through an attached handle, an adopted settle's pending step answered by the attach handlers (and a previously answered step not re-answered), and handle identity between `operations()`/`operation()`.
