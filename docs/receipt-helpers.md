---
---

# Java SDK — Receipt Parsing Helpers

Parse receipt data from Nexo payment responses using type-safe Java helpers instead of manually decoding XML.

The `Receipts` and `ReceiptHelper` classes extract and deserialize receipt XML from `PaymentReceipt[]` entries in payment, reversal, loyalty, and balance inquiry responses.

---

## Extract receipts from a payment response

The `Receipts` class provides a high-level API to extract parsed receipts directly from a `NexoTerminalAPI` response:

```java
import com.bilt.pos.receipt.Receipts;
import com.bilt.pos.receipt.ReceiptType;
import com.bilt.pos.receipt.ReceiptHelper;

NexoTerminalAPI response = client.request(paymentRequest);
Receipts receipts = Receipts.fromResponse(response);

// Customer receipt
if (receipts.hasCustomerReceipt()) {
    ReceiptType customer = receipts.customerReceipt();

    // Plain text — ready for thermal printing
    String plainText = customer.getPlainTextReceipt();

    // HTML — decoded from base64 binary
    String html = ReceiptHelper.htmlAsString(customer);

    // Structured data — individual fields
    ReceiptDataType data = customer.getReceiptData();
    String brand = data.getCardBrand();       // "VISA"
    String total = data.getTotalAmount();      // "94.50"
    String result = data.getTransactionResult(); // "APPROVED"
}

// Merchant receipt
if (receipts.hasMerchantReceipt()) {
    ReceiptType merchant = receipts.merchantReceipt();
    // Same API as customer receipt
}
```

The `Receipts` class looks for receipts in `PaymentResponse`, `ReversalResponse`, `LoyaltyResponse`, and `BalanceInquiryResponse` — whichever is present in the response.

---

## Extract from PaymentReceipt array

If you already have the `PaymentReceipt[]` array, use `fromPaymentReceipts` directly:

```java
PaymentReceipt[] paymentReceipts = response.getSaleToPOIResponse()
    .getPaymentResponse()
    .getPaymentReceipt();

Receipts receipts = Receipts.fromPaymentReceipts(paymentReceipts);
```

---

## Receipt content

Each `ReceiptType` provides three representations of the same receipt:

### Plain text

Pre-formatted text for thermal printers:

```java
String text = receipt.getPlainTextReceipt();
// Verifone
// 300 S Wacker Loop
// Chicago, IL 60606
//
// SALE
// 3/20/26    1:47PM
// TOTAL       $ 94.50
// VISA CREDIT
// ************9791
// APPROVED 0000
```

### HTML

Base64-decoded HTML for web views or email:

```java
// HTML is stored as byte[] (xs:base64Binary in the schema).
// Use the convenience method to get it as a String:
String html = ReceiptHelper.htmlAsString(receipt);

// Or decode manually:
byte[] htmlBytes = receipt.getHtmlReceipt();
String htmlManual = new String(htmlBytes, StandardCharsets.UTF_8);
```

### Structured data

Individual fields for custom formatting, analytics, or storage:

```java
ReceiptDataType data = receipt.getReceiptData();

// Transaction
data.getTransactionType();       // "SALE"
data.getTransactionResult();     // "APPROVED"
data.getTransactionResultCode(); // "0000"
data.getTransactionTimeStamp();  // "2026-03-20T13:47:00"

// Merchant
data.getMerchantName();          // "Verifone"
data.getMerchantID();            // "****0002"

// Amounts
data.getCurrency();              // "USD"
data.getTotalAmount();           // "94.50"
data.getTaxAmount();             // "0.00"
data.getTipsAmount();            // "0.00"

// Card
data.getCardBrand();             // "VISA"
data.getMaskedPAN();             // "************9791"
data.getPaymentInstrument();     // "Contactless"

// Authorization
data.getAuthCode();              // "789DE"
data.getRefNumber();             // "201347000008"

// EMV
data.getAid();                   // "A0000000031010"
data.getTvr();                   // "0000000000"
data.getAcValue();               // "FD68D343F5BE92CC"
```

For the full list of fields, see [Receipt format — receiptData fields](./receipt-format.html#receiptdata-fields).

---

## Serialization

### From Base64 / XML

Parse a receipt from an `OutputXHTML` value or raw XML:

```java
// From Base64 (as found in OutputContent.OutputXHTML)
ReceiptType receipt = ReceiptHelper.fromBase64(outputXhtmlValue);

// From XML string
ReceiptType receipt = ReceiptHelper.fromXml(xmlString);
```

### To Base64 / XML

Serialize a receipt (useful for testing or building mock responses):

```java
ReceiptType receipt = new ReceiptType();
receipt.setType(ReceiptCopyEnum.CUSTOMER);
receipt.setVersion("1.0");
receipt.setPlainTextReceipt("SALE\nTOTAL $10.00");

ReceiptDataType data = new ReceiptDataType();
data.setCardBrand("VISA");
data.setTotalAmount("10.00");
data.setTransactionResult("APPROVED");
receipt.setReceiptData(data);

String base64 = ReceiptHelper.toBase64(receipt);
String xml = ReceiptHelper.toXml(receipt);
```

---

## Kotlin usage

The Java classes work seamlessly in Kotlin:

```kotlin
import com.bilt.pos.receipt.Receipts
import com.bilt.pos.receipt.ReceiptHelper

val response: NexoTerminalAPI = client.request(paymentRequest)
val receipts = Receipts.fromResponse(response)

receipts.customerReceipt()?.let { customer ->
    val plainText = customer.plainTextReceipt
    val html = ReceiptHelper.htmlAsString(customer)
    val brand = customer.receiptData?.cardBrand
}
```

---

## See also

- [Receipt format](./receipt-format.html) — receipt XML structure and field reference
- [Make a payment](./make-payment.html) — payment request/response flow
- [Display Helpers](./display-helpers.html) — building display and input payloads
