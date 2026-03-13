# Signature — Signature capture

Capture a handwritten signature from the user on the terminal.

Use signature capture for payment authorisation, delivery confirmation, or any scenario requiring a written signature. The terminal displays a signature pad with optional prompt text and confirm/cancel buttons. The captured signature is returned as a Base64-encoded image in the response.

Signature capture uses `GetConfirmation` as the `InputCommand`. The terminal switches to signature mode when it detects the `<signature>` element in the XML payload.

For common request fields shared by all input commands, see [Collect input from the terminal](./input-request.md).

---

## Make a signature request

Send a Terminal API input request with `InputCommand` set to `GetConfirmation`. The display content and button labels are defined in an XML payload, Base64-encoded in `OutputXHTML`. The `<signature>` element in the payload tells the terminal to render a signature capture screen instead of a standard yes/no confirmation.

### Input payload

```xml
<?xml version="1.0" encoding="UTF-8"?>
<inputPayload xmlns="urn:bilt:input:v1" version="1.0">
  <display>
    <title>Signature required</title>
    <text>Please sign below to authorise your payment of $94.50</text>
  </display>
  <signature>
    <confirmButton>I agree</confirmButton>
    <cancelButton>Cancel</cancelButton>
  </signature>
</inputPayload>
```

- **`<display>`** — *(optional)* Title and text lines shown above the signature pad.
  - **`<title>`** — The main prompt text.
  - **`<text>`** — *(zero or more)* Additional text lines.
- **`<signature>`** — Renders a signature capture screen. If the element is empty (`<signature/>`), default button labels are used.
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
- **`DisableCancelFlag`** — *(optional)* When `true`, hides the Cancel button.
- **`DisableValidFlag`** — *(optional)* When `true`, hides the Confirm button.

`DisplayOutput` fields:

- **`Device`** — `CustomerDisplay`
- **`InfoQualify`** — `Display`
- **`OutputContent.OutputFormat`** — `XHTML`
- **`OutputContent.OutputXHTML`** — Base64-encoded XML payload.

---

## Examples

### Basic signature capture

Capture a signature with custom prompt:

**XML Payload:**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<inputPayload version="1.0">
  <display>
    <title>Please sign below</title>
    <text>I agree to the terms and conditions</text>
  </display>
  <signature>
    <confirmButton>Accept</confirmButton>
    <cancelButton>Cancel</cancelButton>
    <clearButton>Clear</clearButton>
  </signature>
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
          "OutputXHTML": "PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPGlucHV0UGF5bG9hZCB2ZXJzaW9uPSIxLjAiPgogIDxkaXNwbGF5PgogICAgPHRpdGxlPlBsZWFzZSBzaWduIGJlbG93PC90aXRsZT4KICAgIDx0ZXh0PkkgYWdyZWUgdG8gdGhlIHRlcm1zIGFuZCBjb25kaXRpb25zPC90ZXh0PgogIDwvZGlzcGxheT4KICA8c2lnbmF0dXJlPgogICAgPGNvbmZpcm1CdXR0b24+QWNjZXB0PC9jb25maXJtQnV0dG9uPgogICAgPGNhbmNlbEJ1dHRvbj5DYW5jZWw8L2NhbmNlbEJ1dHRvbj4KICAgIDxjbGVhckJ1dHRvbj5DbGVhcjwvY2xlYXJCdXR0b24+CiAgPC9zaWduYXR1cmU+CjwvaW5wdXRQYXlsb2FkPg=="
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

**Response (signed):**

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
          "Result": "Success",
          "AdditionalResponse": "iVBORw0KGgoAAAANSUhEUgAA..."
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

The signature image is returned as a Base64-encoded PNG in `AdditionalResponse`.

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

### Signature with timeout

Capture signature with 60-second countdown:

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
          "OutputXHTML": "PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPGlucHV0UGF5bG9hZCB2ZXJzaW9uPSIxLjAiPgogIDxkaXNwbGF5PgogICAgPHRpdGxlPlBsZWFzZSBzaWduIGJlbG93PC90aXRsZT4KICAgIDx0ZXh0PkkgYWdyZWUgdG8gdGhlIHRlcm1zIGFuZCBjb25kaXRpb25zPC90ZXh0PgogIDwvZGlzcGxheT4KICA8c2lnbmF0dXJlPgogICAgPGNvbmZpcm1CdXR0b24+QWNjZXB0PC9jb25maXJtQnV0dG9uPgogICAgPGNhbmNlbEJ1dHRvbj5DYW5jZWw8L2NhbmNlbEJ1dHRvbj4KICAgIDxjbGVhckJ1dHRvbj5DbGVhcjwvY2xlYXJCdXR0b24+CiAgPC9zaWduYXR1cmU+CjwvaW5wdXRQYXlsb2FkPg=="
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

### Mandatory signature (no cancel)

Capture signature without allowing cancel:

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
          "OutputXHTML": "PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPGlucHV0UGF5bG9hZCB2ZXJzaW9uPSIxLjAiPgogIDxkaXNwbGF5PgogICAgPHRpdGxlPlBsZWFzZSBzaWduIGJlbG93PC90aXRsZT4KICAgIDx0ZXh0PkkgYWdyZWUgdG8gdGhlIHRlcm1zIGFuZCBjb25kaXRpb25zPC90ZXh0PgogIDwvZGlzcGxheT4KICA8c2lnbmF0dXJlPgogICAgPGNvbmZpcm1CdXR0b24+QWNjZXB0PC9jb25maXJtQnV0dG9uPgogICAgPGNhbmNlbEJ1dHRvbj5DYW5jZWw8L2NhbmNlbEJ1dHRvbj4KICAgIDxjbGVhckJ1dHRvbj5DbGVhcjwvY2xlYXJCdXR0b24+CiAgPC9zaWduYXR1cmU+CjwvaW5wdXRQYXlsb2FkPg=="
        }
      },
      "InputData": {
        "Device": "CustomerInput",
        "InfoQualify": "Input",
        "InputCommand": "GetConfirmation",
        "DisableCancelFlag": true
      }
    }
  }
}
```

The cancel button is hidden. User must sign and accept to proceed.

---

## Response

A successful response includes:

- **`Input.ConfirmedFlag`** — `true` if the user signed and confirmed, `false` if they cancelled.
- **`Response.AdditionalResponse`** — The captured signature as a Base64-encoded PNG image. Only present when `ConfirmedFlag` is `true`.

### Failed input

If the user does not respond within `MaxInputTime` or presses Cancel, the response includes `Result: Failure` with `ErrorCondition: Cancel`. For general guidance on handling failed requests, see [Handle responses](./error-scenarios.md).

---

## Next steps

- [Collect input from the terminal](./input-request.md) — overview of all input commands.
- [GetConfirmation](./input-get-confirmation.md) — simple yes/no without signature capture.
- [Cancel an input request](./input-cancel.md) — abort an in-progress input request.
