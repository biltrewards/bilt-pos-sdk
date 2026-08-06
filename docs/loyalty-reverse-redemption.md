---
---

# Reverse a loyalty redemption

Undo a previous point redemption so the points return to the member's account.

A redemption reversal is used when a sale is abandoned or voided after points have already been redeemed — for example, when the customer rejects the final amount at payment. The loyalty provider re-credits the redeemed points to the member. Reversals reference the original redemption by its `POITransactionID` so the terminal can resolve the underlying loyalty provider transaction.

---

## Before you begin

Make sure you have:

1. Ordered a terminal and boarded it to a store.
2. Read and understood the [Integration Guide](./integration.md).
3. The `POITransactionID` and `TimeStamp` from the original [redemption](./loyalty-redeem-points.md) response.

> **Referenced reversal through the session API:** a `ReversalSession` void sends this request when its builder carries `redemptionPoiTransactionId` / `redemptionPoiTransactionTimestamp` (persist both from the original sale's `CheckoutResult` — `getRedemptionPoiTransactionId()`, `getRedemptionPoiTransactionTimestamp()`); `memberId` on the builder populates `LoyaltyData[].LoyaltyAccountID`. See the [integration guide](./checkout-session-integration.md#reversing-a-prior-sale-reversalsession).

---

## Make a redemption reversal request

To reverse a redemption, send a Terminal API request with `MessageCategory` set to `Loyalty` and `LoyaltyTransactionType` set to `RedemptionRefund`. Reference the original redemption via `OriginalPOITransaction`.

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
    - **`LoyaltyTransaction.LoyaltyTransactionType`** — `RedemptionRefund`.
    - **`LoyaltyTransaction.OriginalPOITransaction.POITransactionID.TransactionID`** — `POITransactionID.TransactionID` of the original redemption.
    - **`LoyaltyTransaction.OriginalPOITransaction.POITransactionID.TimeStamp`** — `POITransactionID.TimeStamp` of the original redemption.
    - **`LoyaltyData[].LoyaltyAccountID`** *(optional)* — The member's loyalty account. May be omitted — the original transaction reference is sufficient for the terminal to resolve the account.
    - **`LoyaltyData[].LoyaltyAmount`** *(optional)* — The amount to re-credit, for a partial reversal (`LoyaltyUnit` `Point`, or `Monetary` with `Currency`). Omit to reverse the full redeemed amount.

   Example request:

   ```json
   {
     "SaleToPOIRequest": {
       "MessageHeader": {
         "ProtocolVersion": "3.0",
         "MessageClass": "Service",
         "MessageCategory": "Loyalty",
         "MessageType": "Request",
         "ServiceID": "SVC-01130",
         "SaleID": "BiltPOS-Lane3",
         "POIID": "VictaLane-275839164"
       },
       "LoyaltyRequest": {
         "SaleData": {
           "SaleTransactionID": {
             "TransactionID": "REV-20260430-00846",
             "TimeStamp": "2026-04-30T15:30:00+00:00"
           }
         },
         "LoyaltyTransaction": {
           "LoyaltyTransactionType": "RedemptionRefund",
           "OriginalPOITransaction": {
             "POITransactionID": {
               "TransactionID": "POI-LYL-703",
               "TimeStamp": "2026-04-30T14:22:08+00:00"
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

2. The request is routed to the terminal. The terminal resolves the original redemption and submits the reversal to the loyalty provider.

3. The loyalty provider re-credits the points to the member's account and returns the result.

---

## Redemption reversal response

The result is returned in the API response in a `LoyaltyResponse` body. The main result is in `LoyaltyResponse.Response.Result`. The structure mirrors the [redemption response](./loyalty-redeem-points.md#redemption-response).

### Successful reversal

When the reversal succeeds, your integration receives:

- **`LoyaltyResponse.Response.Result`** — `Success`.
- **`POIData.POITransactionID.TransactionID`** — terminal reference for this reversal.
- **`LoyaltyResult[0].LoyaltyAccount.LoyaltyAccountID.LoyaltyID`** — echo of the member's loyalty ID.
- **`LoyaltyResult[0].CurrentBalance`** — the member's point balance after the re-credit.

  Example response:

  ```json
  {
    "SaleToPOIResponse": {
      "MessageHeader": {
        "ProtocolVersion": "3.0",
        "MessageClass": "Service",
        "MessageCategory": "Loyalty",
        "MessageType": "Response",
        "ServiceID": "SVC-01130",
        "SaleID": "BiltPOS-Lane3",
        "POIID": "VictaLane-275839164"
      },
      "LoyaltyResponse": {
        "Response": {
          "Result": "Success"
        },
        "SaleData": {
          "SaleTransactionID": {
            "TransactionID": "REV-20260430-00846",
            "TimeStamp": "2026-04-30T15:30:00+00:00"
          }
        },
        "POIData": {
          "POITransactionID": {
            "TransactionID": "POI-LYL-704",
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
            },
            "CurrentBalance": 1240
          }
        ]
      }
    }
  }
  ```

### Failed reversal

When the reversal fails, the result includes:

- **`LoyaltyResponse.Response.Result`** — `Failure`.
- **`LoyaltyResponse.Response.ErrorCondition`** — the reason for failure. For example, `NotFound` if the original transaction cannot be located, or `NotAllowed` if the redemption was already reversed.

  Example response:

  ```json
  {
    "SaleToPOIResponse": {
      "MessageHeader": {
        "ProtocolVersion": "3.0",
        "MessageClass": "Service",
        "MessageCategory": "Loyalty",
        "MessageType": "Response",
        "ServiceID": "SVC-01130",
        "SaleID": "BiltPOS-Lane3",
        "POIID": "VictaLane-275839164"
      },
      "LoyaltyResponse": {
        "Response": {
          "Result": "Failure",
          "ErrorCondition": "NotFound",
          "AdditionalResponse": "Original loyalty transaction POI-LYL-703 not found."
        }
      }
    }
  }
  ```

For general guidance on handling failed requests, see [Handle responses](./error-scenarios.md).

---

## Next steps

- [Reverse a rebate](./loyalty-reverse-rebate.md) — also undo any rebate committed for the same sale.
- [Reverse a points award](./loyalty-reverse-award.md) — also cancel the points earned for the original sale.
- [Query a loyalty balance](./loyalty-balance-inquiry.md) — confirm the points are back on the member's account.
