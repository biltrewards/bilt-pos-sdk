# Browser POS

The reference browser register for the Bilt POS SDK: a Vite + React + TypeScript page that
drives a Bilt terminal through the Terminal Bridge with `@bilt/pos-react`. It is the example the
[JavaScript & React SDK integration guide](../../../docs/javascript-sdk-integration.md) walks through,
and `src/guide-samples/` holds every code sample from that guide, so the samples are type-checked
with the rest of the workspace.

What it shows, top to bottom:

- `BridgeGate` with the install prompt, then `BiltPosProvider` connecting through `localBridge()`;
- a settings panel (terminal or local session, `poiId`, `saleId`, currency, store location,
  bridge route) persisted in `localStorage`; applying it restarts the lane;
- the three basket updaters: scanning from a tiny catalog is incremental `addItem`, the quantity
  buttons are `updateItemQuantity`, "Remove all discounts" is one `mutate` batch, and the
  POS-owned cart's "Sync cart" is `basket.replace`;
- member sign-in by phone resolver and, on a terminal, `identifyMember()`;
- `RetailMediaSurface` on the `lane-banner` placement; a validated `widget.offer` is applied as a
  line discount and announced in a toast;
- settlement with `useSettlement`: a tax-recompute handler for `TOTAL_REQUIRED` after rebates,
  an interactive `RECOVERY_REQUIRED` prompt with a countdown to the host's default, movements as
  they commit, the result and receipts, a void, and "next shopper";
- a local session mode for demoing basket, member, context and widgets without hardware.

## Run it

Start a host first: the [Terminal Bridge](../../../docs/terminal-bridge.md) on this machine, or
the development Session Host from the repository root (`./gradlew :host:run --args=dev-host.json`;
without terminals it still serves local sessions). Then:

```sh
cd js
pnpm install
pnpm build                          # the example resolves its siblings through their dist/
pnpm --filter browser-pos dev       # http://localhost:5173
```

The development bridge sends no CORS headers yet, so the page defaults to the "through this
page's origin" bridge route in development: Vite proxies `/health` and `/v1` (HTTP and
WebSocket) to `http://127.0.0.1:48333` (`BRIDGE_URL=http://127.0.0.1:48334 pnpm dev` if the
bridge fell back to another port). Switch the route to "direct" in the settings to probe
`127.0.0.1:48333` the way a deployed register does.

`pnpm --filter browser-pos test` runs the smoke tests against the SDK's in-memory `BiltPos`
double with the bridge probe scripted as `missing` and `ready`; `pnpm --filter browser-pos build`
produces a static bundle under `dist/`.
