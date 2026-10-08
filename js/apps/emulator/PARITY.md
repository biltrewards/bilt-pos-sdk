# Desktop parity checklist

The browser emulator is a port of the Compose desktop emulator
([`EmulatorApp.kt`](../../../emulator/shared/src/commonMain/kotlin/com/bilt/pos/emulator/EmulatorApp.kt),
[`EmulatorState.kt`](../../../emulator/shared/src/commonMain/kotlin/com/bilt/pos/emulator/session/EmulatorState.kt),
[`NexoEmulatorController.kt`](../../../emulator/shared/src/jvmShared/kotlin/com/bilt/pos/emulator/session/NexoEmulatorController.kt)).
This table maps every desktop state field, controller action, toggle, dialog and tab onto the
browser app. "Main" is the browser app as merged in #150; "Now" is this branch.

Status: **present** (same behaviour), **partial** (exists, but differs), **missing**, **n/a** (does
not apply in a browser; see the deliberate differences below).

## Layout and screens

| Desktop | Main | Now |
| --- | --- | --- |
| Top bar "Bilt POS Emulator" with connection dot, detail, TLS · Encryption · Session line | partial (header with lane summary, no status dot) | present (dot, bridge detail, Bridge · Mode · Session line) |
| `ConnectionPanel` card, full width, connection row + action row | missing (Settings tab + lane bar) | present |
| Loyalty sign-in card (`MemberCard`), only after a sign-in ran | partial (member panel always shown, different content) | present |
| Basket card above the tab content, shared by every tab | partial (basket inside the Sale tab only) | present |
| Event log card in a right-hand column (`EventsCard`) | missing (separate Log tab) | present |
| Bottom tab row Sale / Stored Value / Refund (`EmulatorTab`) | partial (top tabs Sale / Refunds / Companion display / Log / Settings) | present |
| Sale tab with Products / Keypad selector (`SaleTabPane`) | partial (catalog grouped by category, custom-amount form) | present |
| Companion display pane | extra (not in desktop) | removed |
| Narrow-window stacking (log under the content below 1000 px) | missing | present |

## `EmulatorState` fields

| Field | Main | Now |
| --- | --- | --- |
| `terminalAddress`, `addressAutodetected` | n/a | n/a: terminal picker from `/health` |
| `connection` (phase + detail) | partial (bridge gate status) | present (bridge probe + connect state, terminal reachability) |
| `tls` | n/a | n/a: loopback HTTP to the bridge; the bridge owns the terminal TLS |
| `encryptionEnabled`, `hasConfiguredPassphrase` | n/a | n/a: bridge config |
| `sessionId` (null when no checkout) | partial (session always open) | present (explicit Start/End Checkout) |
| `basket` (`BasketLine` with type, discount labels, gift-card flag, editable price) | partial | present |
| `basketTotal`, `basketTax` | present | present |
| `paymentInProgress` | present | present |
| `cardReadInProgress` | missing | present |
| `storedValueInProgress` | partial | present |
| `identifyInProgress` | present | present |
| `refundInProgress` | present | present |
| `lastPayment` summary line | missing | present |
| `paymentOutcome` popup | missing (inline result) | present |
| `paymentRecovery` prompt | partial (inline prompt, different choices) | present (modal, desktop actions) |
| `acquiredCard` | missing | present |
| `member` (`Found` / `Absent` / `Failed`, retained flag) | partial | present |
| `sales` (`StoredSaleUi`) | partial (amount-based view) | present (items, remaining quantities, status label) |
| `events` | missing | present (Events tab) |
| `detailedEvents` | partial (Log tab) | present (Detailed tab) |
| `nexoMessages` | n/a | Protocol tab (HTTP requests + session events) |

## `EmulatorController` actions

| Action | Main | Now |
| --- | --- | --- |
| `autodetectAddress()` | n/a | bridge probe (`useBridge`) and auto-connect when ready |
| `connect(...)` / `disconnect()` | partial (implicit) | present (Connect / Disconnect over `localBridge`) |
| `startSession(identifyOnStart)` | missing (session auto-starts) | present (Start Checkout + Identify checkbox) |
| `endSession()` | partial ("End session") | present (End Checkout) |
| `addProduct(product)` (upsert by SKU, NJ tax) | present | present |
| `addCustomItem(priceMinor)` (keypad, untaxed) | partial (form, optional tax) | present (keypad) |
| `updateCustomItemPrice(sku, minor)` (tap line, live re-price) | missing | present |
| `removeCustomItem(sku)` (✓ at zero) | missing | present |
| `addGiftCardPurchase(amount, card)` | partial (separate panel, activate/reload choice) | present (Stored Value → Purchase, reload fulfilment) |
| `inquireStoredValueBalance(card)` | partial | present (Stored Value → Balance inquiry, outcome popup) |
| `activateStoredValue(card)` | partial | present (Stored Value → Activation, zero balance) |
| `applyCredit(itemId, amount, label)` | partial (no limits) | present (dialog, line and basket caps) |
| `applyDiscount(itemId, amount, label)` (replace; 0 clears) | partial (appends discounts) | present (dialog, replace, 0 clears) |
| `settle(loyalty, storedValue, net)` | partial | present |
| `identifyMember()` (Loyalty Sign-In, keyed entry) | partial | present |
| `acquireCard()` (Read card) | missing | present |
| `refundSale(saleId)` (full refund = void on a fresh session, no checkout open) | partial (void on the open session) | present |
| `addReturnToBasket(saleId, skus)` (item returns into the active basket) | missing | present |
| `clearBasket()` | partial | present |
| `abort()` (recovery prompt → ABORT, refund, operation) | partial (payment only) | present |
| `dismissPaymentOutcome()` | missing | present |

## Toggles, dialogs, controls

| Desktop control | Main | Now |
| --- | --- | --- |
| Rebates / Redemption / Award checkboxes (default off) | partial (default on) | present |
| Gift card checkbox + card field + Read card | partial (separate panel) | present |
| Net settlement checkbox (default on) | partial (default off) | present |
| "Settle $X" / "Settle (refund $X)" / "Settling…" / "Settled" button | partial ("Pay") | present |
| Identify checkbox | missing | present |
| Loyalty Sign-In / Clear basket / Abort operation buttons | partial | present |
| Per-line Discount and Credit buttons + `BasketAdjustmentDialog` | partial (inline form) | present |
| Tap a custom line to edit it on the keypad | missing | present |
| `PaymentRecoveryDialog`: Retry, Skip step, Confirm cash received, Abort and roll back, Abandon recovery, with the desktop rules for which apply | partial | present |
| `PaymentOutcomeDialog` (title, message, receipt, OK) | missing | present |
| Refund tab: `SalesListCard` + `RefundDetailsCard`, Full amount / Selected items radios, per-item checkboxes with remaining quantities, the guard texts | partial (amount, void, unlinked) | present |
| Stored Value tab: Balance inquiry / Activation / Purchase selector | partial | present |
| Log tabs Events / Detailed / Nexo | partial (one filtered table) | Events / Detailed / Protocol |

## Browser-only features removed

These had no home in the desktop structure, so they went with it: the companion display and retail
media (`RetailMediaSurface`, offer toasts, the widget at session start); the separate Log and
Settings tabs; the lane phase control; quantity steppers and the POS-owned cart draft
(`basket.replace`); sign-in by phone resolver or member id; the amount-based referenced refund,
the unreferenced refund and the "void this payment" shortcut; and the "mark reconciled" gate after
an abandoned settlement (the desktop reports it in the outcome popup instead).

## Deliberate differences

- The log's Nexo tab is **Protocol**: the browser never sees Nexo, so it lists the HTTP requests to
  the bridge (method, path, status) and the session events from the event stream.
- The connection row: Terminal IP, adb tunnel, Encrypt and the passphrase live in the bridge's own
  configuration. In their place are the bridge status, a terminal picker from `/health`, the bridge
  port (plus the dev-proxy route) and a Local session toggle.
- Without a terminal (Local session), the terminal operations (sign-in, card read, stored value,
  settlement, refunds) are disabled; the basket still works.

## Not possible through `@bilt/pos-sdk`

- TLS verification status: the page talks plain HTTP to loopback; the bridge holds the terminal
  TLS and does not report it. The top bar says "TLS: bridge-managed".
- Periodic POI diagnosis: `Terminal.diagnose()` exists and is used once on connect for the status
  detail, but the bridge reports terminal reachability in `/health` already, so the browser
  re-probes `/health` instead of polling the terminal.
- Nexo message envelopes: not exposed by the bridge (see Protocol above).
