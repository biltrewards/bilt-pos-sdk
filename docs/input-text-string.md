# TextString — Free-text input

Collect free-text alphanumeric input from the user on the terminal.

Use `TextString` for names, email addresses, notes, or any arbitrary text. The terminal displays a prompt and a text input field. The user types their response and presses the confirm key.

For common request fields shared by all input commands, see [Collect input from the terminal](./input-request.md).

---

## Make a TextString request

Send a Terminal API input request with `InputCommand` set to `TextString`. The display content is defined in an XML payload, Base64-encoded in `OutputXHTML`. For `TextString`, only the `<display>` block is used — the terminal renders the text input control based on the `InputCommand`.

### Input payload

```xml
<?xml version="1.0" encoding="UTF-8"?>
<inputPayload xmlns="urn:bilt:input:v1" version="1.0">
  <display>
    <title>Enter your email address</title>
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
- **`InputCommand`** — `TextString`
- **`MaxInputTime`** — *(optional)* Maximum seconds to wait before automatic cancellation. A visual countdown is displayed.
- **`MinLength`** — *(optional)* Minimum number of characters the user must enter.
- **`MaxLength`** — *(optional)* Maximum number of characters the user can enter.
- **`DefaultInputString`** — *(optional)* Pre-filled text displayed as a placeholder until the user starts typing.
- **`MaskCharactersFlag`** — *(optional)* When `true`, entered characters are masked with `•`. Default `false`.
- **`DisableCancelFlag`** — *(optional)* When `true`, hides the Cancel button.
- **`DisableValidFlag`** — *(optional)* When `true`, hides the Confirm button.
- **`WaitUserValidationFlag`** — *(optional)* When `false` (default) and `MaxLength` is set, auto-submits when `MaxLength` is reached. When `true`, requires explicit confirmation.

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
      "ServiceID": "SVC-01004",
      "SaleID": "BiltPOS-Lane3",
      "POIID": "VictaLane-275839164"
    },
    "InputRequest": {
      "DisplayOutput": {
        "Device": "CustomerDisplay",
        "InfoQualify": "Display",
        "OutputContent": {
          "OutputFormat": "XHTML",
          "OutputXHTML": "PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPGlucHV0UGF5bG9hZCB4bWxucz0idXJuOmJpbHQ6aW5wdXQ6djEiIHZlcnNpb249IjEuMCI+CiAgPGRpc3BsYXk+CiAgICA8dGl0bGU+RW50ZXIgeW91ciBlbWFpbCBhZGRyZXNzPC90aXRsZT4KICA8L2Rpc3BsYXk+CjwvaW5wdXRQYXlsb2FkPg=="
        }
      },
      "InputData": {
        "Device": "CustomerInput",
        "InfoQualify": "Input",
        "InputCommand": "TextString",
        "MaxInputTime": 60,
        "MaxLength": 50
      }
    }
  }
}
```

---

## Response

The response includes **`Input.TextInput`** — the text entered by the user.

Example response:

```json
{
  "SaleToPOIResponse": {
    "MessageHeader": {
      "ProtocolVersion": "3.0",
      "MessageClass": "Device",
      "MessageCategory": "Input",
      "MessageType": "Response",
      "ServiceID": "SVC-01004",
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
          "InputCommand": "TextString",
          "TextInput": "shopper@example.com"
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
- [DigitString](./input-digit-string.md) — collect numeric-only input.
- [DecimalString](./input-decimal-string.md) — collect a decimal number.
