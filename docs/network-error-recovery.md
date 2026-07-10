---
---

# Network error recovery

Recover a request's response after a transient network drop, without risking a
duplicate operation.

A payment can take many seconds while the cardholder interacts with the terminal.
If the network drops during that window, the in-flight response is lost with the
connection. With recovery enabled, the SDK transparently re-attempts the request
under a stable correlation id and retrieves the same response once the network
returns — and the terminal guarantees the operation runs **at most once**, so a
re-attempt never charges the card twice.

> Recovery is **on by default** and requires terminal-side support for the
> correlation protocol. Against a terminal that does **not** implement it, a
> re-send is treated as a new operation and a long-running request could execute
> more than once — disable recovery for such terminals (see below).

---

## What it does

- Tags each request with a correlation id (`X-Bilt-Request-Id`) that stays stable
  across re-attempts.
- On a network error while a request is in flight, re-sends the identical request
  (same id), with backoff, until it gets the response or the operation timeout
  elapses.
- Retries only on **transient** transport failures. A received response (even an
  error response) is returned as-is, and a deterministic failure (a TLS trust
  failure, a protocol error) is surfaced immediately rather than retried.

## On by default — how to disable

Recovery is enabled by default. Turn it off for a terminal that does not implement
the correlation protocol:

**Java**

```java
BiltNexoTerminalClient client = BiltNexoTerminalClient.builder()
        .endpoint("https://192.168.1.100:8443/nexo")
        // .readTimeout(Duration.ofSeconds(60))  // also caps the recovery budget
        .disableRecoveryOnNetworkError()          // opt out
        .build();
```

**Kotlin**

```kotlin
val client = BiltNexoTerminalClient(
    endpoint = "https://192.168.1.100:8443/nexo",
    recoverOnNetworkError = false,                // opt out
    // readTimeout = Duration.ofSeconds(60),      // also caps the recovery budget
)
```

**CLI**

```
scripts/terminal-cli.sh 192.168.1.100 --type payment --no-recover-on-network-error
```

## How long it retries

The overall recovery budget is the **read timeout**, treated as the timeout for
the whole operation (default 120 seconds). Changing the read timeout — on the
client or via a per-request timeout override — changes the recovery budget with
it. Each individual attempt is capped at a shorter timeout — derived from the read
timeout (about a sixth of it, but at least ~10 s), so ~20 s at the 120 s default —
so one stuck connection (for example a half-open connection whose TLS handshake is
silently dropped) can't consume the whole budget before recovery gets to retry.

## Keep-alive ping

Enabling recovery also turns on a **keep-alive ping (default 5 seconds)** unless
you set a ping interval yourself. The ping lets the client notice a dead
connection quickly and start recovering, instead of waiting out the 120-second
read timeout. A false-positive disconnect is harmless — the re-sent request is
deduplicated by the terminal.

To override, set your own interval (Java `pingInterval(...)`, Kotlin
`pingInterval = ...`, CLI `--ping-interval <seconds>`).

## HTTP/1.1 vs HTTP/2

Recovery works on both, but is far more effective on **HTTP/2**. On HTTP/1.1 a
silently severed connection (e.g. a pulled cable) is usually invisible until the
read timeout, so recovery can only react once the failure surfaces as an error.
On HTTP/2 the keep-alive ping/pong actively detects the outage within about the
ping interval and triggers recovery immediately.

## Guarantees and limits

- **At most once:** the operation executes a single time regardless of how many
  times the request is re-attempted.
- **Requires terminal support:** inert without it.
- **Not durable across a terminal restart:** if the terminal restarts mid-request,
  the recovery surfaces as an unconfirmed error; use
  [transaction status](./verify-transaction-status.md) to reconcile.
- **Does not resume the original stream:** it retrieves the outcome under the
  correlation id; it never re-homes a dead HTTP response.
