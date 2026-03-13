# GetMenuEntry — Menu selection

Present a menu of options for the user to select from on the terminal.

Use `GetMenuEntry` for tip selection, product choices, service options, or any scenario where the user picks from a list. You can allow single selection or multiple selection by setting `MinLength` and `MaxLength`. The prompt text is defined in the XML payload, while the menu entries are defined in the nexo `DisplayOutput.MenuEntry` array.

For common request fields shared by all input commands, see [Collect input from the terminal](./input-request.md).

---

## Make a GetMenuEntry request

Send a Terminal API input request with `InputCommand` set to `GetMenuEntry`. The display title is defined in an XML payload, Base64-encoded in `OutputXHTML`. The menu options are defined separately in the `DisplayOutput.MenuEntry` array.

### Input payload

```xml
<?xml version="1.0" encoding="UTF-8"?>
<inputPayload xmlns="urn:bilt:input:v1" version="1.0">
  <display>
    <title>Select a tip amount</title>
  </display>
</inputPayload>
```

- **`<display>`** — Title shown above the menu.
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
- **`InputCommand`** — `GetMenuEntry`
- **`MaxInputTime`** — *(optional)* Maximum seconds to wait before automatic cancellation. A visual countdown is displayed.
- **`MinLength`** — *(optional)* Minimum number of entries the user must select. Default `1`.
- **`MaxLength`** — *(optional)* Maximum number of entries the user can select. Set to `1` for single selection, or higher for multiple selection.
- **`MenuBackFlag`** — *(optional)* When `true`, enables Back (returns `-1`) and Home (returns `0`) navigation keys. Default `false`.
- **`DisableCancelFlag`** — *(optional)* When `true`, hides the Cancel button.
- **`DisableValidFlag`** — *(optional)* When `true`, hides the Confirm button (for multiple selection mode).

`DisplayOutput` fields:

- **`Device`** — `CustomerDisplay`
- **`InfoQualify`** — `Display`
- **`OutputContent.OutputFormat`** — `XHTML`
- **`OutputContent.OutputXHTML`** — Base64-encoded XML payload.
- **`MenuEntry`** — Array of menu entries. Each entry has:
  - **`OutputFormat`** — `Text`.
  - **`OutputText`** — Array with a `Text` field containing the label for this entry.
  - **`MenuEntryTag`** — *(optional)* `Selectable` (default), `NonSelectable` (header/separator), `SubMenu`, or `NonSelectableSubMenu`.
  - **`DefaultSelectedFlag`** — *(optional)* When `true`, this entry is pre-selected. Default `false`.

---

## Examples

### Single selection — payment method

Select a payment method:

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
          "OutputText": [{"Text": "Select payment method"}]
        },
        "MenuEntry": [
          {"OutputFormat": "Text", "OutputText": [{"Text": "Credit Card"}]},
          {"OutputFormat": "Text", "OutputText": [{"Text": "Debit Card"}]},
          {"OutputFormat": "Text", "OutputText": [{"Text": "Cash"}]},
          {"OutputFormat": "Text", "OutputText": [{"Text": "Gift Card"}]}
        ]
      },
      "InputData": {
        "Device": "CustomerInput",
        "InfoQualify": "Input",
        "InputCommand": "GetMenuEntry",
        "MaxLength": 1
      }
    }
  }
}
```

**Response (user selected Debit Card):**

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
          "InputCommand": "GetMenuEntry",
          "MenuEntryNumber": [2]
        }
      }
    }
  }
}
```

---

### Single selection with timeout

Select payment method with 30-second countdown:

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
          "OutputText": [{"Text": "Select payment method"}]
        },
        "MenuEntry": [
          {"OutputFormat": "Text", "OutputText": [{"Text": "Credit Card"}]},
          {"OutputFormat": "Text", "OutputText": [{"Text": "Debit Card"}]},
          {"OutputFormat": "Text", "OutputText": [{"Text": "Cash"}]},
          {"OutputFormat": "Text", "OutputText": [{"Text": "Gift Card"}]}
        ]
      },
      "InputData": {
        "Device": "CustomerInput",
        "InfoQualify": "Input",
        "InputCommand": "GetMenuEntry",
        "MaxLength": 1,
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

### Single selection (no cancel)

Select payment method without allowing cancel:

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
          "OutputText": [{"Text": "Select payment method"}]
        },
        "MenuEntry": [
          {"OutputFormat": "Text", "OutputText": [{"Text": "Credit Card"}]},
          {"OutputFormat": "Text", "OutputText": [{"Text": "Debit Card"}]},
          {"OutputFormat": "Text", "OutputText": [{"Text": "Cash"}]},
          {"OutputFormat": "Text", "OutputText": [{"Text": "Gift Card"}]}
        ]
      },
      "InputData": {
        "Device": "CustomerInput",
        "InfoQualify": "Input",
        "InputCommand": "GetMenuEntry",
        "MaxLength": 1,
        "DisableCancelFlag": true
      }
    }
  }
}
```

The cancel/close button is hidden. User must select an option.

---

### Multiple selection — receipt options

Select multiple receipt options:

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
          "OutputText": [{"Text": "Select receipt options"}]
        },
        "MenuEntry": [
          {"OutputFormat": "Text", "OutputText": [{"Text": "Email receipt"}]},
          {"OutputFormat": "Text", "OutputText": [{"Text": "Print receipt"}]},
          {"OutputFormat": "Text", "OutputText": [{"Text": "SMS receipt"}]},
          {"OutputFormat": "Text", "OutputText": [{"Text": "No receipt"}]}
        ]
      },
      "InputData": {
        "Device": "CustomerInput",
        "InfoQualify": "Input",
        "InputCommand": "GetMenuEntry",
        "MinLength": 1,
        "MaxLength": 3
      }
    }
  }
}
```

**Response (user selected Email and Print):**

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
          "InputCommand": "GetMenuEntry",
          "MenuEntryNumber": [1, 2]
        }
      }
    }
  }
}
```

---

### Multiple selection (no cancel)

Select receipt options without allowing cancel:

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
          "OutputText": [{"Text": "Select receipt options"}]
        },
        "MenuEntry": [
          {"OutputFormat": "Text", "OutputText": [{"Text": "Email receipt"}]},
          {"OutputFormat": "Text", "OutputText": [{"Text": "Print receipt"}]},
          {"OutputFormat": "Text", "OutputText": [{"Text": "SMS receipt"}]},
          {"OutputFormat": "Text", "OutputText": [{"Text": "No receipt"}]}
        ]
      },
      "InputData": {
        "Device": "CustomerInput",
        "InfoQualify": "Input",
        "InputCommand": "GetMenuEntry",
        "MinLength": 1,
        "MaxLength": 3,
        "DisableCancelFlag": true
      }
    }
  }
}
```

---

## Response

The response includes **`Input.MenuEntryNumber`** — an array of 1-based indexes of the selected entries. If `MenuBackFlag` is enabled, `-1` means Back and `0` means Home.

### Failed input

If the user does not respond within `MaxInputTime` or presses Cancel, the response includes `Result: Failure` with `ErrorCondition: Cancel`. For general guidance on handling failed requests, see [Handle responses](./error-scenarios.md).

---

## Next steps

- [Collect input from the terminal](./input-request.md) — overview of all input commands.
- [GetConfirmation](./input-get-confirmation.md) — simple yes/no for binary choices.
