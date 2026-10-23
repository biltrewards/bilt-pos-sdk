## Executive summary

**What this does**
- A browser register can now get its checkout session back after the page reloads or the register crashes: it asks the SDK for the session by the id it saved, and picks up where it left off — reading the result of a payment that was in flight, answering a prompt that is still waiting, cancelling an operation, or ending the session.
- The register can also list a session's operations (the commands running on the terminal, newest first, optionally narrowed by status) and read the outcome of a finished one.
- Answering an in-flight operation's prompts works the way the Java SDK does it: the register attaches, lists the session's operations, and registers its callbacks on the operation itself, not on the session.

**Why**
- Sessions live on the terminal bridge and deliberately outlive the page, but the SDK had no way to get a handle on one again — after a reload the register could not see or act on its own session. Until now the documentation told integrators to call the bridge's raw HTTP endpoints themselves.

**What it costs / risks**
- Nothing changes on the terminal bridge or on the wire: this is entirely a JavaScript SDK change, so existing sessions and registers are unaffected.
- `settle()` now takes its options and its callbacks as two arguments instead of one merged object — the same shape `refund()` already used. Call sites in this repo (the React hook, the emulator, the examples) are updated; external callers written against the single-object form need the same split.
- Attaching to a session that already ended still opens, so its final state stays readable; asking for a session that does not exist, or for the wrong kind, fails with a clear error.

**Status / decision**
- Open for review. Deliberately left out: the proposed per-operation service id (undecided, tracked separately), any bridge changes (none were needed), and the recovery documentation (covered by a separate PR).

## Summary

A `BiltPos` could only ever start sessions: after a page reload, a session still running on the bridge was unreachable through the SDK. This adds `attachTerminalSession`/`attachShopperSession`, `session.operations(filter)`, `session.operation(id)`, and `Operation.guide(handlers)` — and splits `settle()` into `settle(options, handlers)` to match `refund()`/`voidTransaction()` and the Java flow model.

Attach reads the session via `engine.session(id)`, rejects on an unknown id or a kind mismatch, and returns the same impl a start returns, `initialize(attach = true)`: mirrors are rebuilt by the normal pump replaying the event buffer from `since` 0 plus the existing `resync`, and operations still in flight (`queued`/`running`/`awaitingReply`) are adopted — each becomes a `PendingOperation` fed by `accept(resource)` and the held events the replay gathered before it was named. Adopted step events the host already resolved are marked answered via `noteStep` so replay does not re-reply them (the host would answer `notPending`), while a live `pendingStep` is held unanswered until `guide(handlers)` registers this page's handlers — the way a Java `SettlementFlow`/`ReversalFlow` takes its handlers on the operation, not the session. `guide` on a followed (listed-duplicate) handle forwards to the owner, whose handlers are the replying ones. A failed attach detaches only — the session may belong to a page still using it, so unlike a failed start it is not ended.

`settle(options, handlers)` splits the old single `SettleOptions` bag — wire options in the first argument, `SettlementHandlers` in the second, matching `refund(amount?, handlers?)`. `AttachTerminalSessionOptions` is gone: attach takes the same `AttachSessionOptions` (just `widgets`) the shopper attach takes. The React hook's `settle` is split the same way, and callers across the emulator and examples moved over.

Two design choices worth a look:

- `session.operation(id)` is **synchronous** (`Operation | undefined` over tracked handles). An `Operation` is itself a promise, so `Promise<Operation>` would await to the operation's *result*, never the handle; there is no way to hand an async lookup a thenable. `operations()` is the fetch path — it adopts every listed op — and `operation(id)` is the dedupe/lookup over what the session already tracks (in-flight plus a `finished` map of settled handles).
- `TerminalShopperSessionImpl`'s private helper was renamed `operation` → `request`; the name now belongs to the public accessor. Mechanical rename, no behaviour change.

Everything goes through the `Engine` interface: `operations(sessionId, filter?)` gained the status filter (`BridgeEngine` maps it to `?status=`); `session(id)`/`operation(id, opId)`/`events(id, since)` already existed. No host or schema changes, no `serviceId`, no recovery-doc edits (those are in #168).

## Verification

`pnpm lint`, `lint:spec`, `generate:check`, `build`, `typecheck`, `test`, `api:check` all green in `js/` locally (254 tests across the workspace). `test/attach.test.ts` (11 tests) covers: attach to a running session with mirrors rebuilt from replay, attach to an ended session (`ended` settled, `end()` a no-op), unknown id (`NOT_FOUND`), kind mismatch (`INVALID_STATE`), `operations()` unfiltered and `{ status: [...] }`, a finished handle resolving with its stored result and rejecting with its stored error, a running handle settling on completion, `abort()` through an attached handle, an adopted settle's pending step held until `guide()` answers it (and a previously answered step not re-answered), and handle identity between `operations()`/`operation()`.
