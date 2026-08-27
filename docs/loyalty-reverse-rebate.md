---
---

# Reverse a loyalty rebate

Undo a previously committed rebate so the coupons and offers return to the member's account.

A rebate reversal is used when a sale is abandoned or voided after rebates have already been committed — for example, when the customer rejects the final amount at payment. The loyalty provider cancels the rebate and re-credits any consumed coupons to the member, making them available for future use. Reversals reference the original rebate by its `POITransactionID` so the terminal can resolve the underlying loyalty provider transaction.

---

## Before you begin

Make sure you have:

1. Ordered a terminal and boarded it to a store.
2. Read and understood the [Integration Guide](./integration.md).
3. The `POITransactionID` and `TimeStamp` from the original [rebate](./loyalty-apply-rebates.md) response.

> **Referenced reversal through the session API:** `CheckoutSession.voidTransaction(OriginalSaleRecord)` sends this request when the record carries `rebatePoiTransactionId` / `rebatePoiTransactionTimestamp` (persist both from the original sale's `SettlementResult` — `getRebatePoiTransactionId()`, `getRebatePoiTransactionTimestamp()`); `memberId` on the record populates `LoyaltyData[].LoyaltyAccountID`. See the [integration guide](./checkout-session-integration.md#reversing-a-prior-sale-originalsalerecord).

---

## Make a rebate reversal request

To reverse a rebate, send a Terminal API request with `MessageCategory` set to `Loyalty` and `LoyaltyTransactionType` set to `RebateRefund`. Reference the original rebate via `OriginalPOITransaction`.

1. Send a Terminal API request with the following `MessageHeader` fields:

    - **`ProtocolVersion`** — `3.0`
    - **`MessageClass`** — `Service`
    - **`MessageCategory`** — `Loyalty`
    - **`MessageType`** — `Request`
    - **`ServiceID`** — Unique ID, 1–10 alphanumeric characters, unique within 48 hours per terminal.
    - **`SaleID`** — Your POS system identifier.
    - **`POIID`** — Target terminal ID, format: `[model]-[serial]`.

   And the following `LoyaltyRequest` fields:

    - **`SaleData.SaleTransactionID.TransactionID`** — Your reference for this reversal. Each loyalty request is an independent transaction — use a unique value.
    - **`SaleData.SaleTransactionID.TimeStamp`** — Date and time of the request in UTC format.
    - **`LoyaltyTransaction.LoyaltyTransactionType`** — `RebateRefund`.
    - **`LoyaltyTransaction.OriginalPOITransaction.POITransactionID.TransactionID`** — `POITransactionID.TransactionID` of the original rebate.
    - **`LoyaltyTransaction.OriginalPOITransaction.POITransactionID.TimeStamp`** — `POITransactionID.TimeStamp` of the original rebate.
    - **`LoyaltyData[].LoyaltyAccountID`** *(optional)* — The member's loyalty account. May be omitted — the original transaction reference is sufficient for the terminal to resolve the account.

   A rebate reversal always reverses the original rebate in full.

   Example request:

   ```json
   {
     "SaleToPOIRequest": {
       "MessageHeader": {
         "ProtocolVersion": "3.0",
         "MessageClass": "Service",
         "MessageCategory": "Loyalty",
         "MessageType": "Request",
         "ServiceID": "SVC-01150",
         "SaleID": "BiltPOS-Lane3",
         "POIID": "VictaLane-275839164"
       },
       "LoyaltyRequest": {
         "SaleData": {
           "SaleTransactionID": {
             "TransactionID": "REV-20260430-00845",
             "TimeStamp": "2026-04-30T15:30:00+00:00"
           }
         },
         "LoyaltyTransaction": {
           "LoyaltyTransactionType": "RebateRefund",
           "OriginalPOITransaction": {
             "POITransactionID": {
               "TransactionID": "POI-LYL-701",
               "TimeStamp": "2026-04-30T14:20:09+00:00"
             }
           }
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

2. The request is routed to the terminal. The terminal resolves the original rebate and submits the reversal to the loyalty provider.

3. The loyalty provider cancels the rebate, re-credits any consumed coupons, and returns the result.

---

## Rebate reversal response

The result is returned in the API response in a `LoyaltyResponse` body. The main result is in `LoyaltyResponse.Response.Result`.

### Successful reversal

When the reversal succeeds, your integration receives:

- **`LoyaltyResponse.Response.Result`** — `Success`.
- **`POIData.POITransactionID.TransactionID`** — terminal reference for this reversal.
- **`LoyaltyResult[0].LoyaltyAccount.LoyaltyAccountID.LoyaltyID`** — echo of the member's loyalty ID.

  Example response:

  ```json
  {
    "SaleToPOIResponse": {
      "MessageHeader": {
        "ProtocolVersion": "3.0",
        "MessageClass": "Service",
        "MessageCategory": "Loyalty",
        "MessageType": "Response",
        "ServiceID": "SVC-01150",
        "SaleID": "BiltPOS-Lane3",
        "POIID": "VictaLane-275839164"
      },
      "LoyaltyResponse": {
        "Response": {
          "Result": "Success"
        },
        "SaleData": {
          "SaleTransactionID": {
            "TransactionID": "REV-20260430-00845",
            "TimeStamp": "2026-04-30T15:30:00+00:00"
          }
        },
        "POIData": {
          "POITransactionID": {
            "TransactionID": "POI-LYL-705",
            "TimeStamp": "2026-04-30T15:30:02+00:00"
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
              "LoyaltyBrand": "K-Club"
            }
          }
        ]
      }
    }
  }
  ```

### Failed reversal

When the reversal fails, the result includes:

- **`LoyaltyResponse.Response.Result`** — `Failure`.
- **`LoyaltyResponse.Response.ErrorCondition`** — the reason for failure. For example, `NotFound` if the original transaction cannot be located, or `NotAllowed` if the rebate was already reversed.

  Example response:

  ```json
  {
    "SaleToPOIResponse": {
      "MessageHeader": {
        "ProtocolVersion": "3.0",
        "MessageClass": "Service",
        "MessageCategory": "Loyalty",
        "MessageType": "Response",
        "ServiceID": "SVC-01150",
        "SaleID": "BiltPOS-Lane3",
        "POIID": "VictaLane-275839164"
      },
      "LoyaltyResponse": {
        "Response": {
          "Result": "Failure",
          "ErrorCondition": "NotFound",
          "AdditionalResponse": "Original loyalty transaction POI-LYL-701 not found."
        }
      }
    }
  }
  ```

For general guidance on handling failed requests, see [Handle responses](./error-scenarios.md).

---

## Next steps

- [Reverse a redemption](./loyalty-reverse-redemption.md) — also return any redeemed points for the same sale.
- [Reverse a points award](./loyalty-reverse-award.md) — also cancel the points earned for the original sale.
- [Identify a loyalty member](./loyalty-identify-member.md) — confirm the coupons are back on the member's account.
