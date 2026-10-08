# Vanilla TypeScript register

The framework-free counterpart to [`browser-pos`](../browser-pos): a tiny register in Vite and
plain TypeScript that drives a Bilt terminal through the Terminal Bridge with `@bilt/pos-sdk`
and `@bilt/pos-sdk/bridge` only. No React, no UI library: the page renders with
`document.createElement` and re-renders from the session's events. Use it as the starting point
for a register written in another framework (Vue, Svelte, Angular, a server-rendered page) or in
none; `browser-pos` is the React reference and covers more of the API.

What it shows, in `src/register.ts`:

- bridge detection with `detectBridge()`: a missing or outdated bridge gets a message and a retry
  button, a ready one is connected with `BiltPos.connect(localBridge())`;
- a lane settings form (terminal or local session, `poiId`, `saleId`, currency, store location)
  persisted in `localStorage`, which starts a terminal or a local session;
- scanning from a three-item catalog with `basket.addItem`, the basket table and total redrawn
  from `basket.changed`;
- member sign-in by phone resolver with `member.set`, shown from `member.changed`;
- settlement with an `onRebatesRedeemed` handler that re-taxes the rebated lines for
  `TOTAL_REQUIRED`, then the amount charged and the customer receipt;
- `end()`, after which `session.ended` returns the page to the settings form.

## Run it

Start a host first: the [Terminal Bridge](../../../docs/terminal-bridge.md) on this machine, or
the development Session Host from the repository root (`./gradlew :host:run --args=dev-host.json`;
without terminals it still serves local sessions). Then:

```sh
cd js
pnpm install
pnpm build                          # the example resolves its siblings through their dist/
pnpm --filter vanilla-ts dev        # http://localhost:5173
```

The development bridge sends no CORS headers yet, so under `pnpm dev` the page reaches it through
its own origin and Vite proxies `/health` and `/v1` (HTTP and WebSocket) to
`http://127.0.0.1:48333` (`BRIDGE_URL=http://127.0.0.1:48334 pnpm dev` if the bridge fell back to
another port). A production build probes `127.0.0.1:48333` directly, as a deployed register does.

`pnpm --filter vanilla-ts test` runs the smoke tests against the SDK's in-memory `BiltPos` double
with the bridge probe scripted as `missing` and `ready`.
