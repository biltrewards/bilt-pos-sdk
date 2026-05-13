---
---

# Award loyalty points

Credit loyalty points to a member for a completed purchase.

An award transaction submits the basket details to the loyalty provider after a payment has cleared. The provider calculates how many points the purchase earns based on the items, totals, and any active campaigns, then returns the points earned along with the member's updated point balance. Promotional messages (for example, "60 points to your next reward") may also be returned for display on the receipt or terminal.

---

## Before you begin

Make sure you have:

1. Ordered a terminal and boarded it to a store.
2. Read and understood the [Integration Guide](./integration.md).
3. [Identified the member](./loyalty-identify-member.md) — you need a `LoyaltyID` from a prior card acquisition.

---

## Make an award request

To award points, send a Terminal API request with `MessageCategory` set to `Loyalty` and `LoyaltyTransactionType` set to `Award`. Include the basket and the member's loyalty ID.

1. Send a Terminal API request with the following `MessageHeader` fields:

    - **`ProtocolVersion`** — `3.0`
    - **`MessageClass`** — `Service`
    - **`MessageCategory`** — `Loyalty`
    - **`MessageType`** — `Request`
    - **`ServiceID`** — Unique ID, 1–10 alphanumeric characters, unique within 48 hours per terminal.
    - **`SaleID`** — Your POS system identifier.
    - **`POIID`** — Target terminal ID, format: `[model]-[serial]`.

   And the following `LoyaltyRequest` fields:

    - **`SaleData.SaleTransactionID.TransactionID`** — Your reference for this transaction. We recommend using a unique value per transaction. This is the value the loyalty provider will record and the value you reference if you later [reverse the award](./loyalty-reverse-award.md).
    - **`SaleData.SaleTransactionID.TimeStamp`** — Date and time of the request in UTC format.
    - **`LoyaltyTransaction.LoyaltyTransactionType`** — `Award`.
    - **`LoyaltyTransaction.TotalAmount`** — The purchase total. Must equal the sum of `SaleItem[].ItemAmount`.
    - **`LoyaltyTransaction.Currency`** — The transaction currency code (e.g. `USD`).
    - **`LoyaltyTransaction.SaleItem[]`** — Array of line items. Each item includes:
        - **`ItemID`** — Sequential identifier for the line, starting at `1`.
        - **`ProductCode`** — Merchant SKU.
        - **`ProductLabel`** — Human-readable product description.
        - **`Quantity`** — Number of units.
        - **`UnitPrice`** — Price per unit.
        - **`ItemAmount`** — Line total (`Quantity` × `UnitPrice`).
    - **`LoyaltyData[].LoyaltyAccountID.LoyaltyID`** — The member's loyalty account ID from the prior [identification step](./loyalty-identify-member.md).
    - **`LoyaltyData[].LoyaltyAccountID.EntryMode`** — How the member was identified, echoed from the prior step (`Keyed`, `Scanned`, etc.).
    - **`LoyaltyData[].LoyaltyAccountID.IdentificationType`** — `PAN`, `BarCode`, `PhoneNumber`, or `AccountNumber`.

   Example request:

   ```json
   {
     "SaleToPOIRequest": {
       "MessageHeader": {
         "ProtocolVersion": "3.0",
         "MessageClass": "Service",
         "MessageCategory": "Loyalty",
         "MessageType": "Request",
         "ServiceID": "SVC-01110",
         "SaleID": "BiltPOS-Lane3",
         "POIID": "VictaLane-275839164"
       },
       "LoyaltyRequest": {
         "SaleData": {
           "SaleTransactionID": {
             "TransactionID": "TXN-20260430-00847",
             "TimeStamp": "2026-04-30T14:45:00+00:00"
           }
         },
         "LoyaltyTransaction": {
           "LoyaltyTransactionType": "Award",
           "TotalAmount": 64.97,
           "Currency": "USD",
           "SaleItem": [
             {
               "ItemID": 1,
               "ProductCode": "KRK-CNDL-LRG-VAN",
               "ProductLabel": "Large Vanilla Candle",
               "Quantity": 2,
               "UnitPrice": 24.99,
               "ItemAmount": 49.98
             },
             {
               "ItemID": 2,
               "ProductCode": "KRK-FRAME-5X7-BLK",
               "ProductLabel": "5x7 Black Frame",
               "Quantity": 1,
               "UnitPrice": 14.99,
               "ItemAmount": 14.99
             }
           ]
         },
         "LoyaltyData": [
           {
             "LoyaltyAccountID": {
               "EntryMode": "Keyed",
               "IdentificationType": "PAN",
               "LoyaltyID": "98234"
             }
           }
         ]
       }
     }
   }
   ```

2. The request is routed to the terminal. The terminal submits the basket to the loyalty provider.

3. The loyalty provider calculates points earned and returns the result.

---

## Award response

The result is returned in the API response in a `LoyaltyResponse` body. The main result is in `LoyaltyResponse.Response.Result`.

### Successful award

When the award succeeds, your integration receives:

- **`LoyaltyResponse.Response.Result`** — `Success`.
- **`POIData.POITransactionID.TransactionID`** — terminal-assigned reference for this loyalty transaction. Keep this — it is required for a [reversal or refund](./loyalty-reverse-award.md).
- **`POIData.POITransactionID.TimeStamp`** — timestamp of the transaction.
- **`LoyaltyResult[0].LoyaltyAccount.LoyaltyAccountID.LoyaltyID`** — echo of the member's loyalty ID.
- **`LoyaltyResult[0].LoyaltyAccount.LoyaltyBrand`** — the loyalty program (e.g. `K-Club`).
- **`LoyaltyResult[0].LoyaltyAccount.CurrentBalance`** — the member's point balance after the award.
- **`LoyaltyResult[0].LoyaltyAmount.LoyaltyUnit`** — `Point`.
- **`LoyaltyResult[0].LoyaltyAmount.AmountValue`** — points earned by this purchase.
- **`Response.AdditionalResponse`** *(optional)* — URL-encoded promotional messages (e.g. `promotionalMessage=You're+only+60+points+away+from+your+next+$10+reward!`). Use these to display campaign callouts on the receipt or terminal.

  Example response:

  ```json
  {
    "SaleToPOIResponse": {
      "MessageHeader": {
        "ProtocolVersion": "3.0",
        "MessageClass": "Service",
        "MessageCategory": "Loyalty",
        "MessageType": "Response",
        "ServiceID": "SVC-01110",
        "SaleID": "BiltPOS-Lane3",
        "POIID": "VictaLane-275839164"
      },
      "LoyaltyResponse": {
        "Response": {
          "Result": "Success",
          "AdditionalResponse": "promotionalMessage=You're+only+60+points+away+from+your+next+$10+reward!"
        },
        "SaleData": {
          "SaleTransactionID": {
            "TransactionID": "TXN-20260430-00847",
            "TimeStamp": "2026-04-30T14:45:00+00:00"
          }
        },
        "POIData": {
          "POITransactionID": {
            "TransactionID": "POI-LYL-702",
            "TimeStamp": "2026-04-30T14:45:02+00:00"
          }
        },
        "LoyaltyResult": [
          {
            "LoyaltyAccount": {
              "LoyaltyAccountID": {
                "EntryMode": "Keyed",
                "IdentificationType": "PAN",
                "IdentificationSupport": "NoCard",
                "LoyaltyID": "98234"
              },
              "LoyaltyBrand": "K-Club",
              "CurrentBalance": 1240
            },
            "LoyaltyAmount": {
              "LoyaltyUnit": "Point",
              "AmountValue": 65
            }
          }
        ]
      }
    }
  }
  ```

### Failed award

When the award fails, the result includes:

- **`LoyaltyResponse.Response.Result`** — `Failure`.
- **`LoyaltyResponse.Response.ErrorCondition`** — the reason for failure. For example, `NotFound` if the member ID is unknown, `NotAllowed` if the member is suspended, or `Refusal` if the same `TransactionID` was already awarded (the specific reason is carried in `AdditionalResponse`).

  Example response:

  ```json
  {
    "SaleToPOIResponse": {
      "MessageHeader": {
        "ProtocolVersion": "3.0",
        "MessageClass": "Service",
        "MessageCategory": "Loyalty",
        "MessageType": "Response",
        "ServiceID": "SVC-01110",
        "SaleID": "BiltPOS-Lane3",
        "POIID": "VictaLane-275839164"
      },
      "LoyaltyResponse": {
        "Response": {
          "Result": "Failure",
          "ErrorCondition": "Refusal",
          "AdditionalResponse": "Transaction TXN-20260430-00847 has already been awarded."
        }
      }
    }
  }
  ```

For general guidance on handling failed requests, see [Handle responses](./error-scenarios.md).

---

## Next steps

- [Reverse a points award](./loyalty-reverse-award.md) — cancel a previous award after a sale void or refund.
- [Query a loyalty balance](./loyalty-balance-inquiry.md) — fetch the member's current point balance.
- [Redeem loyalty rewards](./loyalty-redeem-rewards.md) — apply rewards earned over time.
