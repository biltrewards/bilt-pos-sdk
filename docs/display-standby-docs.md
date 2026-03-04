# Show the standby screen

Return the terminal to its idle/standby state. You can optionally specify a theme to display a seasonal or branded variant of the standby screen — for example during a holiday campaign.

Send a standby request when you want to clear any previously displayed content (a receipt, QR code, or image) and return the terminal to its default idle appearance.

---

## Overview

The standby payload is the simplest of the display content types — it carries no content other than an optional `theme` attribute. The `standby.xslt` layout handles the rendering and uses the theme value, if provided, to select the appropriate visual variant.

---

## XML payload format

```xml
<displayPayload
    xmlns="urn:bilt:display:v1"
    layout="standby.xslt"
    version="1.0">

  <standby/>

</displayPayload>
```

### `<standby>` attributes

| Attribute | Required | Description |
|---|---|---|
| `theme` | No | A free string identifying the visual variant to display. The available themes depend on the XSLTs deployed on your terminal. If omitted or unrecognised, the default standby screen is shown. |

---

## Examples

### Default standby screen

```xml
<?xml version="1.0" encoding="UTF-8"?>
<displayPayload
    xmlns="urn:bilt:display:v1"
    layout="standby.xslt"
    version="1.0">

  <standby/>

</displayPayload>
```

### Themed standby screen

```xml
<?xml version="1.0" encoding="UTF-8"?>
<displayPayload
    xmlns="urn:bilt:display:v1"
    layout="standby.xslt"
    version="1.0">

  <standby theme="christmas"/>

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

- [Show a virtual receipt](./display-receipt.md)
- [Show a QR code or barcode](./display-qr-code.md)
- [Show an image on the terminal](./display-image.md)
- [Display payload XML schema](./display.xsd)
