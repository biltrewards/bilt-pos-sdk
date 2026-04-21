---
---

# Load a gift card balance

Add funds to an activated gift card.

A load operation increases the balance on an existing, activated gift card. The card must already be activated — to activate a new card, see [Activate a gift card](./gift-card-activate.md). The terminal handles the card interaction and sends the load request to the stored value provider for processing.

> **Note:** These docs use "gift card" terminology, but all operations apply to any stored value card type — including phone cards and other prepaid instruments. Set `StoredValueAccountType` to `GiftCard`, `PhoneCard`, or `Other` as appropriate.

---

## Before you begin

Make sure you have:

1. Ordered a terminal and boarded it to a store.
2. Read and understood the [Integration Guide](./integration.md).
3. [Activated the gift card](./gift-card-activate.md) — only activated cards can receive funds.

---

## Make a load request

To load funds onto a gift card, send a Terminal API request with `MessageCategory` set to `StoredValue` and `StoredValueTransactionType` set to `Load`.

1. Send a Terminal API request with the following `MessageHeader` fields:

    - **`ProtocolVersion`** — `3.0`
    - **`MessageClass`** — `Service`
    - **`MessageCategory`** — `StoredValue`
    - **`MessageType`** — `Request`
    - **`ServiceID`** — Unique ID, 1–10 alphanumeric characters, unique within 48 hours per terminal.
    - **`SaleID`** — Your POS system identifier.
    - **`POIID`** — Target terminal ID, format: `[model]-[serial]`.

   And the following `StoredValueRequest` fields:

    - **`SaleData.SaleTransactionID.TransactionID`** — Your reference for this load. We recommend using a unique value per transaction. This appears as the merchant reference in reports.
    - **`SaleData.SaleTransactionID.TimeStamp`** — Date and time of the request in UTC format.
    - **`StoredValueData.StoredValueTransactionType`** — `Load`
    - **`StoredValueData.ItemAmount`** — The amount of funds to add to the card.
    - **`StoredValueData.Currency`** — The transaction currency code (e.g. `USD`).
    - **`StoredValueData.StoredValueAccountID.StoredValueAccountType`** — The type of stored value card: `GiftCard`, `PhoneCard`, or `Other`.
    - **`StoredValueData.StoredValueAccountID.StoredValueProvider`** — The stored value provider (e.g. `givex`, `svs`, `valuelink`).
    - **`StoredValueData.StoredValueAccountID.IdentificationType`** — How the card is identified: `PAN` (card number), `BarCode` (barcode), `PhoneNumber` (for phone cards), or `AccountNumber` (account-based).
    - **`StoredValueData.StoredValueAccountID.EntryMode`** — How the card is identified: `Scanned`, `MagStripe`, `Keyed`, or `Contactless`.
    - **`StoredValueData.StoredValueAccountID.StoredValueID`** *(conditional)* — The card number or barcode. Required when `EntryMode` is `Scanned` or `Keyed`.
    - **`StoredValueData.StoredValueAccountID.ExpiryDate`** *(optional)* — Card expiration date, if available.

   Example request:

   ```json
   {
     "SaleToPOIRequest": {
       "MessageHeader": {
         "ProtocolVersion": "3.0",
         "MessageClass": "Service",
         "MessageCategory": "StoredValue",
         "MessageType": "Request",
         "ServiceID": "SVC-01030",
         "SaleID": "BiltPOS-Lane3",
         "POIID": "VictaLane-275839164"
       },
       "StoredValueRequest": {
         "SaleData": {
           "SaleTransactionID": {
             "TransactionID": "SVLOAD-20260410-00005",
             "TimeStamp": "2026-04-10T13:45:00+00:00"
           }
         },
         "StoredValueData": [{
           "StoredValueTransactionType": "Load",
           "ItemAmount": 25.00,
           "Currency": "USD",
           "StoredValueAccountID": {
             "StoredValueAccountType": "GiftCard",
             "StoredValueProvider": "givex",
             "IdentificationType": "PAN",
             "EntryMode": "Scanned",
             "StoredValueID": "6006491260550218157",
             "ExpiryDate": "1228"
           }
         }]
       }
     }
   }
   ```

2. The request is routed to the terminal. If `EntryMode` is `MagStripe`, the terminal prompts the shopper to swipe the card.

3. The terminal sends the load request to the stored value provider and your integration receives the result.

---

## Load response

The result is returned in the API response in a `StoredValueResponse` body. The main result is in `StoredValueResponse.Response.Result`.

### Successful load

When a load succeeds, your integration receives:

- **`StoredValueResponse.Response.Result`** — `Success`.
- **`POIData.POITransactionID.TransactionID`** — the transaction identifier for this load.
- **`POIData.POITransactionID.TimeStamp`** — the timestamp of the transaction.
- **`StoredValueResult.StoredValueTransactionType`** — `Load`.
- **`StoredValueResult.ItemAmount`** — the total balance on the card after the load.
- **`StoredValueResult.Currency`** — the currency code.
- **`PaymentReceipt`** — receipt data, if available. See [Receipt format](./receipt-format.md) for details.

  Example response:

  ```json
  {
    "SaleToPOIResponse": {
      "MessageHeader": {
        "ProtocolVersion": "3.0",
        "MessageClass": "Service",
        "MessageCategory": "StoredValue",
        "MessageType": "Response",
        "ServiceID": "SVC-01030",
        "SaleID": "BiltPOS-Lane3",
        "POIID": "VictaLane-275839164"
      },
      "StoredValueResponse": {
        "Response": {
          "Result": "Success"
        },
        "POIData": {
          "POITransactionID": {
            "TransactionID": "d72e4c91-a3f8-4b62-8d19-5e6a7c9f0b23",
            "TimeStamp": "2026-04-10T13:45:07+00:00"
          }
        },
        "StoredValueResult": [{
          "StoredValueTransactionType": "Load",
          "ItemAmount": 75.00,
          "Currency": "USD",
          "StoredValueAccountStatus": {
            "CurrentBalance": 75.00
          }
        }]
      }
    }
  }
  ```

### Failed load

When a load fails, the result includes:

- **`StoredValueResponse.Response.Result`** — `Failure`.
- **`StoredValueResponse.Response.ErrorCondition`** — the reason for failure. For example, `NotAllowed` if the card has not been activated, or `DeviceOut` if the terminal could not be reached.

For general guidance on handling failed requests, see [Handle responses](./error-scenarios.md).

---

## Next steps

- [Query a gift card balance](./gift-card-balance-inquiry.md) — verify the balance after loading.
- [Make a gift card payment](./gift-card-payment.md) — process a payment using a gift card.
