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

- **The session owns the basket.** Items, tax, and totals live in one place, and `Basket` is the single source of truth. Every mutation returns the updated basket.
- **nexo underneath.** Every session operation maps to a standard nexo 3.0 message, but the SDK hides much more than message serialization: it manages the complexity of communicating with the terminal, and it orchestrates payment when the transaction is multi-tender — sequencing rebates, point redemption, stored value, and card, and handling loyalty award and reversal — so the register doesn't have to coordinate any of it.
- **Terminal operations are lazy.** Methods returning a `SessionResult` or `PaymentFlow` send nothing until you call `.execute()`, `.get()`, or `.getOrNull()`. Register handlers first, then execute — a chain without a terminal method never reaches the terminal. See [lazy execution](#lazy-execution).
- **Cart-building is local + auto-display.** `addItem` / `removeItem` / `updateItemQuantity` update the local basket and (with `autoDisplay=true`, the default) push a `DisplayRequest` to the terminal. The terminal may independently evaluate offers while items are scanned, but those offers are **only committed during `pay()`**.
- **`pay()` is a fixed orchestration sequence,** with a blocking callback after each loyalty/stored-value step so the register can update its own model and recompute tax, then return the total that feeds the next step. The shape is fixed, but steps are conditional: loyalty (rebates + points) runs only for identified members and can be disabled via `PaymentOptions`, the stored-value step only runs when a gift card has been registered with `setStoredValueCard`, and card payment runs whenever an amount remains.
- **Errors and aborts roll back cleanly.** If a step fails or `abort()` is called mid-sequence, everything already committed (rebates, points, stored value) is reversed in the opposite order before the session moves to `FAILED` / `ABORTED`.

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

**Abort / error rollback.** If `abort()` fires mid-sequence (e.g. after rebates committed but before card payment), the session reverses everything committed so far — rebate refunds, redemption refunds, stored-value reversals — before moving to `ABORTED`. `abort()` is safe to call from any thread. On error the `PaymentOptions` returned by `onError` decides what happens next:

- `PaymentOptions.voidAndAbort()` (the default) — roll back committed steps in reverse order and fail; the session moves to `FAILED`, from which `pay()` can be retried.
- `PaymentOptions.retryWithoutLoyalty()` — roll back the loyalty steps and restart the sequence with rebates and points disabled.
- Any other options — full rollback, then restart with those options. At most 3 recoveries per execution.

**A failed award never reverses a completed payment:** the checkout completes with the failure reported in `CheckoutResult.getWarnings()`, and the terminal retries the award via Store-and-Forward.

---

## Session state machine

```
IDLE → IDENTIFIED → ACTIVE → PAYING → COMPLETED
                       ↑         ↓        ↓
                       └────── FAILED → VOIDING → VOIDED
abort() → ABORTED (from any non-terminal state)
```

Notes:

- `identifyMember()` can be called from `IDLE` or `ACTIVE`. Called from `ACTIVE`, the terminal re-evaluates offers with member context. Identification is optional — the customer can identify themselves on the terminal, and they can always opt out.
- Removing all items from `ACTIVE` returns to `IDLE` (or `IDENTIFIED` if a member was explicitly identified).
- From `FAILED` the register can retry `pay()` directly, keep editing the basket (back to `ACTIVE`), or `voidTransaction()`.
- `voidTransaction()` runs from `COMPLETED` (or `FAILED`); if the void itself fails, the session is restored to its pre-void state so it can be retried.
- `COMPLETED`, `VOIDED`, and `ABORTED` are terminal. `getState()` reports the current `SessionState`; the basket is frozen while `PAYING`.

---

## Create a session

```java
CheckoutSession session = CheckoutSession.builder()
    .client(client)                       // required
    .saleId("POS-LANE-3")                 // required — your POS identifier (SaleID)
    .poiId("VictaLane-275839164")         // required — target terminal (POIID)
    .currency("USD")                      // required
    .storeLocation("STR-0142")            // optional — sent as SaleTerminalData.TotalsGroupID
    .build();
```

A session represents one checkout. Create a new session per transaction; sessions are intended for use from a single register thread (`abort()` may be called from any thread).

### Lazy execution

Every terminal operation is **lazy**: methods returning a `SessionResult` or `PaymentFlow` send nothing until you invoke one of the terminal methods:

- `execute()` — run and deliver the outcome to the registered `onSuccess`/`onError` handlers;
- `get()` — run and return the value, throwing `SessionException` on failure;
- `getOrNull()` — like `get()`, but returns `null` on failure.

Always end a fluent chain with one of these — a chain without them never reaches the terminal:

```java
session.requestConfirmation("Would you like a receipt?")
    .onSuccess(confirmed -> { if (confirmed) register.printReceipt(); })
    .onError(e -> register.showError(e.getMessage()))
    .execute();
```

---

## Quick start (minimal)

```java
CheckoutSession session = CheckoutSession.builder()
    .client(client)
    .saleId("POS-LANE-3")
    .poiId("VictaLane-275839164")
    .currency("USD")
    .storeLocation("STR-0142")
    .build();

session.addItem(BasketItem.of("KRK-CNDL-LRG-VAN", "Large Vanilla Candle", 1, "24.99"));

session.pay()
    .onSuccess(result -> register.printReceipt(result.getMerchantReceipt()))
    .onError(error -> {
        register.showError(error.getMessage());
        return PaymentOptions.voidAndAbort();
    })
    .execute();
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
    .build();

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
Basket basket = session.addItem(BasketItem.of("KRK-CNDL-LRG-VAN", "Large Vanilla Candle", 2, "24.99"));
register.setTotal(basket.getGrandTotal());  // $49.98

basket = session.addItem(BasketItem.of("KRK-FRAME-5X7-BLK", "5x7 Black Frame", 1, "14.99"));
register.setTotal(basket.getGrandTotal());  // $64.97

// Scan same candle again — upserts, now qty 3
basket = session.addItem(BasketItem.of("KRK-CNDL-LRG-VAN", "Large Vanilla Candle", 1, "24.99"));
register.setTotal(basket.getGrandTotal());  // $89.96

// --- 3. Tax ---
session.setTaxRateBySku("KRK-CNDL-LRG-VAN", new BigDecimal("0.08875"));
session.setTaxRateBySku("KRK-FRAME-5X7-BLK", new BigDecimal("0.08875"));
// Or: session.setTaxTotal(new BigDecimal("7.98"));

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
            .subtract(session.getBasket().getTaxTotal())
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
Basket basket = session.addItem(BasketItem.of("KRK-CNDL-LRG-VAN", "Large Vanilla Candle", 2, "24.99"));
register.setTotal(basket.getGrandTotal());   // 49.98

session.addItem(BasketItem.of("KRK-FRAME-5X7-BLK", "5x7 Black Frame", 1, "14.99"));

// Tax — item-level rate, item-level fixed amount, or basket-level override
session.setTaxRateBySku("KRK-CNDL-LRG-VAN", new BigDecimal("0.08875"));
session.setTaxAmountBySku("KRK-FRAME-5X7-BLK", new BigDecimal("2.50"));
// session.setTaxTotal(new BigDecimal("7.98"));   // overrides item-level computation

// Batch changes with a single display update
session.mutate(m -> m
    .updateItemQuantityBySku("KRK-CNDL-LRG-VAN", 3)
    .removeItemBySku("KRK-FRAME-5X7-BLK"));
```

**Tax computation rules:** explicit item `taxAmount` wins; else item `taxRate` × `originalTotal`; else $0. `basket.taxTotal` is the sum of item amounts unless `setTaxTotal()` overrides it. `grandTotal = originalTotal + taxTotal`.

---

## Refund and void

Linked refunds require `poiTransactionId` / `poiTransactionTimestamp` on the builder (or a payment completed in the same session) and also reverse loyalty points awarded on the original transaction (best-effort). Unlinked refunds are payment-only — no loyalty reversal.

```java
CheckoutSession refundSession = CheckoutSession.builder()
    .client(client)
    .saleId("POS-LANE-3")
    .poiId("VictaLane-275839164")
    .currency("USD")
    .poiTransactionId("POI-TXN-0099")                              // from the original payment
    .poiTransactionTimestamp(Instant.parse("2026-04-30T14:15:05Z"))
    .build();

refundSession.refund(new BigDecimal("24.99"))     // partial linked refund
    .onSuccess(result -> {
        register.printRefundReceipt(result);
        if (result.getPointsReversed() > 0) {
            register.showMessage(result.getPointsReversed() + " points reversed");
        }
    })
    .onError(error -> register.showError(error.getMessage()))
    .execute();
```

- `refund()` / `refund(amount)` — linked refunds; also reverse loyalty points awarded on the original transaction (best-effort).
- `refundUnlinked(amount)` — payment-only, no loyalty reversal.
- `voidTransaction()` — reverses a completed transaction (nexo `ReversalRequest` + loyalty award reversal). On a session that just completed a payment, the transaction reference is remembered — no builder fields needed. A checkout fully covered by rewards has no payment to reverse; voiding it refunds the committed loyalty movements (redemption, rebate, award) instead.

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

While an input prompt is awaiting a response, `updateInputDisplay(payload)` — safe from another thread, like `abort()` — replaces its display content (nexo `InputUpdate`). `playSound("chime-approved", 80)` / `stopSound()` drive the terminal speaker, and `getTotals()` returns the running totals since the last reconciliation without closing the period.

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
    .build();

session.updateDisplay(basket);            // goes to the external display
session.updateDisplay(promotionalPayload);
```

---

## Common entry points (cheat sheet)

Not the full API — just the methods you'll reach for most. Everything returning a `SessionResult` or `PaymentFlow` is lazy: finish the chain with `.execute()`, `.get()`, or `.getOrNull()`.

| Task | Call |
| --- | --- |
| Build a session | `CheckoutSession.builder()...build()` |
| Prompt customer to identify | `session.identifyMember()` |
| POS-driven member lookup (no prompt) | `session.identifyMember(identifier)` |
| Add / remove / update item | `addItem(item)`, `removeItemBySku(sku)`, `updateItemQuantityBySku(sku, qty)` |
| Batch edits, one display update | `session.mutate(m -> ...)` |
| Set tax | `setTaxRateBySku(...)`, `setTaxAmountBySku(...)`, `setTaxTotal(...)` |
| Register gift card for split tender | `session.setStoredValueCard(cardNumber)` |
| Gift card lifecycle | `storedValueBalance / Activate / Load / Unload / Deactivate / Reverse` |
| Read card without charging | `session.acquireCard()` |
| Pay | `session.pay()` → `.beforeStep / .onRebatesRedeemed / .onPointsRedeemed / .onGiftCardPayment / .onSuccess / .onError` → `.execute()` |
| Sync pay | `session.pay().get()` |
| Refund | `refund()`, `refund(amount)`, `refundUnlinked(amount)` |
| Void a completed txn | `session.voidTransaction()` |
| Cancel in-progress op | `session.abort()` (safe from any thread) |
| Collect customer input | `requestConfirmation / requestDigitString / requestMenuEntry / ...` |
| Drop to raw nexo | `session.getClient()` |

**Upsert:** `addItem` with a SKU already in the basket increments its quantity. Use `updateItemQuantityBySku` to set an absolute quantity.

---

## Next steps

- [Integration Guide](./integration.md) — the underlying client, certificates, and raw nexo messages.
- [Make a payment](./make-payment.md) — the wire-level payment exchange the session drives for you.
- [Identify a loyalty member](./loyalty-identify-member.md) — wire-level identification details.
- [Receipt helpers](./receipt-helpers.md) — working with the structured receipt data on results.
