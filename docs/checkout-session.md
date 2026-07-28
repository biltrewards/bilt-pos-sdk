---
---

# CheckoutSession — high-level checkout API

Drive a full loyalty-enabled checkout — member identification, basket management, terminal display, split-tender payment, and reward award — through a single stateful session instead of hand-building nexo messages.

`CheckoutSession` sits on top of [`BiltNexoTerminalClient`](./integration.md). Every operation maps to standard nexo 3.0 messages, and the raw client stays available via `session.getClient()` for anything the session does not cover.

This page is the method-by-method walkthrough. For the concepts, the end-to-end flow, and a complete register integration, start with the [CheckoutSession Integration Guide](./checkout-session-integration.md).

---

## Before you begin

Make sure you have:

1. Ordered a terminal and boarded it to a store.
2. Read and understood the [Integration Guide](./integration.md).
3. Created a `BiltNexoTerminalClient` for your terminal.

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

---

## Pay

`pay()` returns a `PaymentFlow` — a chainable builder that runs the payment sequence when executed: **rebate redemption → point redemption → stored value → card payment → award**. Each step handler receives that step's result and returns the updated total for the next step, so the register can recompute tax on discounted amounts. Handlers are synchronous and run on the calling thread.

```java
session.pay()
    .onRebatesRedeemed(rebates -> {
        register.showMessage("Saved $" + rebates.getTotalRebateAmount());
        BigDecimal newTax = taxCalculator.compute(rebates.getUpdatedBasket());
        return rebates.getSuggestedTotal()
            .subtract(session.getBasket().getTaxTotal())
            .add(newTax);
    })
    .onPointsRedeemed(points -> points.getSuggestedTotal())
    .onSuccess(result -> {
        register.printReceipt(result.getMerchantReceipt());
        register.showPointsEarned(result.getTotalPointsEarned());
    })
    .onError(error -> {
        register.showError(error.getMessage());
        return PaymentOptions.voidAndAbort();
    })
    .execute();
```

Unregistered handlers default to "accept and subtract" (`suggestedTotal`). Steps that do not apply are skipped automatically: loyalty steps run only for identified members, the stored value step only when a card was registered, and the card step only when a balance remains.

### Split tender with a gift card

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
    .onSuccess(result -> register.printReceipt(result.getMerchantReceipt()))
    .execute();
```

When the gift card balance is insufficient, the charge is a partial authorization and the remainder flows to the card payment step.

### Error handling and recovery

If any step fails, the committed steps are **reversed in reverse order** (redemption refund, rebate refund, gift card reversal) and your `onError` handler decides what happens next via the `PaymentOptions` it returns:

```java
session.pay()
    .onError(error -> {
        if (error.getCode() == SessionErrorCode.LOYALTY_UNAVAILABLE) {
            register.showMessage("Loyalty unavailable, retrying payment only...");
            return PaymentOptions.retryWithoutLoyalty();
        }
        register.showError(error.getMessage());
        return PaymentOptions.voidAndAbort();
    })
    .onSuccess(result -> register.printReceipt(result.getMerchantReceipt()))
    .execute();
```

- `PaymentOptions.voidAndAbort()` (the default) — fail the payment; the session moves to `FAILED`, from which `pay()` can be retried.
- Any other options (e.g. `retryWithoutLoyalty()`) — restart the sequence with those options. At most 3 recoveries per execution.
- A **failed award never reverses a completed payment**: the checkout completes with the failure reported in `CheckoutResult.getWarnings()`; the terminal retries the award via store-and-forward.

`session.abort()` — safe from any thread — cancels the in-progress operation, reverses everything committed so far, and moves the session to `ABORTED`.

---

## Refund and void

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
- `voidTransaction()` — reverses a completed transaction (nexo `ReversalRequest` + loyalty award reversal). On a session that just completed a payment, the transaction reference is remembered — no builder fields needed.

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

### External display

To drive a separate customer display device, give the session a second client. Display and input traffic is routed to it, while payments, card reads, and PIN entry stay on the terminal:

```java
CheckoutSession session = CheckoutSession.builder()
    .client(terminalClient)
    .externalDisplayClient(BiltNexoTerminalClient.builder()
        .endpoint("https://192.168.1.50:8443/nexo")
        .securityKey(key)
        .build())
    ...
    .build();
```

---

## Session lifecycle

```
IDLE → IDENTIFIED → ACTIVE → PAYING → COMPLETED
                       ↑         ↓        ↓
                       └────── FAILED → VOIDING → VOIDED
abort() → ABORTED (from any non-terminal state)
```

`getState()` reports the current `SessionState`. The basket is frozen while `PAYING`; emptying the basket returns the session to `IDLE` (or `IDENTIFIED`).

---

## Next steps

- [CheckoutSession Integration Guide](./checkout-session-integration.md) — concepts, payment sequence, state machine, and a full register integration example.
- [Integration Guide](./integration.md) — the underlying client and raw nexo messages.
- [Make a payment](./make-payment.md) — the wire-level payment exchange the session drives for you.
- [Identify a loyalty member](./loyalty-identify-member.md) — wire-level identification details.
- [Receipt helpers](./receipt-helpers.md) — working with the structured receipt data on results.
