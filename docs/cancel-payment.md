# Cancel a payment

Cancel a payment that is currently in progress on the terminal. This stops the transaction before it completes, and is preferable to issuing a refund when possible as it generally results in fewer fees.

> An abort request can only cancel a payment that is **in progress**. Once a payment has completed, it cannot be aborted — use a [reversal](./reverse-payment.md) or [refund](./undo-payment.md) instead.

---

## Before you begin

You need the `ServiceID` and `SaleID` of the in-progress payment request you want to cancel. Make sure your POS app stores these when initiating a payment.

---

## Cancel a payment request

Send a Terminal API abort request from your POS app with the following `MessageHeader` fields:

- **`ProtocolVersion`** — `3.0`
- **`MessageClass`** — `Service`
- **`MessageCategory`** — `Abort`
- **`MessageType`** — `Request`
- **`ServiceID`** — A new unique ID for this abort request, 1–10 alphanumeric characters, unique within 48 hours per terminal.
- **`SaleID`** — Your POS system identifier.
- **`POIID`** — Target terminal ID, format: `[model]-[serial]`.

And the following `AbortRequest` fields:

- **`AbortReason`** — `MerchantAbort`.
- **`MessageReference.MessageCategory`** — The `MessageCategory` of the in-progress request, e.g. `Payment`.
- **`MessageReference.ServiceID`** — The `ServiceID` of the in-progress payment request.
- **`MessageReference.SaleID`** — The `SaleID` of the in-progress payment request.

Example request:

```json
{
  "SaleToPOIRequest": {
    "MessageHeader": {
      "ProtocolVersion": "3.0",
      "MessageClass": "Service",
      "MessageCategory": "Abort",
      "MessageType": "Request",
      "ServiceID": "SVC-00844",
      "SaleID": "BiltPOS-Lane3",
      "POIID": "VictaLane-275839164"
    },
    "AbortRequest": {
      "AbortReason": "MerchantAbort",
      "MessageReference": {
        "MessageCategory": "Payment",
        "ServiceID": "SVC-00842",
        "SaleID": "BiltPOS-Lane3"
      }
    }
  }
}
```

---

## Cancellation response

A successful abort request returns an HTTP `200 OK` with no body. The result of the cancellation is then returned in the original payment response, which will contain:

- **`PaymentResponse.Response.Result`** — `Failure`
- **`PaymentResponse.Response.ErrorCondition`** — `Aborted`

Example payment response after a successful abort:

```json
{
  "SaleToPOIResponse": {
    "MessageHeader": {
      "ProtocolVersion": "3.0",
      "MessageClass": "Service",
      "MessageCategory": "Payment",
      "MessageType": "Response",
      "ServiceID": "SVC-00842",
      "SaleID": "BiltPOS-Lane3",
      "POIID": "VictaLane-275839164"
    },
    "PaymentResponse": {
      "Response": {
        "Result": "Failure",
        "ErrorCondition": "Aborted",
        "AdditionalResponse": "POS aborted"
      }
    }
  }
}
```

### If the abort did not succeed

It is possible for the payment to complete before the abort request reaches the terminal, in which case the original payment response will contain `Result: Success` rather than `Aborted`. Always check the original payment response to confirm the actual outcome before assuming the cancellation succeeded.

If you do not receive the original payment response at all, use [Verify payment status](./verify-transaction-status.md) to determine the outcome.

---

## Next steps

- [Cancel, reverse, or refund a payment](./undo-payment.md) — overview of all options for undoing a payment.
- [Reverse a payment](./reverse-payment.md) — void a completed payment before the batch settles.
- [Referenced refund](./refund-referenced.md) — post-clearing refund linked to the original payment.
- [Verify payment status](./verify-transaction-status.md) — check the status of a transaction when you don't receive a result.
- [Make a payment](./make-payment.md) — initiate a new payment.
