# Referenced refund

Issue a post-clearing refund linked to the original payment.

A referenced refund returns funds to the original payment method by linking the refund to the original transaction via `OriginalPOITransaction`. This lets the platform validate the refund against the original payment, reducing the risk of duplicate refunds and fraud. The shopper does not need to present their card.

You can make a **full refund** to return the total amount, or a **partial refund** to return part of it. Multiple partial refunds can be made against the same original payment, as long as the total does not exceed the original amount.

> If the batch has not yet settled, consider [reversing the payment](./reverse-payment.md) instead — this releases the authorization hold immediately and typically incurs fewer fees.

---

## Before you begin

You need the transaction identifier and timestamp of the original payment. These are returned in the payment response as `POIData.POITransactionID.TransactionID` and `POIData.POITransactionID.TimeStamp`. Make sure your POS app stores these when a payment completes.

---

## Make a referenced refund request

A referenced refund uses a `PaymentRequest` with `PaymentType` set to `Refund` and a reference to the original transaction. Send a Terminal API request with the following `MessageHeader` fields:

- **`ProtocolVersion`** — `3.0`
- **`MessageClass`** — `Service`
- **`MessageCategory`** — `Payment`
- **`MessageType`** — `Request`
- **`ServiceID`** — Unique ID for this request, 1–10 alphanumeric characters, unique within 48 hours per terminal.
- **`SaleID`** — Your POS system identifier.
- **`POIID`** — Target terminal ID, format: `[model]-[serial]`.

And the following `PaymentRequest` fields:

- **`SaleData.SaleTransactionID.TransactionID`** — Your unique reference for this refund. This appears as the merchant reference in reports.
- **`SaleData.SaleTransactionID.TimeStamp`** — Date and time of the request in UTC format.
- **`PaymentData.PaymentType`** — `Refund`
- **`PaymentData.OriginalPOITransaction.POITransactionID.TransactionID`** — The transaction identifier from the original payment response.
- **`PaymentData.OriginalPOITransaction.POITransactionID.TimeStamp`** — The timestamp from the original payment response.
- **`PaymentTransaction.AmountsReq.Currency`** — The transaction currency code (e.g. `USD`).
- **`PaymentTransaction.AmountsReq.RequestedAmount`** — The amount to refund.

Example — full referenced refund:

```json
{
  "SaleToPOIRequest": {
    "MessageHeader": {
      "ProtocolVersion": "3.0",
      "MessageClass": "Service",
      "MessageCategory": "Payment",
      "MessageType": "Request",
      "ServiceID": "SVC-00871",
      "SaleID": "BiltPOS-Lane3",
      "POIID": "VictaLane-275839164"
    },
    "PaymentRequest": {
      "SaleData": {
        "SaleTransactionID": {
          "TransactionID": "RREF-20260302-00201",
          "TimeStamp": "2026-03-02T16:00:00+00:00"
        }
      },
      "PaymentData": {
        "PaymentType": "Refund",
        "OriginalPOITransaction": {
          "POITransactionID": {
            "TransactionID": "a816b0a9-8a11-4dc0-ba9d-5ad1e8c7e0d6",
            "TimeStamp": "2026-03-02T14:35:12+00:00"
          }
        }
      },
      "PaymentTransaction": {
        "AmountsReq": {
          "Currency": "USD",
          "RequestedAmount": 94.50
        }
      }
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
      "MessageCategory": "Payment",
      "MessageType": "Request",
      "ServiceID": "SVC-00872",
      "SaleID": "BiltPOS-Lane3",
      "POIID": "VictaLane-275839164"
    },
    "PaymentRequest": {
      "SaleData": {
        "SaleTransactionID": {
          "TransactionID": "RREF-20260302-00202",
          "TimeStamp": "2026-03-02T16:05:00+00:00"
        }
      },
      "PaymentData": {
        "PaymentType": "Refund",
        "OriginalPOITransaction": {
          "POITransactionID": {
            "TransactionID": "a816b0a9-8a11-4dc0-ba9d-5ad1e8c7e0d6",
            "TimeStamp": "2026-03-02T14:35:12+00:00"
          }
        }
      },
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

The result is returned in the API response in a `PaymentResponse` body. The main result is in `PaymentResponse.Response.Result`.

### Successful refund

When a refund succeeds, your integration receives:

- **`PaymentResponse.Response.Result`** — `Success`.
- **`POIData.POITransactionID.TransactionID`** — the transaction identifier for this refund.

Example response:

```json
{
  "SaleToPOIResponse": {
    "MessageHeader": {
      "ProtocolVersion": "3.0",
      "MessageClass": "Service",
      "MessageCategory": "Payment",
      "MessageType": "Response",
      "ServiceID": "SVC-00871",
      "SaleID": "BiltPOS-Lane3",
      "POIID": "VictaLane-275839164"
    },
    "PaymentResponse": {
      "Response": {
        "Result": "Success",
      },
      "POIData": {
        "POITransactionID": {
          "TransactionID": "bce9bf88-f253-49ee-ab16-0d0d6e962452",
          "TimeStamp": "2026-03-02T16:00:08+00:00"
        }
      }
    }
  }
}
```

### Failed refund

When a refund fails, the result includes:

- **`PaymentResponse.Response.Result`** — `Failure`.
- **`PaymentResponse.Response.ErrorCondition`** — the reason for failure. For example, `Refusal` if the refund amount exceeds the original payment amount.

For general guidance on handling failed requests, see [Handle responses](./error-scenarios.md).

---

## Next steps

- [Cancel, reverse, or refund a payment](./undo-payment.md) — overview of all options for undoing a payment.
- [Unreferenced refund](./refund-unreferenced.md) — refund to any card without linking to the original payment.
- [Reverse a payment](./reverse-payment.md) — void a completed payment before the batch settles.
- [Cancel a payment](./cancel-payment.md) — abort an in-progress payment before it completes.
- [Verify payment status](./verify-transaction-status.md) — check the status of a transaction when you don't receive a result.
