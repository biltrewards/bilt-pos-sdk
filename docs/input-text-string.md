---
---

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
- **`MinLength`** — *(optional)* Minimum number of characters the user must enter. The confirm button is disabled until the minimum is met.
- **`MaxLength`** — *(optional)* Maximum number of characters the user can enter.
- **`DefaultInputString`** — *(optional)* Placeholder text displayed until the user starts typing. The user must type to enable the confirm button.
- **`MaskCharactersFlag`** — *(optional)* When `true`, entered characters are masked with `•`. Default `false`.
- **`DisableCancelFlag`** — *(optional)* When `true`, hides the Cancel button.

`DisplayOutput` fields:

- **`Device`** — `CustomerDisplay`
- **`InfoQualify`** — `Display`
- **`OutputContent.OutputFormat`** — `XHTML`
- **`OutputContent.OutputXHTML`** — Base64-encoded XML payload.

---

## Examples

### Basic name input

Collect the user's name:

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
          "OutputFormat": "Text",
          "OutputText": [{"Text": "Enter your name"}]
        }
      },
      "InputData": {
        "Device": "CustomerInput",
        "InfoQualify": "Input",
        "InputCommand": "TextString",
        "MaxLength": 50
      }
    }
  }
}
```

**Response:**

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
          "InputCommand": "TextString",
          "TextInput": "John Smith"
        }
      }
    }
  }
}
```

---

### Pre-filled name

Edit a name with a default value:

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
          "OutputFormat": "Text",
          "OutputText": [{"Text": "Edit your name"}]
        }
      },
      "InputData": {
        "Device": "CustomerInput",
        "InfoQualify": "Input",
        "InputCommand": "TextString",
        "MaxLength": 50,
        "DefaultInputString": "John Doe"
      }
    }
  }
}
```

The terminal shows "John Doe" as a placeholder. The user must type to enable the confirm button — the placeholder value cannot be submitted directly.

---

### Text input with timeout

Collect input with a 60-second countdown:

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
          "OutputFormat": "Text",
          "OutputText": [{"Text": "Enter your name"}]
        }
      },
      "InputData": {
        "Device": "CustomerInput",
        "InfoQualify": "Input",
        "InputCommand": "TextString",
        "MaxLength": 50,
        "MaxInputTime": 60
      }
    }
  }
}
```

A countdown progress bar is displayed at the top of the screen.

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

### Mandatory input (no cancel)

Collect name without allowing cancel:

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
          "OutputFormat": "Text",
          "OutputText": [{"Text": "Enter your name"}]
        }
      },
      "InputData": {
        "Device": "CustomerInput",
        "InfoQualify": "Input",
        "InputCommand": "TextString",
        "MaxLength": 50,
        "DisableCancelFlag": true
      }
    }
  }
}
```

The cancel/close button is hidden.

---

## Response

The response includes **`Input.TextInput`** — the text entered by the user.

### Failed input

If the user does not respond within `MaxInputTime` or presses Cancel, the response includes `Result: Failure` with `ErrorCondition: Cancel`. For general guidance on handling failed requests, see [Handle responses](./error-scenarios.md).

---

## Next steps

- [Collect input from the terminal](./input-request.md) — overview of all input commands.
- [DigitString](./input-digit-string.md) — collect numeric-only input.
- [DecimalString](./input-decimal-string.md) — collect a decimal number.
