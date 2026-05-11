---
---

# Amount Confirmation — Confirm transaction amount

Display a transaction amount for the customer to confirm before proceeding.

Use amount confirmation to show the total amount to the customer and get their explicit approval before starting a payment or refund. The terminal displays the formatted amount with confirm and cancel buttons. This is commonly used for high-value transactions, refunds, or when the POS requires customer acknowledgment of the transaction total.

Amount confirmation uses `GetConfirmation` as the `InputCommand`. The terminal switches to amount confirmation mode when it detects the `<amountConfirmation>` element in the XML payload.

For common request fields shared by all input commands, see [Collect input from the terminal](./input-request.md).

---

## Make an amount confirmation request

Send a Terminal API input request with `InputCommand` set to `GetConfirmation`. The amount, currency, and optional button labels are defined in an XML payload, Base64-encoded in `OutputXHTML`. The `<amountConfirmation>` element in the payload tells the terminal to render an amount confirmation screen.

### Input payload

```xml
<?xml version="1.0" encoding="UTF-8"?>
<inputPayload xmlns="urn:bilt:input:v1" version="1.0">
  <amountConfirmation>
    <amount>94.50</amount>
    <currency>USD</currency>
    <title>Confirm Payment</title>
    <confirmButton>Pay Now</confirmButton>
    <cancelButton>Cancel</cancelButton>
  </amountConfirmation>
</inputPayload>
```

- **`<amountConfirmation>`** — Renders an amount confirmation screen.
  - **`<amount>`** — *(required)* The transaction amount as a decimal number (e.g., `94.50`).
  - **`<currency>`** — *(optional)* ISO 4217 currency code. Defaults to `USD` if not specified.
  - **`<title>`** — *(optional)* Custom title text displayed above the amount. If not provided, a default title is used based on the transaction type.
  - **`<confirmButton>`** — *(optional)* Label for the confirm button.
  - **`<cancelButton>`** — *(optional)* Label for the cancel button.

### Request fields

`MessageHeader` fields:

- **`ProtocolVersion`** — `3.0`
- **`MessageClass`** — `Device`
- **`MessageCategory`** — `Input`
- **`MessageType`** — `Request`
- **`ServiceID`** — Unique ID for this request, 1–10 alphanumeric characters, unique within 48 hours per terminal.
- **`SaleID`** — Your POS system identifier.
- **`POIID`** — Target terminal ID, format: `[model]-[serial]`.

`InputData` fields:

- **`Device`** — `CustomerInput`
- **`InfoQualify`** — `Input`
- **`InputCommand`** — `GetConfirmation`
- **`MaxInputTime`** — *(optional)* Maximum seconds to wait before automatic cancellation. A visual countdown is displayed.

`DisplayOutput` fields:

- **`Device`** — `CustomerDisplay`
- **`InfoQualify`** — `Display`
- **`OutputContent.OutputFormat`** — `XHTML`
- **`OutputContent.OutputXHTML`** — Base64-encoded XML payload.

---

## Examples

### Basic amount confirmation

Confirm a payment amount with the customer:

**XML Payload:**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<inputPayload version="1.0">
  <amountConfirmation>
    <amount>94.50</amount>
    <currency>USD</currency>
    <confirmButton>Confirm</confirmButton>
    <cancelButton>Cancel</cancelButton>
  </amountConfirmation>
</inputPayload>
```

**Request:**

```json
{
  "SaleToPOIRequest": {
    "MessageHeader": {
      "MessageCategory": "Input",
      "MessageClass": "Device",
      "MessageType": "Request",
      "POIID": "POI-1",
      "SaleID": "SALE-1"
    },
    "InputRequest": {
      "DisplayOutput": {
        "Device": "CustomerDisplay",
        "InfoQualify": "Display",
        "OutputContent": {
          "OutputFormat": "XHTML",
          "OutputXHTML": "PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPGlucHV0UGF5bG9hZCB2ZXJzaW9uPSIxLjAiPgogIDxhbW91bnRDb25maXJtYXRpb24+CiAgICA8YW1vdW50Pjk0LjUwPC9hbW91bnQ+CiAgICA8Y3VycmVuY3k+VVNEPC9jdXJyZW5jeT4KICAgIDxjb25maXJtQnV0dG9uPkNvbmZpcm08L2NvbmZpcm1CdXR0b24+CiAgICA8Y2FuY2VsQnV0dG9uPkNhbmNlbDwvY2FuY2VsQnV0dG9uPgogIDwvYW1vdW50Q29uZmlybWF0aW9uPgo8L2lucHV0UGF5bG9hZD4="
        }
      },
      "InputData": {
        "Device": "CustomerInput",
        "InfoQualify": "Input",
        "InputCommand": "GetConfirmation"
      }
    }
  }
}
```

**Response (confirmed):**

```json
{
  "SaleToPOIResponse": {
    "MessageHeader": {
      "MessageCategory": "Input",
      "MessageClass": "Device",
      "MessageType": "Response",
      "POIID": "POI-1",
      "SaleID": "SALE-1"
    },
    "InputResponse": {
      "InputResult": {
        "Device": "CustomerInput",
        "InfoQualify": "Input",
        "Response": {
          "Result": "Success"
        },
        "Input": {
          "InputCommand": "GetConfirmation",
          "ConfirmedFlag": true
        }
      }
    }
  }
}
```

**Response (cancelled):**

```json
{
  "SaleToPOIResponse": {
    "MessageHeader": {
      "MessageCategory": "Input",
      "MessageClass": "Device",
      "MessageType": "Response",
      "POIID": "POI-1",
      "SaleID": "SALE-1"
    },
    "InputResponse": {
      "InputResult": {
        "Device": "CustomerInput",
        "InfoQualify": "Input",
        "Response": {
          "Result": "Success"
        },
        "Input": {
          "InputCommand": "GetConfirmation",
          "ConfirmedFlag": false
        }
      }
    }
  }
}
```

---

### Refund confirmation

Confirm a refund amount with custom title:

**XML Payload:**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<inputPayload version="1.0">
  <amountConfirmation>
    <amount>25.00</amount>
    <currency>USD</currency>
    <title>Confirm Refund</title>
    <confirmButton>Process Refund</confirmButton>
    <cancelButton>Cancel</cancelButton>
  </amountConfirmation>
</inputPayload>
```

**Request:**

```json
{
  "SaleToPOIRequest": {
    "MessageHeader": {
      "MessageCategory": "Input",
      "MessageClass": "Device",
      "MessageType": "Request",
      "POIID": "POI-1",
      "SaleID": "SALE-1"
    },
    "InputRequest": {
      "DisplayOutput": {
        "Device": "CustomerDisplay",
        "InfoQualify": "Display",
        "OutputContent": {
          "OutputFormat": "XHTML",
          "OutputXHTML": "PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPGlucHV0UGF5bG9hZCB2ZXJzaW9uPSIxLjAiPgogIDxhbW91bnRDb25maXJtYXRpb24+CiAgICA8YW1vdW50PjI1LjAwPC9hbW91bnQ+CiAgICA8Y3VycmVuY3k+VVNEPC9jdXJyZW5jeT4KICAgIDx0aXRsZT5Db25maXJtIFJlZnVuZDwvdGl0bGU+CiAgICA8Y29uZmlybUJ1dHRvbj5Qcm9jZXNzIFJlZnVuZDwvY29uZmlybUJ1dHRvbj4KICAgIDxjYW5jZWxCdXR0b24+Q2FuY2VsPC9jYW5jZWxCdXR0b24+CiAgPC9hbW91bnRDb25maXJtYXRpb24+CjwvaW5wdXRQYXlsb2FkPg=="
        }
      },
      "InputData": {
        "Device": "CustomerInput",
        "InfoQualify": "Input",
        "InputCommand": "GetConfirmation"
      }
    }
  }
}
```

---

### Amount confirmation with timeout

Confirm amount with 60-second countdown:

**Request:**

```json
{
  "SaleToPOIRequest": {
    "MessageHeader": {
      "MessageCategory": "Input",
      "MessageClass": "Device",
      "MessageType": "Request",
      "POIID": "POI-1",
      "SaleID": "SALE-1"
    },
    "InputRequest": {
      "DisplayOutput": {
        "Device": "CustomerDisplay",
        "InfoQualify": "Display",
        "OutputContent": {
          "OutputFormat": "XHTML",
          "OutputXHTML": "PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPGlucHV0UGF5bG9hZCB2ZXJzaW9uPSIxLjAiPgogIDxhbW91bnRDb25maXJtYXRpb24+CiAgICA8YW1vdW50Pjk0LjUwPC9hbW91bnQ+CiAgICA8Y3VycmVuY3k+VVNEPC9jdXJyZW5jeT4KICA8L2Ftb3VudENvbmZpcm1hdGlvbj4KPC9pbnB1dFBheWxvYWQ+"
        }
      },
      "InputData": {
        "Device": "CustomerInput",
        "InfoQualify": "Input",
        "InputCommand": "GetConfirmation",
        "MaxInputTime": 60
      }
    }
  }
}
```

A countdown progress bar is displayed. If timeout expires:

**Response (timeout):**

```json
{
  "SaleToPOIResponse": {
    "MessageHeader": {
      "MessageCategory": "Input",
      "MessageClass": "Device",
      "MessageType": "Response",
      "POIID": "POI-1",
      "SaleID": "SALE-1"
    },
    "InputResponse": {
      "InputResult": {
        "Device": "CustomerInput",
        "InfoQualify": "Input",
        "Response": {
          "Result": "Failure",
          "ErrorCondition": "Cancel"
        }
      }
    }
  }
}
```

---

### Euro amount confirmation

Confirm an amount in EUR:

**XML Payload:**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<inputPayload version="1.0">
  <amountConfirmation>
    <amount>49.99</amount>
    <currency>EUR</currency>
    <confirmButton>Bestätigen</confirmButton>
    <cancelButton>Abbrechen</cancelButton>
  </amountConfirmation>
</inputPayload>
```

The terminal formats the amount according to the specified currency (e.g., `€49.99`).

---

## Response

A successful response includes:

- **`Input.ConfirmedFlag`** — `true` if the user confirmed the amount, `false` if they cancelled.

### Failed input

If the user does not respond within `MaxInputTime` or presses Cancel, the response includes `Result: Failure` with `ErrorCondition: Cancel`. For general guidance on handling failed requests, see [Handle responses](./error-scenarios.md).

---

## Next steps

- [Collect input from the terminal](./input-request.md) — overview of all input commands.
- [GetConfirmation](./input-get-confirmation.md) — simple yes/no confirmation without amount display.
- [Signature](./input-signature.md) — capture customer signature.
- [Cancel an input request](./input-cancel.md) — abort an in-progress input request.
