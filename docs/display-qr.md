---
---

# Show a QR code or barcode on the terminal

Display a QR code or barcode on the customer-facing terminal screen. This can be used standalone — for example to present a loyalty card, a download link, or a sign-up prompt — or embedded inside a receipt. This page covers the standalone use case; for embedding inside a receipt see [Show a virtual receipt](./display-receipt.md).

A display request does not time out. The terminal continues to show the content until you send a new request — for example, a payment request or a request to show the standby screen.

---

## Overview

The content is described in an XML document, encoded as a Base64 string, and passed inside a nexo `DisplayRequest`. The `<qrCode>` element supports QR codes as well as several barcode symbologies via the `type` attribute.

---

## XML payload format

Use a `<displayPayload>` root with a `<qrCode>` content element and a `layout` that matches the desired symbology.

```xml
<displayPayload
    xmlns="urn:bilt:display:v1"
    layout="qr.xslt"
    version="1.0">

  <qrCode type="qr">
    <header><text>Scan to join our loyalty programme</text></header>
    <data>https://example.com/loyalty?store=Store42</data>
    <callToAction>Scan now</callToAction>
    <footer><text>Earn points on every purchase</text></footer>
  </qrCode>

</displayPayload>
```

### `<qrCode>` elements

All child elements are optional except `<data>`.

| Element | Required | Description |
|---|---|---|
| `<header><text>` | No | Heading displayed above the code |
| `<data>` | ✅ | The value encoded in the code. Use a URI for QR codes, a raw string for barcodes. Must be XML-escaped (e.g. `&amp;` for `&`). |
| `<callToAction>` | No | Short instruction displayed alongside or below the code (e.g. "Scan now", "Present to cashier") |
| `<footer><text>` | No | Secondary message displayed below the code |

### `type` attribute

The `type` attribute on `<qrCode>` controls the symbology rendered by the XSLT. 

| `type` | `layout` | Description |
|---|---|---|
| `qr` (default) | `qr.xslt` | QR code |
| `barcode128` | `barcode.xslt` | Code 128 barcode |
| `pdf417` | `barcode.xslt` | PDF417 barcode |
| `datamatrix` | `barcode.xslt` | Data Matrix barcode |

> The `layout` attribute selects the XSLT; the `type` attribute tells that XSLT which symbology to render. When using `qr.xslt`, the `type` attribute is ignored and a QR code is always rendered.

---

## Examples

### Standalone QR code

```xml
<?xml version="1.0" encoding="UTF-8"?>
<displayPayload
    xmlns="urn:bilt:display:v1"
    layout="qr.xslt"
    version="1.0">

  <qrCode type="qr">
    <header><text>Scan to join our loyalty programme</text></header>
    <data>https://example.com/loyalty?store=Store42</data>
    <callToAction>Scan now</callToAction>
    <footer><text>Earn points on every purchase</text></footer>
  </qrCode>

</displayPayload>
```

### Standalone barcode (Code 128)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<displayPayload
    xmlns="urn:bilt:display:v1"
    layout="barcode.xslt"
    version="1.0">

  <qrCode type="barcode128">
    <header><text>Your loyalty card</text></header>
    <data>1234567890128</data>
    <callToAction>Present to cashier</callToAction>
  </qrCode>

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

All incoming payloads are parsed for well-formedness. A malformed document returns a failure response immediately.

Optionally, XSD validation against `display.xsd` can be enabled. When active, any element that is missing, misnamed, or out of order will produce an `XSD validation failed` error response. Validation error details are written to the terminal log.

---

## See also

- [Show a virtual receipt](./display-receipt.md)
- [Show an image on the terminal](./display-image.md)
- [Show the standby screen](./display-standby.md)
- [Display payload XML schema](./display.xsd)
