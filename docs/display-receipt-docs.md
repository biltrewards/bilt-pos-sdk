# Show a virtual receipt on the terminal

Display an itemised purchase summary on the customer-facing terminal screen before the payment transaction begins. You can also embed a QR code or a banner image in the receipt.

A display request does not time out. The terminal continues to show the receipt until you send a new request — for example, a payment request or a request to show the standby screen.

---

## Overview

The content you want to display is described in an XML document, encoded as a Base64 string, and passed inside a nexo `DisplayRequest`. The XML format described here is our own format (`urn:bilt:display:v1`). A limited subset of the Adyen virtual receipt format is also accepted — see [Adyen format support](#adyen-format-support).

The XML document is the **only** place you define display content. Transport context — terminal ID, sale ID, service ID, protocol version — belongs in the nexo `MessageHeader` and must not be repeated in the XML.

---

## XML payload format

The root element is `<displayPayload>`. It carries a `layout` attribute that identifies the XSLT used to render the content on the terminal, and contains exactly one content element.

```xml
<displayPayload
    xmlns="urn:bilt:display:v1"
    layout="receipt-qr.xslt"
    version="1.0">

  <!-- one of: <receipt>, <qrCode>, <image> -->

</displayPayload>
```

### `layout` values

| `layout` | Effect |
|---|---|
| `receipt.xslt` | Receipt, sticky header |
| `receipt-qr.xslt` | Receipt with QR code, sticky header |
| `qr.xslt` | Standalone QR code screen |
| `barcode.xslt` | Standalone barcode screen |
| `image.xslt` | Standalone image screen |

### Content types

Exactly one content element is allowed per payload.

| Element | Description |
|---|---|
| `<receipt>` | Itemised purchase summary. Can optionally embed a `<qrCode>` and/or an `<image>`. |
| `<qrCode>` | A QR code or barcode. Used standalone or embedded in a `<receipt>`. |
| `<image>` | A Base64-encoded image. Used standalone or embedded in a `<receipt>`. |

---

## Receipt

A `<receipt>` can contain the following elements, all optional:

| Element | Description |
|---|---|
| `<image>` | Banner image at the top of the receipt (Base64-encoded). |
| `<qrCode>` | QR code or barcode embedded in the receipt. |
| `<header><text>` | Section heading displayed above the line items. |
| `<lineItems>` | Container for one or more `<lineItem>` rows. |
| `<subtotal>` | Amount before taxes and discounts. |
| `<tax>` | Tax breakdown block. |
| `<total>` | Final amount due. |
| `<footer><text>` | Closing message displayed at the bottom of the receipt. |

### Line items

Each `<lineItem>` has a `kind` attribute that controls how the row is rendered:

| `kind` | Typical children | Description |
|---|---|---|
| `item` (default) | `description`, `quantity`, `unitPrice`, `amount`, optionally `image`, `subtitle` | A purchasable item |
| `heading` | `description` | A section label (e.g. SALES, RETURNS) |
| `discount` | `description`, `amount` (negative) | An applied discount |
| `return` | `description`, `quantity`, `unitPrice`, `amount` (negative) | A returned item |
| `separator` | _(none)_ | A horizontal rule |
| `spacer` | _(none)_ | A blank line |

### Tax block

The `<tax>` element can contain:

| Element | Occurrences | Description |
|---|---|---|
| `<totalDiscount>` | 0 or 1 | Sum of all discounts |
| `<taxItem>` | 0 or more | Individual tax line (e.g. state tax, county tax) |
| `<taxTotal>` | 0 or 1 | Total tax amount |

Each of these uses a `<description>` and `<amount>` child.

### Amounts

All monetary values use the `<currency>` + `<value>` pair:

```xml
<amount>
  <currency>$</currency>
  <value>79.99</value>
</amount>
```

---

## QR code and barcode

The `<qrCode>` element is used both as a standalone content type and embedded inside a `<receipt>`.

```xml
<qrCode type="qr">
  <header><text>Scan to access your member card</text></header>
  <data>https://example.com/loyalty?store=Store42</data>
  <callToAction>Scan now</callToAction>
  <footer><text>Don't have the app yet? Scan to download</text></footer>
</qrCode>
```

The `type` attribute controls the symbology. Supported values: `qr`, `barcode128`, `pdf417`, `datamatrix`.

---

## Example: receipt with embedded QR code

```xml
<?xml version="1.0" encoding="UTF-8"?>
<displayPayload
    xmlns="urn:bilt:display:v1"
    layout="receipt-qr.xslt"
    version="1.0">

  <receipt>

    <image mediaType="image/png" altText="Store logo">iVBORw0KGgoAAAANS...</image>

    <qrCode type="qr">
      <header><text>Scan to access your member card</text></header>
      <data>https://example.com/signup?store=Store42&amp;pos=REG0042&amp;hash=AAhbcd=</data>
      <callToAction>Scan now</callToAction>
      <footer><text>Don't have the app yet? Scan to download</text></footer>
    </qrCode>

    <header><text>Your items</text></header>

    <lineItems>

      <lineItem kind="heading">
        <description>SALES</description>
      </lineItem>

      <lineItem kind="item">
        <image mediaType="image/png" altText="Running shoes">iVBORw0KGgoAAAANS...</image>
        <description>Running shoes</description>
        <subtitle>Size 10 / Blue</subtitle>
        <quantity>1</quantity>
        <unitPrice><currency>$</currency><value>79.99</value></unitPrice>
        <amount><currency>$</currency><value>79.99</value></amount>
      </lineItem>

      <lineItem kind="item">
        <description>Green T-shirt</description>
        <quantity>2</quantity>
        <unitPrice><currency>$</currency><value>9.89</value></unitPrice>
        <amount><currency>$</currency><value>19.78</value></amount>
      </lineItem>

      <lineItem kind="separator"/>

      <lineItem kind="heading">
        <description>RETURNS</description>
      </lineItem>

      <lineItem kind="return">
        <description>Grey T-shirt</description>
        <quantity>1</quantity>
        <unitPrice><currency>$</currency><value>12.99</value></unitPrice>
        <amount><currency>$</currency><value>-12.99</value></amount>
      </lineItem>

      <lineItem kind="spacer"/>

      <lineItem kind="heading">
        <description>DISCOUNTS</description>
      </lineItem>

      <lineItem kind="discount">
        <description>Loyalty discount</description>
        <amount><currency>$</currency><value>-4.48</value></amount>
      </lineItem>

      <lineItem kind="separator"/>

    </lineItems>

    <subtotal>
      <description>Subtotal</description>
      <amount><currency>$</currency><value>86.78</value></amount>
    </subtotal>

    <tax>
      <totalDiscount>
        <description>Total discount</description>
        <amount><currency>$</currency><value>-4.48</value></amount>
      </totalDiscount>
      <taxItem>
        <description>State tax</description>
        <amount><currency>$</currency><value>5.97</value></amount>
      </taxItem>
      <taxItem>
        <description>County tax</description>
        <amount><currency>$</currency><value>1.85</value></amount>
      </taxItem>
      <taxTotal>
        <description>Total tax</description>
        <amount><currency>$</currency><value>7.82</value></amount>
      </taxTotal>
    </tax>

    <total>
      <description>Total amount</description>
      <amount><currency>$</currency><value>90.12</value></amount>
    </total>

    <footer><text>Thank you! Returns accepted within 30 days.</text></footer>

  </receipt>

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

All incoming payloads are parsed for well-formedness. A malformed document (e.g. a missing closing tag) returns a failure response immediately.

The content is being validated before displaying and any element that is missing, misnamed, or out of order will produce an `XSD validation failed` error response. Validation error details are written to the terminal log.

---

## Adyen format support

The Adyen virtual receipt XML format is also accepted, to ease migration from an existing Adyen integration. The support is limited due to the xslt-s used being different. xsd validation is also not supported in this case. 

> **Recommendation:** use the native `urn:bilt:display:v1` format for all new development. The Adyen format is supported for backward compatibility only and may not receive support for future features.

---

## See also

- [Show a QR code on the terminal](./display-qr-code.md)
- [Show an image on the terminal](./display-image.md)
- [Show the standby screen](./idle-display.md)
- [Display payload XML schema](./display.xsd)
