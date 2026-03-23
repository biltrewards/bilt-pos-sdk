---
---

# Cancel an input request

Cancel an in-progress input request on the terminal from your POS app.

If your POS app has sent an `InputRequest` and no longer needs the user's response — for example because a timeout occurred in your application logic, the workflow changed, or the operator wants to move on — you can cancel it by sending an `AbortRequest` that references the original input request.

The user can also cancel directly on the terminal by pressing the Cancel key (or tapping the cancel icon on keypad-less terminals).

---

## Before you begin

You need the `ServiceID` and `SaleID` of the in-progress input request you want to cancel. Make sure your POS app stores these when sending an input request.

---

## Cancel an input request

Send a Terminal API abort request with `MessageCategory` set to `Abort` and a `MessageReference` pointing to the original input request.

`MessageHeader` fields:

- **`ProtocolVersion`** — `3.0`
- **`MessageClass`** — `Service`
- **`MessageCategory`** — `Abort`
- **`MessageType`** — `Request`
- **`ServiceID`** — A new unique ID for this abort request, 1–10 alphanumeric characters, unique within 48 hours per terminal.
- **`SaleID`** — Your POS system identifier.
- **`POIID`** — Target terminal ID, format: `[model]-[serial]`.

`AbortRequest` fields:

- **`AbortReason`** — `MerchantAbort`.
- **`MessageReference.MessageCategory`** — `Input`.
- **`MessageReference.ServiceID`** — The `ServiceID` of the in-progress input request.
- **`MessageReference.SaleID`** — The `SaleID` of the in-progress input request.

Example request — cancel an input request with ServiceID `SVC-01002`:

```json
{
  "SaleToPOIRequest": {
    "MessageHeader": {
      "ProtocolVersion": "3.0",
      "MessageClass": "Service",
      "MessageCategory": "Abort",
      "MessageType": "Request",
      "ServiceID": "SVC-01020",
      "SaleID": "BiltPOS-Lane3",
      "POIID": "VictaLane-275839164"
    },
    "AbortRequest": {
      "AbortReason": "MerchantAbort",
      "MessageReference": {
        "MessageCategory": "Input",
        "ServiceID": "SVC-01002",
        "SaleID": "BiltPOS-Lane3"
      }
    }
  }
}
```

---

## Cancellation response

A successful abort request returns an HTTP `200 OK` with no body. The input screen is immediately dismissed on the terminal. The result of the cancellation is then returned in the original `InputResponse`:

- For **GetConfirmation** — the response contains `Result: Success` with `ConfirmedFlag: false` (treated as if the user declined).
- For **TextString**, **DigitString**, **DecimalString**, and **GetMenuEntry** — the response contains `Result: Failure` with `ErrorCondition: Aborted`.

Example input response after a successful abort of a DigitString request:

```json
{
  "SaleToPOIResponse": {
    "MessageHeader": {
      "ProtocolVersion": "3.0",
      "MessageClass": "Device",
      "MessageCategory": "Input",
      "MessageType": "Response",
      "ServiceID": "SVC-01002",
      "SaleID": "BiltPOS-Lane3",
      "POIID": "VictaLane-275839164"
    },
    "InputResponse": {
      "InputResult": {
        "Device": "CustomerInput",
        "InfoQualify": "Input",
        "Response": {
          "Result": "Failure",
          "ErrorCondition": "Aborted",
          "AdditionalResponse": "POS aborted"
        }
      }
    }
  }
}
```

### If the input already completed

It is possible for the user to respond before the abort request reaches the terminal. In that case, the original `InputResponse` will contain the user's actual input rather than an abort result. Always check the original input response to confirm the actual outcome.

---

## Next steps

- [Collect input from the terminal](./input-request.md) — overview of all input commands.
- [Cancel a payment](./cancel-payment.md) — abort an in-progress payment.
- [Handle responses](./error-scenarios.md) — handle errors and edge cases.
