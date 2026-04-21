---
---

# Deactivate a gift card

Disable an activated gift card so it can no longer be used for payments or balance loads.

Deactivation permanently disables a gift card by unloading any remaining balance and marking the card as inactive with the stored value provider. Once deactivated, the card cannot be reactivated. This is typically used for damaged cards, return-to-stock scenarios, or fraud prevention.

> **Note:** These docs use "gift card" terminology, but all operations apply to any stored value card type — including phone cards and other prepaid instruments. Set `StoredValueAccountType` to `GiftCard`, `PhoneCard`, or `Other` as appropriate.

> **Important:** Not all stored value providers support deactivation. Check with your provider before implementing this operation.

---

## Before you begin

Make sure you have:

1. Ordered a terminal and boarded it to a store.
2. Read and understood the [Integration Guide](./integration.md).
3. Confirmed that your stored value provider supports the deactivation operation.

---

## Make a deactivation request

To deactivate a gift card, send a Terminal API request with `MessageCategory` set to `StoredValue` and `StoredValueTransactionType` set to `Unload` with `ItemAmount` set to `0`.

1. Send a Terminal API request with the following `MessageHeader` fields:

    - **`ProtocolVersion`** — `3.0`
    - **`MessageClass`** — `Service`
    - **`MessageCategory`** — `StoredValue`
    - **`MessageType`** — `Request`
    - **`ServiceID`** — Unique ID, 1–10 alphanumeric characters, unique within 48 hours per terminal.
    - **`SaleID`** — Your POS system identifier.
    - **`POIID`** — Target terminal ID, format: `[model]-[serial]`.

   And the following `StoredValueRequest` fields:

    - **`SaleData.SaleTransactionID.TransactionID`** — Your reference for this deactivation. We recommend using a unique value per transaction. This appears as the merchant reference in reports.
    - **`SaleData.SaleTransactionID.TimeStamp`** — Date and time of the request in UTC format.
    - **`StoredValueData.StoredValueTransactionType`** — `Unload`
    - **`StoredValueData.ItemAmount`** — `0` (required — setting the amount to zero signals a deactivation rather than a balance unload).
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
         "ServiceID": "SVC-01050",
         "SaleID": "BiltPOS-Lane3",
         "POIID": "VictaLane-275839164"
       },
       "StoredValueRequest": {
         "SaleData": {
           "SaleTransactionID": {
             "TransactionID": "SVDEACT-20260410-00002",
             "TimeStamp": "2026-04-10T15:00:00+00:00"
           }
         },
         "StoredValueData": [{
           "StoredValueTransactionType": "Unload",
           "ItemAmount": 0,
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

2. The request is routed to the terminal. If `EntryMode` is `MagStripe`, the terminal prompts the customer to swipe the card.

3. The terminal sends the deactivation to the stored value provider and your integration receives the result.

---

## Deactivation response

The result is returned in the API response in a `StoredValueResponse` body. The main result is in `StoredValueResponse.Response.Result`.

### Successful deactivation

When a deactivation succeeds, your integration receives:

- **`StoredValueResponse.Response.Result`** — `Success`.
- **`POIData.POITransactionID.TransactionID`** — the transaction identifier for this deactivation.
- **`StoredValueResult.StoredValueTransactionType`** — `Unload`.
- **`StoredValueResult.ItemAmount`** — `0`.
- **`StoredValueResult.StoredValueAccountStatus.CurrentBalance`** — `0`.

  Example response:

  ```json
  {
    "SaleToPOIResponse": {
      "MessageHeader": {
        "ProtocolVersion": "3.0",
        "MessageClass": "Service",
        "MessageCategory": "StoredValue",
        "MessageType": "Response",
        "ServiceID": "SVC-01050",
        "SaleID": "BiltPOS-Lane3",
        "POIID": "VictaLane-275839164"
      },
      "StoredValueResponse": {
        "Response": {
          "Result": "Success"
        },
        "POIData": {
          "POITransactionID": {
            "TransactionID": "f94a6eb3-c5d0-4d84-ae31-7a8c9e1b2d45",
            "TimeStamp": "2026-04-10T15:00:06+00:00"
          }
        },
        "StoredValueResult": [{
          "StoredValueTransactionType": "Unload",
          "ItemAmount": 0,
          "Currency": "USD",
          "StoredValueAccountStatus": {
            "CurrentBalance": 0
          }
        }]
      }
    }
  }
  ```

### Failed deactivation

When a deactivation fails, the result includes:

- **`StoredValueResponse.Response.Result`** — `Failure`.
- **`StoredValueResponse.Response.ErrorCondition`** — the reason for failure. For example, `UnavailableService` if the provider does not support deactivation, or `NotAllowed` if the card is not in a state that permits deactivation.

For general guidance on handling failed requests, see [Handle responses](./error-scenarios.md).

---

## Next steps

- [Activate a gift card](./gift-card-activate.md) — enable a new gift card.
- [Query a gift card balance](./gift-card-balance-inquiry.md) — check the balance on a gift card.
- [Handle responses](./error-scenarios.md) — general guidance on handling failed requests.
