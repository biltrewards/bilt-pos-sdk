# Request input from the customer

Prompt the customer for input on the terminal screen — a confirmation, text entry, numeric value, signature, or menu selection. The terminal displays the appropriate input UI and returns the customer's response.

Use input requests when you need to collect information from the customer during a transaction — for example, accepting terms and conditions, entering a phone number, or selecting a payment option.

---

## Overview

An `InputRequest` combines two parts:

1. **DisplayOutput** — What to show: a heading, body text, and button labels.
2. **InputData** — What to collect: the type of input expected from the customer.

The `PredefinedContent.ReferenceID` in `DisplayOutput` identifies the screen layout to use. The `InputCommand` in `InputData` specifies the input type. The terminal renders the appropriate UI and waits for the customer to respond.

---

## Reference IDs

| ReferenceID | InputCommand | Description |
|-------------|--------------|-------------|
| `GetConsent` | `GetConfirmation` | Yes/No confirmation, terms and conditions |
| `GetNumber` | `DigitString` | Numeric digit entry (zip code, PIN, etc.) |
| `GetTextInput` | `TextString` | Free-form text entry (email, name, etc.) |
| `GetAmountInput` | `DecimalString` | Currency amount with decimals |
| `GetPhoneNumberInput` | `DigitString` | Phone number entry |
| `GetChoice` | `GetMenuEntry` | Single or multiple selection from a list |
| `GetSign` | `GetConfirmation` | Signature capture |
| `GetRatingFeedback` | `GetMenuEntry` | Rating scale (0–10) |

---

## GetConsent — Confirmation

Request a yes/no confirmation from the customer. Use for terms acceptance, amount confirmation, or any binary choice.

### OutputText structure

| Index | Purpose | Example |
|-------|---------|---------|
| 0 | Heading | `"Terms and Conditions"` |
| 1 | Body text (use `\n` for line breaks) | `"By proceeding, you agree to..."` |
| 2 | Decline button label | `"Decline"` |
| 3 | Accept button label | `"Accept"` |

### Request

```json
{
  "SaleToPOIRequest": {
    "MessageHeader": {
      "ProtocolVersion": "3.0",
      "MessageClass": "Device",
      "MessageCategory": "Input",
      "MessageType": "Request",
      "ServiceID": "input001",
      "SaleID": "POS-01",
      "POIID": "T400M-123456789"
    },
    "InputRequest": {
      "DisplayOutput": {
        "Device": "CustomerDisplay",
        "InfoQualify": "Display",
        "OutputContent": {
          "OutputFormat": "Text",
          "PredefinedContent": {
            "ReferenceID": "GetConsent"
          },
          "OutputText": [
            { "Text": "Terms and Conditions" },
            { "Text": "By proceeding, you agree to our terms of service.\n\nYour data will be processed according to our privacy policy." },
            { "Text": "Decline" },
            { "Text": "Accept" }
          ]
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

### Response (accepted)

```json
{
  "SaleToPOIResponse": {
    "MessageHeader": {
      "ProtocolVersion": "3.0",
      "MessageClass": "Device",
      "MessageCategory": "Input",
      "MessageType": "Response",
      "ServiceID": "input001",
      "SaleID": "POS-01",
      "POIID": "T400M-123456789"
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

### Response (declined)

```json
{
  "SaleToPOIResponse": {
    "MessageHeader": { ... },
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

## GetNumber — Numeric entry

Request numeric digits from the customer. Use for zip codes, loyalty numbers, or any numeric input.

### OutputText structure

| Index | Purpose | Example |
|-------|---------|---------|
| 0 | Prompt | `"Enter your zip code"` |

### Request

```json
{
  "SaleToPOIRequest": {
    "MessageHeader": {
      "ProtocolVersion": "3.0",
      "MessageClass": "Device",
      "MessageCategory": "Input",
      "MessageType": "Request",
      "ServiceID": "input002",
      "SaleID": "POS-01",
      "POIID": "T400M-123456789"
    },
    "InputRequest": {
      "DisplayOutput": {
        "Device": "CustomerDisplay",
        "InfoQualify": "Display",
        "OutputContent": {
          "OutputFormat": "Text",
          "PredefinedContent": {
            "ReferenceID": "GetNumber"
          },
          "OutputText": [
            { "Text": "Enter your zip code" }
          ]
        }
      },
      "InputData": {
        "Device": "CustomerInput",
        "InfoQualify": "Input",
        "InputCommand": "DigitString",
        "MaxInputTime": 60,
        "MinLength": 5,
        "MaxLength": 5
      }
    }
  }
}
```

### Response

```json
{
  "SaleToPOIResponse": {
    "MessageHeader": { ... },
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

## GetTextInput — Text entry

Request free-form text from the customer. Use for email addresses, names, or other alphanumeric input.

### OutputText structure

| Index | Purpose | Example |
|-------|---------|---------|
| 0 | Prompt | `"Enter your email address"` |

### Request

```json
{
  "SaleToPOIRequest": {
    "MessageHeader": {
      "ProtocolVersion": "3.0",
      "MessageClass": "Device",
      "MessageCategory": "Input",
      "MessageType": "Request",
      "ServiceID": "input003",
      "SaleID": "POS-01",
      "POIID": "T400M-123456789"
    },
    "InputRequest": {
      "DisplayOutput": {
        "Device": "CustomerDisplay",
        "InfoQualify": "Display",
        "OutputContent": {
          "OutputFormat": "Text",
          "PredefinedContent": {
            "ReferenceID": "GetTextInput"
          },
          "OutputText": [
            { "Text": "Enter your email address" }
          ]
        }
      },
      "InputData": {
        "Device": "CustomerInput",
        "InfoQualify": "Input",
        "InputCommand": "TextString",
        "MaxInputTime": 120,
        "MaxLength": 100
      }
    }
  }
}
```

### Response

```json
{
  "SaleToPOIResponse": {
    "MessageHeader": { ... },
    "InputResponse": {
      "InputResult": {
        "Device": "CustomerInput",
        "InfoQualify": "Input",
        "Response": {
          "Result": "Success"
        },
        "Input": {
          "InputCommand": "TextString",
          "TextInput": "customer@example.com"
        }
      }
    }
  }
}
```

---

## GetAmountInput — Amount entry

Request a currency amount from the customer. Use for tip entry, donation amounts, or custom payment amounts.

### OutputText structure

| Index | Purpose | Example |
|-------|---------|---------|
| 0 | Prompt | `"Enter tip amount"` |

### Request

```json
{
  "SaleToPOIRequest": {
    "MessageHeader": {
      "ProtocolVersion": "3.0",
      "MessageClass": "Device",
      "MessageCategory": "Input",
      "MessageType": "Request",
      "ServiceID": "input004",
      "SaleID": "POS-01",
      "POIID": "T400M-123456789"
    },
    "InputRequest": {
      "DisplayOutput": {
        "Device": "CustomerDisplay",
        "InfoQualify": "Display",
        "OutputContent": {
          "OutputFormat": "Text",
          "PredefinedContent": {
            "ReferenceID": "GetAmountInput"
          },
          "OutputText": [
            { "Text": "Enter tip amount" }
          ]
        }
      },
      "InputData": {
        "Device": "CustomerInput",
        "InfoQualify": "Input",
        "InputCommand": "DecimalString",
        "MaxInputTime": 60,
        "MaxDecimalLength": 2
      }
    }
  }
}
```

### Response

```json
{
  "SaleToPOIResponse": {
    "MessageHeader": { ... },
    "InputResponse": {
      "InputResult": {
        "Device": "CustomerInput",
        "InfoQualify": "Input",
        "Response": {
          "Result": "Success"
        },
        "Input": {
          "InputCommand": "DecimalString",
          "DigitInput": "5.00"
        }
      }
    }
  }
}
```

---

## GetPhoneNumberInput — Phone number entry

Request a phone number from the customer. Use for loyalty lookups, contact information, or SMS receipts.

### OutputText structure

| Index | Purpose | Example |
|-------|---------|---------|
| 0 | Prompt | `"Enter your phone number"` |

### Request

```json
{
  "SaleToPOIRequest": {
    "MessageHeader": {
      "ProtocolVersion": "3.0",
      "MessageClass": "Device",
      "MessageCategory": "Input",
      "MessageType": "Request",
      "ServiceID": "input005",
      "SaleID": "POS-01",
      "POIID": "T400M-123456789"
    },
    "InputRequest": {
      "DisplayOutput": {
        "Device": "CustomerDisplay",
        "InfoQualify": "Display",
        "OutputContent": {
          "OutputFormat": "Text",
          "PredefinedContent": {
            "ReferenceID": "GetPhoneNumberInput"
          },
          "OutputText": [
            { "Text": "Enter your phone number" }
          ]
        }
      },
      "InputData": {
        "Device": "CustomerInput",
        "InfoQualify": "Input",
        "InputCommand": "DigitString",
        "MaxInputTime": 60,
        "MinLength": 10,
        "MaxLength": 10
      }
    }
  }
}
```

### Response

```json
{
  "SaleToPOIResponse": {
    "MessageHeader": { ... },
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

## GetChoice — Menu selection

Request the customer to select one or more options from a list. Use for payment method selection, product options, or survey questions.

### OutputText structure

| Index | Purpose | Example |
|-------|---------|---------|
| 0 | Prompt | `"Select payment method"` |

Menu options are defined in the `MenuEntry` array.

### Request (single selection)

```json
{
  "SaleToPOIRequest": {
    "MessageHeader": {
      "ProtocolVersion": "3.0",
      "MessageClass": "Device",
      "MessageCategory": "Input",
      "MessageType": "Request",
      "ServiceID": "input006",
      "SaleID": "POS-01",
      "POIID": "T400M-123456789"
    },
    "InputRequest": {
      "DisplayOutput": {
        "Device": "CustomerDisplay",
        "InfoQualify": "Display",
        "OutputContent": {
          "OutputFormat": "Text",
          "PredefinedContent": {
            "ReferenceID": "GetChoice"
          },
          "OutputText": [
            { "Text": "Select payment method" }
          ]
        },
        "MenuEntry": [
          {
            "OutputFormat": "Text",
            "OutputText": [{ "Text": "Credit Card" }]
          },
          {
            "OutputFormat": "Text",
            "OutputText": [{ "Text": "Debit Card" }]
          },
          {
            "OutputFormat": "Text",
            "OutputText": [{ "Text": "Gift Card" }]
          }
        ]
      },
      "InputData": {
        "Device": "CustomerInput",
        "InfoQualify": "Input",
        "InputCommand": "GetMenuEntry",
        "MaxInputTime": 60
      }
    }
  }
}
```

### Response

```json
{
  "SaleToPOIResponse": {
    "MessageHeader": { ... },
    "InputResponse": {
      "InputResult": {
        "Device": "CustomerInput",
        "InfoQualify": "Input",
        "Response": {
          "Result": "Success"
        },
        "Input": {
          "InputCommand": "GetMenuEntry",
          "MenuEntryNumber": [1]
        }
      }
    }
  }
}
```

> **Note:** `MenuEntryNumber` is 1-indexed. A value of `[1]` means the first option was selected.

---

## GetSign — Signature capture

Request a signature from the customer. Use for high-value transactions, delivery confirmation, or agreement signing.

### OutputText structure

| Index | Purpose | Example |
|-------|---------|---------|
| 0 | Heading | `"Please sign below"` |
| 1 | Subheading (optional) | `"Sign to confirm your purchase"` |
| 2 | Clear button label | `"Clear"` |
| 3 | Accept button label | `"Accept"` |

### Request

```json
{
  "SaleToPOIRequest": {
    "MessageHeader": {
      "ProtocolVersion": "3.0",
      "MessageClass": "Device",
      "MessageCategory": "Input",
      "MessageType": "Request",
      "ServiceID": "input007",
      "SaleID": "POS-01",
      "POIID": "T400M-123456789"
    },
    "InputRequest": {
      "DisplayOutput": {
        "Device": "CustomerDisplay",
        "InfoQualify": "Display",
        "OutputContent": {
          "OutputFormat": "Text",
          "PredefinedContent": {
            "ReferenceID": "GetSign"
          },
          "OutputText": [
            { "Text": "Please sign below" },
            { "Text": "Sign to confirm your purchase" },
            { "Text": "Clear" },
            { "Text": "Accept" }
          ]
        }
      },
      "InputData": {
        "Device": "CustomerInput",
        "InfoQualify": "Input",
        "InputCommand": "GetConfirmation",
        "MaxInputTime": 120
      }
    }
  }
}
```

### Response

The signature data is returned as an array of `SignaturePoint` objects, where each point contains hexadecimal X and Y coordinates. A point with both X and Y equal to `"FFFF"` indicates the pen was lifted.

```json
{
  "SaleToPOIResponse": {
    "MessageHeader": { ... },
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
      },
      "CapturedSignature": {
        "AreaSize": {
          "X": "0320",
          "Y": "01E0"
        },
        "SignaturePoint": [
          { "X": "0064", "Y": "00C8" },
          { "X": "0065", "Y": "00C9" },
          { "X": "0066", "Y": "00CA" },
          { "X": "FFFF", "Y": "FFFF" },
          { "X": "0096", "Y": "00FA" },
          { "X": "0097", "Y": "00FB" }
        ]
      }
    }
  }
}
```

---

## GetRatingFeedback — Rating scale

Request a rating from the customer on a scale of 0–10. Use for NPS surveys, service feedback, or product ratings.

### OutputText structure

| Index | Purpose | Example |
|-------|---------|---------|
| 0 | Prompt | `"How was your experience today?"` |

### Request

```json
{
  "SaleToPOIRequest": {
    "MessageHeader": {
      "ProtocolVersion": "3.0",
      "MessageClass": "Device",
      "MessageCategory": "Input",
      "MessageType": "Request",
      "ServiceID": "input008",
      "SaleID": "POS-01",
      "POIID": "T400M-123456789"
    },
    "InputRequest": {
      "DisplayOutput": {
        "Device": "CustomerDisplay",
        "InfoQualify": "Display",
        "OutputContent": {
          "OutputFormat": "Text",
          "PredefinedContent": {
            "ReferenceID": "GetRatingFeedback"
          },
          "OutputText": [
            { "Text": "How was your experience today?" }
          ]
        },
        "MenuEntry": [
          { "OutputFormat": "Text", "OutputText": [{ "Text": "0" }] },
          { "OutputFormat": "Text", "OutputText": [{ "Text": "1" }] },
          { "OutputFormat": "Text", "OutputText": [{ "Text": "2" }] },
          { "OutputFormat": "Text", "OutputText": [{ "Text": "3" }] },
          { "OutputFormat": "Text", "OutputText": [{ "Text": "4" }] },
          { "OutputFormat": "Text", "OutputText": [{ "Text": "5" }] },
          { "OutputFormat": "Text", "OutputText": [{ "Text": "6" }] },
          { "OutputFormat": "Text", "OutputText": [{ "Text": "7" }] },
          { "OutputFormat": "Text", "OutputText": [{ "Text": "8" }] },
          { "OutputFormat": "Text", "OutputText": [{ "Text": "9" }] },
          { "OutputFormat": "Text", "OutputText": [{ "Text": "10" }] }
        ]
      },
      "InputData": {
        "Device": "CustomerInput",
        "InfoQualify": "Input",
        "InputCommand": "GetMenuEntry",
        "MaxInputTime": 60
      }
    }
  }
}
```

### Response

```json
{
  "SaleToPOIResponse": {
    "MessageHeader": { ... },
    "InputResponse": {
      "InputResult": {
        "Device": "CustomerInput",
        "InfoQualify": "Input",
        "Response": {
          "Result": "Success"
        },
        "Input": {
          "InputCommand": "GetMenuEntry",
          "MenuEntryNumber": [9]
        }
      }
    }
  }
}
```

> **Note:** `MenuEntryNumber` is 1-indexed. A value of `[9]` corresponds to rating "8" (the 9th item in the 0–10 list).

---

## Cancellation and timeout

### Customer cancels

If the customer presses the cancel/close button:

```json
{
  "SaleToPOIResponse": {
    "MessageHeader": { ... },
    "InputResponse": {
      "InputResult": {
        "Device": "CustomerInput",
        "InfoQualify": "Input",
        "Response": {
          "Result": "Failure",
          "ErrorCondition": "Cancel",
          "AdditionalResponse": "User cancelled"
        }
      }
    }
  }
}
```

### Timeout

If `MaxInputTime` is exceeded without input:

```json
{
  "SaleToPOIResponse": {
    "MessageHeader": { ... },
    "InputResponse": {
      "InputResult": {
        "Device": "CustomerInput",
        "InfoQualify": "Input",
        "Response": {
          "Result": "Failure",
          "ErrorCondition": "Timeout",
          "AdditionalResponse": "Input timeout exceeded"
        }
      }
    }
  }
}
```

---

## See also

- [Show the standby screen](./display-standby.md)
- [Show a virtual receipt](./display-receipt.md)
- [Make a payment](./make-payment.md)
