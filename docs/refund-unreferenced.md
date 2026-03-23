---
---

# Unreferenced refund

Issue a refund to any card presented to the terminal, without linking it to an original payment.

An unreferenced refund pushes a specified amount from the merchant account to any card the shopper presents. Unlike a [referenced refund](./refund-referenced.md), there is no validation against an original transaction, so your POS app is responsible for reconciling the refund against the original purchase to prevent return fraud and human error. Use this when you don't have the original transaction reference, or the refund is being made to a different card than the original payment.

> Unreferenced refunds must be enabled before use. Contact support to enable this feature for your account.

---

## Make an unreferenced refund request

Send a Terminal API payment request with `PaymentType` set to `Refund`, without a reference to an original transaction. The terminal prompts the shopper to present their card, then shows that the transaction is approved.

`MessageHeader` fields:

- **`ProtocolVersion`** — `3.0`
- **`MessageClass`** — `Service`
- **`MessageCategory`** — `Payment`
- **`MessageType`** — `Request`
- **`ServiceID`** — Unique ID for this request, 1–10 alphanumeric characters, unique within 48 hours per terminal.
- **`SaleID`** — Your POS system identifier.
- **`POIID`** — Target terminal ID, format: `[model]-[serial]`.

`PaymentRequest` fields:

- **`SaleData.SaleTransactionID.TransactionID`** — Your unique reference for this refund. This appears as the merchant reference in reports.
- **`SaleData.SaleTransactionID.TimeStamp`** — Date and time of the request in UTC format.
- **`PaymentTransaction.AmountsReq.Currency`** — The transaction currency code (e.g. `USD`).
- **`PaymentTransaction.AmountsReq.RequestedAmount`** — The amount to refund. This field is required for unreferenced refunds.
- **`PaymentData.PaymentType`** — `Refund`

Example request:

```json
{
  "SaleToPOIRequest": {
    "MessageHeader": {
      "ProtocolVersion": "3.0",
      "MessageClass": "Service",
      "MessageCategory": "Payment",
      "MessageType": "Request",
      "ServiceID": "SVC-00861",
      "SaleID": "BiltPOS-Lane3",
      "POIID": "VictaLane-275839164"
    },
    "PaymentRequest": {
      "SaleData": {
        "SaleTransactionID": {
          "TransactionID": "UREF-20260302-00105",
          "TimeStamp": "2026-03-02T15:30:00+00:00"
        }
      },
      "PaymentTransaction": {
        "AmountsReq": {
          "Currency": "USD",
          "RequestedAmount": 45.00
        }
      },
      "PaymentData": {
        "PaymentType": "Refund"
      }
    }
  }
}
```

---

## Refund response

The result is returned in the API response in a `PaymentResponse` body. The main result is in `PaymentResponse.Response.Result`.

### Successful refund

When a refund succeeds, your integration receives a result containing:

- **`PaymentResponse.Response.Result`** — `Success`.
- **`POIData.POITransactionID.TransactionID`** — the transaction identifier for this refund.
- **`PaymentReceipt`** — receipt data for the refund transaction.

Example response:

```json
{
  "SaleToPOIResponse": {
    "MessageHeader": {
      "ProtocolVersion": "3.0",
      "MessageClass": "Service",
      "MessageCategory": "Payment",
      "MessageType": "Response",
      "ServiceID": "SVC-00861",
      "SaleID": "BiltPOS-Lane3",
      "POIID": "VictaLane-275839164"
    },
    "PaymentResponse": {
      "Response": {
        "Result": "Success"
      },
      "POIData": {
        "POITransactionID": {
          "TransactionID": "bce9bf88-f253-49ee-ab16-0d0d6e962452",
          "TimeStamp": "2026-03-02T15:30:09+00:00"
        }
      }
    }
  }
}
```

### Failed refund

When a refund fails, the result includes:

- **`PaymentResponse.Response.Result`** — `Failure`.
- **`PaymentResponse.Response.ErrorCondition`** — the reason for failure.

For general guidance on handling failed requests, see [Handle responses](./error-scenarios.md).

---

## Next steps

- [Cancel, reverse, or refund a payment](./undo-payment.md) — overview of all options for undoing a payment.
- [Referenced refund](./refund-referenced.md) — refund linked to the original payment.
- [Reverse a payment](./reverse-payment.md) — void a completed payment before the batch settles.
- [Cancel a payment](./cancel-payment.md) — abort an in-progress payment before it completes.
- [Verify payment status](./verify-transaction-status.md) — check the status of a transaction when you don't receive a result.
