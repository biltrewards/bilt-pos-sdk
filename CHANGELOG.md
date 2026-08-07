# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed (breaking)

- `execute()` is now **asynchronous** on `SessionResult`, `PaymentFlow`, and `ReversalFlow`: it submits the operation to the session's operation executor — a single lazily-created thread per session, so operations run one at a time in submission order — and returns immediately, delivering the outcome through the registered handlers. The previous blocking behavior is now spelled **`executeSync()`**; `get()`/`getOrNull()`/`isSuccess()` are unchanged (and wait for an in-flight `execute()` to settle). Migration: a mechanical `execute()` → `executeSync()` keeps the old semantics everywhere.
- Callback delivery for asynchronous operations is configurable: `callbackExecutor(Executor)` on the `CheckoutSession`/`ReversalSession` builders sets the session-wide default (e.g. Android's main-thread executor), `callbackOn(executor)` overrides per call/flow. For `PaymentFlow`/`ReversalFlow`, every handler — step handlers included — is delivered on the callback executor with the flow thread awaiting the answer, so the value-returning handlers steer the sequence exactly as if inline while physically running on the integrator's thread (keep them quick, and never block a callback thread on the flow's `get()`). Without a callback executor, handlers run directly on the session's operation thread and must be fast, non-blocking, and must not synchronously invoke another session operation.
- New `onComplete(Runnable)` on all three lazy types: a cleanup hook guaranteed to run exactly once on every completion path — success, failure, unexpected exception, a throwing outcome handler, and an operation rejected because the session had already ended (its executor shuts down at `end()`).

### Added

- Item-based refunds: `ReversalSession.basket()` is a refund cart — the same `SessionBasket` API as a checkout basket, with every line a credit line — and `refundBasket()` executes a linked refund of the cart's total against the referenced card leg, attaching the returned items to the refund's `PaymentRequest` as `SaleItem`s (positive magnitudes; `PaymentType=Refund` carries the direction). Cart mutations refresh the customer display with negative amounts; the cart is consumed the moment the tender refund moves money (even if a later award step aborts the flow) and kept for retry while it hasn't. `ReversalSession.Builder` gains the display options from `CheckoutSession.Builder`: `autoDisplay` (default on), `displayRenderer`, and `externalDisplayClient`.
- Credit lines: `BasketItem.credit(sku, description, quantity, unitPrice)` (or `Builder.credit(true)`) marks a line that subtracts from the basket — a return or trade-in rung into a sale. Quantity and unit price stay positive; the direction carries the sign, so the line's totals and tax are negative on snapshots (`BasketLineItem.isCredit()`) and on the customer display. The SKU upsert keys on (SKU, direction) — selling and taking back the same SKU produces two lines — and the `...BySku` mutators target the sale line when both directions exist (credit lines are addressed by itemId). On a sale's `PaymentRequest`, credit lines are sent with negative `Quantity`/`ItemAmount` (the mixed-basket return-line convention); `pay()` still requires a positive grand total.

- `ReversalSession` — referenced (cross-session) reversal of every sale movement as its own session type. Built from the POI references a POS persisted from the original sale's `CheckoutResult` (`poiTransactionId`, `storedValuePoiTransactionId`, `rebatePoiTransactionId`, `redemptionPoiTransactionId`, `awardPoiTransactionId`, each with a timestamp, plus `memberId`), it voids every referenced movement (card, gift card, redemption, rebate, award) or refunds the card leg + award — days later, in a different process. At least one transaction reference is required at `start()`; the session is bracketed on the terminal like a checkout (start/end signals, `AutoCloseable`). Internally a void is a uniform list of reversal movements executed in order; a loyalty-only void (no money leg) is strict with partial-unwind resume. `CheckoutSession.voidTransaction()`/`refund()` now reverse only the session's own completed payment — its builder no longer carries transaction references (`poiTransactionId`, `awardPoiTransactionId`, `memberId`, and their siblings moved to `ReversalSession.Builder`).
- `ReversalFlow`, returned by `voidTransaction()`, `refund()`/`refund(amount)`, and `refundUnlinked(amount)`: per-step failure control mirroring `pay()`'s handler style. `onError((step, error) -> ...)` returns a `ReversalDecision` — `RETRY` re-sends the failed step, `SKIP` leaves the movement standing and continues, `ABORT` stops with reversed legs standing and the session restored (a later void resumes at the first movement still standing; successful steps are never undone). Without a handler the previous semantics apply as the default policy: money-leg failures abort, loyalty failures are best-effort when a money leg anchors the reversal and strict when they are its substance.

- `identifyMember()` is now allowed on a `FAILED` session, so a declined guest payment can attach a member and retry with loyalty enabled (previously the retry could only run as a guest — identification was rejected until a basket edit resumed the checkout).
- `PaymentOptions.disableAward` — skips the loyalty award step of the payment sequence, mirroring `disableRebates`/`disablePoints`. Previously the award ran unconditionally whenever a member was identified; combining all three now yields a fully loyalty-free payment for an identified member.
- Emulator: payment. The basket card gains Rebates/Redemption/Award checkboxes and a Pay button wired through `CheckoutSession.pay()`; enabling any loyalty step with no member attached runs the terminal identification prompt first (a declined or not-found lookup degrades to a guest checkout). Step results, promotion messages, and award warnings stream into the event feed; one payment per session, and starting a session clears the customer display with an empty basket so the previous checkout's receipt doesn't linger.

- `CheckoutSession` lifecycle brackets: the builder's `start()` announces the session to the terminal (nexo `Admin` signal `BiltSession,Start,v1,<sessionId>`) and yields the session once acknowledged, and `end()` tells the terminal to discard its session-scoped data, sealing the session in the new terminal state `ENDED` (no further operations, no restart). `CheckoutSession` is now `AutoCloseable` — try-with-resources sends the end signal best-effort even on exception paths.
- Emulator: first functional slice. Terminal address autodetect via adb (as the Python emulator did), a connect flow with automatic connectivity diagnostics every 60s driving a connected/unreachable status indicator, explicit Start/End Session controls (one `CheckoutSession` per customer checkout, bracketed on the terminal, ended best-effort on disconnect and app exit), message encryption from `NEXO_PASSPHRASE` (env or `local.properties`) with an in-UI toggle and passphrase field, an out-of-band TLS verification probe (chain + hostname pattern) that reports failures but never blocks communication, and a quick-buy product grid backed by a `ProductProvider` abstraction with a 24-product mock catalog (sub-$1 to $549.99) that rings items into the session basket with NJ sales tax.

### Changed (breaking)

- `CheckoutSession`'s basket surface moved behind `basket()`: the item and tax mutators (`addItem`, `removeItem`/`removeItemBySku`, `updateItemQuantity`/`updateItemQuantityBySku`, `setTaxRate`/`setTaxRateBySku`, `setTaxAmount`/`setTaxAmountBySku`, `setTaxTotal`, `mutate`) and the snapshot accessor (`getBasket()` → `basket().snapshot()`) now live on the new `SessionBasket` type returned by `session.basket()`. Behavior is unchanged — mutations follow the same session lifecycle rules and refresh the customer display under `autoDisplay` — but the basket now has a single home that future session types can share.
- `SessionState.ABORTED` is removed and `abort()` is now operation-scoped: it interrupts the in-flight operation and the session continues — an abort is a register maneuver (cancel a prompt, stop a tender to take a gift card), not an abandonment. An aborted payment unwinds and settles in `FAILED` (basket intact, `pay()` retryable; the error still carries the `ABORTED` code), aborted prompts deliver their aborted/cancelled outcome with the state unchanged, and `abort()` with nothing in flight is a no-op. Abandoning a checkout is `end()`. Terminal-initiated payment aborts likewise settle in `FAILED`.
- `CheckoutSession.Builder.build()` is replaced by `start()`, which returns a lazy `SessionResult<CheckoutSession>` — finish with `execute()`/`get()`/`getOrNull()` like every other session operation. An unstarted session can no longer exist.

- Terminal emulator scaffold (`:emulator`) — a Compose Multiplatform app (Android + desktop from one shared UI) that will drive a terminal through `CheckoutSession`, replacing the Python/tkinter nexo emulator. Run the desktop app with `./gradlew :emulator:desktop:run`; the Android app is `:emulator:android`. UI is a placeholder shell for now.

- `CheckoutSession` — a high-level, stateful checkout API (`com.bilt.pos.session`) on top of `BiltNexoTerminalClient`: basket with three-level tax rules and automatic terminal display, member identification (terminal-prompted and POS-driven), customer input/PIN wrappers, linked/unlinked refunds and voids, an external-display client for routing display/input traffic, and a `pay()` orchestration that sequences rebate redemption → point redemption → stored value → card payment → award with commit tracking, reverse-order rollback, and `onError`-driven retry. All terminal operations are lazy — chains execute via `execute()`/`get()`/`getOrNull()`. New `session` demo command in the CLI and a [CheckoutSession guide](docs/checkout-session-integration.md) in the docs.
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
