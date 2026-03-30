---
---

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
| `<adjustments>` | Order-level discounts (loyalty rewards, coupons, etc.). |
| `<tax>` | Tax breakdown block. |
| `<total>` | Final amount due. |
| `<footer><text>` | Closing message displayed at the bottom of the receipt. |

### Line items

Each `<lineItem>` has a `kind` attribute that controls how the row is rendered:

| `kind` | Typical children | Description |
|---|---|---|
| `item` (default) | `description`, `quantity`, `unitPrice`, `amount`, optionally `image`, `subtitle`, `originalAmount`, `discount` | A purchasable item |
| `heading` | `description` | A section label (e.g. SALES, RETURNS). _Accepted but not rendered on terminal._ |
| `discount` | `description`, `amount` (negative) | An applied discount |
| `return` | `description`, `quantity`, `unitPrice`, `amount` (negative) | A returned item from a previous transaction. See [Returned items](#returned-items). |
| `void` | `description`, `quantity`, `unitPrice`, `amount` (negative) | A voided/cancelled item. Rendered with red text and "Voided from transaction" label. See [Voided items](#voided-items) for usage. |
| `separator` | _(none)_ | A horizontal rule. _Accepted but not rendered on terminal._ |
| `spacer` | _(none)_ | A blank line. _Accepted but not rendered on terminal._ |

### Discounts

Discounts can be applied at two levels:

#### Item-level discounts

Item-level discounts are attached directly to individual line items. Use these when a discount applies to a specific product (e.g., "20% off this item", "Buy one get one free").

For `kind="item"` line items, you can include:

| Element | Description |
|---|---|
| `<originalAmount>` | The price before the discount was applied (displayed with strikethrough) |
| `<section>` | One or more discount groups, each with a `<label>` and `<items>` containing individual discount descriptions |

Each `<section>` groups related discounts under a shared label:

```xml
<lineItem kind="item">
  <description>Merrell Moab 3 Mid WP Boot</description>
  <subtitle>Size 10 / Walnut</subtitle>
  <quantity>1</quantity>
  <unitPrice><currency>$</currency><value>144.95</value></unitPrice>
  <amount><currency>$</currency><value>78.97</value></amount>
  <originalAmount><currency>$</currency><value>144.95</value></originalAmount>
  <section>
    <label>Discount</label>
    <items>
      <item><description>20% Off Footwear Sale -$28.99</description></item>
      <item><description>Member discount -$14.50</description></item>
    </items>
  </section>
  <section>
    <label>CLEARANCE</label>
    <items>
      <item><description>End of season -$22.49</description></item>
    </items>
  </section>
</lineItem>
```

Multiple `<section>` elements can be included when an item has different types of discounts (e.g., regular discounts + clearance). Discounts with the same label are grouped together under a single `<section>` element with multiple `<item>` entries.

#### Order-level discounts

Order-level discounts apply to the entire order rather than specific items. Use these for loyalty rewards, coupon codes, or store-wide promotions that aren't tied to individual products.

Order-level discounts appear in a dedicated `<adjustments>` block between `<subtotal>` and `<tax>`:

```xml
<adjustments>
  <adjustmentItem>
    <description>Loyalty reward</description>
    <amount><currency>$</currency><value>-5.00</value></amount>
  </adjustmentItem>
  <adjustmentItem>
    <description>Coupon: SAVE10</description>
    <amount><currency>$</currency><value>-10.00</value></amount>
  </adjustmentItem>
</adjustments>
```

> **Design decision:** Item-level discounts are displayed inline with the product they apply to (showing original price struck through and the discount badge). Order-level discounts appear in a separate `<adjustments>` block below the subtotal, keeping them distinct from the `<tax>` block which contains only tax-related items. This separation makes the XML structure clearer and helps customers understand which discounts are product-specific vs. cart-wide.

### Voided items

When an item is voided (removed from the transaction), the POS must send **both** the original item line **and** a void line. This approach provides a clear audit trail showing what the customer originally added and what was subsequently voided.

#### Example: Single item voided

Customer adds a jacket, then decides they don't want it:

```xml
<lineItems>
  <!-- Original item as it was added -->
  <lineItem kind="item">
    <description>The North Face Jacket</description>
    <quantity>1</quantity>
    <unitPrice><currency>$</currency><value>230.00</value></unitPrice>
    <amount><currency>$</currency><value>230.00</value></amount>
  </lineItem>

  <!-- Void entry with negative amount -->
  <lineItem kind="void">
    <description>The North Face Jacket</description>
    <subtitle>Wrong size</subtitle>
    <quantity>1</quantity>
    <unitPrice><currency>$</currency><value>230.00</value></unitPrice>
    <amount><currency>$</currency><value>-230.00</value></amount>
  </lineItem>
</lineItems>
```

The void line is rendered with red text and a "Voided from transaction" label. The `subtitle` field can optionally explain the reason for voiding.

#### Example: One of three items voided

Customer adds 3 t-shirts, then removes 1:

```xml
<lineItems>
  <!-- Original item showing full quantity added -->
  <lineItem kind="item">
    <description>Blue T-shirt</description>
    <quantity>3</quantity>
    <unitPrice><currency>$</currency><value>19.99</value></unitPrice>
    <amount><currency>$</currency><value>59.97</value></amount>
  </lineItem>

  <!-- Void entry for 1 item -->
  <lineItem kind="void">
    <description>Blue T-shirt</description>
    <subtitle>Customer changed mind</subtitle>
    <quantity>1</quantity>
    <unitPrice><currency>$</currency><value>19.99</value></unitPrice>
    <amount><currency>$</currency><value>-19.99</value></amount>
  </lineItem>
</lineItems>
```

> **Important for POS implementers:** Do NOT simply reduce the quantity on the original item when voiding. Always send the original item as it was added, plus a separate void line. This pattern:
> - Shows customers a clear history of what happened
> - Provides an audit trail for the transaction
> - Matches standard retail receipt conventions

### Returned items

Returns are items from a **previous transaction** being returned for a refund. Returns and purchases are always separate transactions — you cannot mix returns with new purchases in the same transaction.

```xml
<!-- Return transaction (separate from any purchase) -->
<lineItems>
  <lineItem kind="return">
    <description>Blue T-shirt</description>
    <subtitle>Original purchase: Mar 15, 2024</subtitle>
    <quantity>1</quantity>
    <unitPrice><currency>$</currency><value>19.99</value></unitPrice>
    <amount><currency>$</currency><value>-19.99</value></amount>
  </lineItem>
</lineItems>
```

> **Void vs Return:**
> - Use `void` when an item is removed from the **current** transaction before checkout
> - Use `return` when an item from a **previous** transaction is being returned for refund
> - These are always separate transactions — never combine purchases and returns

### Adjustments block

The `<adjustments>` element contains order-level discounts:

| Element | Occurrences | Description |
|---|---|---|
| `<adjustmentItem>` | 0 or more | Individual order-level discount (e.g. loyalty reward, coupon) |

### Tax block

The `<tax>` element contains only tax-related items:

| Element | Occurrences | Description |
|---|---|---|
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
        <image mediaType="image/png" altText="Merrell hiking boot">iVBORw0KGgoAAAANS...</image>
        <description>Merrell Moab 3 Mid WP Boot</description>
        <subtitle>Size 10 / Walnut</subtitle>
        <quantity>1</quantity>
        <unitPrice><currency>$</currency><value>144.95</value></unitPrice>
        <amount><currency>$</currency><value>78.97</value></amount>
        <originalAmount><currency>$</currency><value>144.95</value></originalAmount>
        <section>
          <label>Discount</label>
          <items>
            <item><description>20% Off Footwear Sale -$28.99</description></item>
            <item><description>Member discount -$14.50</description></item>
          </items>
        </section>
        <section>
          <label>CLEARANCE</label>
          <items>
            <item><description>End of season -$22.49</description></item>
          </items>
        </section>
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

    </lineItems>

    <subtotal>
      <description>Subtotal</description>
      <amount><currency>$</currency><value>86.78</value></amount>
    </subtotal>

    <adjustments>
      <adjustmentItem>
        <description>Loyalty reward</description>
        <amount><currency>$</currency><value>-5.00</value></amount>
      </adjustmentItem>
    </adjustments>

    <tax>
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
