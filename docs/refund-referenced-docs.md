# Referenced refund

Issue a refund linked to the original payment.

A referenced refund is tied to the original transaction using its transaction identifier. This lets the platform validate the refund against the original payment, reducing the risk of duplicate refunds and fraud. Referenced refunds are the preferred refund method and are universally supported. The funds are returned to the original payment method without requiring the shopper to present their card.

You can make a **full refund** to return the total amount, or a **partial refund** to return part of it. Multiple partial refunds can be made against the same original payment, as long as the total does not exceed the original amount.

If the batch has not yet settled, consider [cancelling the payment](./cancel-a-payment.md) instead — this generally results in fewer fees.

---

## Before you begin

You need the transaction identifier of the original payment. This is returned in the payment response as `POIData.POITransactionID.TransactionID`, in the format `tenderReference.pspReference`. Make sure your POS app stores this when a payment completes.

---

## Make a referenced refund request

A referenced refund uses a `ReversalRequest` body. Send a Terminal API request with the following `MessageHeader` fields:

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
- **`ReversalReason`** — `MerchantCancel` for a full refund; `Malfunction` may apply in other cases depending on your use case.
- **`TransactionConditions.AcquirerID`** — *(optional)* restrict the refund to a specific acquirer.
- **`PaymentTransaction.AmountsReq.Currency`** — The transaction currency code (e.g. `USD`). Required for partial refunds.
- **`PaymentTransaction.AmountsReq.RequestedAmount`** — The amount to refund. Omit for a full refund. Must not exceed the original payment amount.

Example — full referenced refund:

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

Example — partial referenced refund:

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

## Refund response

The result is returned in the API response in a `ReversalResponse` body. The main result is in `ReversalResponse.Response.Result`.

### Successful refund

When a refund request is accepted, your integration receives:

- **`ReversalResponse.Response.Result`** — `Success`. This confirms the request was received; the refund is processed asynchronously.
- **`POIData.POITransactionID.TransactionID`** — the transaction identifier for this refund.

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

### Failed refund

When a refund fails, the result includes:

- **`ReversalResponse.Response.Result`** — `Failure`.
- **`ReversalResponse.Response.ErrorCondition`** — the reason for failure. For example, `Refusal` if the refund amount exceeds the original payment amount.

For general guidance on handling failed requests, see [Handle responses](./error-scenarios.md).

---

## Next steps

- [Unreferenced refund](./refund-unreferenced.md) — refund to any card without linking to the original payment.
- [Cancel a payment](./cancel-a-payment.md) — abort an in-progress payment before it completes.
- [Verify payment status](./verify-transaction-status.md) — check the status of a transaction when you don't receive a result.
