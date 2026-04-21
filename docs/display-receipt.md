---
---

# Show a virtual receipt on the terminal

Display an itemised purchase summary on the customer-facing terminal screen before the payment transaction begins. You can also embed a QR code in the receipt.

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
| `<receipt>` | Itemised purchase summary. Can optionally embed a `<qrCode>`. |
| `<qrCode>` | A QR code or barcode. Used standalone or embedded in a `<receipt>`. |
| `<image>` | A Base64-encoded image. Used standalone. |

---

## Receipt

A `<receipt>` can contain the following elements, all optional:

| Element | Description |
|---|---|
| `<qrCode>` | QR code or barcode embedded in the receipt. |
| `<waiting>` | Status message shown during processing (e.g. `<waiting>Processing payment…</waiting>`). Empty `<waiting/>` defaults to "Waiting for cashier". |
| `<header><text>` | Section heading displayed above the line items. |
| `<lineItems>` | Container for one or more `<lineItem>` rows. |
| `<subtotal>` | Amount before taxes and discounts. |
| `<adjustments>` | Order-level discounts (loyalty rewards, coupons, etc.). |
| `<tax>` | Tax breakdown block. |
| `<total>` | Final amount due. |
| `<footer><text>` | Closing message displayed at the bottom of the receipt. _Accepted but not rendered on terminal._ |

### Line items

Each `<lineItem>` has a `kind` attribute that controls how the row is rendered:

| `kind` | Typical children | Description |
|---|---|---|
| `item` (default) | `description`, `quantity`, `unitPrice`, `amount`, optionally `image`, `subtitle`, `originalAmount`, `section` | A purchasable item |
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

> ⚠️ **IMPORTANT:** A void line item (`kind="void"`) must ALWAYS be immediately preceded by its corresponding original item (`kind="item"`). The original and void must be consecutive — never send a void line item without its original item directly before it. The customer display needs both to show the complete transaction history. The subtotal/total should reflect the net amount after accounting for the void.

#### How void items are rendered

| Element | XML Value | Rendering |
|---|---|---|
| `quantity` | Negative (e.g., `-1`) | Displayed as negative (e.g., "-1 @ $19.99") |
| `unitPrice` | Positive (e.g., `19.99`) | Displayed as positive |
| `amount` | Negative (e.g., `-19.99`) | Red text, negative value |
| `description` | — | Red text |
| `subtitle` | — | Optional (e.g., SKU or product details) |
| Label | — | "Voided from transaction" label shown below |

**Key difference from regular items:** Regular items only show quantity when > 1. Void items **always** show quantity to clearly indicate the removal.

#### Example 1: Single item voided

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

  <!-- Void entry - negative quantity and amount -->
  <lineItem kind="void">
    <description>The North Face Jacket</description>
    <quantity>-1</quantity>
    <unitPrice><currency>$</currency><value>230.00</value></unitPrice>
    <amount><currency>$</currency><value>-230.00</value></amount>
  </lineItem>
</lineItems>
```

**Rendered as:**
- Original: "The North Face Jacket" — $230.00
- Void: "The North Face Jacket" — -$230.00, "-1 @ $230.00", "Voided from transaction" (red text)

#### Example 2: Multiple quantity item, one voided

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

  <!-- Void entry for 1 item - negative quantity and amount -->
  <lineItem kind="void">
    <description>Blue T-shirt</description>
    <quantity>-1</quantity>
    <unitPrice><currency>$</currency><value>19.99</value></unitPrice>
    <amount><currency>$</currency><value>-19.99</value></amount>
  </lineItem>
</lineItems>
```

**Rendered as:**
- Original: "Blue T-shirt" — $59.97, "3 @ $19.99"
- Void: "Blue T-shirt" — -$19.99, "-1 @ $19.99", "Voided from transaction" (red text)

#### Example 3: Multiple quantity item, two voided

Customer adds 3 hiking socks, then removes 2:

```xml
<lineItems>
  <!-- Original item showing full quantity added -->
  <lineItem kind="item">
    <description>Darn Tough Hiking Socks</description>
    <quantity>3</quantity>
    <unitPrice><currency>$</currency><value>25.99</value></unitPrice>
    <amount><currency>$</currency><value>77.97</value></amount>
  </lineItem>

  <!-- Void entry for 2 items - negative quantity and amount -->
  <lineItem kind="void">
    <description>Darn Tough Hiking Socks</description>
    <quantity>-2</quantity>
    <unitPrice><currency>$</currency><value>25.99</value></unitPrice>
    <amount><currency>$</currency><value>-51.98</value></amount>
  </lineItem>
</lineItems>
```

**Rendered as:**
- Original: "Darn Tough Hiking Socks" — $77.97, "3 @ $25.99"
- Void: "Darn Tough Hiking Socks" — -$51.98, "-2 @ $25.99", "Voided from transaction" (red text)

#### Example 4: Mixed transaction with multiple voids

A more complex transaction showing various void scenarios:

```xml
<lineItems>
  <!-- Item 1: No void -->
  <lineItem kind="item">
    <description>Columbia FlexROC Pants</description>
    <quantity>1</quantity>
    <unitPrice><currency>$</currency><value>74.99</value></unitPrice>
    <amount><currency>$</currency><value>74.99</value></amount>
  </lineItem>

  <!-- Item 2: Single item, fully voided -->
  <lineItem kind="item">
    <description>Patagonia Fleece Jacket</description>
    <quantity>1</quantity>
    <unitPrice><currency>$</currency><value>139.00</value></unitPrice>
    <amount><currency>$</currency><value>139.00</value></amount>
  </lineItem>

  <lineItem kind="void">
    <description>Patagonia Fleece Jacket</description>
    <quantity>-1</quantity>
    <unitPrice><currency>$</currency><value>139.00</value></unitPrice>
    <amount><currency>$</currency><value>-139.00</value></amount>
  </lineItem>

  <!-- Item 3: Multiple quantity, partial void -->
  <lineItem kind="item">
    <description>Smartwool Hiking Socks</description>
    <quantity>4</quantity>
    <unitPrice><currency>$</currency><value>22.99</value></unitPrice>
    <amount><currency>$</currency><value>91.96</value></amount>
  </lineItem>

  <lineItem kind="void">
    <description>Smartwool Hiking Socks</description>
    <quantity>-2</quantity>
    <unitPrice><currency>$</currency><value>22.99</value></unitPrice>
    <amount><currency>$</currency><value>-45.98</value></amount>
  </lineItem>
</lineItems>
```

> **Important for POS implementers:**
> - Do NOT reduce the quantity on the original item when voiding — always send the original item as it was added, plus a separate void line
> - For the void line, send **negative** values for `quantity` and `amount` (e.g., `<quantity>-1</quantity>`, `<amount>...<value>-230.00</value></amount>`)
> - The `unitPrice` should remain **positive** (it's the price per unit, not a refund)
> - This pattern shows customers a clear history, provides an audit trail, and matches standard retail receipt conventions

### Returned items

Returns are items from a **previous transaction** being returned for a refund. Returns and purchases are always separate transactions — you cannot mix returns with new purchases in the same transaction.

#### How return items are rendered

| Element | Rendering |
|---|---|
| `description` | Standard text |
| `quantity` | Displayed with unit price (e.g., "2 @ $19.99") |
| `amount` | Negative value (refund amount) |
| `subtitle` | Optional - typically shows original purchase date |

#### Example 1: Single item return

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

#### Example 2: Multiple items return

```xml
<lineItems>
  <lineItem kind="return">
    <description>Smartwool Hiking Socks</description>
    <subtitle>Original purchase: Mar 10, 2024</subtitle>
    <quantity>2</quantity>
    <unitPrice><currency>$</currency><value>22.99</value></unitPrice>
    <amount><currency>$</currency><value>-45.98</value></amount>
  </lineItem>
</lineItems>
```

#### Example 3: Complete return transaction

A full return transaction showing multiple items with totals:

```xml
<receipt>
  <header><text>Return - #RET-12345</text></header>
  <lineItems>
    <lineItem kind="return">
      <description>The North Face Jacket</description>
      <subtitle>Original purchase: Mar 5, 2024</subtitle>
      <quantity>1</quantity>
      <unitPrice><currency>$</currency><value>230.00</value></unitPrice>
      <amount><currency>$</currency><value>-230.00</value></amount>
    </lineItem>
    <lineItem kind="return">
      <description>Columbia Hiking Pants</description>
      <subtitle>Original purchase: Mar 5, 2024</subtitle>
      <quantity>1</quantity>
      <unitPrice><currency>$</currency><value>74.99</value></unitPrice>
      <amount><currency>$</currency><value>-74.99</value></amount>
    </lineItem>
  </lineItems>
  <subtotal>
    <description>Subtotal</description>
    <amount><currency>$</currency><value>-304.99</value></amount>
  </subtotal>
  <tax>
    <taxTotal>
      <description>Tax Refund</description>
      <amount><currency>$</currency><value>-27.07</value></amount>
    </taxTotal>
  </tax>
  <total>
    <description>Refund Total</description>
    <amount><currency>$</currency><value>-332.06</value></amount>
  </total>
  <footer><text>Refund will be credited to original payment method</text></footer>
</receipt>
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
| `<totalDiscount>` | 0 or 1 | **DEPRECATED.** Use `<adjustments><adjustmentItem>` instead. Retained for backwards compatibility only. |
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

    <qrCode type="qr">
      <header><text>Scan to access your member card</text></header>
      <data>https://example.com/signup?store=Store42&amp;pos=REG0042&amp;hash=AAhbcd=</data>
      <callToAction>Scan now</callToAction>
      <footer><text>Don't have the app yet? Scan to download</text></footer>
    </qrCode>

    <header><text>Your items</text></header>

    <lineItems>

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

      <lineItem kind="item">
        <description>Green T-shirt</description>
        <quantity>2</quantity>
        <unitPrice><currency>$</currency><value>9.89</value></unitPrice>
        <amount><currency>$</currency><value>19.78</value></amount>
      </lineItem>

      <lineItem kind="separator"/>

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

- [Show a QR code on the terminal](./display-qr.md)
- [Show an image on the terminal](./display-image.md)
- [Show the standby screen](./display-standby.md)
- [Display payload XML schema](./display.xsd)
