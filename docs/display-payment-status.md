---
---

# Show a payment status screen

Display a payment status screen on the terminal to inform the customer of the current transaction state. The screen shows an icon, title, and subtitle appropriate to the payment status.

Use this display type during payment processing to provide visual feedback to the customer — for example while authorizing a transaction, or to confirm approval or decline.

---

## Overview

The payment status payload specifies a `state` that determines the visual appearance (icon and color), along with customizable `title` and `subtitle` text. The `payment-status.xslt` layout renders the appropriate screen based on the state value.

---

## XML payload format

```xml
<displayPayload
    xmlns="urn:bilt:display:v1"
    layout="payment-status.xslt"
    version="1.0">

  <paymentStatus
      state="authorizing"
      title="Authorizing payment"
      subtitle="Your transaction is processing" />

</displayPayload>
```

### `<paymentStatus>` attributes

| Attribute | Required | Description |
|---|---|---|
| `state` | Yes | The payment state. Must be one of: `authorizing`, `approved`, or `declined`. Determines the icon displayed. |
| `title` | Yes | The main heading text displayed on the screen. |
| `subtitle` | Yes | Secondary text displayed below the title. |

### State values

| State | Icon | Description |
|---|---|---|
| `authorizing` | Spinner | Animated spinner indicating the transaction is in progress. |
| `approved` | Checkmark | Green checkmark indicating successful payment. |
| `declined` | X | Red X indicating the payment was declined. |

---

## Examples

### Authorizing

Display while the payment is being processed:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<displayPayload
    xmlns="urn:bilt:display:v1"
    layout="payment-status.xslt"
    version="1.0">

  <paymentStatus
      state="authorizing"
      title="Authorizing payment"
      subtitle="Your transaction is processing" />

</displayPayload>
```

### Approved

Display when the payment is successful:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<displayPayload
    xmlns="urn:bilt:display:v1"
    layout="payment-status.xslt"
    version="1.0">

  <paymentStatus
      state="approved"
      title="Payment approved"
      subtitle="Thank you for shopping with us" />

</displayPayload>
```

### Declined

Display when the payment is declined:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<displayPayload
    xmlns="urn:bilt:display:v1"
    layout="payment-status.xslt"
    version="1.0">

  <paymentStatus
      state="declined"
      title="Payment declined"
      subtitle="Your transaction could not be processed" />

</displayPayload>
```

---

## Make a display request

1. **Build the XML payload** using the format described above.
2. **Encode it** as a Base64 string.
3. **Send a nexo `DisplayRequest`** with the following `MessageHeader` fields:

   - **`ProtocolVersion`** — `3.0`
   - **`MessageClass`** — `Device`
   - **`MessageCategory`** — `Display`
   - **`MessageType`** — `Request`
   - **`ServiceID`** — Unique ID, 1–10 alphanumeric characters, unique within 48 hours per terminal.
   - **`SaleID`** — Your POS system identifier.
   - **`POIID`** — Target terminal ID, format: `[model]-[serial]`.

   And the following `DisplayOutput` fields:

   - **`Device`** — `CustomerDisplay`
   - **`InfoQualify`** — `Display`
   - **`OutputContent.OutputFormat`** — `XHTML`
   - **`OutputContent.OutputXHTML`** — Your Base64-encoded XML string.

4. **Check the response.** A successful response includes `Response.Result: Success`. On failure, `AdditionalResponse` and `ErrorCondition` describe the problem.

---

## XML validation

The content is validated before displaying. Any element that is missing, misnamed, or out of order will produce an `XSD validation failed` error response. Validation error details are written to the terminal log.

---

## See also

- [Show the standby screen](./display-standby.md)
- [Show a virtual receipt](./display-receipt.md)
- [Show a QR code or barcode](./display-qr.md)
- [Show an image on the terminal](./display-image.md)
- [Display payload XML schema](./display.xsd)
