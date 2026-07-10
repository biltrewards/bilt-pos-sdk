# Network error recovery — server implementation guide

Audience: engineers building the **terminal service** (the server the SDK talks
to). This is the "how to build it" companion to the contract in
[NETWORK_ERROR_RECOVERY.md](NETWORK_ERROR_RECOVERY.md). Read that first for the
model and the normative requirements; this document gives the data model, state
machine, concurrency rules, pseudocode, and edge cases needed to implement it.

The server wraps the existing application (Nexo) handler with a thin layer keyed
on the `X-Bilt-Request-Id` header. The layer is protocol-agnostic: it treats the
request and response bodies as opaque.

---

## Data model

Maintain an in-memory registry keyed by `(clientIdentity, requestId)`:

```
Registry: Map<Key, Entry>

Key   = (clientIdentity, requestId)          // clientIdentity from mTLS; see Security
Entry = {
    state:        NEW | PROCESSING | COMPLETE
    waiter:       the connection/response-writer currently bound to this id, or none
    response:     buffered response bytes (status + headers + body), once COMPLETE
    completedAt:  timestamp, set on transition to COMPLETE
    lock:         per-entry mutex
}
```

- `clientIdentity` is derived from the authenticated mTLS peer certificate, not
  from anything the client sends in the body or a spoofable header.
- The `response` buffer must capture **everything needed to reproduce the response
  verbatim** — status line, headers, and full body — because it may be replayed to
  a later connection.

---

## State machine

```
        register (first arrival)
  ──────────────────────────────────▶ PROCESSING
                                          │
                                          │ handler completes
                                          ▼
                                       COMPLETE ──────── retention TTL ───────▶ (discarded)
```

- `NEW` is transient — an id is registered directly into `PROCESSING` on intake.
- `PROCESSING → COMPLETE` happens once, when the wrapped handler returns.
- `COMPLETE → discarded` happens when the retention timer expires.
- There is no path back to `PROCESSING`; the operation executes exactly once.

---

## Request handling algorithm

There is a single endpoint (the existing request endpoint). Every request —
original or recovery — is handled identically:

```
handle(request):
    identity = authenticatedIdentity(request)         # from mTLS
    id       = request.header("X-Bilt-Request-Id")
    if id is absent:
        # Recovery not in use for this request; handle normally, no bookkeeping.
        return dispatchToHandler(request)

    key   = (identity, id)
    entry = registry.computeIfAbsent(key, () -> new Entry(state = PROCESSING))

    lock(entry):
        switch entry.state:

          case PROCESSING:
              if entry.isFirstArrival:                 # created by this call
                  entry.isFirstArrival = false
                  bindWaiter(entry, thisConnection)
                  # Register-before-dispatch: the id is already PROCESSING before
                  # the handler runs, so a concurrent recovery cannot double-dispatch.
                  spawn: runHandler(entry, request)    # exactly-once execution
              else:
                  # Recovery arriving while the operation is still in flight.
                  supersede(entry, thisConnection)     # abandon the old waiter
              awaitCompletion(entry, thisConnection)   # block until COMPLETE or connection dies
              return

          case COMPLETE:
              write(thisConnection, entry.response)    # replay buffered response
              return


runHandler(entry, request):
    response = dispatchToHandler(request)              # the real Nexo operation
    lock(entry):
        entry.response   = capture(response)
        entry.state      = COMPLETE
        entry.completedAt = now()
        w = entry.waiter
    if w is present:
        write(w, entry.response)                       # deliver to whoever is waiting now
    scheduleDiscard(key, RETENTION_TTL)
```

Key points:

- **Register-before-dispatch.** The entry is created in `PROCESSING` and the
  handler is only spawned on the first arrival, under the lock. A recovery that
  races in never triggers a second dispatch.
- **`computeIfAbsent` is the dedupe.** No separate "have I seen this?" check — the
  atomic map insertion decides first-arrival vs recovery.
- **Deliver to the current waiter.** Completion writes to whatever connection is
  bound at that moment, and the buffer is retained so any later recovery replays
  it.

---

## Superseding a connection

When a recovery arrives for a `PROCESSING` id, a stale connection may still be
bound (the client abandoned it, but the server may not know it is dead):

```
supersede(entry, newConnection):
    old = entry.waiter
    entry.waiter = newConnection
    if old is present:
        closeQuietly(old)      # do not attempt to write the response to it
```

Rules:

- Always write the eventual response to `entry.waiter` (the most recent
  connection), never to a superseded one.
- Closing the old connection is best-effort; if it is already dead, the close is a
  no-op. Never block on it.

---

## Completion / supersede race

Completion and a late recovery can interleave. The per-entry lock plus the
retained buffer make every ordering safe:

- **Complete, then recovery:** recovery sees `COMPLETE` and replays the buffer.
- **Recovery, then complete:** completion delivers to the new waiter.
- **Simultaneous:** the lock serializes them; whichever runs second observes the
  state the first left. If completion wrote to a waiter that turns out to be dead,
  the client's next recovery replays from the buffer.

Because the server can never be certain a write reached the client (Two Generals),
correctness does **not** depend on detecting delivery — it depends on retaining the
buffer and letting the client drive replay.

---

## Retention and GC

- On `COMPLETE`, start a discard timer of `RETENTION_TTL`.
- `RETENTION_TTL` **must be ≥ the client's operation (read) timeout** (SDK default
  120s), which is the client's recovery budget — so a response is never discarded
  while the client may still re-attempt. Choose a margin (e.g. client timeout +
  30–60s).
- Also bound `PROCESSING` entries: if the handler itself never completes (its own
  operation timeout), transition the entry to a terminal error/discard so it does
  not leak.
- After discard, a request for that id is treated as unknown (below).

---

## Unknown id after the fact

A request whose id is **not** in the registry (never registered, or already
discarded) is handled as:

```
case (no entry, id present):
    return error(410 Gone, "unknown or expired request id")
```

- Recommended status: **`410 Gone`** (the response existed or would have, but is no
  longer available), or `404` if preferred. Pick one and keep it stable.
- Rationale: a completed, delivered operation is never re-attempted (the client
  has its response), so an unknown id means the operation timed out, failed to
  register, or expired. The SDK surfaces this to the caller as **unconfirmed**;
  nothing executed that must be reconciled, and the caller may start a fresh
  operation with a new id.
- Do **not** dispatch the handler for an unknown id on a recovery path — only the
  first arrival of an id ever dispatches. (In practice the first arrival always
  registers, so "unknown id" is only ever an expired/never-registered case.)

---

## Security

- Derive `clientIdentity` from the **mTLS peer certificate**, and key the registry
  on `(clientIdentity, requestId)`.
- If a request presents an id that exists under a **different** identity, treat it
  as unknown for the presenting identity (return `410`/`404`); never cross-serve a
  buffered response. This prevents one client from fetching another's response by
  guessing or replaying an id.
- Request ids are opaque capabilities; log them but never expose one client's id to
  another.

---

## Concurrency checklist

- One lock per entry (not a single global lock) so unrelated ids don't contend.
- `computeIfAbsent` (or equivalent atomic get-or-create) for first-arrival
  detection.
- Handler dispatch happens once, under the lock, on first arrival.
- Waiter rebinding and response delivery are serialized by the entry lock.
- Discard/GC removes the entry atomically and cancels any waiter.

---

## What the client does (for reference)

- Sends every attempt as the **same request** with the same `X-Bilt-Request-Id`.
- Retries on transport `IOException`, with backoff, up to its operation (read)
  timeout.
- Caps each attempt at a shorter per-attempt timeout — about a sixth of the read
  timeout, at least ~10s (≈20s at the default) — so during a long-running operation
  the client re-sends **periodically** (roughly that often), not only when a hard
  failure occurs. Each such re-send is a normal recovery: the
  server must find the known id `PROCESSING`, attach the new connection, and keep
  waiting — it must **not** re-dispatch. This is the common path, not an edge case,
  so make attach-and-continue cheap.
- Returns any received HTTP response (including your `410`) to its caller as-is;
  it does not retry a received response.

So the server only ever needs to reason about: *is this id new, in flight,
complete, or unknown* — and respond accordingly.

---

## Transport note

The client detects silent outages far better on HTTP/2 (keep-alive ping/pong) than
on HTTP/1.1 (where a severed connection is often invisible until the read timeout).
The server side is identical either way — it just receives more timely recovery
attempts when the connection is HTTP/2. Support HTTP/2 (ALPN `h2`) on the terminal
listener to get the full benefit.
