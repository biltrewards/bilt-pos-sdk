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
- **`MaxInputTime`** — *(optional)* Maximum seconds to wait before automatic cancellation. A visual countdown is displayed.
- **`MinLength`** — *(optional)* Minimum number of digits the user must enter. The confirm button is disabled until the minimum is met.
- **`MaxLength`** — *(optional)* Maximum number of digits the user can enter.
- **`StringMask`** — *(optional)* Format mask for the input display. When the mask contains 10+ digit placeholders (`#`, `d`, or `9`) with parentheses or hyphens (e.g., `(###) ###-####`), the terminal displays the input with phone number formatting.
- **`MaskCharactersFlag`** — *(optional)* When `true`, entered digits are masked with `•` (for PIN entry). Default `false`.
- **`DefaultInputString`** — *(optional)* Placeholder digits displayed until the user starts typing. The user must type to enable the confirm button.
- **`DisableCancelFlag`** — *(optional)* When `true`, hides the Cancel button.

`DisplayOutput` fields:

- **`Device`** — `CustomerDisplay`
- **`InfoQualify`** — `Display`
- **`OutputContent.OutputFormat`** — `XHTML`
- **`OutputContent.OutputXHTML`** — Base64-encoded XML payload.

---

## Examples

### ZIP code (auto-submit)

Collect a 5-digit ZIP code that auto-submits when complete:

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
          "OutputText": [{"Text": "Enter ZIP Code"}]
        }
      },
      "InputData": {
        "Device": "CustomerInput",
        "InfoQualify": "Input",
        "InputCommand": "DigitString",
        "MaxLength": 5
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
          "InputCommand": "DigitString",
          "DigitInput": "90210"
        }
      }
    }
  }
}
```

---

### Phone number with format mask

Collect a phone number with formatted display:

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
          "OutputText": [{"Text": "Enter Phone Number"}]
        }
      },
      "InputData": {
        "Device": "CustomerInput",
        "InfoQualify": "Input",
        "InputCommand": "DigitString",
        "StringMask": "(###) ###-####"
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
          "InputCommand": "DigitString",
          "DigitInput": "5551234567"
        }
      }
    }
  }
}
```

---

### Masked PIN entry

Collect a 4-digit PIN with masked display:

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
          "OutputText": [{"Text": "Enter PIN"}]
        }
      },
      "InputData": {
        "Device": "CustomerInput",
        "InfoQualify": "Input",
        "InputCommand": "DigitString",
        "MaskCharactersFlag": true,
        "MaxLength": 4
      }
    }
  }
}
```

Each digit is displayed as `•` on the terminal.

---

### Pre-filled ZIP code

Edit a ZIP code with a default value:

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
          "OutputText": [{"Text": "Edit ZIP Code"}]
        }
      },
      "InputData": {
        "Device": "CustomerInput",
        "InfoQualify": "Input",
        "InputCommand": "DigitString",
        "MaxLength": 5,
        "DefaultInputString": "90210"
      }
    }
  }
}
```

The terminal shows "90210" as a placeholder. If confirmed without typing, "90210" is submitted.

---

### ZIP code with timeout

Collect ZIP code with a 30-second countdown:

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
          "OutputText": [{"Text": "Enter ZIP Code"}]
        }
      },
      "InputData": {
        "Device": "CustomerInput",
        "InfoQualify": "Input",
        "InputCommand": "DigitString",
        "MaxLength": 5,
        "MaxInputTime": 30
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

### Mandatory input (no cancel)

Collect ZIP code without allowing cancel:

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
          "OutputText": [{"Text": "Enter ZIP Code"}]
        }
      },
      "InputData": {
        "Device": "CustomerInput",
        "InfoQualify": "Input",
        "InputCommand": "DigitString",
        "MaxLength": 5,
        "DisableCancelFlag": true
      }
    }
  }
}
```

The cancel/close button is hidden. User must enter a value to proceed.

---

## Response

The response includes **`Input.DigitInput`** — the digit string entered by the user.

### Failed input

If the user does not respond within `MaxInputTime` or presses Cancel, the response includes `Result: Failure` with `ErrorCondition: Cancel`. For general guidance on handling failed requests, see [Handle responses](./error-scenarios.md).

---

## Next steps

- [Collect input from the terminal](./input-request.md) — overview of all input commands.
- [TextString](./input-text-string.md) — collect free-text alphanumeric input.
- [DecimalString](./input-decimal-string.md) — collect a decimal number.
