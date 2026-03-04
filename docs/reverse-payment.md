# Reverse a payment

Void a completed payment before the batch settles, using a `ReversalRequest`.

A reversal cancels a completed payment that has not yet been cleared by the acquirer. Because the funds were authorized but not yet settled, a reversal releases the hold on the shopper's account immediately and typically incurs fewer fees than a post-clearing refund.

You can make a **full reversal** to void the entire amount, or a **partial reversal** to reduce the authorized amount. Multiple partial reversals can be made against the same original payment, as long as the total does not exceed the original amount.

> A reversal must be made **before the batch settles**. Once the batch has cleared, use a [referenced refund](./refund-referenced.md) or [unreferenced refund](./refund-unreferenced.md) instead.

---

## Before you begin

You need the transaction identifier of the original payment. This is returned in the payment response as `POIData.POITransactionID.TransactionID`, in the format `tenderReference.pspReference`. Make sure your POS app stores this when a payment completes.

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

Example — full reversal:

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
      "POIID": "P400Plus-275839164"
    },
    "ReversalRequest": {
      "OriginalPOITransaction": {
        "POITransactionID": {
          "TransactionID": "4rKV001726384910000.AJ7F2M9KR43TPQB8",
          "TimeStamp": "2026-03-02T14:35:12+00:00"
        }
      },
      "ReversalReason": "MerchantCancel"
    }
  }
}
```

Example — partial reversal:

```json
{
  "SaleToPOIRequest": {
    "MessageHeader": {
      "ProtocolVersion": "3.0",
      "MessageClass": "Service",
      "MessageCategory": "Reversal",
      "MessageType": "Request",
      "ServiceID": "SVC-00852",
      "SaleID": "BiltPOS-Lane3",
      "POIID": "P400Plus-275839164"
    },
    "ReversalRequest": {
      "OriginalPOITransaction": {
        "POITransactionID": {
          "TransactionID": "4rKV001726384910000.AJ7F2M9KR43TPQB8",
          "TimeStamp": "2026-03-02T14:35:12+00:00"
        }
      },
      "ReversalReason": "MerchantCancel",
      "PaymentTransaction": {
        "AmountsReq": {
          "Currency": "USD",
          "RequestedAmount": 29.99
        }
      }
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
      "POIID": "P400Plus-275839164"
    },
    "ReversalResponse": {
      "Response": {
        "Result": "Success",
        "AdditionalResponse": "transactionType=REFUND&pspReference=9MB3001726385900000..."
      },
      "POIData": {
        "POITransactionID": {
          "TransactionID": "9MB3001726385900000.CP4J6R2MT87WSNQ1",
          "TimeStamp": "2026-03-02T15:10:08+00:00"
        }
      }
    }
  }
}
```

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
