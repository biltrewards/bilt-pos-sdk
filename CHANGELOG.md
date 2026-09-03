# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed (breaking)

- Completed void status is terminal-owned: after `voidTransaction()` succeeds, `CheckoutSession` discards its temporary reversal progress instead of locally rejecting later void or linked-refund attempts. Partial-void progress remains guarded and retryable until the attempt completes.
- `CheckoutSession.pay()` is replaced by `CheckoutSession.settle()`. `SettlementFlow` now handles sale, return, and register-credit lines, refund allocations, stored-value fulfillment, exchanges, and refund-only settlements while retaining the total-returning callbacks for rebate, point, and gift-card charge steps.
- `PaymentFlow`, `PaymentOptions`, `CheckoutResult`, `TransactionContext`, and `TransactionStep` are now `SettlementFlow`, `SettlementOptions`, `SettlementResult`, `SettlementContext`, and `SettlementStep`.
- `SessionState`, `SessionStateMachine`, and `CheckoutSession.getState()` are removed. A checkout session is now an open terminal bracket that can run multiple sequential settlements and other operations; the register owns the business flow while the SDK retains targeted in-flight, rollback, refund, and void safety guards.
- `ReversalSession` is removed. Whole prior-sale voids now use `CheckoutSession.voidTransaction(OriginalSaleRecord)` from any open checkout session; item returns and exchanges use return lines plus `SettlementOptions` refund allocations.
- `updateDisplay(Basket)`/`updateDisplay(DisplayPayload)` and `abort()` are now lazy `SessionResult<Void>`s like every other terminal operation — nothing is sent until `execute()` (returns immediately) or `executeSync()` (blocks) is invoked (migration: append `.executeSync()` for the old semantics). `updateDisplay` runs on the session's operation lane and delivers failures through its own per-call `onError` instead of swallowing them into the JUL log; after `end()` it fails with `INVALID_STATE` rather than skipping quietly. `abort()` is deliberately *unordered* — like `updateInputDisplay`, it exists to overlap the in-flight operation occupying the operation lane, so `abort().execute()` overtakes a parked settlement instead of queueing behind the very thing it cancels.
- The automatic customer-display refresh (`autoDisplay`, default on) is now **asynchronous and conflated**: a basket mutation is pure local compute — it returns the updated snapshot immediately and enqueues the display push on the session's operation lane, so a register can ring items from its UI thread without paying a terminal roundtrip per tap. Pushes conflate to the newest snapshot, stay ordered against settlement, and a push that outlives the live basket sends nothing. Failures remain best-effort — logged, never interrupting the checkout — and additionally report through the new `onBackgroundError` handler.
- The device and admin operations — `diagnose()`, `getTotals()`, `reconcile()`, `print()`, `playSound()`/`stopSound()` — moved off `CheckoutSession` onto `Terminal` (migration: `session.diagnose()` → `session.terminal().diagnose()`, etc.). They are SERVICE/DEVICE-class nexo messages with no session reference on the wire, and the move makes them behave like it: they work with no session at all, keep working after `end()`, and no longer queue behind an in-flight settlement. `getTransactionStatus()` stays on `CheckoutSession`.
- `execute()` is now **asynchronous** on `SessionResult`, `SettlementFlow`, and `ReversalFlow`: it submits the operation to the session's operation executor — a single lazily-created thread per session, so operations run one at a time in submission order — and returns immediately, delivering the outcome through the registered handlers. The previous blocking behavior is now spelled **`executeSync()`**; `get()`/`getOrNull()`/`isSuccess()` are unchanged.
- Callback delivery for asynchronous operations is configurable: `callbackExecutor(Executor)` on the `CheckoutSession` builder sets the session-wide default (e.g. Android's main-thread executor), `callbackOn(executor)` overrides per call/flow. For `SettlementFlow`/`ReversalFlow`, every handler — step handlers included — is delivered on the callback executor with the flow thread awaiting the answer.
- New `onComplete(Runnable)` on all three lazy types: a cleanup hook guaranteed to run exactly once on every completion path — success, failure, unexpected exception, a throwing outcome handler, and an operation rejected because the session had already ended (its executor shuts down at `end()`).
- Settlement recovery is now checkpointed. `SettlementFlow.onError` receives `SettlementFailure` before recovery begins; `retry()` retries only the failed step, `skip()` continues past an optional step, and only `abort()` unwinds the full charge-side flow. Indeterminate terminal outcomes are resolved with TransactionStatus before non-abandon recovery. `retryWithoutLoyalty()` is removed.

### Added

- `CheckoutSession.forceEnd(reason)` as an explicit escape hatch for unrecoverable settlement rollback, committed-refund, or partial-void state. It logs the abandoned recovery categories, attempts the terminal End signal, and seals the local session even if that signal fails; normal `end()`, `close()`, and `basket().clear()` retain their recovery guards.
- Net settlement for mixed sale/return baskets: `SettlementOptions.builder().settlementType(SettlementType.NET)` charges or refunds only the signed difference. `Basket.getRefundAmount(SettlementType)` exposes the required allocation total for either settlement mode before settlement starts.
- `onBackgroundError(Consumer<SessionError>)` on the `CheckoutSession` builder: a handler for failures of work the session performs on its own behalf, with no result object to report through. Today that is the automatic display push after a basket mutation; the reserved reactive `onBasketUpdated` channel will report here too.
- `Terminal` — the session-less home of the device and admin operations (`com.bilt.pos.session`). `Terminal.builder()` takes `client`/`saleId`/`poiId` plus optional `storeLocation` and `callbackExecutor`, and `build()` sends nothing — there is no bracket. Operations are lazy `SessionResult`s with the full async `execute()` machinery. `CheckoutSession.terminal()` returns a cached `Terminal` built from the session's configuration, keeping the operations one call away mid-checkout.
- Typed basket intents and refund allocations: `BasketItem.sale(...)`, `returnItem(...)`, and `credit(...)` distinguish purchases, merchandise returns, and register-originated credits. Returns are resolved by `SettlementOptions.refunds(...)` / `addRefund(...)`; credits reduce the charge side without a refund allocation and cannot create a payout. Separate settlement refunds returns and charges sales less credits, while net settlement moves only the signed difference. The SDK does not choose tender policy.
- First-class stored value purchases: any referenced `BasketItem.sale(...)` can be paired with `SettlementOptions.addFulfillment(StoredValueLoad.activate/reload(reference, card))`. Settlement uses the sale line's original total as the load amount, funds the basket before loading the card, rolls funding back when fulfillment fails, records each load in `SettlementResult` / `OriginalSaleRecord`, and reverses loads before funding during a whole-sale void.
- Register-applied line discounts through `BasketDiscount.manual(...)` and `BasketDiscount.offer(...)`, accepted at item construction or replaced later through `SessionBasket.setDiscounts(...)` / `setDiscountsBySku(...)`. Discounts are itemized separately from terminal rebates, reduce taxable line subtotal, and may reduce a line to exactly zero but never below zero.
- `OriginalSaleRecord` — persisted references from a completed sale that `CheckoutSession.voidTransaction(originalSaleRecord)` can use for a whole prior-sale void. The record carries card, stored value, rebate, redemption, award, and member references.
- `SettlementFlow.onMovement(...)` plus per-movement callbacks for card charge, award, card refund, stored value refund, point refund, rebate refund, and award refund.
- `ReversalFlow`, returned by `voidTransaction()`, `refund()`/`refund(amount)`, and `refundUnlinked(amount)`: per-step failure control. `onError((step, error) -> ...)` returns a `ReversalDecision` — `RETRY` re-sends the failed step, `SKIP` leaves the movement standing and continues, `ABORT` stops with reversed legs standing and the session restored.
- External and manual settlement recovery: `external(ExternalPayment)` replaces a failed final card tender with register-managed payment, while `abandon()` stops with no status check or unwind and hands an `AbandonedSettlementRecord` to `onAbandoned`/`SessionException`. Abandonment deliberately leaves the basket reusable and removes SDK recovery guardrails.

- `identifyMember()` remains available after a failed settlement, so a declined guest payment can attach a member and retry with loyalty enabled.
- `SettlementOptions.disableAward` — skips the loyalty award step of the settlement sequence, mirroring `disableRebates`/`disablePoints`.
- Emulator: settlement. The basket card gains Rebates/Redemption/Award checkboxes and a Settle button wired through `CheckoutSession.settle()`; enabling any loyalty step with no member attached runs the terminal identification prompt first. Step results, promotion messages, and award warnings stream into the event feed.

- `CheckoutSession` lifecycle brackets: the builder's `start()` announces the session to the terminal (nexo `Admin` signal `BiltSession,Start,v1,<sessionId>`) and yields the session once acknowledged, and `end()` tells the terminal to discard its session-scoped data and seals the session (no further operations, no restart). `CheckoutSession` is now `AutoCloseable` — try-with-resources sends the end signal best-effort even on exception paths.
- Emulator: first functional slice. Terminal address autodetect via adb (as the Python emulator did), a connect flow with automatic connectivity diagnostics every 60s driving a connected/unreachable status indicator, explicit Start/End Session controls (one `CheckoutSession` per customer checkout, bracketed on the terminal, ended best-effort on disconnect and app exit), message encryption from `NEXO_PASSPHRASE` (env or `local.properties`) with an in-UI toggle and passphrase field, an out-of-band TLS verification probe (chain + hostname pattern) that reports failures but never blocks communication, and a quick-buy product grid backed by a `ProductProvider` abstraction with a 24-product mock catalog (sub-$1 to $549.99) that rings items into the session basket with NJ sales tax.

### Changed (breaking)

- `CheckoutSession`'s basket surface moved behind `basket()`: the item and tax mutators (`addItem`, `removeItem`/`removeItemBySku`, `updateItemQuantity`/`updateItemQuantityBySku`, `setTaxRate`/`setTaxRateBySku`, `setTaxAmount`/`setTaxAmountBySku`, `setTaxTotal`, `mutate`) and the snapshot accessor (`getBasket()` → `basket().snapshot()`) now live on the new `SessionBasket` type returned by `session.basket()`. A successful settlement consumes the current basket; `basket().clear()` starts a fresh basket with a new cart ID for another settlement in the same session and clears the previous split-tender stored-value selection. A same-session void that has already reversed a movement must be resumed before the basket can be cleared, another settlement can replace its target, or the session can end.
- `abort()` is operation-scoped: it interrupts the in-flight operation and the session continues — an abort is a register maneuver (cancel a prompt, stop a tender to take a gift card), not an abandonment. An aborted settlement unwinds with its basket intact and retryable (the error still carries the `ABORTED` code), aborted prompts deliver their aborted/cancelled outcome, and `abort()` with nothing in flight is a no-op. Abandoning the terminal bracket is `end()`.
- `CheckoutSession.Builder.build()` is replaced by `start()`, which returns a lazy `SessionResult<CheckoutSession>` — finish with `execute()`/`get()`/`getOrNull()` like every other session operation. An unstarted session can no longer exist.

- Terminal emulator scaffold (`:emulator`) — a Compose Multiplatform app (Android + desktop from one shared UI) that will drive a terminal through `CheckoutSession`, replacing the Python/tkinter nexo emulator. Run the desktop app with `./gradlew :emulator:desktop:run`; the Android app is `:emulator:android`. UI is a placeholder shell for now.

- `CheckoutSession` — a high-level checkout API (`com.bilt.pos.session`) on top of `BiltNexoTerminalClient`: basket with three-level tax rules and automatic terminal display, member identification (terminal-prompted and POS-driven), customer input/PIN wrappers, linked/unlinked refunds and voids, an external-display client for routing display/input traffic, and settlement orchestration. All terminal operations are lazy — chains execute via `execute()`/`get()`/`getOrNull()`. New `session` demo command in the CLI and a [CheckoutSession guide](docs/checkout-session-integration.md) in the docs.
- `CheckoutSession` stored value lifecycle (`storedValueBalance`/`Activate`/`Load`/`Unload`/`Deactivate`/`Reserve`/`Reverse`/`Duplicate` with a typed `StoredValueCard`), transaction-status options (receipt reprint, non-payment originals), `playSound`/`stopSound`, `getTotals()`, and `updateInputDisplay()` (nexo `InputUpdate` for in-progress prompts).

### Changed

- Build: Gradle wrapper 9.2.1 → 9.6.1 and AGP 8.13.2 → 9.3.1. AGP 8.x fails to configure under Gradle ≥ 9.6 (it used a removed Gradle-internal API), which broke builds with a system-installed Gradle. The AGP 9 migration drops the standalone `kotlin-android` plugin (built-in Kotlin) and moves `:emulator:shared` to `com.android.kotlin.multiplatform.library`. Published artifacts are unaffected.
- XML marshalling of display/input/receipt payloads no longer uses the JAXB runtime, which does not run on Android; Jackson (`jackson-dataformat-xml`) now reads the same `jakarta.xml.bind` annotations on the generated models. Public API is unchanged (`DisplayPayloadHelper`/`ReceiptHelper` signatures still throw `JAXBException`); `org.glassfish.jaxb:jaxb-runtime` is dropped from the SDK's dependencies. Android consumers get the Woodstox StAX implementation transitively but must additionally bundle the `javax.xml.stream` API classes (absent from the Android platform), e.g. `javax.xml.stream:stax-api:1.0-2`.

### Fixed

- The automatic basket display (receipt built on `addItem`) printed the ISO currency code next to every amount (`USD 79.99`); it now renders the currency symbol (`$79.99`), falling back to the code for currencies without a known symbol.
- `docs/refund-referenced.md` placed `OriginalPOITransaction` under `PaymentData`; per the nexo schema it belongs under `PaymentTransaction`.

- Network-error recovery for `BiltNexoTerminalClient` (on by default): re-sends an in-flight request under an `X-Bilt-Request-Id` correlation header across transient network failures, with at-most-once execution guaranteed by terminal-side dedupe. Includes a configurable keep-alive ping and HTTP protocol / pong diagnostics, mirrored across the Java and Kotlin clients and the CLI ([#83](https://github.com/biltrewards/bilt-pos-sdk/pull/83))
- Receipt Requirements documentation with Fiserv EMV guidelines and Bilt Platform fields

## [0.5.10] - 2025

### Added

- Documentation for `ForceEntryMode` in unreferenced refunds to restrict card entry methods

## [0.5.9] - 2025

### Fixed

- TaxType element ordering in XSD schema to restore backwards compatibility ([#36](https://github.com/biltrewards/bilt-pos-sdk/pull/36))

## [0.5.8] - 2025

### Added

- Receipt parsing helpers documentation ([#37](https://github.com/biltrewards/bilt-pos-sdk/pull/37))
- CONTRIBUTING.md ([#41](https://github.com/biltrewards/bilt-pos-sdk/pull/41))

### Security

- Bump picomatch from 4.0.3 to 4.0.4 ([#38](https://github.com/biltrewards/bilt-pos-sdk/pull/38))
- Bump brace-expansion in /schema ([#39](https://github.com/biltrewards/bilt-pos-sdk/pull/39))
- Bump handlebars from 4.7.8 to 4.7.9 ([#40](https://github.com/biltrewards/bilt-pos-sdk/pull/40))

## [0.5.7] - 2025

Initial public release. Java and Kotlin libraries for integrating with Bilt payment terminals via the Nexo Sale to POI v3.0 protocol.

### Added

- Nexo Retailer SDK for Android (Kotlin/Java) with 245+ type-safe model classes
- BiltNexoTerminalClient with AES-256-CBC encryption and HMAC-SHA256 authentication
- Support for payments, refunds, reversals, gift card transactions, diagnosis, transaction status, and abort operations
- Display helpers for terminal screens (standby, QR codes, receipts, images)
- Input handling for confirmations, signatures, digit/decimal strings, and menu selections
- Receipt XML parsing helpers and schema
- Display XSD schema with code generation
- CLI tool for testing terminal interactions
- Documentation site with GitHub Pages
- Maven Central publishing
