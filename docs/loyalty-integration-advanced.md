---
---

# Loyalty integration: advanced flows

Everything the loyalty program offers beyond point redemption: coupons and offers (rebates), point awards, the reversal paths that unwind a broken sale, and register-side member lookup.

This page builds on the [minimal](./loyalty-integration-minimal.md) and [identified-member](./loyalty-integration-complete.md) flows — read those first. Each section below slots into that same session bracket.

---

## The full flow

```
Register                                            Terminal (POI)
   │ ── AdminRequest (BiltSession,Start) ─────────────> │  session scope opened
   │ ── DisplayRequest (virtual receipt) ─────────────> │  … per basket change …
   │ ── CardAcquisitionRequest (loyalty) ─────────────> │  member identified
   │                                                    │
   │ ── LoyaltyRequest (Rebate) ──────────────────────> │  coupons/offers committed
   │ <── LoyaltyResponse (Rebates) ──────────────────── │
   │ ── LoyaltyRequest (Redemption) ──────────────────> │  points redeemed
   │ <── LoyaltyResponse (monetary value) ───────────── │
   │                                                    │
   │ ── PaymentRequest (LoyaltyHandling=Processed) ───> │  card payment
   │ <── PaymentResponse ────────────────────────────── │
   │                                                    │
   │ ── LoyaltyRequest (Award) ───────────────────────> │  points earned
   │ <── LoyaltyResponse (points, new balance) ──────── │
   │ ── AdminRequest (BiltSession,End) ───────────────> │  session state discarded
```

Each `LoyaltyRequest` is an independent, individually-reversible transaction with its own `SaleTransactionID` and its own `POITransactionID`. Keep every `POITransactionID` until the sale is settled — they are the handles the reversal paths need.

```
Final amount = basket total − TotalRebate − per-item rebates − redeemed point value
```

---

## Rebates: coupons and offers

A `LoyaltyRequest` with `LoyaltyTransactionType` set to `Rebate`, sent **before** the point redemption. The request carries the basket (`TotalAmount` + `SaleItem[]`), and the terminal commits the coupons and offers that apply — either ones the customer selected on its screen, or ones the provider applies automatically.

The response returns the discount in `LoyaltyResult[0].Rebates`:

```json
"Rebates": {
  "TotalRebate": 5.00,
  "RebateLabel": "K-Club member offer",
  "SaleItemRebate": [
    { "ItemID": 1, "ProductCode": "KRK-CNDL-LRG-VAN", "ItemAmount": 3.00, "RebateLabel": "15% off candles" }
  ]
}
```

Subtract the total and per-item amounts, print the labels, and feed the reduced total into the redemption step. Full field reference: [Apply loyalty rebates](./loyalty-apply-rebates.md).

Rebates and redemption are separate transactions by design — the payment message pair can carry awards and rebates but never a redemption, so a flow that redeems points does **all** loyalty in standalone `LoyaltyRequest`s and marks the payment `LoyaltyHandling = Processed`.

---

## Awards: earning points

After the payment clears, submit the purchase for earning with a `LoyaltyRequest` of type `Award`, carrying the basket and the member's account. The provider computes the points from items, totals, and active campaigns, and returns the points earned plus the member's new balance — and possibly promotional messages to print on the receipt.

Send the award after the `PaymentResponse` and before session End. A failed award should not fail the sale — report it and retry out of band rather than reversing a completed payment.

Full field reference: [Award loyalty points](./loyalty-award-points.md).

---

## Reversals: unwinding a broken sale

Loyalty commits **before** payment, so a sale that dies at the payment step leaves committed loyalty transactions standing. Reverse everything committed so far, in the opposite order it was committed, using the stored `POITransactionID`s:

| Committed step | Reversal | Reference |
| --- | --- | --- |
| Redemption | `LoyaltyRequest` type `RedemptionRefund` | [Reverse a redemption](./loyalty-reverse-redemption.md) |
| Rebate | `LoyaltyRequest` type `RebateRefund` | [Reverse a rebate](./loyalty-reverse-rebate.md) |
| Award | `LoyaltyRequest` type `AwardRefund` | [Reverse a points award](./loyalty-reverse-award.md) |

Typical triggers:

- **Customer rejects the final amount** — reverse the redemption, then the rebate, and end the session.
- **Sale voided after completion** — reverse the payment itself ([reverse](./reverse-payment.md) / [refund](./refund-referenced.md)), then the award, the redemption, and the rebate.
- **Partial refund of a line item** — a [referenced refund](./refund-referenced.md) for the amount, plus an `AwardRefund` for the points that line earned.

Session End never discards what a reversal needs: reversals reference the original transactions by `POITransactionID`, which the register holds itself.

---

## Member lookup from the register

When the register already holds an identifier — a phone number given at the counter, an account number on file — it can resolve the member **without any terminal prompt** using a `BalanceInquiryRequest` (`MessageCategory` `BalanceInquiry`) with `IdentificationType` `PhoneNumber` or `AccountNumber`:

```json
"BalanceInquiryRequest": {
  "LoyaltyAccountReq": {
    "LoyaltyAccountID": {
      "EntryMode": "Keyed",
      "IdentificationType": "PhoneNumber",
      "LoyaltyID": "5558675309"
    }
  }
}
```

The response resolves the member and returns their point balance and active rewards; no points move. Use it to look up the member before checkout, or with `IdentificationType` `PAN` to fetch just the balance of an already-identified member.

Full field reference: [Query a loyalty balance](./loyalty-balance-inquiry.md). Related register-side operations: [enroll a new member](./loyalty-enroll-member.md), [check member status](./loyalty-member-status.md).

---

## Next steps

- [Loyalty integration: minimal](./loyalty-integration-minimal.md) — the five-exchange baseline.
- [Loyalty integration: with member identification](./loyalty-integration-complete.md) — card acquisition and member context at the register.
- [Session start and end](./session-start-end.md) — the bracket every flow above runs inside.
