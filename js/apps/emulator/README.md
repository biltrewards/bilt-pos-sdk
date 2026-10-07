# Register emulator (browser)

A Vite + React + TypeScript register that exercises the whole checkout contract of the Bilt POS
SDK through `@bilt/pos-react` and the Terminal Bridge. It sits beside the Compose desktop and
Android emulator in [`emulator/`](../../../emulator) and mirrors its features for the browser:
the same mock catalog, the same New Jersey tax policy, the same persisted sale records for
referenced refunds and voids. Where [`examples/browser-pos`](../../examples/browser-pos) stays
minimal as the integration guide's reference, this app is the place to try every feature.

## Run it

Start a host first: the [Terminal Bridge](../../../docs/terminal-bridge.md) on this machine
(`./gradlew :bridge:run --args="--config /path/to/config.json"`; without terminals it still serves
local sessions), or the Session Host alone. Then:

```sh
cd js
pnpm install
pnpm build                              # the app resolves its siblings through their dist/
pnpm --filter @bilt/pos-emulator dev    # http://localhost:5173
```

The development bridge sends no CORS headers yet, so in development the page defaults to the
"through this page's origin" bridge route: Vite proxies `/health` and `/v1` (HTTP and WebSocket)
to `http://127.0.0.1:48333`; `BRIDGE_URL=http://127.0.0.1:48334 pnpm dev` retargets it when the
bridge fell back to another port. The "direct" route probes `127.0.0.1:<port>` the way a deployed
register does, with the port from the Settings tab.

## Configure

The **Settings** tab holds the bridge port and route, the session mode (terminal or local), the
terminal `poiId` picked from the bridge's `/health` terminal list (with a free-text field for one
the bridge does not list), the sale id, currency and store location, and the retail-media
widget toggle. Everything persists in `localStorage`; applying restarts the lane with the new
options. The Settings tab is reachable in front of the install prompt too, since the port lives
there.

Tax is the desktop emulator's `NjSalesTax` ported to `src/tax.ts`: 6.625% on every line except
the Grocery and Apparel categories, carried as `taxRate` on the `BasketItem` and re-applied by
the register after rebates (`recomputeTotal` in `src/money.ts`). Change the constant to try
another rate.

## Tabs

**Sale.** The catalog (24 products from `MockProductProvider`, grouped by category) scans with
incremental `addItem`; a custom amount rings a keyed-in line with a per-cart `CUSTOM-` SKU. The
basket panel adjusts quantities (`updateItemQuantity`), adds per-line discounts (manual, or an
offer with a reference) and register credits (a `CREDIT` line tied to the sale line), removes
every discount in one `mutate` batch, and the POS-owned cart pushes whole with `replace`. The
loyalty panel signs in by phone resolver, by member id or with `identifyMember()` on the
terminal, and shows the member card (program, points, rewards). The gift-card panel sells a card
(a referenced `GIFT-CARD` line plus an activation or reload fulfilment at settlement), registers
a card as split tender with `setStoredValueCard`, and asks for a balance. Pay runs `useSettlement`
with `beforeStep` persisting the sale transaction id (`localStorage`, cleared on completion), a
tax recompute on `TOTAL_REQUIRED` after rebates, the suggested total after points or a gift card,
and an interactive `RECOVERY_REQUIRED` prompt (retry, skip, abort, abandon, or, for a failed card
charge, cash for exactly the amount due) counting down to the host's deadline. A settlement abandoned with committed
movements lists them and refuses another payment until the cashier marks it reconciled, since
the SDK leaves the basket reusable and duplicate prevention to the register. Movements show as they commit, the result
with receipts afterwards; the sale is recorded to IndexedDB for the Refunds tab. Abort, "Next
shopper" (basket and member cleared, settlement reset) and "End session" are on the panel and
the lane bar. The lane bar also moves the phase by hand on a local session.

**Refunds.** Past sales from IndexedDB (`SaleRecord` with every committed leg's POI reference,
ported from the desktop store), newest first, with their refund and void ledger. A referenced
refund of a prior sale rings a `RETURN` line for the amount and settles it with a `CARD` (or
`STORED_VALUE`) refund allocation against the original tender; a full refund of a sale with an
award also files the award reversal. When the selected sale is this session's own last payment
and the leg to refund is its card, the linked `refund()` runs instead; it refunds the card only,
so a stored value leg keeps the allocation path. A sale that loaded a gift card is not refundable: its tender
funded the load, so it is voided, which reverses the load before its funding. Void reverses every standing leg by `OriginalSaleRecord`
(or the parameterless `voidTransaction()` for this session's payment); a failed leg opens the
reversal decision prompt (retry, skip, abort) with the Java default policy as the fallback, and
the legs a stopped void did reverse are recorded. A retry in the same session resends the
identical record, which the session resumes from its own progress; a void started afresh in a
later session sends only the legs still standing. The unreferenced refund is
`refundUnlinked(amount)`.

**Companion display.** `RetailMediaSurface` on the `lane-banner` placement with an explicit "no
creatives served yet" state (the host's retail media engine is an API skeleton today; the
development bridge goes further and refuses to start a session that asks for the widget at all,
`UNSUPPORTED: this host has no ad decision service`, so the lane bar offers "Retry without
retail media", which turns the widget off in the settings), the
offers the host validated with an "apply as discount" action (also offered as a toast wherever
you are), pause/resume of the widget, and a customer-display mirror of the basket.

**Log.** Every session event (the log's own sequence, type, payload summary and the raw payload
on demand), the lifecycle of every operation the page started, and every SDK error, with filters
and "Copy diagnostics" (settings, engine, session and the whole log as JSON on the clipboard).

## Tests

`pnpm --filter @bilt/pos-emulator test` runs the pane tests against the SDK's in-memory `BiltPos`
double with IndexedDB from `fake-indexeddb`. `pnpm --filter @bilt/pos-emulator test:contract`
drives the real Session Host and the scripted terminal of the SDK's contract suite (needs a JDK):
a sale whose rebate is re-taxed and whose declined charge is retried through the recovery
decision, the sale persisted, a gift-card tender, and a referenced refund of the persisted sale
from a later session. `BILT_HOST_LOG=/path` keeps the host's output.
