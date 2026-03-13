# Collect input from the terminal

Prompt the shopper or cashier for input on the terminal using an `InputRequest`.

An input request displays a message on the terminal and waits for the user to respond. The type of input is controlled by the `InputCommand` field. You can collect confirmations, text, numbers, menu selections, signature.

---

## Input commands

| InputCommand | Purpose | Response field | Doc |
|---|---|---|---|
| `GetConfirmation` | Yes/No confirmation | `ConfirmedFlag` | [GetConfirmation](./input-get-confirmation.md) |
| `GetConfirmation` | Signature capture | `ConfirmedFlag` + `AdditionalResponse` | [Signature](./input-signature.md) |
| `TextString` | Free-text input | `TextInput` | [TextString](./input-text-string.md) |
| `DigitString` | Numeric-only input | `DigitInput` | [DigitString](./input-digit-string.md) |
| `DecimalString` | Decimal number input | `TextInput` | [DecimalString](./input-decimal-string.md) |
| `GetMenuEntry` | Menu selection (single or multiple) | `MenuEntryNumber` | [GetMenuEntry](./input-get-menu-entry.md) |

---

## Common request fields

Every input request uses `MessageCategory: Input` and `MessageClass: Device`. The `InputData` object specifies the type of input and its constraints, and the optional `DisplayOutput` object controls what is shown on the terminal screen.

`MessageHeader` fields:

- **`ProtocolVersion`** — `3.0`
- **`MessageClass`** — `Device`
- **`MessageCategory`** — `Input`
- **`MessageType`** — `Request`
- **`ServiceID`** — Unique ID for this request, 1–10 alphanumeric characters, unique within 48 hours per terminal.
- **`SaleID`** — Your POS system identifier.
- **`POIID`** — Target terminal ID, format: `[model]-[serial]`.

`InputData` fields (common to all commands):

- **`Device`** — The input device. Use `CustomerInput` for the shopper-facing device.
- **`InfoQualify`** — `Input`.
- **`InputCommand`** — The type of input to collect. See the table above.
- **`MaxInputTime`** — *(optional)* Maximum seconds to wait for the user to respond. When the timeout expires, the terminal displays a "Please wait…" overlay and returns an `InputResponse` with `ErrorCondition: Cancel`. A visual countdown progress bar is displayed while the timer is active.
- **`DefaultInputString`** — *(optional)* Pre-fills the input field with a default value. The value is displayed as a placeholder until the user starts typing. If the user confirms without typing, the placeholder value is submitted. For `GetConfirmation`, use `"Y"` or `"N"` to pre-select yes or no.
- **`DisableCancelFlag`** — *(optional)* When `true`, the Cancel button is hidden and the user cannot cancel the input. Default `false`.
- **`DisableValidFlag`** — *(optional)* When `true`, the Confirm/Valid button is hidden. Use this when input should be confirmed via another mechanism (e.g., automatic submission on reaching `MaxLength`). Default `false`.
- **`WaitUserValidationFlag`** — *(optional)* When `false` (default) and `MaxLength` is set, the input automatically submits once the user reaches `MaxLength`. When `true`, the user must explicitly press confirm even after reaching `MaxLength`.

`DisplayOutput` fields (common to all commands):

- **`Device`** — `CustomerDisplay`
- **`InfoQualify`** — `Display`
- **`OutputContent.OutputFormat`** — `XHTML`
- **`OutputContent.OutputXHTML`** — Base64-encoded XML payload following the `input.xsd` schema.

### Input payload

The display content is defined by an XML payload that is Base64-encoded and placed in `OutputXHTML`. The payload uses the `urn:bilt:input:v1` namespace:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<inputPayload xmlns="urn:bilt:input:v1" version="1.0">
  <display>
    <title>Your prompt here</title>
    <text>Optional additional text line(s)</text>
  </display>
  <!-- For GetConfirmation: -->
  <confirmation>
    <confirmButton>Yes</confirmButton>
    <cancelButton>No</cancelButton>
  </confirmation>
</inputPayload>
```

- **`<display>`** — *(optional)* Title and text lines shown above the input control.
  - **`<title>`** — The main prompt text.
  - **`<text>`** — *(zero or more)* Additional text lines.
- **`<confirmation>`** — *(optional)* Custom button labels for confirmation-style inputs. If omitted, default labels are used.
  - **`<confirmButton>`** — Label for the confirm button.
  - **`<cancelButton>`** — Label for the cancel button.
- **`<signature>`** — Alternative to `<confirmation>`. Renders a signature capture screen.

For commands like `TextString`, `DigitString`, `DecimalString`, and `GetMenuEntry`, only the `<display>` block is needed — the terminal renders the appropriate input control based on the `InputCommand`.

---

## Common response fields

The result is returned in an `InputResponse` body. The main result is in `InputResponse.InputResult.Response.Result`.

- **`InputResult.Device`** — The device that captured the input.
- **`InputResult.InfoQualify`** — `Input`.
- **`InputResult.Response.Result`** — `Success` or `Failure`.
- **`InputResult.Input`** — The data entered by the user. The specific field depends on the `InputCommand` (see table above).

### Failed input

When an input request fails, the response includes `Result: Failure` with an `ErrorCondition` indicating the reason:

- **`Cancel`** — The user pressed Cancel, or the `MaxInputTime` timeout expired. When a timeout occurs, the terminal displays a "Please wait…" overlay with a spinner before sending the response.
- **`Busy`** — The terminal is processing another request (e.g. a payment arrived while waiting for input).
- **`DeviceOut`** — The terminal is unavailable.

For general guidance on handling failed requests, see [Handle responses](./error-scenarios.md).

---

## Input timeout behavior

When `MaxInputTime` is specified, the terminal displays a countdown progress bar at the top of the input screen. The progress bar visually indicates the remaining time.

When the timeout expires:

1. The input control is disabled
2. A "Please wait…" overlay with a spinner is displayed
3. An `InputResponse` is sent with `ErrorCondition: Cancel`

The POS cannot distinguish between a user-initiated cancel and a timeout via the `ErrorCondition` alone — both return `Cancel`. If you need to differentiate, track the elapsed time on the POS side.

> **Note:** If the user submits input at the exact moment the timeout fires, the terminal guarantees only one response is sent — either the user's input or the timeout, but never both.
