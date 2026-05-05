---
---

# Receipt format

The `PaymentReceipt` array in payment and reversal responses contains receipt data in a structured XML format. Each receipt entry wraps up to three representations of the same receipt, so your POS app can choose the format that best fits its printing or display capabilities.

---

## Receipt XML structure

Each `PaymentReceipt[].OutputContent.OutputXHTML` value is an XML document conforming to [receipt.xsd](./receipt.xsd). A single document can contain any combination of these elements:

| Element             | Description                                                        |
|---------------------|--------------------------------------------------------------------|
| `htmlReceipt`       | Base64-encoded HTML receipt, ready for rendering in a web view.    |
| `plainTextReceipt`  | Pre-formatted plain-text receipt, ready for a thermal printer.     |
| `receiptData`       | Structured key-value fields (merchant, amounts, card, EMV, etc.).  |

The root `<receipt>` element carries two attributes:

- **`type`** — `CUSTOMER` or `MERCHANT`.
- **`version`** — Schema version, currently `1.0`.

---

## Example

A payment response includes two receipts — one for the merchant and one for the customer:

```json
"PaymentReceipt": [
  {
    "DocumentQualifier": "SaleReceipt",
    "OutputContent": {
      "OutputFormat": "XHTML",
      "OutputXHTML": "<?xml version=\"1.0\" ...>..."
    }
  },
  {
    "DocumentQualifier": "CustomerReceipt",
    "OutputContent": {
      "OutputFormat": "XHTML",
      "OutputXHTML": "<?xml version=\"1.0\" ...>..."
    }
  }
]
```

When decoded, each `OutputXHTML` value looks like this:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<r:receipt xmlns:r="urn:bilt:receipt:v1"
    type="CUSTOMER"
    version="1.0">

    <r:htmlReceipt>PGh0bWw+PGJvZHk+Li4uPC9ib2R5PjwvaHRtbD4=</r:htmlReceipt>

    <r:plainTextReceipt>
Verifone
300 S Wacker Loop
Chicago, IL 60606

SALE
3/20/26    1:47PM
Merchant ID: ****0002
TOTAL       $ 0.01
VISA CREDIT
************9791
APPROVED 0000
    </r:plainTextReceipt>

    <r:receiptData>
        <r:transactionType>SALE</r:transactionType>
        <r:transactionResult>APPROVED</r:transactionResult>
        <r:transactionResultCode>0000</r:transactionResultCode>
        <r:merchantName>Verifone</r:merchantName>
        <r:merchantID>****0002</r:merchantID>
        <r:currency>USD</r:currency>
        <r:totalAmount>0.01</r:totalAmount>
        <r:cardBrand>VISA</r:cardBrand>
        <r:maskedPAN>************9791</r:maskedPAN>
        <r:paymentInstrument>Contactless</r:paymentInstrument>
        <r:authCode>789DE</r:authCode>
        <r:refNumber>201347000008</r:refNumber>
        <r:aid>A0000000031010</r:aid>
        <r:arc>3030</r:arc>
        <r:tvr>0000000000</r:tvr>
        <r:tsi>0000</r:tsi>
        <r:acValue>FD68D343F5BE92CC</r:acValue>
    </r:receiptData>

</r:receipt>
```

---

## receiptData fields

All fields inside `<r:receiptData>` are optional. Only fields with values are included.

### Receipt metadata

| Field                     | Description                                      |
|---------------------------|--------------------------------------------------|
| `receiptCopy`             | Copy type: `CUSTOMER` or `MERCHANT`.             |
| `offlineDeletedReceipt`   | Whether this is an offline-deleted receipt.       |
| `offlineDeletedTimeStamp` | Timestamp of the offline deletion.               |

### Transaction

| Field                   | Description                                      |
|-------------------------|--------------------------------------------------|
| `transactionType`       | `SALE`, `REFUND`, `REVERSAL`, etc.               |
| `transactionTimeStamp`  | Date and time of the transaction.                |
| `transactionResult`     | `APPROVED`, `DECLINED`, etc.                     |
| `transactionResultCode` | Numeric result code (e.g., `0000`).              |
| `rejectionReason`       | Reason for rejection, if applicable.             |

### Merchant

| Field                 | Description                          |
|-----------------------|--------------------------------------|
| `merchantName`        | Business name.                       |
| `merchantAddress1`–`4`| Address lines.                      |
| `merchantPhoneNumber` | Phone number.                        |
| `merchantRegNumber`   | Merchant registration number.        |
| `merchantID`          | Merchant identifier (masked).        |

### Amounts

| Field               | Description                      |
|---------------------|----------------------------------|
| `currency`          | Currency code (e.g., `USD`).     |
| `transactionAmount` | Requested amount.                |
| `totalAmount`       | Final total.                     |
| `taxAmount`         | Tax portion.                     |
| `cashbackAmount`    | Cash back portion.               |
| `surchargeAmount`   | Surcharge amount.                |
| `tipsAmount`        | Tip amount.                      |

### Card / Payment

| Field               | Description                                 |
|---------------------|---------------------------------------------|
| `cardBrand`         | Card network (e.g., `VISA`, `MASTERCARD`).  |
| `maskedPAN`         | Masked card number.                         |
| `psn`               | PAN sequence number.                        |
| `expiryDate`        | Card expiry.                                |
| `paymentInstrument` | Entry method (e.g., `Contactless`, `Chip`). |
| `apmName`           | Alternative payment method name.            |
| `cvmType`           | Cardholder verification method used.        |
| `encryptedPan`      | Encrypted PAN value.                        |
| `encryptedKsn`      | Encryption key serial number.               |

### Terminal / Acquirer

| Field              | Description                    |
|--------------------|--------------------------------|
| `terminalID`       | Terminal identifier (masked).  |
| `poiSerialNumber`  | Terminal serial number.        |
| `acquirerName`     | Acquirer / processor name.     |
| `acquirerID`       | Acquirer identifier.           |

### Authorization

| Field        | Description                             |
|--------------|-----------------------------------------|
| `authCode`   | Authorization code.                     |
| `authSource` | Source of authorization (e.g., Online). |
| `refNumber`  | Reference number.                       |

### EMV tags

| Field     | Description                            |
|-----------|----------------------------------------|
| `atc`     | Application Transaction Counter.       |
| `aed`     | Application Expiry Date.               |
| `aid`     | Application Identifier.                |
| `acType`  | Application Cryptogram type.           |
| `acValue` | Application Cryptogram value.          |
| `arc`     | Authorization Response Code.           |
| `tvr`     | Terminal Verification Results.         |
| `tsi`     | Transaction Status Information.        |

### Stored value / Gift card

| Field              | Description                                              |
|--------------------|----------------------------------------------------------|
| `availableBalance` | Remaining balance on the gift card after the transaction, formatted as a decimal string (e.g. `65.00`). Only included when the stored value provider returns balance information. |

---

## Choosing a receipt format

| Format           | Best for                                          |
|------------------|---------------------------------------------------|
| `htmlReceipt`    | Rich rendering in a web view or email.            |
| `plainTextReceipt` | Direct printing on thermal or line printers.   |
| `receiptData`    | Custom receipt formatting, analytics, or storage. |

Your POS app can use one or more formats. For example, print `plainTextReceipt` on a thermal printer while storing `receiptData` fields for reporting.

---

## Schema

The full XML schema is available at [receipt.xsd](./receipt.xsd).
