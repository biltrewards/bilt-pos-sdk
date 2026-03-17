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
- **`DisableCancelFlag`** — *(optional)* When `true`, hides the decline/cancel button, leaving only the confirm button.

`DisplayOutput` fields:

- **`Device`** — `CustomerDisplay`
- **`InfoQualify`** — `Display`
- **`OutputContent.OutputFormat`** — `XHTML`
- **`OutputContent.OutputXHTML`** — Base64-encoded XML payload.

---

## Examples

### Basic confirmation

Ask the user to confirm an amount:

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
          "OutputText": [{"Text": "Amount OK?"}]
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

**Response (declined):**

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

### Terms and conditions (short)

Display terms with custom button labels using XML payload:

**XML Payload:**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<inputPayload version="1.0">
  <display>
    <title>Terms and Conditions</title>
    <text>By proceeding, you agree to our terms of service and privacy policy.</text>
  </display>
  <confirmation>
    <confirmButton>I Agree</confirmButton>
    <cancelButton>Decline</cancelButton>
  </confirmation>
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
          "OutputXHTML": "PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPGlucHV0UGF5bG9hZCB2ZXJzaW9uPSIxLjAiPgogIDxkaXNwbGF5PgogICAgPHRpdGxlPlRlcm1zIGFuZCBDb25kaXRpb25zPC90aXRsZT4KICAgIDx0ZXh0PkJ5IHByb2NlZWRpbmcsIHlvdSBhZ3JlZSB0byBvdXIgdGVybXMgb2Ygc2VydmljZSBhbmQgcHJpdmFjeSBwb2xpY3kuPC90ZXh0PgogIDwvZGlzcGxheT4KICA8Y29uZmlybWF0aW9uPgogICAgPGNvbmZpcm1CdXR0b24+SSBBZ3JlZTwvY29uZmlybUJ1dHRvbj4KICAgIDxjYW5jZWxCdXR0b24+RGVjbGluZTwvY2FuY2VsQnV0dG9uPgogIDwvY29uZmlybWF0aW9uPgo8L2lucHV0UGF5bG9hZD4="
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

### Confirmation with timeout

Ask confirmation with a 30-second countdown:

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
          "OutputText": [{"Text": "Amount OK?"}]
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

### Mandatory acknowledgment (no cancel)

Force user to confirm without allowing decline:

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
          "OutputText": [{"Text": "Amount OK?"}]
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

Only the confirm button is shown. The user must tap it to proceed.

---

## Response

The response includes **`Input.ConfirmedFlag`** — `true` if the user confirmed, `false` if they declined.

### Failed input

If the user does not respond within `MaxInputTime`, the response includes `Result: Failure` with `ErrorCondition: Cancel`. For general guidance on handling failed requests, see [Handle responses](./error-scenarios.md).

---

## Next steps

- [Collect input from the terminal](./input-request.md) — overview of all input commands.
- [GetMenuEntry](./input-get-menu-entry.md) — present a menu of options for more than two choices.

