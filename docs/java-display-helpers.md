---
---

# Java SDK — Display and Input Payload Helpers

Build display and input payloads using type-safe Java helpers instead of hand-crafting XML.

The `DisplayPayloadHelper` class provides factory methods and builders to create payloads that are automatically serialized to Base64-encoded XML for use in `OutputXHTML` fields.

---

## Installation

Add the dependency to your project:

**Gradle (Kotlin DSL):**

```kotlin
implementation("com.bilt:bilt-pos-sdk:0.5.6")
```

**Gradle (Groovy):**

```groovy
implementation 'com.bilt:bilt-pos-sdk:0.5.6'
```

**Maven:**

```xml
<dependency>
    <groupId>com.bilt</groupId>
    <artifactId>bilt-pos-sdk</artifactId>
    <version>0.5.6</version>
</dependency>
```

---

## Display payloads

### Standby screen

Return the terminal to its idle state:

```java
import com.bilt.pos.display.DisplayPayloadHelper;

// Default standby
DisplayPayload payload = DisplayPayloadHelper.standby("standby.xslt");
String encoded = DisplayPayloadHelper.toBase64(payload);

// Themed standby (e.g., holiday campaign)
DisplayPayload themed = DisplayPayloadHelper.standby("standby.xslt", "christmas");
String encodedThemed = DisplayPayloadHelper.toBase64(themed);
```

### QR code

Display a QR code for the customer to scan:

```java
// Simple QR code
DisplayPayload qr = DisplayPayloadHelper.qrCode("qr.xslt", "https://example.com/pay/12345");
String encoded = DisplayPayloadHelper.toBase64(qr);

// QR code with header and call-to-action
DisplayPayload qrWithText = DisplayPayloadHelper.qrCode(
    "qr.xslt",
    "https://example.com/pay/12345",
    "Scan to pay",           // header text
    "Point your camera here" // call-to-action
);
```

### Receipt

Build a receipt with line items, tax, and totals:

```java
import com.bilt.pos.display.*;
import java.math.BigDecimal;

DisplayPayload payload = new DisplayPayload();
payload.setVersion("1.0");
payload.setLayout("receipt.xslt");

ReceiptType receipt = new ReceiptType();
receipt.setHeader(DisplayPayloadHelper.header("Your purchase"));

// Line items
LineItemsType lineItems = new LineItemsType();
lineItems.getLineItem().add(
    DisplayPayloadHelper.lineItem(LineItemKindType.HEADING, "ITEMS")
);
lineItems.getLineItem().add(
    DisplayPayloadHelper.productItem(
        "Running shoes",
        BigDecimal.ONE,           // quantity
        "$",                      // currency
        new BigDecimal("79.99"),  // unit price
        new BigDecimal("79.99")   // total
    )
);
lineItems.getLineItem().add(
    DisplayPayloadHelper.productItem(
        "Sports socks (3-pack)",
        new BigDecimal("2"),
        "$",
        new BigDecimal("12.99"),
        new BigDecimal("25.98")
    )
);
lineItems.getLineItem().add(
    DisplayPayloadHelper.lineItem(LineItemKindType.SEPARATOR, null)
);
receipt.setLineItems(lineItems);

// Tax
TaxType tax = new TaxType();
tax.getTaxItem().add(DisplayPayloadHelper.labeledAmount("State tax", "$", 8.48));
tax.setTaxTotal(DisplayPayloadHelper.labeledAmount("Total tax", "$", 8.48));
receipt.setTax(tax);

// Total
receipt.setTotal(DisplayPayloadHelper.labeledAmount("Total", "$", 114.45));
receipt.setFooter(DisplayPayloadHelper.footer("Thank you for your purchase!"));

payload.setReceipt(receipt);

String encoded = DisplayPayloadHelper.toBase64(payload);
```

---

## Input payloads

### Confirmation (Yes/No)

Ask the user a yes/no question:

```java
InputPayload payload = DisplayPayloadHelper.confirmation("Would you like a receipt?");
String encoded = DisplayPayloadHelper.toBase64(payload);
```

Use with `InputCommand: GetConfirmation`. Response includes `ConfirmedFlag: true/false`.

### Signature capture

Prompt the user to sign on the terminal:

```java
InputPayload payload = DisplayPayloadHelper.signature("Please sign below");
String encoded = DisplayPayloadHelper.toBase64(payload);
```

Use with `InputCommand: Signature`. Response includes the signature data.

### Display-only prompts (native nexo input)

For nexo native input commands (`DigitString`, `DecimalString`, `TextString`, `GetMenuEntry`), use `display()` to create a prompt without confirmation or signature elements:

```java
// Zip code entry (DigitString)
InputPayload zipPrompt = DisplayPayloadHelper.display("Enter your zip code");

// Name entry (TextString)
InputPayload namePrompt = DisplayPayloadHelper.display("Enter your name");

// Amount entry (DecimalString)
InputPayload amountPrompt = DisplayPayloadHelper.display("Enter tip amount");

// Menu selection (GetMenuEntry)
InputPayload menuPrompt = DisplayPayloadHelper.display("Select a tip amount");

// With additional text lines
InputPayload detailedPrompt = DisplayPayloadHelper.display(
    "Enter your phone number",
    "We'll send order updates via SMS",
    "Standard rates may apply"
);

String encoded = DisplayPayloadHelper.toBase64(zipPrompt);
```

The input type (digits, text, menu options) is controlled by the nexo `InputCommand` field, not the XML payload.

---

## Builder helpers

Create common types used in receipts:

```java
// Money amount
MoneyType price = DisplayPayloadHelper.money("$", 29.99);
MoneyType precise = DisplayPayloadHelper.money("USD", new BigDecimal("29.99"));

// Labeled amount (for subtotals, totals, tax)
LabeledAmountType subtotal = DisplayPayloadHelper.labeledAmount("Subtotal", "$", 105.97);

// Line item
LineItemType heading = DisplayPayloadHelper.lineItem(LineItemKindType.HEADING, "ITEMS");
LineItemType separator = DisplayPayloadHelper.lineItem(LineItemKindType.SEPARATOR, null);
LineItemType discount = DisplayPayloadHelper.lineItem(LineItemKindType.DISCOUNT, "Member discount");

// Product line item (with quantity and prices)
LineItemType product = DisplayPayloadHelper.productItem(
    "Product name",
    new BigDecimal("2"),      // quantity
    "$",                      // currency
    new BigDecimal("10.00"),  // unit price
    new BigDecimal("20.00")   // line total
);

// Header and footer
HeaderFooterType header = DisplayPayloadHelper.header("Your items");
HeaderFooterType footer = DisplayPayloadHelper.footer("Thank you!");
```

### Line item kinds

| Kind | Use case |
|------|----------|
| `HEADING` | Section headers (e.g., "ITEMS", "DISCOUNTS") |
| `ITEM` | Regular product line |
| `DISCOUNT` | Discount line (typically negative amount) |
| `RETURN` | Returned item |
| `VOID` | Voided item |
| `SEPARATOR` | Visual separator line |
| `SPACER` | Empty space |

---

## Serialization

### To Base64 (for OutputXHTML)

```java
DisplayPayload payload = DisplayPayloadHelper.standby("standby.xslt");
String base64 = DisplayPayloadHelper.toBase64(payload);
// Use base64 in OutputContent.OutputXHTML
```

### To XML (for debugging)

```java
String xml = DisplayPayloadHelper.toXml(payload);
System.out.println(xml);
```

### From Base64/XML (parsing responses)

```java
// Parse display payload
DisplayPayload parsed = DisplayPayloadHelper.fromBase64(base64String);
DisplayPayload fromXml = DisplayPayloadHelper.fromXml(xmlString);

// Parse input payload
InputPayload input = DisplayPayloadHelper.inputFromBase64(base64String);
InputPayload inputFromXml = DisplayPayloadHelper.inputFromXml(xmlString);
```

---

## Kotlin usage

The Java classes work seamlessly in Kotlin:

```kotlin
import com.bilt.pos.display.DisplayPayloadHelper

// Standby
val standby = DisplayPayloadHelper.standby("standby.xslt")
val encoded = DisplayPayloadHelper.toBase64(standby)

// Confirmation
val confirm = DisplayPayloadHelper.confirmation("Proceed with payment?")

// Display-only input
val prompt = DisplayPayloadHelper.display("Enter your email")
```

---

## See also

- [Show the standby screen](./display-standby.md)
- [Show a virtual receipt](./display-receipt.md)
- [Show a QR code](./display-qr.md)
- [GetConfirmation](./input-get-confirmation.md)
- [Signature capture](./input-signature.md)
- [Display payload XML schema](./display.xsd)
- [Input payload XML schema](./input.xsd)
