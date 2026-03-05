# DigitString — Numeric input

Collect numeric-only input from the user on the terminal.

Use `DigitString` for zip codes, phone numbers, loyalty card numbers, or other digit sequences. The terminal displays a prompt and a numeric input field. Only digit keys are accepted.

For common request fields shared by all input commands, see [Collect input from the terminal](./input-request.md).

---

## Make a DigitString request

Send a Terminal API input request with `InputCommand` set to `DigitString`. The display content is defined in an XML payload, Base64-encoded in `OutputXHTML`. For `DigitString`, only the `<display>` block is used — the terminal renders the numeric input control based on the `InputCommand`.

### Input payload

```xml
<?xml version="1.0" encoding="UTF-8"?>
<inputPayload xmlns="urn:bilt:input:v1" version="1.0">
  <display>
    <title>Enter your zip code</title>
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
- **`InputCommand`** — `DigitString`
- **`MaxInputTime`** — *(optional)* Maximum seconds to wait before automatic cancellation.
- **`MinLength`** — *(optional)* Minimum number of digits the user must enter.
- **`MaxLength`** — *(optional)* Maximum number of digits the user can enter.
- **`StringMask`** — *(optional)* Format mask for the input. Use `d` for a required digit position (e.g. `ddddd` for a 5-digit zip code).
- **`DefaultInputString`** — *(optional)* Pre-filled digits in the input field.

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
      "ServiceID": "SVC-01005",
      "SaleID": "BiltPOS-Lane3",
      "POIID": "VictaLane-275839164"
    },
    "InputRequest": {
      "DisplayOutput": {
        "Device": "CustomerDisplay",
        "InfoQualify": "Display",
        "OutputContent": {
          "OutputFormat": "XHTML",
          "OutputXHTML": "PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPGlucHV0UGF5bG9hZCB4bWxucz0idXJuOmJpbHQ6aW5wdXQ6djEiIHZlcnNpb249IjEuMCI+CiAgPGRpc3BsYXk+CiAgICA8dGl0bGU+RW50ZXIgeW91ciB6aXAgY29kZTwvdGl0bGU+CiAgPC9kaXNwbGF5Pgo8L2lucHV0UGF5bG9hZD4="
        }
      },
      "InputData": {
        "Device": "CustomerInput",
        "InfoQualify": "Input",
        "InputCommand": "DigitString",
        "MaxInputTime": 30,
        "MinLength": 5,
        "MaxLength": 5
      }
    }
  }
}
```

---

## Response

The response includes **`Input.DigitInput`** — the digit string entered by the user.

Example response:

```json
{
  "SaleToPOIResponse": {
    "MessageHeader": {
      "ProtocolVersion": "3.0",
      "MessageClass": "Device",
      "MessageCategory": "Input",
      "MessageType": "Response",
      "ServiceID": "SVC-01005",
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
          "InputCommand": "DigitString",
          "DigitInput": "10001"
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
- [TextString](./input-text-string.md) — collect free-text alphanumeric input.
- [DecimalString](./input-decimal-string.md) — collect a decimal number.
