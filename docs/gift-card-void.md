---
---

# Void a gift card load

Reverse a recent gift card load operation using a `StoredValueRequest` with transaction type `Reverse`.

A void cancels a previous [load operation](./gift-card-load.md), removing the loaded funds from the card and returning the balance to its state before the load. This is useful when a load was made in error or needs to be corrected.

> A void can only reverse the most recent load transaction, and the original load must have been made within the past 24 hours. To void a gift card **payment**, use a standard [reversal](./reverse-payment.md) or [refund](./refund-referenced.md) instead.

---

## Before you begin

You need the transaction identifier and timestamp of the original load. These are returned in the load response as `POIData.POITransactionID.TransactionID` and `POIData.POITransactionID.TimeStamp`. Make sure your POS app stores these when a load completes.

---

## Make a void request

To void a gift card load, send a Terminal API request with `MessageCategory` set to `StoredValue` and `StoredValueTransactionType` set to `Reverse`.

1. Send a Terminal API request with the following `MessageHeader` fields:

    - **`ProtocolVersion`** — `3.0`
    - **`MessageClass`** — `Service`
    - **`MessageCategory`** — `StoredValue`
    - **`MessageType`** — `Request`
    - **`ServiceID`** — Unique ID, 1–10 alphanumeric characters, unique within 48 hours per terminal.
    - **`SaleID`** — Your POS system identifier.
    - **`POIID`** — Target terminal ID, format: `[model]-[serial]`.

   And the following `StoredValueRequest` fields:

    - **`SaleData.SaleTransactionID.TransactionID`** — Your reference for this void. We recommend using a unique value per transaction. This appears as the merchant reference in reports.
    - **`SaleData.SaleTransactionID.TimeStamp`** — Date and time of the request in UTC format.
    - **`StoredValueData.StoredValueTransactionType`** — `Reverse`
    - **`StoredValueData.OriginalPOITransaction.POITransactionID.TransactionID`** — The transaction identifier from the original load response.
    - **`StoredValueData.OriginalPOITransaction.POITransactionID.TimeStamp`** — The timestamp from the original load response.

   > **Note:** Card details are not required for a void — they are retrieved from the original transaction.

   Example request:

   ```json
   {
     "SaleToPOIRequest": {
       "MessageHeader": {
         "ProtocolVersion": "3.0",
         "MessageClass": "Service",
         "MessageCategory": "StoredValue",
         "MessageType": "Request",
         "ServiceID": "SVC-01040",
         "SaleID": "BiltPOS-Lane3",
         "POIID": "VictaLane-275839164"
       },
       "StoredValueRequest": {
         "SaleData": {
           "SaleTransactionID": {
             "TransactionID": "SVVOID-20260410-00003",
             "TimeStamp": "2026-04-10T14:10:00+00:00"
           }
         },
         "StoredValueData": [{
           "StoredValueTransactionType": "Reverse",
           "OriginalPOITransaction": {
             "POITransactionID": {
               "TransactionID": "d72e4c91-a3f8-4b62-8d19-5e6a7c9f0b23",
               "TimeStamp": "2026-04-10T13:45:07+00:00"
             }
           }
         }]
       }
     }
   }
   ```

---

## Void response

The result is returned in the API response in a `StoredValueResponse` body. The main result is in `StoredValueResponse.Response.Result`.

### Successful void

When a void succeeds, your integration receives:

- **`StoredValueResponse.Response.Result`** — `Success`.
- **`POIData.POITransactionID.TransactionID`** — the transaction identifier for this void.
- **`StoredValueResult.StoredValueTransactionType`** — `Reverse`.
- **`StoredValueResult.StoredValueAccountStatus.CurrentBalance`** — the card balance after the reversal.

  Example response:

  ```json
  {
    "SaleToPOIResponse": {
      "MessageHeader": {
        "ProtocolVersion": "3.0",
        "MessageClass": "Service",
        "MessageCategory": "StoredValue",
        "MessageType": "Response",
        "ServiceID": "SVC-01040",
        "SaleID": "BiltPOS-Lane3",
        "POIID": "VictaLane-275839164"
      },
      "StoredValueResponse": {
        "Response": {
          "Result": "Success"
        },
        "POIData": {
          "POITransactionID": {
            "TransactionID": "e83f5da2-b4c9-4c73-9e20-6f7b8d0a1c34",
            "TimeStamp": "2026-04-10T14:10:05+00:00"
          }
        },
        "StoredValueResult": [{
          "StoredValueTransactionType": "Reverse",
          "StoredValueAccountStatus": {
            "CurrentBalance": 50.00
          }
        }]
      }
    }
  }
  ```

### Failed void

When a void fails, the result includes:

- **`StoredValueResponse.Response.Result`** — `Failure`.
- **`StoredValueResponse.Response.ErrorCondition`** — the reason for failure. For example, `NotAllowed` if the original transaction was made more than 24 hours ago or is not the most recent load.

For general guidance on handling failed requests, see [Handle responses](./error-scenarios.md).

---

## Next steps

- [Load a gift card balance](./gift-card-load.md) — add funds to a gift card.
- [Query a gift card balance](./gift-card-balance-inquiry.md) — verify the balance after a void.
- [Cancel, reverse, or refund a payment](./undo-payment.md) — options for undoing a standard card payment.
