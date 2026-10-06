# @bilt/pos-react

React bindings for the Bilt POS SDK. A provider holds the `BiltPos` connection, hooks turn a
shopper session's basket, member, context and settlement into React state, `RetailMediaSurface`
draws a retail-media placement in the DOM, and `@bilt/pos-react/bridge` adds the Terminal
Bridge detection and install prompt for registers that run over the bridge.

Peer dependencies: `react` 18 or 19, `@bilt/pos-sdk`, `@bilt/pos-protocol`.

```sh
pnpm add @bilt/pos-react @bilt/pos-sdk @bilt/pos-protocol
```

## Two entry points

| Entry point              | Contents                                                                                                                                                                                            |
| ------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `@bilt/pos-react`        | `BiltPosProvider`, `useBiltPos`, `useShopperSession`, `useTerminalSession`, `useBasket`, `useMember`, `useSessionContext`, `useSettlement`, `useOperation`, `useSessionEvent`, `RetailMediaSurface` |
| `@bilt/pos-react/bridge` | `useBridge`, `InstallBridgePrompt`, `BridgeGate`, `detectPlatform`, the manifest helpers                                                                                                            |

Nothing in the core entry point knows the bridge exists. A register that connects over the
Cloud Session Service uses `@bilt/pos-react` alone; one that runs over the Terminal Bridge wraps
itself in `BridgeGate` from the bridge entry point. The engine behind `BiltPos` is the
provider's business, never a hook's.

## A register

```tsx
import { BiltPos } from '@bilt/pos-sdk';
import { localBridge } from '@bilt/pos-sdk/bridge';
import {
  BiltPosProvider,
  RetailMediaSurface,
  useBasket,
  useMember,
  useSettlement,
  useSessionEvent,
  useTerminalSession,
} from '@bilt/pos-react';
import { BridgeGate } from '@bilt/pos-react/bridge';

const MANIFEST_URL = 'https://downloads.bilt.com/bridge/manifest.json';

export function Register() {
  return (
    <BridgeGate manifestUrl={MANIFEST_URL}>
      <BiltPosProvider connect={() => BiltPos.connect(localBridge())}>
        <Lane />
      </BiltPosProvider>
    </BridgeGate>
  );
}

function Lane() {
  const { session, status, error } = useTerminalSession({
    saleId: 'LANE-3',
    poiId: 'VictaLane-275839164',
    currency: 'USD',
    storeLocation: 'STR-0142',
    widgets: [{ type: 'retail-media', placements: ['lane-banner'] }],
  });
  const { basket, addItem } = useBasket(session);
  const { member, set: signIn } = useMember(session);
  const settlement = useSettlement(session, { interactive: ['TOTAL_REQUIRED'] });

  useSessionEvent(session, 'widget.offer', ({ offer }) => applyOffer(offer));

  if (status === 'error') return <p>Could not start the lane: {error?.message}</p>;
  if (!session) return <p>Starting the lane…</p>;

  const step = settlement.pendingStep;
  if (step?.kind === 'TOTAL_REQUIRED') {
    return (
      <TotalPrompt
        suggested={step.suggestedTotal}
        rebates={step.rebates}
        onAccept={(total) => settlement.reply({ total })}
        onDefault={step.useDefault}
      />
    );
  }

  return (
    <>
      <RetailMediaSurface session={session} placement="lane-banner" />
      <Scanner onScan={(item) => addItem(item)} />
      <BasketView basket={basket} member={member} />
      <button onClick={() => signIn({ resolver: { type: 'PHONE', value: phone } })}>Sign in</button>
      <button onClick={() => settlement.settle()} disabled={settlement.status === 'running'}>
        Pay {basket?.grandTotal}
      </button>
      {settlement.status === 'succeeded' && <Receipt result={settlement.result} />}
      {settlement.status === 'failed' && <p>{settlement.error?.message}</p>}
    </>
  );
}
```

## Provider and connection

`BiltPosProvider` takes either a `BiltPos` the page connected itself (`pos`) or an async
`connect` factory it runs on mount and closes on unmount. `useBiltPos()` returns
`{ pos, status, error, reconnect }` with `status` one of `connecting`, `connected`, `error`,
`closed`; `reconnect()` runs the factory again after a failure.

## Sessions

`useTerminalSession(options)` and `useShopperSession(options)` start a session when the
component mounts (once the provider is connected) and end it when the component unmounts,
through the session's `[Symbol.asyncDispose]`, so a refused end is reported rather than thrown.
They return `{ session, status, error, end, restart }`; `status` runs `idle` → `starting` →
`open` → `ending` → `ended`, or `error`. Pass `{ enabled: false }` as the second argument to
hold off until a lane or terminal is chosen; `restart()` ends the session and starts a fresh one
for the next shopper.

The state hooks each take the session (or `null`) and re-render on the matching event:

- `useBasket(session)` → `{ basket, addItem, removeItem, updateItemQuantity, setDiscounts, setTaxRate, setTaxAmount, setTaxTotal, mutate, replace, clear, refresh, … }`, following `basket.changed`.
- `useMember(session)` → `{ member, set, clear, refresh }`, following `member.changed`.
- `useSessionContext(session)` → `{ context, phase, attributes, setPhase, setAttribute, removeAttribute }`, following `context.changed`. (Named to stay clear of React's `useContext`.)
- `useSessionEvent(session, type, handler)` subscribes to any event for the component's lifetime; the handler may be an inline arrow function.
- `useOperation(op)` tracks any thenable, typically an SDK `Operation` held in state: `{ status, result, error, pending, abort }`.

## Settlement

`useSettlement(session, { interactive })` wraps `session.settle()`:

```ts
const settlement = useSettlement(session, { interactive: ['TOTAL_REQUIRED', 'RECOVERY_REQUIRED'] });
settlement.settle({ settlementType: 'NET' }); // any SettleOptions; handlers passed here still run
settlement.status; // idle | running | awaitingReply | succeeded | failed | aborted
settlement.pendingStep; // the step waiting on reply(), or null
settlement.reply({ total: '12.34' }); // or { recovery: 'RETRY' } or { saleTransactionId }
settlement.pendingStep?.useDefault(); // answer with the SDK default
settlement.movements; // every SettlementMovement so far
settlement.result; // SettlementResult once succeeded
settlement.error; // SessionError once failed or aborted
await settlement.abort();
settlement.reset();
```

The steps are the register callbacks of the Java `SettlementFlow`, surfaced over the wire as
`TOTAL_REQUIRED` (after rebates, points or a gift card moved: set the new running total),
`RECOVERY_REQUIRED` (a charge-side failure: retry, skip, record an external tender, abort or
abandon) and `BEFORE_STEP` (the `SaleTransactionID` for the next step). Only the kinds listed in
`interactive` are held open as `pendingStep`; a handler given to `settle()` for a step always
wins; everything else takes its SDK default at once. An unanswered step falls back to its
default at the host's deadline (`pendingStep.deadline`, `pendingStep.signal`), the same as a
missing handler would, so the register cannot wedge a payment.

## RetailMediaSurface

```tsx
<RetailMediaSurface
  session={session}
  placement="lane-banner"
  viewabilityMs={1000}
  dismissible
  onRendering={(rendering) => log(rendering)}
/>
```

The browser half of the retail-media widget: the host decides what to show and validates every
tap, the surface renders and reports. It listens for `widget.rendering` and `widget.clear` on
its placement and draws

- an image as `<img>`,
- a video as a muted, autoplaying, inline `<video>` with its poster, reporting `completed` when it ends,
- an HTML5 creative in `<iframe sandbox="allow-scripts" referrerpolicy="no-referrer">`, an opaque origin with no access to the page, its storage or the bridge token,

plus the headline, body and up to two call-to-action buttons. Taps go to
`session.widget('retail-media').perform(rendering, cta)` with the token exactly as received;
the host answers a valid `APPLY_OFFER` with a `widget.offer` event, which the register handles
with `useSessionEvent(session, 'widget.offer', …)`. `viewed` is reported once per rendering
after it has been on screen for `viewabilityMs` (default 1000 ms), measured with
`IntersectionObserver` where available and from mount where not. `dismissible` adds a close
control that reports `dismissed`.

The output is headless: a container `div` with `data-placement`, `data-state` (`empty` or
`rendering`) and `data-media` attributes and class names under a configurable prefix (default
`bilt-rm`): `bilt-rm-media`, `bilt-rm-image` / `-video` / `-html`, `bilt-rm-copy`,
`bilt-rm-headline`, `bilt-rm-body`, `bilt-rm-actions`, `bilt-rm-cta`, `bilt-rm-cta-primary`,
`bilt-rm-cta-secondary`, `bilt-rm-dismiss`. The only inline styles make the media fill its
container; `--bilt-rm-media-fit` (default `cover`) and `--bilt-rm-media-aspect` tune them. Add a
Content Security Policy `frame-src` for the creative CDN when HTML creatives are enabled.

The session must have a retail-media widget configured (`widgets: [{ type: 'retail-media', … }]`
in the session options); like `session.widget()`, the surface throws on a session without one.

## Bridge detection and install prompt

```tsx
import { useBridge, InstallBridgePrompt, BridgeGate } from '@bilt/pos-react/bridge';

function Register() {
  const bridge = useBridge({ manifestUrl: MANIFEST_URL });
  if (bridge.status === 'missing' || bridge.status === 'outdated') {
    return <InstallBridgePrompt bridge={bridge} downloadUrl={FALLBACK_INSTALLER} />;
  }
  if (bridge.status === 'detecting') return <Connecting />;
  return <Lane />;
}
```

`useBridge({ port?, host?, fallbackPorts?, healthTimeoutMs?, manifestUrl?, pollIntervalMs?, autoDetect?, detect? })`
runs `detectBridge()` from `@bilt/pos-sdk/bridge`, the probe behind `localBridge()`: `GET /health`
on port 48333 and its fallback range, 400 ms each. It returns
`{ status, error, health, baseUrl, probed, attempts, platform, manifest, download, retry }`,
where `status` is `detecting`, `missing`, `outdated` or `ready`. While `missing` or `outdated`
it probes again every two seconds, so a cashier who installs or updates the bridge is let
through without a reload; `retry()` probes at once. `error` is the same `BridgeMissingError` or
`BridgeOutdatedError` (with `required` and `available` protocol versions) that
`BiltPos.connect(localBridge())` would reject with, so a prompt and a failed connect look alike.

`InstallBridgePrompt` renders the install or update copy for the state, the installer link for
this platform (read off the update manifest at `manifestUrl`, else the `downloadUrl` prop), and
a note explaining the one-time permission Chrome asks for before a page may reach loopback.
Pass `autoDetect: false` to show that note before the first probe, with a Continue button that
triggers it. Give it the `bridge` state from your own `useBridge()` to avoid a second probe, or
let it run its own. `productName` and `labels` rebrand the copy; the markup is a headless `div`
with `data-status` and `bilt-bridge-*` class names. `BridgeGate` is `useBridge` plus the prompt
in one: it renders the prompt until `ready`, then its children.

There is no pairing state or dialog in this iteration.

## Testing a register

The hooks are written against the public `@bilt/pos-sdk` interface only, so a register can run
its own tests against any `BiltPos` double by passing it as `pos` to the provider; the SDK
package's in-memory doubles under `js/packages/sdk/test` are what this package's tests use.
