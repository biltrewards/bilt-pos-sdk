---
---

# Session Host

The Session Host is a Java library (`:host`, Java 17) that serves the SDK's
`ShopperSession` and `TerminalShopperSession` over HTTP, Server-Sent Events and
WebSocket, so a browser or any non-JVM register can drive a checkout. It is the
engine inside the Terminal Bridge and, later, the Cloud Session Service; it has
no tray, packaging, pairing or authentication of its own. Design:
[Terminal Bridge, Session Protocol & JavaScript SDK](https://app.notion.com/p/3f0e1baadc2881088783ff86972c57c4).

The wire contract is the Session Protocol in `schema/session-protocol/openapi.yaml`.
The short version:

- a session is a resource under `/v1/sessions/{id}` with a basket, a member and a
  context;
- every lazy SDK operation is `POST .../operations` returning an operation
  resource that moves through `queued`, `running`, `awaitingReply` and a terminal
  status; ending the session is itself an `end` operation (`DELETE` answers 202);
- register callbacks become **steps**: `operation.step` events with a deadline
  and a default, answered with `POST .../operations/{id}/reply`; a client declares
  the step kinds it will answer in the request's `handledSteps`, and the rest take
  the SDK default at once;
- `GET .../events` is the ordered, replayable event stream (SSE, or WebSocket on
  the same path), reconnectable with `?since=<seq>`;
- every state-changing request under `/v1/sessions` carries an `Idempotency-Key`;
  a replayed key returns the stored response, and a duplicate that arrives while the
  first request is still running is refused with 409 so it can be retried.

## Embedding

```java
SessionHost host = SessionHost.builder()
    .bindAddress("127.0.0.1")
    .port(48333)
    .terminalClients(myTerminals)            // TerminalClientProvider
    .allowedOrigins(List.of("https://pos.example.com")) // CORS; "*" for a dev host
    .auth(HostAuth.permitAll())              // default; the bridge plugs pairing in here
    .stepDeadlines(StepDeadlines.defaults()) // 30 s totals, 120 s recovery decisions
    .build();
host.start();
int open = host.activeSessions();            // for a tray icon
// ...
host.stop();
```

`TerminalClientProvider` is the only thing the host needs from you: given a
`poiId` it returns the SDK `TerminalClient` that reaches that device, and it
lists the terminals to advertise. Where the address, CA and passphrase come from
is the embedding application's business — the page never sees them.
`SessionFactory` lets you hand the host pre-configured SDK builders (Bilt
platform credentials, display renderer); `adDecisionService(..)` enables
`retail-media` widgets. `GET /health` answers with the spec's `Health` (`host`,
`hostVersion`, `sdkVersion`, `protocolVersions`, `terminals`) plus a `sessions`
count, the same number `activeSessions()` returns, for a tray or dashboard that
only has HTTP.

## Conformance

`host/src/test/java/com/bilt/pos/host/SpecConformanceTest.java` is the guard
against drift between the host and `schema/session-protocol`. It reads the
multi-file OpenAPI document as it is (no bundling step) and, for every request
example under `schema/session-protocol/examples/`, sends it to a running host
and validates the response against the schema the spec gives that route and
status; every event the host emits during the local- and terminal-session flows
is validated against the `Event` schema. The same validator sits inside the
tests' `HostClient`, so every other host test checks each response and event
too. A failure prints the JSON path, the offending value and the schema path
that rejected it. Run it with `./gradlew :host:test`.

## Development config

`DevMain` starts a host from a JSON file so the protocol can be driven before the
bridge exists:

```json
{
  "port": 48333,
  "terminals": [
    {
      "poiId": "VictaLane-275839164",
      "host": "192.168.1.40",
      "port": 8443,
      "encryption": false,
      "trustAll": true,
      "model": "VictaLane"
    }
  ]
}
```

```sh
./gradlew :host:run --args="$PWD/dev-host.json"
```

Per terminal: `tls` (default true), `encryption` (default true; needs
`passphrase` and `keyIdentifier`), `trustAll` or `caFile`. Without a file the
host starts with no terminals and still serves `local` sessions. The dev host
answers CORS for every origin unless the file narrows it with
`"allowedOrigins": ["https://pos.example.com"]`.

## A local session with curl

```sh
H='Content-Type: application/json'
B=http://127.0.0.1:48333

curl -s $B/health

SID=$(curl -s -H "$H" -H 'Idempotency-Key: c1' -d '{"kind":"local","saleId":"LANE-1","currency":"USD"}' \
  $B/v1/sessions | jq -r .id)

# watch events in another shell
curl -N $B/v1/sessions/$SID/events

curl -s -H "$H" -H 'Idempotency-Key: i1' \
  -d '{"sku":"SKU-4471","description":"Toothpaste","quantity":1,"unitPrice":"4.99"}' \
  $B/v1/sessions/$SID/basket/items

curl -s -X PUT -H "$H" -H 'Idempotency-Key: m1' \
  -d '{"resolver":{"type":"PHONE","value":"+12015550123"}}' $B/v1/sessions/$SID/member

curl -s -X PATCH -H "$H" -H 'Idempotency-Key: x1' -d '{"phase":"TENDERING"}' $B/v1/sessions/$SID/context

curl -s -X DELETE -H 'Idempotency-Key: e1' $B/v1/sessions/$SID   # 202, the end operation
```

A `terminal` session adds `"poiId"` to the creation body and unlocks
`POST .../operations` with `{"type":"settle","handledSteps":["TOTAL_REQUIRED"]}`,
`identifyMember`, `requestConfirmation`, `refund`, `voidTransaction` and the
rest. A settlement that redeems rebates then publishes an `operation.step` of
kind `TOTAL_REQUIRED`; answer it with `{"stepId":"...","total":"89.50"}` or let
the deadline apply the suggested total. Leave `handledSteps` out to take every
default without being asked.

## What this iteration leaves out

- Authentication and pairing: `HostAuth.permitAll()` is the only policy;
  `Authorization` is accepted and ignored.
- Rendering: `retail-media` widgets need an `AdDecisionService` from the
  embedding application; `DevMain` wires the in-memory one with no creatives.
- `TerminalInfo.reachable` is whatever the provider reports; the host does not
  probe terminals on its own.
