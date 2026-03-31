---
---

# Show an image on the terminal

Display a full-screen image on the customer-facing terminal screen. This can be used standalone — for example to show a promotional banner, a thank-you screen, or a brand graphic.

A display request does not time out. The terminal continues to show the content until you send a new request — for example, a payment request or a request to show the standby screen.

---

## Overview

The content is described in an XML document, encoded as a Base64 string, and passed inside a nexo `DisplayRequest`. The image itself is also Base64-encoded and embedded directly in the XML payload.

---

## XML payload format

Use a `<displayPayload>` root with an `<image>` content element.

```xml
<displayPayload
    xmlns="urn:bilt:display:v1"
    layout="image.xslt"
    version="1.0">

  <image mediaType="image/png" altText="Thank you for your purchase!">
    iVBORw0KGgoAAAANS...
  </image>

</displayPayload>
```

### `<image>` attributes

| Attribute | Required | Description |
|---|---|---|
| `mediaType` | No | MIME type of the image. Default: `image/png`. Supported values: `image/png`, `image/jpeg`. |
| `altText` | No | Short description of the image, used for logging and diagnostics. |

### Image content

The text content of the `<image>` element is the Base64-encoded binary of the image file. Encode the raw image bytes — not a data URI (i.e. do not include the `data:image/png;base64,` prefix).

**Image guidelines:**
- Format: PNG (recommended) or JPEG
- Resolution: match your terminal's display resolution for best results; the XSLT will scale the image to fit but oversized images increase payload size
- Transparency: supported for PNG only
- Colour profile: sRGB

---

## Example

```xml
<?xml version="1.0" encoding="UTF-8"?>
<displayPayload
    xmlns="urn:bilt:display:v1"
    layout="image.xslt"
    version="1.0">

  <image mediaType="image/png" altText="Thank you for your purchase!">
    iVBORw0KGgoAAAANSUhEUgAA...
  </image>

</displayPayload>
```

---

## Make a display request

1. **Prepare the image** — encode your image file as a Base64 string.
2. **Build the XML payload** using the format described above, embedding the Base64 string as the content of `<image>`.
3. **Encode the full XML document** as a second Base64 string.
4. **Send a nexo `DisplayRequest`** with the following `MessageHeader` fields:

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

5. **Check the response.** A successful response includes `Response.Result: Success`. On failure, `AdditionalResponse` and `ErrorCondition` describe the problem.

> **Payload size:** because the image is Base64-encoded twice (once as image content, once as the XML wrapper), a large image can significantly increase payload size. If you encounter message size errors, reduce the image dimensions or compress the source file before encoding.

---

## XML validation

All incoming payloads are parsed for well-formedness. A malformed document returns a failure response immediately.

Optionally, XSD validation against `display.xsd` can be enabled. When active, any element that is missing, misnamed, or out of order will produce an `XSD validation failed` error response. Validation error details are written to the terminal log.

---

## See also

- [Show a virtual receipt](./display-receipt.md)
- [Show a QR code or barcode](./display-qr-code.md)
- [Show the standby screen](./idle-display.md)
- [Display payload XML schema](./display.xsd)
