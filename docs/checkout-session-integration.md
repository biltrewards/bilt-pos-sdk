---
---

# CheckoutSession — Integration Guide

`CheckoutSession` is a higher-level abstraction on top of [`BiltNexoTerminalClient`](./integration.md). It manages the full lifecycle of a loyalty-enabled checkout — member identification, cart management, terminal display, payment orchestration (rebate redemption, point redemption, stored value, card payment), and reward award — as a single stateful session.

You keep working with one object for the whole transaction instead of hand-assembling nexo messages. The raw nexo client is still available via `session.getClient()` as an escape hatch.

---

## Before you begin

Make sure you have:

1. Ordered a terminal and boarded it to a store.
2. Read and understood the [Integration Guide](./integration.md).
3. Created a `BiltNexoTerminalClient` for your terminal.

---

## Mental model

A few principles explain most of the behavior:

- **A session is bracketed on the terminal.** The builder's `start()` announces the session to the terminal and only hands out the `CheckoutSession` once the terminal acknowledged — an unstarted session never exists. `end()` tells the terminal to discard the session-scoped data it accumulated and seals the session: nothing can run on it afterwards, and it cannot be restarted. One session per checkout, always closed with `end()` (or try-with-resources — the session is `AutoCloseable`).
- **The session owns the basket.** Items, tax, and totals live in one place, and `Basket` is the single source of truth. Every mutation returns the updated basket.
- **nexo underneath.** Every session operation maps to a standard nexo 3.0 message, but the SDK hides much more than message serialization: it manages the complexity of communicating with the terminal, and it orchestrates payment when the transaction is multi-tender — sequencing rebates, point redemption, stored value, and card, and handling loyalty award and reversal — so the register doesn't have to coordinate any of it.
- **Terminal operations are lazy.** Methods returning a `SessionResult` or `PaymentFlow` send nothing until you call `.execute()` (asynchronous, handlers deliver the outcome), `.executeSync()`, `.get()`, or `.getOrNull()` (blocking). Register handlers first, then execute — a chain without a terminal method never reaches the terminal. See [lazy execution](#lazy-execution).
- **Cart-building is local + auto-display.** The basket surface lives on `session.basket()`: `addItem` / `removeItem` / `updateItemQuantity` update the local basket and (with `autoDisplay=true`, the default) push a `DisplayRequest` to the terminal. The terminal may independently evaluate offers while items are scanned, but those offers are **only committed during `pay()`**.
- **`pay()` is a fixed orchestration sequence,** with a blocking callback after each loyalty/stored-value step so the register can update its own model and recompute tax, then return the total that feeds the next step. The shape is fixed, but steps are conditional: loyalty (rebates + points) runs only for identified members and can be disabled via `PaymentOptions`, the stored-value step only runs when a gift card has been registered with `setStoredValueCard`, and card payment runs whenever an amount remains.
- **Errors and aborts roll back cleanly.** If a step fails or `abort()` is called mid-sequence, everything already committed (rebates, points, stored value) is reversed in the opposite order before the session moves to `FAILED` — basket intact, `pay()` retryable. An abort is a register maneuver, not an abandonment; `end()` abandons the checkout.

### Built-in loyalty handling

Loyalty is where a checkout gets complicated: identifying the member, looking up their offers and point balance, deciding what applies, committing rebates and redemptions, interleaving all of that with the actual payment, awarding points at the end, and unwinding everything correctly if anything fails. Most of that complexity is handled for you — it happens seamlessly as part of the normal session and `pay()` flow. Concretely:

- **You don't have to identify a member.** The register never needs to call `identifyMember()` unless it specifically wants the member ID for its own purposes. The customer can identify themselves at the terminal, and loyalty still works end to end.
- **No loyalty service to call, no orchestration to write.** You don't reach out to a separate loyalty service or sequence loyalty calls against the payment yourself. The session drives rebate → redemption → payment → award (and the matching reversals) internally.
- **You only handle cart updates when an applied offer forces one** — for example, recomputing tax on the discounted subtotal inside `onRebatesRedeemed`. If a jurisdiction doesn't require that, you can skip the callback entirely and take the defaults.
- **The rest just happens.** Offer evaluation, point redemption, award, and Store-and-Forward when loyalty is briefly unreachable all occur in the background without register involvement.

---

## The end-to-end flow

```
Register                    CheckoutSession                  Terminal (POI)
   │                              │                                │
   │ ── builder().start()…get() > │ ── Admin(SessionStart) ──────> │  session announced
   │ <── started session ──────── │                                │
   │                              │                                │
   │ ── addItem(item) ──────────> │ upsert into basket             │
   │                              │ ── DisplayRequest ───────────> │  (auto-display)
   │ <── updated Basket ───────── │                                │
   │        … repeat per scan / tax change …                       │
   │                              │                                │
   │ ── pay()…execute() ────────> │                                │
   │                              │ ── LoyaltyRequest(Rebate) ───> │  offers committed
   │ <── onRebatesRedeemed ────── │                                │
   │ ─── updated total ─────────> │                                │
   │                              │ ── LoyaltyRequest(Redemption)> │  points redeemed
   │ <── onPointsRedeemed ─────── │                                │
   │ ─── updated total ─────────> │                                │
   │                              │ ── PaymentRequest ───────────> │  gift card charged
   │ <── onGiftCardPayment ────── │    (StoredValue instrument,    │
   │ ─── updated total ─────────> │     if a card was registered)  │
   │                              │                                │
   │                              │ ── PaymentRequest ───────────> │  card approved
   │                              │ ── LoyaltyRequest(Award) ────> │  points earned (SAF)
   │                              │ ── DisplayRequest (receipt) ─> │
   │ <── onSuccess(result) ────── │                                │
   │                              │                                │
   │ ── end()…execute() ────────> │ ── Admin(SessionEnd) ────────> │  session data discarded
```

The terminal forwards loyalty requests to POS Loyalty for offer evaluation, redemption, and award; when loyalty is briefly unreachable the award is stored and forwarded by the terminal.

---

## The payment sequence explained

`pay()` returns a `PaymentFlow` — a chainable builder where the register hooks into each step, executed when you call `.execute()` / `.get()` / `.getOrNull()`. `beforeStep` is called before every step with a `TransactionContext`; it is a chance to persist pending state and return the sale transaction ID to use for that step. The sequence is always:

1. **Rebate redemption** (identified members, if enabled) — terminal commits applicable offers/coupons → `onRebatesRedeemed`
2. **Point redemption** (identified members, if enabled and a balance remains) — terminal redeems points for monetary value → `onPointsRedeemed`
3. **Stored value** (if a card was registered and a balance remains) — terminal charges the gift card → `onGiftCardPayment`
4. **Card payment** (if a balance remains) — terminal processes the card for the remaining amount
5. **Award** — terminal submits the loyalty award (Store-and-Forward if loyalty is down)
6. `onSuccess` / `onError`

**Callbacks are synchronous and blocking.** Each runs on the calling thread, and the `BigDecimal` it returns becomes the total passed to the next step. This is deliberate: some jurisdictions tax the discounted price, so the register may need to recompute tax after each discount and feed the corrected total forward. That total → tax → total pipeline only works if the steps are sequential.

**Defaults when a callback isn't registered:**

| Step | Default |
| --- | --- |
| `beforeStep` | Returns a new UUID as the sale transaction ID. |
| `onRebatesRedeemed` | Accept rebates. New total = previous − rebate amount. |
| `onPointsRedeemed` | Accept points. New total = previous − monetary value. |
| `onGiftCardPayment` | Accept charge. New total = previous − amount charged. |
| `onSuccess` | No-op (the result is still available via `.get()`). |
| `onError` | `PaymentOptions.voidAndAbort()` — roll back and fail the payment. |

**Abort / error rollback.** `abort()` is operation-scoped: it interrupts the in-flight operation and the session continues. If it fires mid-payment (e.g. after rebates committed but before card payment), the session reverses everything committed so far — rebate refunds, redemption refunds, stored-value reversals — and settles in `FAILED` with the basket intact, so `pay()` can retry (the thrown error carries the `ABORTED` code); if a reversal fails, `voidTransaction()` finishes the unwind. Aborted prompts (input, PIN, identification) deliver their aborted/cancelled outcome and leave the state unchanged; with nothing in flight `abort()` is a no-op. `abort()` is safe to call from any thread. An operation that completes on the terminal despite a racing abort always delivers its outcome — a prompt was genuinely answered, money may have genuinely moved, and the register must know. On error the `PaymentOptions` returned by `onError` decides what happens next:

- `PaymentOptions.voidAndAbort()` (the default) — roll back committed steps in reverse order and fail; the session moves to `FAILED`, from which `pay()` can be retried. If the rollback itself was incomplete, a retried `pay()` first finishes the standing reversals — and refuses to start if one still cannot go through.
- `PaymentOptions.retryWithoutLoyalty()` — roll back the loyalty steps and restart the sequence with rebates and points disabled.
- Any other options — full rollback, then restart with those options. At most 3 recoveries per execution.

**A failed award never reverses a completed payment:** the checkout completes with the failure reported in `CheckoutResult.getWarnings()`, and the terminal retries the award via Store-and-Forward.

---

## Session state machine

```
IDLE → IDENTIFIED → ACTIVE → PAYING → COMPLETED
                       ↑         ↓        ↓
                       └────── FAILED → VOIDING → VOIDED
abort() interrupts the in-flight operation only (an aborted payment → FAILED)
end()   → ENDED   (from any state except PAYING/VOIDING)
```

Notes:

- `identifyMember()` can be called from `IDLE` or `ACTIVE`. Called from `ACTIVE`, the terminal re-evaluates offers with member context. Identification is optional — the customer can identify themselves on the terminal, and they can always opt out.
- Removing all items from `ACTIVE` returns to `IDLE` (or `IDENTIFIED` if a member was explicitly identified).
- From `FAILED` the register can retry `pay()` directly, keep editing the basket (back to `ACTIVE`), identify a member, or `voidTransaction()`. While a failed payment's rollback is incomplete, edits keep the session in `FAILED` — preserving the path to finish the unwind.
- `voidTransaction()` runs from `COMPLETED` (or `FAILED`); if the void itself fails, the session is restored to its pre-void state so it can be retried.
- `COMPLETED`, `VOIDED`, and `ENDED` are terminal. `getState()` reports the current `SessionState`; the basket is frozen while `PAYING`.
- `ENDED` is the strictest terminal state: `COMPLETED`/`VOIDED` still allow the wrap-up operations that reference the settled transaction (void, refund, receipt reprint, diagnostics), but after `end()` nothing runs at all — the terminal has discarded the session.

---

## Start a session

```java
CheckoutSession session = CheckoutSession.builder()
    .client(client)                       // required
    .saleId("POS-LANE-3")                 // required — your POS identifier (SaleID)
    .poiId("VictaLane-275839164")         // required — target terminal (POIID)
    .currency("USD")                      // required
    .storeLocation("STR-0142")            // optional — sent as SaleTerminalData.TotalsGroupID
    .start()                              // lazy, like every terminal operation
    .get();                               // announces the session; throws if refused
```

The builder's `start()` announces the session to the terminal (the [session start signal](./session-start-end.md)) and yields the `CheckoutSession` once the terminal acknowledged. It is lazy like every other operation — chain `onSuccess`/`onError` and finish with `execute()`, `get()`, or `getOrNull()`. A refused start hands out no session; call `start()` again for a fresh attempt. And if your `onSuccess` handler itself throws, the just-started session is ended on the terminal (best-effort) before the exception propagates — a `start()` whose execution threw never leaves a terminal-side session behind.

A session represents one checkout. Create a new session per transaction; sessions are intended for use from a single register thread (`abort()` may be called from any thread).

### End the session

```java
session.end().execute();          // or in a handler-style chain, or .get()
```

`end()` sends the [session end signal](./session-start-end.md) — the terminal discards the session-scoped data it accumulated — and moves the session to `ENDED`. After that **nothing** runs on the session — not even diagnostics or display — and it cannot be restarted; create a new session for the next checkout. Rules:

- Allowed from any state except `PAYING` and `VOIDING` (money in flight).
- Refused while a failed payment's rollback is incomplete — finish the unwind with `voidTransaction()` first.
- If the end signal itself fails, the session keeps its state and `end()` can be retried.
- A concurrent `abort()` never cancels an in-flight `end()` — like an in-flight void, the end exchange always settles (cancelling cleanup would only strand terminal-side session data).

`CheckoutSession` is `AutoCloseable`: `close()` is a best-effort `end()` (failures are logged, an already-ended session is left alone), so try-with-resources guarantees the terminal is told to clean up even on exception paths:

```java
try (CheckoutSession session = CheckoutSession.builder()....start().get()) {
    // scan, pay, ...
}   // Admin(SessionEnd) sent here
```

### Lazy execution

Every terminal operation is **lazy**: methods returning a `SessionResult`, `PaymentFlow`, or `ReversalFlow` send nothing until you invoke one of the terminal methods:

- `execute()` — run **asynchronously** on the session's operation thread (a single thread per session, so operations run in submission order) and deliver the outcome to the registered `onSuccess`/`onError`/`onComplete` handlers; returns immediately;
- `executeSync()` — run blocking; the operation still takes its turn on the session's operation thread (queueing behind anything in flight — a sync call never races an async one), handlers dispatched on the calling thread;
- `get()` — run and return the value, throwing `SessionException` on failure (waits for an in-flight `execute()` to settle);
- `getOrNull()` — like `get()`, but returns `null` on failure.

Always end a fluent chain with one of these — a chain without them never reaches the terminal:

```java
session.requestConfirmation("Would you like a receipt?")
    .onSuccess(confirmed -> { if (confirmed) register.printReceipt(); })
    .onError(e -> register.showError(e.getMessage()))
    .execute();
```

Under `execute()`, handlers are delivered through the **callback executor** — configure a session-wide one with the builder's `callbackExecutor(...)` (e.g. Android's main-thread executor, so handlers may touch UI directly) or override per call with `callbackOn(executor)`. For `PaymentFlow` and `ReversalFlow`, whose handlers are part of the payment/reversal negotiation (step handlers return the running total, `onError` returns the recovery decision), the flow thread **waits for each handler's answer**: the handlers steer the sequence exactly as if they ran inline, just physically on your thread — keep them quick (the payment is paused while they run), and never block the callback thread on the flow's own `get()`. Handlers **may** invoke further session operations synchronously — an operation started from inside a handler runs inline, as part of the operation being handled (asking the cashier a blocking `requestConfirmation` from `onRebatesRedeemed` works). What must be avoided is blocking the callback executor's thread on a session call **outside** a handler while operations are in flight: an in-flight operation may need that thread for its handlers before it can finish, and both sides would wait forever. From that thread — teardown included — use `execute()` with `onComplete`; prefer `end().execute()` over the blocking `close()` for UI-driven teardown.

`onComplete(...)` registers a cleanup hook that runs exactly once on every completion path — success, failure, even an operation rejected because the session had already ended. Use it for the cleanup that must not leak: re-enabling buttons, releasing claims.

---

## Quick start (minimal)

```java
CheckoutSession session = CheckoutSession.builder()
    .client(client)
    .saleId("POS-LANE-3")
    .poiId("VictaLane-275839164")
    .currency("USD")
    .storeLocation("STR-0142")
    .start()
    .get();

session.basket().addItem(BasketItem.of("KRK-CNDL-LRG-VAN", "Large Vanilla Candle", 1, "24.99"));

session.pay()
    .onSuccess(result -> register.printReceipt(result.getMerchantReceipt()))
    .onError(error -> {
        register.showError(error.getMessage());
        return PaymentOptions.voidAndAbort();
    })
    .execute();

session.end().execute();   // the terminal discards its session data
```

---

## Full integration example

```java
// --- Setup ---
BiltNexoTerminalClient client = BiltNexoTerminalClient.builder()
    .endpoint("https://192.168.1.42:8443/nexo")
    .securityKey(key)
    .build();

CheckoutSession session = CheckoutSession.builder()
    .client(client)
    .saleId("POS-LANE-3")
    .poiId("VictaLane-275839164")
    .currency("USD")
    .storeLocation("STR-0142")
    .start()
    .get();

// --- 1. Identify member (optional — customer can also do it on the terminal) ---
session.identifyMember()
    .onSuccess(member -> {
        if (member.getStatus() == IdentifyStatus.FOUND) {
            register.showMember(member.getMemberId());
            register.showRewards(member.getRewards());
        }
        // NOT_FOUND / CANCELLED / SUSPENDED → guest checkout, no action needed
    })
    .onError(error -> register.showError(error.getMessage()))
    .execute();

// --- 2. Scan items ---
Basket basket = session.basket().addItem(BasketItem.of("KRK-CNDL-LRG-VAN", "Large Vanilla Candle", 2, "24.99"));
register.setTotal(basket.getGrandTotal());  // $49.98

basket = session.basket().addItem(BasketItem.of("KRK-FRAME-5X7-BLK", "5x7 Black Frame", 1, "14.99"));
register.setTotal(basket.getGrandTotal());  // $64.97

// Scan same candle again — upserts, now qty 3
basket = session.basket().addItem(BasketItem.of("KRK-CNDL-LRG-VAN", "Large Vanilla Candle", 1, "24.99"));
register.setTotal(basket.getGrandTotal());  // $89.96

// --- 3. Tax ---
session.basket().setTaxRateBySku("KRK-CNDL-LRG-VAN", new BigDecimal("0.08875"));
session.basket().setTaxRateBySku("KRK-FRAME-5X7-BLK", new BigDecimal("0.08875"));
// Or: session.basket().setTaxTotal(new BigDecimal("7.98"));

// --- 4. Pay ---
session.pay()
    .beforeStep(ctx -> {
        String id = "TXN-" + ctx.getStep() + "-" + register.nextSequence();
        register.logPending(id, ctx.getStep(), ctx.getCurrentTotal());
        return id;
    })
    .onRebatesRedeemed(rebates -> {
        register.showMessage("Saved $" + rebates.getTotalRebateAmount());
        // Recalculate tax on the discounted subtotal if the jurisdiction requires it
        BigDecimal newTax = taxCalculator.compute(rebates.getUpdatedBasket());
        return rebates.getSuggestedTotal()
            .subtract(session.basket().snapshot().getTaxTotal())
            .add(newTax);
    })
    .onPointsRedeemed(points -> {
        register.showMessage(points.getPointsUsed() + " points applied");
        return points.getSuggestedTotal();
    })
    .onSuccess(result -> {
        register.printReceipt(result.getMerchantReceipt());
        register.showMessage("Earned " + result.getTotalPointsEarned() + " points!");
        for (EarnedReward r : result.getEarnedRewards()) {
            register.showMessage("Earned: " + r.getDescription());
        }
    })
    .onError(error -> {
        register.showError(error.getMessage());
        return PaymentOptions.voidAndAbort();
    })
    .execute();

// --- 5. End the session (the terminal discards its session data) ---
session.end().execute();
```

### Variant: gift card split tender

```java
session.setStoredValueCard("6006491260550218157");
// or, for scanned/swiped cards and provider routing:
session.setStoredValueCard(StoredValueCard.scanned("6006491260550218157").withProvider("givex"));
session.pay()
    .onGiftCardPayment(gc -> {
        register.showMessage("Gift card: -$" + gc.getAmountCharged()
            + " (remaining: $" + gc.getRemainingCardBalance() + ")");
        return gc.getSuggestedTotal();   // remainder goes to the card payment
    })
    .onSuccess(result -> {
        register.showMessage(
            "GC: $" + result.getStoredValueAmountUsed() +
            " | Card: $" + result.getCardAmountCharged());
        register.printReceipt(result.getMerchantReceipt());
    })
    .execute();
```

When the gift card balance is insufficient, the charge is a partial authorization and the remainder flows to the card payment step.

### Variant: retry without loyalty

```java
session.pay()
    .onSuccess(result -> register.printReceipt(result.getMerchantReceipt()))
    .onError(error -> {
        if (error.getCode() == SessionErrorCode.LOYALTY_UNAVAILABLE) {
            register.showMessage("Loyalty unavailable, retrying payment only...");
            return PaymentOptions.retryWithoutLoyalty();
        }
        register.showError("Payment failed: " + error.getMessage());
        return PaymentOptions.voidAndAbort();
    })
    .execute();
```

---

## Identify a member

```java
session.identifyMember()                     // terminal prompts the customer
    .onSuccess(member -> {
        if (member.getStatus() == IdentifyStatus.FOUND) {
            register.showMember(member.getMemberId());
            register.showRewards(member.getRewards());
        }
        // NOT_FOUND / CANCELLED / SUSPENDED → guest checkout, no action needed
    })
    .onError(error -> register.showError(error.getMessage()))
    .execute();
```

Identification is optional — the flow works for guests. Outcomes that simply leave the checkout without a member (`NOT_FOUND`, `SUSPENDED`, `CANCELLED`) are delivered to `onSuccess` with the corresponding `IdentifyStatus`; `onError` fires only for real failures.

For a POS-driven lookup without a terminal prompt (identifier already on file):

```java
session.identifyMember(MemberIdentifier.phoneNumber("555-867-5309")).execute();
```

---

## Build the basket

The session owns the basket. Adding an item whose SKU is already present increments its quantity (upsert). With `autoDisplay` (default on), every change refreshes the customer display with an itemised virtual receipt.

```java
Basket basket = session.basket().addItem(BasketItem.of("KRK-CNDL-LRG-VAN", "Large Vanilla Candle", 2, "24.99"));
register.setTotal(basket.getGrandTotal());   // 49.98

session.basket().addItem(BasketItem.of("KRK-FRAME-5X7-BLK", "5x7 Black Frame", 1, "14.99"));

// Tax — item-level rate, item-level fixed amount, or basket-level override
session.basket().setTaxRateBySku("KRK-CNDL-LRG-VAN", new BigDecimal("0.08875"));
session.basket().setTaxAmountBySku("KRK-FRAME-5X7-BLK", new BigDecimal("2.50"));
// session.basket().setTaxTotal(new BigDecimal("7.98"));   // overrides item-level computation

// Batch changes with a single display update
session.basket().mutate(m -> m
    .updateItemQuantityBySku("KRK-CNDL-LRG-VAN", 3)
    .removeItemBySku("KRK-FRAME-5X7-BLK"));
```

**Tax computation rules:** explicit item `taxAmount` wins; else item `taxRate` × `originalTotal`; else $0. `basket.taxTotal` is the sum of item amounts unless `setTaxTotal()` overrides it. `grandTotal = originalTotal + taxTotal`.

---

## Refund and void

`CheckoutSession` reverses the payment it took itself: `refund()` and `voidTransaction()` work on the session's completed payment, no references needed. Reversing a sale taken by an earlier, gone session is `ReversalSession`'s job — see [Reversing a prior sale](#reversing-a-prior-sale-reversalsession) below.

- `refund()` / `refund(amount)` — linked refunds of the completed payment; also reverse the loyalty award when one ran, best-effort by default. Repeated partial refunds against the same payment are allowed (the acquirer enforces the cumulative limit), but once a refund — linked or unlinked — has returned money, the payment can no longer be voided from that session: a void would return the full amount on top of the refund. Refunds cover the card leg + award only; the sale's committed rebate and redemption movements are reversed by `voidTransaction()`.
- `refundUnlinked(amount)` — payment-only, not tied to a prior transaction, no loyalty reversal.
- `voidTransaction()` — reverses every movement the completed payment committed, in order: card and stored value legs (nexo `ReversalRequest`), then the redemption, rebate, and award (their `LoyaltyRequest` refund types). A checkout fully covered by rewards has no money leg; voiding it refunds the loyalty movements alone. And when a failed payment's rollback was incomplete (the error names the movements still standing), `voidTransaction()` on that session finishes the unwind by retrying the reversals that did not go through.

Both return a `ReversalFlow` — lazy like every session operation (`execute()` / `get()` / `getOrNull()`). When a step fails, the `onError` handler decides how to proceed:

```java
session.voidTransaction()
    .onError((step, error) -> step == ReversalStep.AWARD
            ? ReversalDecision.SKIP      // leave it standing (terminal retries via SAF)
            : ReversalDecision.ABORT)    // stop; reversed legs stand, session restores
    .onSuccess(result -> register.printVoidReceipt(result))
    .execute();
```

`RETRY` re-sends the failed step, `SKIP` leaves the movement standing and continues, `ABORT` stops the flow (already-reversed steps always stand — there is no compensating re-commit; a later `voidTransaction()` resumes at the first movement still standing). Without a handler the default policy applies: money-leg failures abort; loyalty failures are skipped when a money leg anchors the void, and abort when the loyalty movements are the substance of the void.

---

## Reversing a prior sale (ReversalSession)

Every movement of a completed sale can be reversed from a fresh process — days later, long after the checkout session is gone — by persisting the references from the sale's `CheckoutResult` and supplying them to a `ReversalSession`:

| Movement | Persist from `CheckoutResult` | `ReversalSession.Builder` fields |
|---|---|---|
| Card payment | `getPoiTransactionId()` / `getPoiTransactionTimestamp()` | `poiTransactionId` / `poiTransactionTimestamp` |
| Stored value (gift card) leg | `getStoredValuePoiTransactionId()` / `getStoredValuePoiTransactionTimestamp()` | `storedValuePoiTransactionId` / `storedValuePoiTransactionTimestamp` |
| Rebate (coupons) | `getRebatePoiTransactionId()` / `getRebatePoiTransactionTimestamp()` | `rebatePoiTransactionId` / `rebatePoiTransactionTimestamp` |
| Redemption (points) | `getRedemptionPoiTransactionId()` / `getRedemptionPoiTransactionTimestamp()` | `redemptionPoiTransactionId` / `redemptionPoiTransactionTimestamp` |
| Award | `getAwardPoiTransactionId()` / `getAwardPoiTransactionTimestamp()` | `awardPoiTransactionId` / `awardPoiTransactionTimestamp` |
| Member | `IdentifyResult.getMemberId()` (or POS records) | `memberId` |

```java
try (ReversalSession session = ReversalSession.builder()
        .client(client)
        .saleId("POS-LANE-3")
        .poiId("VictaLane-275839164")
        .currency("USD")
        .poiTransactionId(stored.cardTxnId).poiTransactionTimestamp(stored.cardTs)
        .storedValuePoiTransactionId(stored.giftCardTxnId)
        .storedValuePoiTransactionTimestamp(stored.giftCardTs)
        .rebatePoiTransactionId(stored.rebateTxnId).rebatePoiTransactionTimestamp(stored.rebateTs)
        .redemptionPoiTransactionId(stored.redemptionTxnId)
        .redemptionPoiTransactionTimestamp(stored.redemptionTs)
        .awardPoiTransactionId(stored.awardTxnId).awardPoiTransactionTimestamp(stored.awardTs)
        .memberId(stored.memberId)
        .start()
        .get()) {
    session.voidTransaction().execute();   // card, gift card, redemption, rebate, award
}
```

A reversal session is bracketed on the terminal like a checkout (session start/end signals; it is `AutoCloseable`), carries the reversal operations — `voidTransaction()`, `refund()`, `refund(amount)`, `refundBasket()` — and requires at least one transaction reference at `start()`. Only the movements you supply references for are reversed: a sale with no card leg (rewards covered everything) is voided by its loyalty references alone, and the loyalty refunds are then strict rather than best-effort. The award is reversed only by its own reference; if `awardPoiTransactionId` was not persisted, no award reversal is sent. `refund()` requires the card reference and reverses the card leg + award only. The same `ReversalFlow` decision handling applies as on `CheckoutSession`.

### Item-based refunds (the refund cart)

To refund specific items of a prior sale, ring the returns into the session's refund cart — the same `SessionBasket` API as a checkout basket, except every line is a **credit line**: totals are negative, and each mutation refreshes the customer display with the itemised returns at negative amounts (`autoDisplay`, on by default; `externalDisplayClient` and `displayRenderer` route and shape it as on a checkout). `refundBasket()` then refunds the cart total against the referenced card leg, attaching the returned items to the refund's `PaymentRequest`:

```java
try (ReversalSession session = ReversalSession.builder()
        .client(client)
        .saleId("POS-LANE-3")
        .poiId("VictaLane-275839164")
        .currency("USD")
        .poiTransactionId(stored.cardTxnId).poiTransactionTimestamp(stored.cardTs)
        .start()
        .get()) {
    session.basket().addItem(
            BasketItem.of("KRK-CNDL-LRG-VAN", "Large Vanilla Candle", 1, "24.99"));
    session.basket().setTaxRateBySku("KRK-CNDL-LRG-VAN", new BigDecimal("0.08875"));
    // customer display now shows: Large Vanilla Candle −$24.99, Total −$27.21

    RefundResult result = session.refundBasket().get();   // refunds 27.21, items attached
}
```

The cart is free-form — which items may be returned, and in what quantities, is the register's decision (the acquirer enforces the cumulative refund limit). The cart is consumed (and the display cleared) the moment the tender refund moves money, even if a later award reversal step aborts the flow; while no money has moved — a failed or `onError`-skipped tender — it stays intact for a retry. Ring further returns for additional partial refunds.

**Credit lines on a sale.** The same mechanism marks a return or trade-in rung into a *checkout* basket: `BasketItem.credit(sku, desc, qty, price)` (or `.credit(true)` on the builder) makes the line subtract from the sale's totals and display negative. Quantities and unit prices stay positive — the direction carries the sign — and a credit line never upserts into a sale line of the same SKU: the basket shows both, like a paper receipt. `pay()` still requires a positive grand total.

---

## Stored value card operations

Beyond split-tender payment, the session covers the full gift card lifecycle (see the wire-level guides: [activate](./gift-card-activate.md), [load](./gift-card-load.md), [deactivate](./gift-card-deactivate.md), [balance](./gift-card-balance-inquiry.md)):

```java
StoredValueCard card = StoredValueCard.scanned("6006491260550218157").withProvider("givex");

session.storedValueBalance(card).onSuccess(b -> register.show(b.getBalance())).execute();
session.storedValueActivate(card, new BigDecimal("25.00")).execute();  // ZERO activates empty
session.storedValueLoad(card, new BigDecimal("10.00")).execute();
session.storedValueUnload(card, new BigDecimal("5.00")).execute();     // cash-out
session.storedValueDeactivate(card).execute();                         // permanent

// Reverse a prior stored value operation by its terminal reference
session.storedValueReverse(result.getPoiTransactionId(), result.getPoiTransactionTimestamp()).execute();
```

`storedValueReserve(...)` and `storedValueDuplicate(...)` complete the nexo verb set; provider support for reserve, duplicate, and deactivate varies — confirm with your stored value provider.

---

## Customer input and display

The session wraps the nexo input operations (see [Collect input](./input-request.md) for the underlying messages):

```java
session.requestDigitString("Enter your zip code").onSuccess(zip -> ...).execute();
session.requestDecimalString("Enter tip amount").onSuccess(tip -> ...).execute();
session.requestConfirmation("Print receipt?", ConfirmationOptions.withButtons("Print", "No thanks")).execute();
session.requestMenuEntry("Select tip", List.of("15%", "18%", "20%", "No tip")).execute();
session.requestSignature("Please sign below").onSuccess(sig -> ...).execute();
session.requestAmountConfirmation(basket.getGrandTotal(), "Confirm total").execute();
session.requestPinEntry(PinOptions.builder().timeout(Duration.ofSeconds(30)).build()).execute();
```

`updateDisplay(basket)` refreshes the itemised receipt manually; `updateDisplay(payload)` sends a custom [display payload](./display-helpers.md). Display is best-effort and never interrupts a checkout.

While an input prompt is awaiting a response, `updateInputDisplay(payload)` — safe from another thread, like `abort()` — replaces its display content (nexo `InputUpdate`). The device operations that used to sit alongside it — sound, printing, diagnostics, totals — live on [`Terminal`](#terminal-device--admin-operations-without-a-session), one call away via `session.terminal()`.

---

## External display

Setting an external display client routes all display/input calls (`updateDisplay`, `requestConfirmation`, etc.) to a separate screen driven by its own client, while payment, card read, and PIN entry still go to the terminal. The external display client runs on the machine the display is attached to (the register or another device).

```java
CheckoutSession session = CheckoutSession.builder()
    .client(client)
    .saleId("POS-LANE-3")
    .poiId("VictaLane-275839164")
    .externalDisplayClient(externalDisplayClient)
    .currency("USD")
    .storeLocation("STR-0142")
    .start()
    .get();

session.updateDisplay(basket);            // goes to the external display
session.updateDisplay(promotionalPayload);
```

---

## Terminal (device & admin operations without a session)

Diagnostics, totals, reconciliation, printing, and sound are SERVICE/DEVICE-class nexo messages that carry no session reference on the wire, so they don't need — or belong to — a session. They live on `Terminal`, which has no bracket: `build()` sends nothing, and `close()` sends nothing (it only stops the object accepting operations).

```java
Terminal terminal = Terminal.builder()
    .client(client)
    .saleId("POS-LANE-3")
    .poiId("VictaLane-275839164")
    .storeLocation("STR-0142")     // optional; the TotalsGroupID getTotals() filters by
    .callbackExecutor(uiExecutor)  // optional; same semantics as the session builders
    .build();                      // sends nothing

terminal.diagnose().onSuccess(d -> register.showStatus(d.getPoiStatus())).execute();
terminal.getTotals().onSuccess(t -> register.showTotals(t)).execute();      // running totals
terminal.reconcile().execute();                                             // closes the period
terminal.print(PrintPayload.text("THANK YOU")).execute();
terminal.playSound("chime-approved", 80).execute();
terminal.stopSound().execute();
```

Use it for a connectivity ping before the first checkout, end-of-day reconciliation with no customer present, or a receipt reprint after the session that took the payment has ended. Operations are lazy `SessionResult`s like everything else — nothing is sent until `execute()`/`executeSync()`/`get()`/`getOrNull()`.

Mid-checkout, `session.terminal()` (on `CheckoutSession` and `ReversalSession` alike) returns a cached `Terminal` built from the session's client, identifiers, and callback executor. It is deliberately independent of the session: it has its own operation thread and exchange, so its operations don't queue behind an in-flight payment (a connectivity check mid-payment works), they keep working after `end()`, and `session.abort()` never targets them. Its `close()` likewise doesn't touch the session.

---

## Common entry points (cheat sheet)

Not the full API — just the methods you'll reach for most. Everything returning a `SessionResult` or `PaymentFlow` is lazy: finish the chain with `.execute()`, `.get()`, or `.getOrNull()`.

| Task | Call |
| --- | --- |
| Start a session | `CheckoutSession.builder()...start().get()` |
| End the session (terminal discards its data) | `session.end()` |
| Prompt customer to identify | `session.identifyMember()` |
| POS-driven member lookup (no prompt) | `session.identifyMember(identifier)` |
| Add / remove / update item | `session.basket().addItem(item)`, `.removeItemBySku(sku)`, `.updateItemQuantityBySku(sku, qty)` |
| Batch edits, one display update | `session.basket().mutate(m -> ...)` |
| Set tax | `session.basket().setTaxRateBySku(...)`, `.setTaxAmountBySku(...)`, `.setTaxTotal(...)` |
| Basket snapshot | `session.basket().snapshot()` |
| Register gift card for split tender | `session.setStoredValueCard(cardNumber)` |
| Gift card lifecycle | `storedValueBalance / Activate / Load / Unload / Deactivate / Reverse` |
| Read card without charging | `session.acquireCard()` |
| Pay | `session.pay()` → `.beforeStep / .onRebatesRedeemed / .onPointsRedeemed / .onGiftCardPayment / .onSuccess / .onError` → `.execute()` |
| Sync pay | `session.pay().get()` |
| Refund | `refund()`, `refund(amount)`, `refundUnlinked(amount)` |
| Item-based refund of a prior sale | `ReversalSession` → `basket().addItem(...)` → `refundBasket()` |
| Void a completed txn | `session.voidTransaction()` |
| Cancel in-progress op | `session.abort()` (safe from any thread) |
| Collect customer input | `requestConfirmation / requestDigitString / requestMenuEntry / ...` |
| Device & admin ops (no session needed) | `Terminal.builder()...build()` → `diagnose / getTotals / reconcile / print / playSound / stopSound` |
| Device & admin ops mid-checkout | `session.terminal().diagnose()` etc. |
| Drop to raw nexo | `session.getClient()` |

**Upsert:** `addItem` with a SKU already in the basket increments its quantity. Use `updateItemQuantityBySku` to set an absolute quantity.

---

## Next steps

- [Integration Guide](./integration.md) — the underlying client, certificates, and raw nexo messages.
- [Make a payment](./make-payment.md) — the wire-level payment exchange the session drives for you.
- [Identify a loyalty member](./loyalty-identify-member.md) — wire-level identification details.
- [Receipt helpers](./receipt-helpers.md) — working with the structured receipt data on results.
