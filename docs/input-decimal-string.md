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
- **`MaxInputTime`** — *(optional)* Maximum seconds to wait before automatic cancellation. A visual countdown is displayed.
- **`MinLength`** — *(optional)* Minimum total number of digits.
- **`MaxLength`** — *(optional)* Maximum total number of digits.
- **`MaxDecimalLength`** — *(optional)* Maximum number of digits after the decimal point. Must be between `MinLength` and `MaxLength`.
- **`FromRightToLeftFlag`** — *(optional)* When `true`, digits are entered right-to-left (useful for amount entry where the decimal point is fixed). Default `false`.
- **`DefaultInputString`** — *(optional)* Pre-filled value displayed as a placeholder until the user starts typing.
- **`DisableCancelFlag`** — *(optional)* When `true`, hides the Cancel button.
- **`DisableValidFlag`** — *(optional)* When `true`, hides the Confirm button.
- **`WaitUserValidationFlag`** — *(optional)* When `false` (default) and `MaxLength` is set, auto-submits when `MaxLength` is reached. When `true`, requires explicit confirmation.

`DisplayOutput` fields:

- **`Device`** — `CustomerDisplay`
- **`InfoQualify`** — `Display`
- **`OutputContent.OutputFormat`** — `XHTML`
- **`OutputContent.OutputXHTML`** — Base64-encoded XML payload.

---

## Examples

### Amount entry with XML payload

Collect a tip amount with currency display using XML payload:

**XML Payload:**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<inputPayload version="1.0">
  <display>
    <title>Enter Amount</title>
  </display>
  <amount>
    <currencySymbol>$</currencySymbol>
    <currencyCode>USD</currencyCode>
    <confirmButton>OK</confirmButton>
    <cancelButton>Cancel</cancelButton>
  </amount>
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
          "OutputXHTML": "PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPGlucHV0UGF5bG9hZCB2ZXJzaW9uPSIxLjAiPgogIDxkaXNwbGF5PgogICAgPHRpdGxlPkVudGVyIEFtb3VudDwvdGl0bGU+CiAgPC9kaXNwbGF5PgogIDxhbW91bnQ+CiAgICA8Y3VycmVuY3lTeW1ib2w+JDwvY3VycmVuY3lTeW1ib2w+CiAgICA8Y3VycmVuY3lDb2RlPlVTRDwvY3VycmVuY3lDb2RlPgogICAgPGNvbmZpcm1CdXR0b24+T0s8L2NvbmZpcm1CdXR0b24+CiAgICA8Y2FuY2VsQnV0dG9uPkNhbmNlbDwvY2FuY2VsQnV0dG9uPgogIDwvYW1vdW50Pgo8L2lucHV0UGF5bG9hZD4="
        }
      },
      "InputData": {
        "Device": "CustomerInput",
        "InfoQualify": "Input",
        "InputCommand": "DecimalString"
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
          "InputCommand": "DecimalString",
          "TextInput": "15.50"
        }
      }
    }
  }
}
```

---

### Basic decimal input

Collect a tip amount with simple text prompt:

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
          "OutputText": [{"Text": "Enter tip amount"}]
        }
      },
      "InputData": {
        "Device": "CustomerInput",
        "InfoQualify": "Input",
        "InputCommand": "DecimalString",
        "MaxLength": 6,
        "MaxDecimalLength": 2
      }
    }
  }
}
```

---

### Decimal with timeout

Collect amount with 30-second countdown:

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
          "OutputText": [{"Text": "Enter tip amount"}]
        }
      },
      "InputData": {
        "Device": "CustomerInput",
        "InfoQualify": "Input",
        "InputCommand": "DecimalString",
        "MaxLength": 6,
        "MaxDecimalLength": 2,
        "MaxInputTime": 30
      }
    }
  }
}
```

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

## Response

The response includes **`Input.TextInput`** — the decimal value entered by the user, as a string.

### Failed input

If the user does not respond within `MaxInputTime` or presses Cancel, the response includes `Result: Failure` with `ErrorCondition: Cancel`. For general guidance on handling failed requests, see [Handle responses](./error-scenarios.md).

---

## Next steps

- [Collect input from the terminal](./input-request.md) — overview of all input commands.
- [DigitString](./input-digit-string.md) — collect numeric-only input without decimals.
- [TextString](./input-text-string.md) — collect free-text alphanumeric input.
