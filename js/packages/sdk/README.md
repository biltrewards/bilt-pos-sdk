# @bilt/pos-sdk

The Bilt POS SDK for JavaScript: shopper sessions, settlement, refunds and widgets for a
browser register, mirroring the Java `ShopperSession` / `TerminalShopperSession` API. The
browser never talks to a terminal itself; the SDK speaks the
[Session Protocol](../../../schema/session-protocol) to a host that embeds the Java SDK, the
**Terminal Bridge** on the register machine today and the Cloud Session Service later.

Design: [Terminal Bridge, Session Protocol & JavaScript SDK](https://app.notion.com/p/3f0e1baadc2881088783ff86972c57c4).

| Entry point              | Contents                                                                                                                           |
| ------------------------ | ---------------------------------------------------------------------------------------------------------------------------------- |
| `@bilt/pos-sdk`          | `BiltPos`, `ShopperSession`, `TerminalShopperSession`, `Operation`, `SessionError`; engine-independent, no bridge concepts.        |
| `@bilt/pos-sdk/bridge`   | `localBridge()`, `detectBridge()`, `BridgeMissingError`, `BridgeOutdatedError`: the Terminal Bridge engine and its install states. |
| `@bilt/pos-sdk/internal` | The `Engine` seam an engine implements. Unstable; registers never import it.                                                       |

## Connect, start, settle

```ts
import { BiltPos, SessionError } from '@bilt/pos-sdk';
import { BridgeMissingError, BridgeOutdatedError, localBridge } from '@bilt/pos-sdk/bridge';

let pos;
try {
  pos = await BiltPos.connect(localBridge());
} catch (error) {
  if (error instanceof BridgeMissingError) return showInstallPrompt();
  if (error instanceof BridgeOutdatedError) return showUpdatePrompt(error.available);
  throw error;
}

await using session = await pos.startTerminalSession({
  saleId: 'LANE-3',
  poiId: 'VictaLane-275839164',
  currency: 'USD',
  storeLocation: 'STR-0142',
  widgets: [{ type: 'retail-media', placements: ['lane-banner'] }],
});

session.on('basket.changed', (change) => render(change.current));
session.on('widget.rendering', ({ placement, rendering }) => draw(placement, rendering));
session.on('widget.offer', ({ offer }) => applyOffer(offer));
session.on('background.error', (error) => log.warn(error));

await session.basket.addItem({ sku: 'SKU-4471', description: 'Toothpaste', unitPrice: '4.99' });
await session.member.set({ resolver: { type: 'PHONE', value: '+12015550123' } });

const settlement = session.settle({
  onRebatesRedeemed: (result) => recomputeTax(result.updatedBasket, result.suggestedTotal),
  onError: (failure) => (failure.outcomeCertainty === 'DEFINITIVE' ? 'ABORT' : 'RETRY'),
  onCardCharged: (movement) => journal(movement),
});
cancelButton.onclick = () => settlement.abort();

try {
  const result = await settlement;
  printReceipt(result.customerReceipt);
} catch (error) {
  if (error instanceof SessionError && error.code === 'ABORTED') return;
  throw error;
}
// `await using` ends the session on the way out; call `session.end()` yourself otherwise.
```

### What the runtime does for you

- **Operations are promises with a handle.** Every terminal method returns an `Operation`:
  `await` it for the result, read `status`, or `abort()` it. Operations on one session run in
  order on the host; the promise settles from the host's `operation.completed` event, and if
  the event stream is down the SDK polls the operation until the stream is back.
- **Steps.** Where the Java `SettlementFlow` blocks on a handler, the host raises a step with a
  deadline. The SDK declares the step kinds you passed handlers for, calls your handler with a
  `StepInfo` whose `signal` aborts at the deadline, and sends the reply. A handler that throws
  or answers after the deadline sends nothing and the host's documented default applies (the
  suggested total, `ABORT` for recoveries); a step for which you passed no handler is answered
  with its default at once. Movement handlers (`onMovement`, `onCardCharged`, …) fan out from
  `operation.movement` events.
- **State mirrors.** `session.basket.current`, `session.member.current` and `session.context`
  are kept current from the event stream, so reads are synchronous; writes return promises and
  install the host's answer.
- **Idempotency.** Every mutating call carries a fresh UUID v4 `Idempotency-Key`; a request the
  bridge never received is retried with the same key, so a dropped connection cannot double a
  payment or a basket line.
- **Reconnection.** The bridge engine streams events over a WebSocket, reconnects with the last
  sequence number it processed (exponential backoff, 250 ms to 10 s), drops duplicates and
  falls back to Server-Sent Events where WebSocket does not get through. A pending step is
  replayed on reconnect, so a settlement survives a flaky connection — and, on the bridge, a
  page reload (`pos.capabilities.survivesPageReload`).
- **Failures you must not miss** arrive as `SessionError`s from the operation or call that
  caused them. Failures nobody awaits (a widget token the host rejected, a step handler that
  threw) arrive as `background.error` events; a throwing event handler is reported to the
  console and never breaks the stream.

## Bridge detection states

`detectBridge()` is what an install prompt polls (every two seconds, say); `localBridge()` runs
it once inside `BiltPos.connect`.

| State      | How it is reported                                                                                                 | What the register shows                                                                                 |
| ---------- | ------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------- |
| `ready`    | `detectBridge()` → `{ status: 'ready', baseUrl, health }`; `connect` resolves                                      | Nothing; the lane opens.                                                                                |
| `missing`  | `{ status: 'missing', probed }`; `connect` rejects with `BridgeMissingError` (an `EngineUnavailableError`)         | The install prompt with the download link for the user's OS; keep polling.                              |
| `outdated` | `{ status: 'outdated', baseUrl, health }`; `connect` rejects with `BridgeOutdatedError` (an `EngineOutdatedError`) | The update prompt; `error.available` lists what the bridge speaks, `error.required` what the SDK needs. |

Detection probes `GET /health` on `127.0.0.1:48333` and the ten ports above it, all at once,
with a 400 ms timeout each, so a missing bridge is known in well under a second. Requests are
sent with `targetAddressSpace: "loopback"`, which lets a page served from a public origin reach
loopback under Chrome's local network access rules; Chrome asks the user once per origin, so
explain the prompt before the first `detectBridge()`.

Options: `localBridge({ port, fallbackPorts, healthTimeoutMs, bearerToken, retries, fetch, webSocket, eventSource })`.
`bearerToken` sets the `Authorization` header for the day pairing exists; the bridge of this
iteration needs none. `webSocket` and `eventSource` take constructors for runtimes without the
globals (Node 20, React Native polyfills).

## Engines other than the bridge

The core talks to an `Engine` (`@bilt/pos-sdk/internal`); `localBridge()` is the first
implementation and `cloud(..)` will be the second. Nothing in the public API depends on which
one is behind it: wire details (ports, operation ids, sequence numbers, idempotency keys, HTTP
statuses) never leave the engine, and behavioural differences are data in
`pos.capabilities`.

## Testing

```sh
pnpm test            # unit tests against the in-memory engines, including type tests
pnpm test:contract   # the same runtime against the real Session Host (needs a JDK)
```

The contract suite builds `:host` with `./gradlew :host:installDist` (set `BILT_HOST_REPO` to a
checkout that has the module, `BILT_HOST_REBUILD=1` to force a build, or `BILT_HOST_BIN` to
point at a start script), starts it on an ephemeral port with a scripted plaintext terminal, and
drives local and terminal sessions through the bridge engine: settlement steps answered by
handlers, host defaults, abort mid-operation, and a reconnect mid-settlement with the pending
step replayed through `since`. Without a host module the suite skips and says why.
