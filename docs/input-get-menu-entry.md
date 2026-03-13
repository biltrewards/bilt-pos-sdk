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

### Single selection

Example request — select a tip amount:

```json
{
  "SaleToPOIRequest": {
    "MessageHeader": {
      "ProtocolVersion": "3.0",
      "MessageClass": "Device",
      "MessageCategory": "Input",
      "MessageType": "Request",
      "ServiceID": "SVC-01008",
      "SaleID": "BiltPOS-Lane3",
      "POIID": "VictaLane-275839164"
    },
    "InputRequest": {
      "DisplayOutput": {
        "Device": "CustomerDisplay",
        "InfoQualify": "Display",
        "OutputContent": {
          "OutputFormat": "XHTML",
          "OutputXHTML": "PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPGlucHV0UGF5bG9hZCB4bWxucz0idXJuOmJpbHQ6aW5wdXQ6djEiIHZlcnNpb249IjEuMCI+CiAgPGRpc3BsYXk+CiAgICA8dGl0bGU+U2VsZWN0IGEgdGlwIGFtb3VudDwvdGl0bGU+CiAgPC9kaXNwbGF5Pgo8L2lucHV0UGF5bG9hZD4="
        },
        "MenuEntry": [
          {
            "OutputFormat": "Text",
            "OutputText": [{ "Text": "15% — $14.18" }]
          },
          {
            "OutputFormat": "Text",
            "OutputText": [{ "Text": "20% — $18.90" }]
          },
          {
            "OutputFormat": "Text",
            "OutputText": [{ "Text": "25% — $23.63" }]
          },
          {
            "OutputFormat": "Text",
            "OutputText": [{ "Text": "No tip" }]
          }
        ]
      },
      "InputData": {
        "Device": "CustomerInput",
        "InfoQualify": "Input",
        "InputCommand": "GetMenuEntry",
        "MaxInputTime": 30
      }
    }
  }
}
```

Example response — user selected the second option:

```json
{
  "SaleToPOIResponse": {
    "MessageHeader": {
      "ProtocolVersion": "3.0",
      "MessageClass": "Device",
      "MessageCategory": "Input",
      "MessageType": "Response",
      "ServiceID": "SVC-01008",
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
          "InputCommand": "GetMenuEntry",
          "MenuEntryNumber": [2]
        }
      }
    }
  }
}
```

### Multiple selection

To allow multiple selections, set `MaxLength` to the maximum number of entries the user can select. The response `MenuEntryNumber` array will contain all selected indexes.

Example input payload:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<inputPayload xmlns="urn:bilt:input:v1" version="1.0">
  <display>
    <title>Select toppings</title>
  </display>
</inputPayload>
```

Example request — select toppings:

```json
{
  "SaleToPOIRequest": {
    "MessageHeader": {
      "ProtocolVersion": "3.0",
      "MessageClass": "Device",
      "MessageCategory": "Input",
      "MessageType": "Request",
      "ServiceID": "SVC-01009",
      "SaleID": "BiltPOS-Lane3",
      "POIID": "VictaLane-275839164"
    },
    "InputRequest": {
      "DisplayOutput": {
        "Device": "CustomerDisplay",
        "InfoQualify": "Display",
        "OutputContent": {
          "OutputFormat": "XHTML",
          "OutputXHTML": "PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPGlucHV0UGF5bG9hZCB4bWxucz0idXJuOmJpbHQ6aW5wdXQ6djEiIHZlcnNpb249IjEuMCI+CiAgPGRpc3BsYXk+CiAgICA8dGl0bGU+U2VsZWN0IHRvcHBpbmdzPC90aXRsZT4KICA8L2Rpc3BsYXk+CjwvaW5wdXRQYXlsb2FkPg=="
        },
        "MenuEntry": [
          {
            "OutputFormat": "Text",
            "OutputText": [{ "Text": "Extra cheese" }]
          },
          {
            "OutputFormat": "Text",
            "OutputText": [{ "Text": "Pepperoni" }]
          },
          {
            "OutputFormat": "Text",
            "OutputText": [{ "Text": "Mushrooms" }]
          },
          {
            "OutputFormat": "Text",
            "OutputText": [{ "Text": "Olives" }]
          }
        ]
      },
      "InputData": {
        "Device": "CustomerInput",
        "InfoQualify": "Input",
        "InputCommand": "GetMenuEntry",
        "MaxInputTime": 30,
        "MinLength": 1,
        "MaxLength": 4
      }
    }
  }
}
```

Example response — user selected multiple options:

```json
{
  "SaleToPOIResponse": {
    "MessageHeader": {
      "ProtocolVersion": "3.0",
      "MessageClass": "Device",
      "MessageCategory": "Input",
      "MessageType": "Response",
      "ServiceID": "SVC-01009",
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
          "InputCommand": "GetMenuEntry",
          "MenuEntryNumber": [1, 3]
        }
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
