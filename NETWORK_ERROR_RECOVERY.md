# Network error recovery — protocol & design spec

Status: design spec (internal). For SDK users, see
[docs/network-error-recovery](docs/network-error-recovery.md). For the terminal
service implementation, see
[NETWORK_ERROR_RECOVERY_SERVER.md](NETWORK_ERROR_RECOVERY_SERVER.md).

This document specifies how the SDK recovers a request's response after the
connection is torn down by a transient network failure, without re-executing the
operation on the terminal.

---

## Problem

A payment request can take many seconds while the cardholder interacts with the
terminal. If the network drops during that window, the in-flight HTTP response is
lost with the connection — HTTP has no way to resume a response on a new
connection. Re-establishing a connection does **not** cause the terminal to
re-deliver the original response; it has no memory of a response owed to a
connection that no longer exists.

The recovery layer sits **above** the application protocol (Nexo): the client
re-attempts the request under a stable correlation identifier, and the server
guarantees the underlying operation runs **at most once** and re-delivers the same
response to whichever connection is currently waiting.

This is a **client + server contract**. This repository ships the client half;
the terminal service must implement the server half (see the server guide) for
recovery to work.

---

## Goals and non-goals

**Goals**

- Survive a transient network outage during an in-flight request and still obtain
  the operation's real response.
- Never execute the underlying operation (e.g. a payment) more than once, no
  matter how many times the client re-attempts.
- Stay agnostic to the request/response payload — the recovery layer treats
  bodies as opaque bytes and works for any request type.

**Non-goals**

- **Resuming the original HTTP response stream.** Impossible over HTTP; once a
  stream/connection dies, its response cannot be re-homed onto a new connection.
  Recovery *retrieves* the outcome under a correlation key; it does not resume a
  stream.
- **Surviving a terminal restart.** Correlation state is in memory; a restart
  loses it and the client then sees the operation as unconfirmed.
- **Business reconciliation of a never-registered operation.** If nothing
  executed, the client is told so and may start a fresh operation; that policy is
  the caller's.

---

## Overview

| Concern | Mechanism |
|---|---|
| Correlate attempts | `X-Bilt-Request-Id` header (one UUID per logical request) |
| At-most-once execution | Server keys execution on the header; a request whose id is already known is a **recovery**, not a new operation |
| Recover after a drop | Client re-sends the **identical** request (same id) on any network error, with backoff, up to the operation (read) timeout |
| Re-deliver the response | Server delivers the completed response to whichever connection currently holds the id, and buffers it for replay |

The design is uniform: **every attempt is the same request carrying the same id.**
There is no separate recovery verb or endpoint. The server distinguishes "start"
from "resume" purely by whether it already knows the id.

---

## The correlation header

```
X-Bilt-Request-Id: <uuid-v4>
```

- The client generates one UUID per logical request and reuses it across the
  original attempt and every re-attempt, until it receives a complete response.
- The id is **independent of Nexo's `ServiceID`**. The recovery layer neither
  reads nor depends on any field in the body.
- The next logical request gets a fresh id.

---

## Client behavior

Recovery is **on by default** (disable it per client if the terminal does not
implement the server contract — see the caution below). While on, the client:

1. Attaches `X-Bilt-Request-Id: <uuid>` to the outgoing request.
2. Sends it.
3. On a **transient network error** (`IOException` — connect failure, reset,
   read/handshake timeout, ping-triggered failure), waits a backoff interval (with
   jitter) and **re-sends the identical request with the same id**. Repeats until
   either:
   - a response is received (success or an HTTP error) → returned to the caller; or
   - the **operation timeout** elapses → the last `IOException` is surfaced.
4. Does **not** retry a **deterministic** failure — a TLS trust/identity failure or
   protocol error is surfaced immediately, since a re-attempt cannot fix it.
5. Does **not** retry on a received HTTP response — a response, even non-2xx, is a
   definitive answer and is returned as-is.

> **Caution — recovery is on by default and assumes terminal support.** Because a
> long-running request is re-sent periodically (see per-attempt cap below), a
> terminal that does *not* implement the correlation contract would treat each
> re-send as a *new* operation and could execute a payment more than once. Disable
> recovery against such terminals.

Supporting behavior:

- The overall recovery budget **is the read timeout**, treated as the timeout for
  the whole operation. Changing the timeout — on the client or via a per-request
  override — changes the budget with it. (A read timeout of 0, "no timeout", means
  recovery retries until it succeeds.)
- Each individual attempt is **capped at a shorter per-attempt timeout**, derived
  from the read timeout as `max(readTimeout / 6, min(10s, readTimeout))` — ≈20s at
  the 120s default — so a single stuck attempt cannot consume the whole budget.
  This matters because a connection that half-opens — TCP connects but the TLS
  handshake is silently black-holed — is otherwise bounded by the *full* read
  timeout (OkHttp uses the read timeout as the handshake socket timeout), which
  would let one dead attempt eat the entire budget and leave no time to retry.
  The per-attempt cap is derived, not configurable; it scales with the read
  timeout (including per-request overrides).
- Enabling recovery also enables a **keep-alive ping** (default 5s) unless the
  caller set a ping interval explicitly, so a dead connection is detected quickly
  and folded into the retry loop rather than waiting out the read timeout. A
  false-positive disconnect is harmless: the re-sent request is deduped by the
  server.
- A short connect timeout is recommended so connect failures are detected quickly.
  OkHttp's `retryOnConnectionFailure` only does per-attempt route fallback (it is
  route-bounded, not time-bounded); the client's loop owns the time budget.
- Because re-sending is always safe (the server dedupes on the id), the client
  needs no "was the request actually delivered?" classification — it retries on
  *any* network error.

---

## Transport: much more effective on HTTP/2

Recovery only ever triggers on a detected network error, so how the transport
surfaces an outage matters:

- On **HTTP/1.1**, a client waiting for the response is simply blocked reading the
  socket; there is no keep-alive mechanism. A **silently severed** connection
  (cable pulled, NAT/firewall state dropped, peer vanished without a RST) is
  typically **not detected until the read timeout** (120s). Recovery still helps
  when the failure *does* surface as an `IOException` — a connection reset, or a
  connect failure on the next attempt — but it cannot react to an outage the
  transport never reports.
- On **HTTP/2**, the keep-alive **PING/PONG** actively probes the connection. A
  silent outage is detected within roughly the ping interval and turned into an
  `IOException`, which triggers recovery immediately instead of after the full
  read timeout. This is why enabling recovery also enables a default ping.

In short: this mechanism works on HTTP/1.1 for the cases the transport reports,
but it is substantially more useful on HTTP/2, where ping/pong makes otherwise
undetectable outages detectable and actionable.

---

## Server contract (summary)

Full implementation detail — data model, state machine, concurrency, pseudocode,
edge cases — is in [NETWORK_ERROR_RECOVERY_SERVER.md](NETWORK_ERROR_RECOVERY_SERVER.md).
The normative requirements:

1. **Register the id on intake, before dispatching** to the application handler
   (closes the pre-registration race).
2. **Treat a known id as a recovery, not a new operation.**
   - Unknown id → first arrival: register, dispatch, hold the connection open.
   - Known id, still processing → supersede the bound connection, do not dispatch
     again, keep waiting.
   - Known id, completed → return the buffered response immediately.
3. **Deliver to the current connection and buffer for replay.** The server must
   not assume the write reached the client (it cannot reliably detect a mid-write
   death — the Two Generals problem); the client is the authority on receipt and
   re-attempts if incomplete.
4. **Unknown id after the fact means "timed out / discarded".** A completed,
   delivered operation would never be re-attempted, so an unknown id can only mean
   it timed out or never registered. Return a definitive not-found/expired error.
5. **Retention ≥ client's operation timeout.** Retain each id's state (and
   completed response bytes) for a bounded window at least as long as the client's
   operation (read) timeout, since that is the client's recovery budget.
6. **Scope the id to the authenticated identity.** Bind each id to the CA-anchored
   mTLS client identity and reject a mismatched id.

---

## Failure scenarios

| Scenario | Server state | Outcome |
|---|---|---|
| Connect fails (request never sent) | no id registered | Re-attempt becomes the first arrival; executes once; normal response |
| Drop after request received, before response | id registered, processing | Re-attempt supersedes the dead connection and waits; response delivered when ready |
| Drop while response in flight | id completed, buffered | Re-attempt replays the buffered response |
| Repeated drops | unchanged | Each re-attempt supersedes the previous; safe any number of times |
| Operation timeout exceeded / retention expired | discarded | Client surfaces the operation as unconfirmed |

---

## Relationship to Nexo `TransactionStatus`

Nexo already offers outcome reconciliation via `ServiceID` + `TransactionStatus`.
Network error recovery is a lower-level, protocol-agnostic layer that recovers the
same in-flight response transparently, so callers usually never need a
`TransactionStatus` poll. `TransactionStatus` remains the right tool for
reconciliation across a terminal restart or after the operation timeout is
exhausted.
