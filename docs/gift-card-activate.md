---
---

# Activate a gift card

Enable a new gift card and optionally load an initial balance onto it.

Activation is the first step in a gift card's lifecycle. An inactive card cannot be used for payments — activation registers the card with the stored value provider and, if an amount is specified, loads an initial balance. The terminal handles the card interaction (swipe, scan, or manual entry) and sends the activation to the provider for processing.

---

## Before you begin

Make sure you have:

1. Ordered a terminal and boarded it to a store.
2. Read and understood the [Integration Guide](./integration.md).

---

## Make an activation request

To activate a gift card, send a Terminal API request with `MessageCategory` set to `StoredValue` and `StoredValueTransactionType` set to `Activate`.

1. Send a Terminal API request with the following `MessageHeader` fields:

    - **`ProtocolVersion`** — `3.0`
    - **`MessageClass`** — `Service`
    - **`MessageCategory`** — `StoredValue`
    - **`MessageType`** — `Request`
    - **`ServiceID`** — Unique ID, 1–10 alphanumeric characters, unique within 48 hours per terminal.
    - **`SaleID`** — Your POS system identifier.
    - **`POIID`** — Target terminal ID, format: `[model]-[serial]`.

   And the following `StoredValueRequest` fields:

    - **`SaleData.SaleTransactionID.TransactionID`** — Your reference for this activation. We recommend using a unique value per transaction. This appears as the merchant reference in reports.
    - **`SaleData.SaleTransactionID.TimeStamp`** — Date and time of the request in UTC format.
    - **`StoredValueData.StoredValueTransactionType`** — `Activate`
    - **`StoredValueData.ItemAmount`** — The initial balance to load onto the card. Use `0` to activate the card without loading funds.
    - **`StoredValueData.Currency`** — The transaction currency code (e.g. `USD`).
    - **`StoredValueData.StoredValueAccountID.StoredValueAccountType`** — The type of stored value card: `GiftCard`, `PhoneCard`, or `Other`.
    - **`StoredValueData.StoredValueAccountID.StoredValueProvider`** — The stored value provider (e.g. `givex`, `svs`, `valuelink`).
    - **`StoredValueData.StoredValueAccountID.IdentificationType`** — How the card is identified: `PAN` (card number), `BarCode` (barcode), `PhoneNumber` (for phone cards), or `AccountNumber` (account-based).
    - **`StoredValueData.StoredValueAccountID.EntryMode`** — How the card is identified: `Scanned`, `MagStripe`, `Keyed`, or `Contactless`.
    - **`StoredValueData.StoredValueAccountID.StoredValueID`** *(conditional)* — The card number or barcode. Required when `EntryMode` is `Scanned` or `Keyed`; omit when `MagStripe` (the terminal reads the card).
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
         "ServiceID": "SVC-01001",
         "SaleID": "BiltPOS-Lane3",
         "POIID": "VictaLane-275839164"
       },
       "StoredValueRequest": {
         "SaleData": {
           "SaleTransactionID": {
             "TransactionID": "SVACT-20260410-00001",
             "TimeStamp": "2026-04-10T10:15:00+00:00"
           }
         },
         "StoredValueData": [{
           "StoredValueTransactionType": "Activate",
           "ItemAmount": 50.00,
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

3. The terminal sends the activation to the stored value provider and your integration receives the result.

---

## Activation response

The result is returned in the API response in a `StoredValueResponse` body. The main result is in `StoredValueResponse.Response.Result`.

### Successful activation

When an activation succeeds, your integration receives:

- **`StoredValueResponse.Response.Result`** — `Success`.
- **`POIData.POITransactionID.TransactionID`** — the transaction identifier for this activation.
- **`POIData.POITransactionID.TimeStamp`** — the timestamp of the transaction.
- **`StoredValueResult.StoredValueTransactionType`** — `Activate`.
- **`StoredValueResult.ItemAmount`** — the balance on the card after activation.
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
        "ServiceID": "SVC-01001",
        "SaleID": "BiltPOS-Lane3",
        "POIID": "VictaLane-275839164"
      },
      "StoredValueResponse": {
        "Response": {
          "Result": "Success"
        },
        "POIData": {
          "POITransactionID": {
            "TransactionID": "8fba3e01-cc29-4d17-a4e1-912c1a3d7f09",
            "TimeStamp": "2026-04-10T10:15:08+00:00"
          }
        },
        "StoredValueResult": [{
          "StoredValueTransactionType": "Activate",
          "ItemAmount": 50.00,
          "Currency": "USD",
          "StoredValueAccountStatus": {
            "CurrentBalance": 50.00
          }
        }]
      }
    }
  }
  ```

### Failed activation

When an activation fails, the result includes:

- **`StoredValueResponse.Response.Result`** — `Failure`.
- **`StoredValueResponse.Response.ErrorCondition`** — the reason for failure. For example, `Refusal` if the provider rejected the activation, or `DeviceOut` if the terminal could not be reached.

  Example response:

  ```json
  {
    "SaleToPOIResponse": {
      "MessageHeader": {
        "ProtocolVersion": "3.0",
        "MessageClass": "Service",
        "MessageCategory": "StoredValue",
        "MessageType": "Response",
        "ServiceID": "SVC-01001",
        "SaleID": "BiltPOS-Lane3",
        "POIID": "VictaLane-275839164"
      },
      "StoredValueResponse": {
        "Response": {
          "Result": "Failure",
          "ErrorCondition": "Refusal",
          "AdditionalResponse": "Card already activated."
        }
      }
    }
  }
  ```

For general guidance on handling failed requests, see [Handle responses](./error-scenarios.md).

---

## Next steps

- [Load a gift card balance](./gift-card-load.md) — add funds to an activated gift card.
- [Make a gift card payment](./gift-card-payment.md) — process a payment using a gift card.
- [Query a gift card balance](./gift-card-balance-inquiry.md) — check the remaining balance on a gift card.
- [Deactivate a gift card](./gift-card-deactivate.md) — disable a gift card.
