---
---

# Verify payment status

Check the outcome of a transaction when your POS app does not receive a response.

If your POS app sends a payment request but does not receive a response — for example due to a network interruption, timeout, or application crash — the transaction may or may not have been processed. Do not assume the payment failed and do not retry immediately, as this could result in a duplicate charge. Instead, send a `TransactionStatusRequest` to determine the actual outcome.

We strongly recommend implementing transaction status verification as part of your integration.

---

## When to verify

Send a status request when:

- Your payment request times out without a response.
- A network error occurs after the request was sent.
- Your POS app crashes or restarts during a transaction.
- You receive an ambiguous or incomplete response.

---

## Before you begin

You need the `ServiceID` and `SaleID` from the original payment request. Make sure your POS app persists these before sending a payment, so they are available even after a crash or restart.

---

## Make a transaction status request

Send a Terminal API request with the following `MessageHeader` fields:

- **`ProtocolVersion`** — `3.0`
- **`MessageClass`** — `Service`
- **`MessageCategory`** — `TransactionStatus`
- **`MessageType`** — `Request`
- **`ServiceID`** — A new unique ID for this status request, 1–10 alphanumeric characters, unique within 48 hours per terminal.
- **`SaleID`** — Your POS system identifier.
- **`POIID`** — Target terminal ID, format: `[model]-[serial]`.

And the following `TransactionStatusRequest` fields:

- **`ReceiptReprintFlag`** — `true` to include receipt data in the response. Set to `true` if you need to reprint a receipt.
- **`DocumentQualifier`** — Array of receipt types to include, e.g. `["CashierReceipt", "CustomerReceipt"]`. Only relevant when `ReceiptReprintFlag` is `true`.
- **`MessageReference.MessageCategory`** — The message category of the original request, e.g. `Payment`.
- **`MessageReference.ServiceID`** — The `ServiceID` of the original request.
- **`MessageReference.SaleID`** — The `SaleID` of the original request.

Example request:

```json
{
  "SaleToPOIRequest": {
    "MessageHeader": {
      "ProtocolVersion": "3.0",
      "MessageClass": "Service",
      "MessageCategory": "TransactionStatus",
      "MessageType": "Request",
      "ServiceID": "SVC-00900",
      "SaleID": "BiltPOS-Lane3",
      "POIID": "VictaLane-275839164"
    },
    "TransactionStatusRequest": {
      "ReceiptReprintFlag": true,
      "DocumentQualifier": ["CashierReceipt", "CustomerReceipt"],
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

## Transaction status response

The result is returned in the API response in a `TransactionStatusResponse` body. The main result is in `TransactionStatusResponse.Response.Result`.

### Transaction completed

When the original transaction has completed, the status response contains the original response in `RepeatedMessageResponse`. This is the same response your POS app would have received if the connection had not been interrupted.

- **`TransactionStatusResponse.Response.Result`** — `Success`. This means the status lookup succeeded, not necessarily that the original payment was approved.
- **`RepeatedMessageResponse.MessageHeader`** — The message header of the original response.
- **`RepeatedMessageResponse.RepeatedResponseMessageBody.PaymentResponse`** — The original payment response. Check `PaymentResponse.Response.Result` to determine whether the payment was approved (`Success`), declined (`Failure`), or cancelled (`Failure` with `ErrorCondition: Aborted` or `Cancel`).

Example response — original payment was approved:

```json
{
  "SaleToPOIResponse": {
    "MessageHeader": {
      "ProtocolVersion": "3.0",
      "MessageClass": "Service",
      "MessageCategory": "TransactionStatus",
      "MessageType": "Response",
      "ServiceID": "SVC-00900",
      "SaleID": "BiltPOS-Lane3",
      "POIID": "VictaLane-275839164"
    },
    "TransactionStatusResponse": {
      "Response": {
        "Result": "Success"
      },
      "MessageReference": {
        "MessageCategory": "Payment",
        "ServiceID": "SVC-00842",
        "SaleID": "BiltPOS-Lane3"
      },
      "RepeatedMessageResponse": {
        "MessageHeader": {
          "ProtocolVersion": "3.0",
          "MessageClass": "Service",
          "MessageCategory": "Payment",
          "MessageType": "Response",
          "ServiceID": "SVC-00842",
          "SaleID": "BiltPOS-Lane3",
          "POIID": "VictaLane-275839164"
        },
        "RepeatedResponseMessageBody": {
          "PaymentResponse": {
            "Response": {
              "Result": "Success",
            },
            "POIData": {
              "POITransactionID": {
                "TransactionID": "22339000-b171-4956-ac1f-4a263a8c26c6",
                "TimeStamp": "2026-03-02T14:35:12+00:00"
              }
            },
            "PaymentResult": {
              "PaymentType": "Normal",
              "AmountsResp": {
                "Currency": "USD",
                "AuthorizedAmount": 94.50
              }
            }
          }
        }
      }
    }
  }
}
```

### Transaction still in progress

When the original transaction is still being processed (for example, waiting for the shopper to present their card or enter a PIN), the status response indicates this with `ErrorCondition: InProgress`.

- **`TransactionStatusResponse.Response.Result`** — `Failure`.
- **`TransactionStatusResponse.Response.ErrorCondition`** — `InProgress`.

Example response:

```json
{
  "SaleToPOIResponse": {
    "MessageHeader": {
      "ProtocolVersion": "3.0",
      "MessageClass": "Service",
      "MessageCategory": "TransactionStatus",
      "MessageType": "Response",
      "ServiceID": "SVC-00900",
      "SaleID": "BiltPOS-Lane3",
      "POIID": "VictaLane-275839164"
    },
    "TransactionStatusResponse": {
      "Response": {
        "Result": "Failure",
        "ErrorCondition": "InProgress",
      },
      "MessageReference": {
        "MessageCategory": "Payment",
        "ServiceID": "SVC-00842",
        "SaleID": "BiltPOS-Lane3"
      }
    }
  }
}
```

**Action:** Send the status request again until you receive a final result (`Success` or a `Failure` other than `InProgress`).

### Transaction not found

When the terminal cannot find a transaction matching the provided reference, the status response indicates this with `ErrorCondition: NotFound`.

- **`TransactionStatusResponse.Response.Result`** — `Failure`.
- **`TransactionStatusResponse.Response.ErrorCondition`** — `NotFound`.

Example response:

```json
{
  "SaleToPOIResponse": {
    "MessageHeader": {
      "ProtocolVersion": "3.0",
      "MessageClass": "Service",
      "MessageCategory": "TransactionStatus",
      "MessageType": "Response",
      "ServiceID": "SVC-00900",
      "SaleID": "BiltPOS-Lane3",
      "POIID": "VictaLane-275839164"
    },
    "TransactionStatusResponse": {
      "Response": {
        "Result": "Failure",
        "ErrorCondition": "NotFound",
        "AdditionalResponse": "Transaction not found"
      },
      "MessageReference": {
        "MessageCategory": "Payment",
        "ServiceID": "SVC-00842",
        "SaleID": "BiltPOS-Lane3"
      }
    }
  }
}
```
When the transaction is not found it can be because:
 * The `POIID`, `SaleID`, and `ServiceID` are incorrect.
 * The transaction was never received by the terminal, or the terminal never processed the request due to some error, like connection issues.

---

## Next steps

- [Cancel, reverse, or refund a payment](./undo-payment.md) — overview of all options for undoing a payment.
- [Handle responses](./error-scenarios.md) — handle errors and edge cases during payment processing.
- [Timeout handling](./timeout-handling.md) — detailed guide to timeout scenarios.
- [Make a payment](./make-payment.md) — initiate a new payment.
