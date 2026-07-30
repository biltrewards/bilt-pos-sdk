---
---

# Loyalty integration: with member identification

The [minimal flow](./loyalty-integration-minimal.md) plus one exchange: a card acquisition that identifies the member to the **register**, not just the terminal.

In the minimal flow the customer may sign in on the terminal, but the register never knows. Adding a `CardAcquisitionRequest` before payment does two things:

- **It nudges the customer.** If nobody is signed in yet, the terminal prompts them to identify — scan the app, swipe a loyalty card, or key a phone number.
- **It tells the register who they are.** If the customer is already signed in (or identifies at the prompt), the response carries their loyalty account, so the register can greet the member, show rewards on its own screen, and record the membership on the receipt.

The rest of the flow is unchanged; the redemption still works even if the customer declines to identify.

---

## The flow

```
Register                                            Terminal (POI)
   │ ── AdminRequest (BiltSession,Start) ─────────────> │  session scope opened
   │ <── AdminResponse (Success) ────────────────────── │
   │                                                    │
   │ ── DisplayRequest (virtual receipt) ─────────────> │  basket shown
   │ <── DisplayResponse ────────────────────────────── │
   │        … repeat on every basket change …           │
   │                                                    │
   │ ── CardAcquisitionRequest (loyalty) ─────────────> │  prompts sign-in, or returns
   │ <── CardAcquisitionResponse (LoyaltyAccount) ───── │  the already-signed-in member
   │                                                    │
   │ ── LoyaltyRequest (Redemption) ──────────────────> │  customer's points applied
   │ <── LoyaltyResponse (monetary value) ───────────── │
   │                                                    │
   │ ── PaymentRequest (final amount) ────────────────> │  card payment
   │ <── PaymentResponse ────────────────────────────── │
   │                                                    │
   │ ── AdminRequest (BiltSession,End) ───────────────> │  session state discarded
   │ <── AdminResponse ──────────────────────────────── │
```

---

## Step by step

### 1–2. Start the session, show the basket

Identical to the minimal flow: open the bracket with [session Start](./session-start-end.md), and keep the customer display current with [`DisplayRequest`s](./display-receipt.md) as items are scanned.

### 3. Identify the member

Send a `CardAcquisitionRequest` with loyalty handling enabled — typically once the basket is rung up, before totals. If the customer is already signed in on the terminal, it returns their account without a prompt; otherwise the terminal asks them to identify.

Key request choices:

- **`CardAcquisitionTransaction.LoyaltyHandling`** — `Proposed` to let the flow continue without a member, or `Required` to fail when no member is found.
- **`CardAcquisitionTransaction.TotalAmount`** *(optional)* — the running total, for context on the prompt.
- **`SaleData.SaleTransactionID`** — your reference for the acquisition; follow-up loyalty requests can reference it via `CardAcquisitionReference`.

The response returns the member in `CardAcquisitionResponse.LoyaltyAccount[]` — `LoyaltyID`, how it was captured (`EntryMode`, `IdentificationType`), and the program brand. Use it to greet the member and show loyalty state on the register. A customer who declines simply yields no `LoyaltyAccount`; continue as the minimal flow would.

Full field reference: [Identify a loyalty member](./loyalty-identify-member.md) (the loyalty-focused variant) and [Acquire card data](./card-acquisition-request.md) (the general message). To also show the member's point balance at the register, follow up with a [balance inquiry](./loyalty-balance-inquiry.md).

### 4. Redeem points

Same `LoyaltyRequest` with `LoyaltyTransactionType` `Redemption` as the minimal flow, and still terminal-driven — omit `LoyaltyAmount` and the terminal applies the amount the customer selected on its screen.

The one difference: the register now holds the member's account, so it **may** carry it in the request — either the acquired `LoyaltyID` in `LoyaltyData[].LoyaltyAccountID`, or a `LoyaltyData[].CardAcquisitionReference` pointing at step 3's acquisition:

```json
"LoyaltyData": [
  {
    "LoyaltyAccountID": {
      "EntryMode": "Scanned",
      "IdentificationType": "PAN",
      "LoyaltyID": "98234"
    }
  }
]
```

This is optional — the terminal still holds the identification from the acquisition and falls back to it when the field is omitted. Sending it makes the register's intent explicit and pins the redemption to the acquired account.

Read `LoyaltyResult[0].LoyaltyAmount` and keep `POITransactionID` exactly as in the minimal flow. Full field reference: [Redeem loyalty points](./loyalty-redeem-points.md).

### 5–6. Pay and end the session

Unchanged from the minimal flow: send the [`PaymentRequest`](./make-payment.md) for the reduced amount with `PaymentTransaction.TransactionConditions.LoyaltyHandling` set to `Processed`, then close the bracket with [session End](./session-start-end.md). If the customer rejects the final amount, [reverse the redemption](./loyalty-reverse-redemption.md) first.

---

## Next steps

- [Loyalty integration: advanced flows](./loyalty-integration-advanced.md) — add rebates, point awards, reversals, and register-side member lookup.
- [Enroll a loyalty member](./loyalty-enroll-member.md) — sign up a customer who has no account, as a fallback when identification finds no match.
- [Member status](./loyalty-member-status.md) — check a member's standing without a terminal prompt.
