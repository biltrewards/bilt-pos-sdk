---
---

# Reverse a payment

Void a completed payment before the batch settles, using a `ReversalRequest`.

A reversal cancels a completed payment that has not yet been cleared by the acquirer. Because the funds were authorized but not yet settled, a reversal releases the hold on the shopper's account immediately and typically incurs fewer fees than a post-clearing refund.

Voiding a payment is an all or nothing operation, only the full amount can be reversed.

> A reversal must be made **before the batch settles**. Once the batch has cleared, use a [referenced refund](./refund-referenced.md) or [unreferenced refund](./refund-unreferenced.md) instead.

---

## Before you begin

You need the transaction identifier of the original payment. This is returned in the payment response as `POIData.POITransactionID.TransactionID`. Make sure your POS app stores this when a payment completes.

> Through the session API: `CheckoutSession.voidTransaction()` reverses the payment the session itself just took, and `CheckoutSession.voidTransaction(OriginalSaleRecord)` voids a prior sale. A void also reverses the sale's other movements when the original-sale record includes stored value, rebate, redemption, award, and member references from the original `SettlementResult`. See the [integration guide](./checkout-session-integration.md#reversing-a-prior-sale-originalsalerecord).

---

## Make a reversal request

A reversal uses a `ReversalRequest` body. Send a Terminal API request with the following `MessageHeader` fields:

- **`ProtocolVersion`** — `3.0`
- **`MessageClass`** — `Service`
- **`MessageCategory`** — `Reversal`
- **`MessageType`** — `Request`
- **`ServiceID`** — Unique ID for this request, 1–10 alphanumeric characters, unique within 48 hours per terminal.
- **`SaleID`** — Your POS system identifier.
- **`POIID`** — Target terminal ID, format: `[model]-[serial]`.

And the following `ReversalRequest` fields:

- **`OriginalPOITransaction.POITransactionID.TransactionID`** — The transaction identifier from the original payment response.
- **`OriginalPOITransaction.POITransactionID.TimeStamp`** — The timestamp from the original payment response.
- **`ReversalReason`** — `MerchantCancel` for a full reversal; `Malfunction` may apply in other cases depending on your use case.
- **`PaymentTransaction.AmountsReq.Currency`** — The transaction currency code (e.g. `USD`). Required for partial reversals.
- **`PaymentTransaction.AmountsReq.RequestedAmount`** — The amount to reverse. Omit for a full reversal. Must not exceed the original payment amount.

Example — reversal:

```json
{
  "SaleToPOIRequest": {
    "MessageHeader": {
      "ProtocolVersion": "3.0",
      "MessageClass": "Service",
      "MessageCategory": "Reversal",
      "MessageType": "Request",
      "ServiceID": "SVC-00851",
      "SaleID": "BiltPOS-Lane3",
      "POIID": "VictaLane-275839164"
    },
    "ReversalRequest": {
      "OriginalPOITransaction": {
        "POITransactionID": {
          "TransactionID": "1f8a2301-5c3d-49a5-bb17-b1c10dd74ed6",
          "TimeStamp": "2026-03-02T14:35:12+00:00"
        }
      },
      "ReversalReason": "MerchantCancel"
    }
  }
}
```

---

## Reversal response

The result is returned in the API response in a `ReversalResponse` body. The main result is in `ReversalResponse.Response.Result`.

### Successful reversal

When a reversal is accepted, your integration receives:

- **`ReversalResponse.Response.Result`** — `Success`.
- **`POIData.POITransactionID.TransactionID`** — the transaction identifier for this reversal.
- **`ReversalResponse.Response.AdditionalResponse`** *(gift cards only, optional)* — URL-encoded string containing `currentBalance`, the gift card's balance after the reversal (e.g. `currentBalance=100.00`). Only included when reversing a gift card payment and the stored value provider returns balance information.

Example response:

```json
{
  "SaleToPOIResponse": {
    "MessageHeader": {
      "ProtocolVersion": "3.0",
      "MessageClass": "Service",
      "MessageCategory": "Reversal",
      "MessageType": "Response",
      "ServiceID": "SVC-00851",
      "SaleID": "BiltPOS-Lane3",
      "POIID": "VictaLane-275839164"
    },
    "ReversalResponse": {
      "Response": {
        "Result": "Success"
      },
      "POIData": {
        "POITransactionID": {
          "TransactionID": "a816b0a9-8a11-4dc0-ba9d-5ad1e8c7e0d6",
          "TimeStamp": "2026-03-02T15:10:08+00:00"
        }
      }
    }
  }
}
```

Example response — gift card reversal (includes balance):

```json
{
  "SaleToPOIResponse": {
    "MessageHeader": {
      "ProtocolVersion": "3.0",
      "MessageClass": "Service",
      "MessageCategory": "Reversal",
      "MessageType": "Response",
      "ServiceID": "SVC-00852",
      "SaleID": "BiltPOS-Lane3",
      "POIID": "VictaLane-275839164"
    },
    "ReversalResponse": {
      "Response": {
        "Result": "Success",
        "AdditionalResponse": "currentBalance=100.00"
      },
      "POIData": {
        "POITransactionID": {
          "TransactionID": "b927c1ba-9b22-5ed1-ca9e-6be2f9d8f1e7",
          "TimeStamp": "2026-03-02T15:12:14+00:00"
        }
      }
    }
  }
}
```

> **Note:** The `currentBalance` field is only included when reversing a gift card payment. For standard card reversals, `AdditionalResponse` is not included.

### Failed reversal

When a reversal fails, the result includes:

- **`ReversalResponse.Response.Result`** — `Failure`.
- **`ReversalResponse.Response.ErrorCondition`** — the reason for failure. For example, `NotAllowed` if the batch has already settled.

For general guidance on handling failed requests, see [Handle responses](./error-scenarios.md).

---

## Next steps

- [Cancel, reverse, or refund a payment](./undo-payment.md) — overview of all options for undoing a payment.
- [Cancel a payment](./cancel-payment.md) — abort an in-progress payment before it completes.
- [Referenced refund](./refund-referenced.md) — post-clearing refund linked to the original payment.
- [Unreferenced refund](./refund-unreferenced.md) — refund to any card without linking to the original payment.
- [Verify payment status](./verify-transaction-status.md) — check the status of a transaction when you don't receive a result.
