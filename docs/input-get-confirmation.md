# GetConfirmation — Yes/No confirmation

Ask the user a yes/no question on the terminal.

The terminal displays a message with two buttons and waits for the user to respond. Use this for receipt prompts, age verification, terms acceptance, or any binary choice. The button labels are configured via the `<confirmation>` element in the XML payload.

For common request fields shared by all input commands, see [Collect input from the terminal](./input-request.md).

---

## Make a GetConfirmation request

Send a Terminal API input request with `InputCommand` set to `GetConfirmation`. The display content and button labels are defined in an XML payload, Base64-encoded in `OutputXHTML`.

### Input payload

```xml
<?xml version="1.0" encoding="UTF-8"?>
<inputPayload xmlns="urn:bilt:input:v1" version="1.0">
  <display>
    <title>Would you like a receipt?</title>
    <text>We can email your receipt to you.</text>
  </display>
  <confirmation>
    <confirmButton>Yes please</confirmButton>
    <cancelButton>No thanks</cancelButton>
  </confirmation>
</inputPayload>
```

- **`<display>`** — *(optional)* Title and text lines shown above the buttons.
- **`<confirmation>`** — *(optional)* Custom button labels. If omitted, default confirm/cancel labels are used.
  - **`<confirmButton>`** — Label for the confirm button.
  - **`<cancelButton>`** — Label for the cancel button.

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
- **`DefaultInputString`** — *(optional)* Pre-select the default answer: `"Y"` for yes, `"N"` for no.
- **`DisableCancelFlag`** — *(optional)* When `true`, hides the decline/cancel button, leaving only the confirm button.
- **`DisableValidFlag`** — *(optional)* When `true`, hides the confirm button, leaving only the decline/cancel button.

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
      "ServiceID": "SVC-01002",
      "SaleID": "BiltPOS-Lane3",
      "POIID": "VictaLane-275839164"
    },
    "InputRequest": {
      "DisplayOutput": {
        "Device": "CustomerDisplay",
        "InfoQualify": "Display",
        "OutputContent": {
          "OutputFormat": "XHTML",
          "OutputXHTML": "PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPGlucHV0UGF5bG9hZCB4bWxucz0idXJuOmJpbHQ6aW5wdXQ6djEiIHZlcnNpb249IjEuMCI+CiAgPGRpc3BsYXk+CiAgICA8dGl0bGU+V291bGQgeW91IGxpa2UgYSByZWNlaXB0PzwvdGl0bGU+CiAgICA8dGV4dD5XZSBjYW4gZW1haWwgeW91ciByZWNlaXB0IHRvIHlvdS48L3RleHQ+CiAgPC9kaXNwbGF5PgogIDxjb25maXJtYXRpb24+CiAgICA8Y29uZmlybUJ1dHRvbj5ZZXMgcGxlYXNlPC9jb25maXJtQnV0dG9uPgogICAgPGNhbmNlbEJ1dHRvbj5ObyB0aGFua3M8L2NhbmNlbEJ1dHRvbj4KICA8L2NvbmZpcm1hdGlvbj4KPC9pbnB1dFBheWxvYWQ+"
        }
      },
      "InputData": {
        "Device": "CustomerInput",
        "InfoQualify": "Input",
        "InputCommand": "GetConfirmation",
        "MaxInputTime": 30
      }
    }
  }
}
```

---

## Response

The response includes **`Input.ConfirmedFlag`** — `true` if the user confirmed, `false` if they declined.

Example response — user confirmed:

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

### Failed input

If the user does not respond within `MaxInputTime`, the response includes `Result: Failure` with `ErrorCondition: Cancel`. For general guidance on handling failed requests, see [Handle responses](./error-scenarios.md).

---

## Next steps

- [Collect input from the terminal](./input-request.md) — overview of all input commands.
- [GetMenuEntry](./input-get-menu-entry.md) — present a menu of options for more than two choices.

