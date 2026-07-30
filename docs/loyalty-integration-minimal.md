---
---

# Loyalty integration: minimal

The smallest loyalty-enabled checkout a register can run — five message exchanges, no member handling at all.

The register never identifies the member and never learns who they are. This works because the terminal lets the customer sign in on its own screen at any point during the checkout, and applies that identity to the loyalty request itself. The register sends the same messages for every checkout:

- If a member is signed in, the point redemption returns the monetary value of the points they chose to spend.
- If nobody signed in, the same redemption succeeds with a value of `0.00` — the register subtracts zero and proceeds as a guest checkout.

One code path, guests and members alike.

---

## The flow

```
Register                                            Terminal (POI)
   │ ── AdminRequest (BiltSession,Start) ─────────────> │  session scope opened
   │ <── AdminResponse (Success) ────────────────────── │
   │                                                    │
   │ ── DisplayRequest (virtual receipt) ─────────────> │  basket shown; customer
   │ <── DisplayResponse ────────────────────────────── │  may sign in meanwhile
   │        … repeat on every basket change …           │
   │                                                    │
   │ ── LoyaltyRequest (Redemption) ──────────────────> │  customer's points applied
   │ <── LoyaltyResponse (monetary value, or 0.00) ──── │
   │                                                    │
   │ ── PaymentRequest (final amount) ────────────────> │  card payment
   │ <── PaymentResponse ────────────────────────────── │
   │                                                    │
   │ ── AdminRequest (BiltSession,End) ───────────────> │  session state discarded
   │ <── AdminResponse ──────────────────────────────── │
```

---

## Step by step

### 1. Start the session

Open the session bracket with an `AdminRequest` carrying `BiltSession,Start,v1,<sessionId>` before anything else. The terminal opens a session scope — the container for everything it accumulates during this checkout, including the customer's sign-in. See [Session start and end](./session-start-end.md).

### 2. Show the basket

Send a [`DisplayRequest` with the virtual receipt](./display-receipt.md) on every basket change. Besides keeping the customer's screen current, this gives the terminal the basket context it uses for loyalty — and the customer can sign in on the terminal while items are being scanned.

### 3. Redeem points when ready to pay

When the cashier totals the sale, send a `LoyaltyRequest` with `LoyaltyTransactionType` set to `Redemption` — and leave out everything member-related:

- **No `LoyaltyAccountID`** — the terminal falls back to the account the customer signed in with.
- **No `LoyaltyAmount`** — the terminal applies the amount the customer selected on its screen.

```json
{
  "SaleToPOIRequest": {
    "MessageHeader": {
      "ProtocolVersion": "3.0",
      "MessageClass": "Service",
      "MessageCategory": "Loyalty",
      "MessageType": "Request",
      "ServiceID": "SVC-01210",
      "SaleID": "BiltPOS-Lane3",
      "POIID": "VictaLane-275839164"
    },
    "LoyaltyRequest": {
      "SaleData": {
        "SaleTransactionID": {
          "TransactionID": "TXN-20260430-00901",
          "TimeStamp": "2026-04-30T14:22:00+00:00"
        }
      },
      "LoyaltyTransaction": {
        "LoyaltyTransactionType": "Redemption"
      }
    }
  }
}
```

Read the redeemed value from the response and subtract it from the total:

- **`LoyaltyResult[0].LoyaltyAmount.AmountValue`** (with `LoyaltyUnit` `Monetary`) — the discount to apply. `0.00` when no member signed in or the member spent no points.
- **`POIData.POITransactionID`** — keep it; it is needed to [reverse the redemption](./loyalty-reverse-redemption.md) if the sale is abandoned after this point.

Full field reference: [Redeem loyalty points](./loyalty-redeem-points.md).

### 4. Take the payment

Send the [`PaymentRequest`](./make-payment.md) for the reduced amount, with `PaymentTransaction.TransactionConditions.LoyaltyHandling` set to `Processed` — loyalty was already handled by the standalone redemption, so the payment must not attempt any of its own.

```
Final amount = basket total − redeemed point value
```

If the customer rejects the final amount, [reverse the committed redemption](./loyalty-reverse-redemption.md) before closing out.

### 5. End the session

Close the bracket with `BiltSession,End,v1,<sessionId>` so the terminal discards its session-scoped state — including the customer's sign-in. Send it however the checkout concluded; a session left open is closed implicitly only when the next one starts. See [Session start and end](./session-start-end.md).

---

## What this flow gives up

The register never sees who the member is, cannot greet them or show their point balance, and no coupons/offers or point awards are involved. When you want any of that:

- [Loyalty integration: with member identification](./loyalty-integration-complete.md) — the register learns the member via card acquisition and applies their coupons/offers as rebates.
- [Loyalty integration: advanced flows](./loyalty-integration-advanced.md) — awards, reversals, and register-side member lookup.
