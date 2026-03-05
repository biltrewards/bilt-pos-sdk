# DecimalString — Decimal number input

Collect a decimal number from the user on the terminal.

Use `DecimalString` for amounts, quantities, or other values that include a decimal point. The terminal displays a prompt and a numeric input field that accepts a decimal separator.

For common request fields shared by all input commands, see [Collect input from the terminal](./input-request.md).

---

## Make a DecimalString request

Send a Terminal API input request with `InputCommand` set to `DecimalString`. The display content is defined in an XML payload, Base64-encoded in `OutputXHTML`. For `DecimalString`, only the `<display>` block is used — the terminal renders the decimal input control based on the `InputCommand`.

### Input payload

```xml
<?xml version="1.0" encoding="UTF-8"?>
<inputPayload xmlns="urn:bilt:input:v1" version="1.0">
  <display>
    <title>Enter tip amount</title>
  </display>
</inputPayload>
```

- **`<display>`** — Title shown above the input field.
  - **`<title>`** — The main prompt text.

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
- **`InputCommand`** — `DecimalString`
- **`MaxInputTime`** — *(optional)* Maximum seconds to wait before automatic cancellation.
- **`MinLength`** — *(optional)* Minimum total number of digits.
- **`MaxLength`** — *(optional)* Maximum total number of digits.
- **`MaxDecimalLength`** — *(optional)* Maximum number of digits after the decimal point. Must be between `MinLength` and `MaxLength`.
- **`FromRightToLeftFlag`** — *(optional)* When `true`, digits are entered right-to-left (useful for amount entry where the decimal point is fixed). Default `false`.

`DisplayOutput` fields:

- **`Device`** — `CustomerDisplay`
- **`InfoQualify`** — `Display`
- **`OutputContent.OutputFormat`** — `XHTML`
- **`OutputContent.OutputXHTML`** — Base64-encoded XML payload.

### Example request

```json
{
  "SaleToPOIRequest": {
    "MessageHeader": {
      "ProtocolVersion": "3.0",
      "MessageClass": "Device",
      "MessageCategory": "Input",
      "MessageType": "Request",
      "ServiceID": "SVC-01006",
      "SaleID": "BiltPOS-Lane3",
      "POIID": "VictaLane-275839164"
    },
    "InputRequest": {
      "DisplayOutput": {
        "Device": "CustomerDisplay",
        "InfoQualify": "Display",
        "OutputContent": {
          "OutputFormat": "XHTML",
          "OutputXHTML": "PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPGlucHV0UGF5bG9hZCB4bWxucz0idXJuOmJpbHQ6aW5wdXQ6djEiIHZlcnNpb249IjEuMCI+CiAgPGRpc3BsYXk+CiAgICA8dGl0bGU+RW50ZXIgdGlwIGFtb3VudDwvdGl0bGU+CiAgPC9kaXNwbGF5Pgo8L2lucHV0UGF5bG9hZD4="
        }
      },
      "InputData": {
        "Device": "CustomerInput",
        "InfoQualify": "Input",
        "InputCommand": "DecimalString",
        "MaxInputTime": 30,
        "MaxLength": 6,
        "MaxDecimalLength": 2
      }
    }
  }
}
```

---

## Response

The response includes **`Input.TextInput`** — the decimal value entered by the user, as a string.

Example response:

```json
{
  "SaleToPOIResponse": {
    "MessageHeader": {
      "ProtocolVersion": "3.0",
      "MessageClass": "Device",
      "MessageCategory": "Input",
      "MessageType": "Response",
      "ServiceID": "SVC-01006",
      "SaleID": "BiltPOS-Lane3",
      "POIID": "VictaLane-275839164"
    },
    "InputResponse": {
      "InputResult": {
        "Device": "CustomerInput",
        "InfoQualify": "Input",
        "Response": {
          "Result": "Success"
        },
        "Input": {
          "InputCommand": "DecimalString",
          "TextInput": "5.00"
        }
      }
    }
  }
}
```

### Failed input

If the user does not respond within `MaxInputTime` or presses Cancel, the response includes `Result: Failure` with `ErrorCondition: Cancel`. For general guidance on handling failed requests, see [Handle responses](./error-scenarios.md).

---

## Next steps

- [Collect input from the terminal](./input-request.md) — overview of all input commands.
- [DigitString](./input-digit-string.md) — collect numeric-only input without decimals.
- [TextString](./input-text-string.md) — collect free-text alphanumeric input.
