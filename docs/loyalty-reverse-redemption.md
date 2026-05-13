---
---

# Reverse a loyalty redemption

Undo a previous reward or coupon redemption so the rewards return to the member's account.

A redemption reversal is used when a sale is voided after rewards have already been applied. The loyalty provider re-credits the specified `rewardRef` values to the member, making them available for future redemption. Reversals reference the original redemption by its `POITransactionID` so the terminal can resolve the underlying loyalty provider transaction.

---

## Before you begin

Make sure you have:

1. Ordered a terminal and boarded it to a store.
2. Read and understood the [Integration Guide](./integration.md).
3. The `POITransactionID` and `TimeStamp` from the original [redemption](./loyalty-redeem-rewards.md) response.
4. The list of `rewardRef` values to reverse.

---

## Make a redemption reversal request

To reverse a redemption, send a Terminal API request with `MessageCategory` set to `Loyalty` and `LoyaltyTransactionType` set to `RedemptionRefund`. Reference the original redemption via `OriginalPOITransaction`, and carry the list of reward references to reverse in `SaleToPOIData`.

1. Send a Terminal API request with the following `MessageHeader` fields:

    - **`ProtocolVersion`** — `3.0`
    - **`MessageClass`** — `Service`
    - **`MessageCategory`** — `Loyalty`
    - **`MessageType`** — `Request`
    - **`ServiceID`** — Unique ID, 1–10 alphanumeric characters, unique within 48 hours per terminal.
    - **`SaleID`** — Your POS system identifier.
    - **`POIID`** — Target terminal ID, format: `[model]-[serial]`.

   And the following `LoyaltyRequest` fields:

    - **`SaleData.SaleTransactionID.TransactionID`** — Your reference for this reversal. We recommend a unique value per transaction.
    - **`SaleData.SaleTransactionID.TimeStamp`** — Date and time of the request in UTC format.
    - **`LoyaltyTransaction.LoyaltyTransactionType`** — `RedemptionRefund`.
    - **`LoyaltyTransaction.OriginalPOITransaction.POITransactionID.TransactionID`** — `POITransactionID.TransactionID` of the original redemption.
    - **`LoyaltyTransaction.OriginalPOITransaction.POITransactionID.TimeStamp`** — `POITransactionID.TimeStamp` of the original redemption.
    - **`LoyaltyData[].LoyaltyAccountID.LoyaltyID`** — The member's loyalty account ID.
    - **`LoyaltyData[].LoyaltyAccountID.EntryMode`** — Echoed from the prior identification (`Keyed`, `Scanned`, etc.).
    - **`LoyaltyData[].LoyaltyAccountID.IdentificationType`** — `PAN`, `BarCode`, `PhoneNumber`, or `AccountNumber`.
    - **`SaleToPOIData`** — Base64-encoded JSON containing the list of `rewardRef` values to reverse. The payload has the shape `{"rewardRefs":["rwd:RWD-44021"]}`. May be a subset of the original redemption if you only want to reverse some rewards.

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
             "TransactionID": "REV-20260430-00847",
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
         ],
         "SaleToPOIData": "eyJyZXdhcmRSZWZzIjpbInJ3ZDpSV0QtNDQwMjEiXX0="
       }
     }
   }
   ```

   The `SaleToPOIData` value above decodes to:

   ```json
   {"rewardRefs":["rwd:RWD-44021"]}
   ```

2. The request is routed to the terminal. The terminal resolves the original redemption and submits the reversal to the loyalty provider.

3. The loyalty provider re-credits each reward to the member and returns per-item results.

---

## Redemption reversal response

The result is returned in the API response in a `LoyaltyResponse` body. The main result is in `LoyaltyResponse.Response.Result`. The structure mirrors the [redemption response](./loyalty-redeem-rewards.md#redemption-response).

### Successful reversal

When all rewards are reversed, your integration receives:

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
            "TransactionID": "REV-20260430-00847",
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
            }
          }
        ]
      }
    }
  }
  ```

### Partial reversal

When some rewards are reversed and others fail (for example, a reward was already reversed):

- **`LoyaltyResponse.Response.Result`** — `Partial`.
- **`LoyaltyResponse.Response.AdditionalResponse`** — per-reward outcomes.

### Failed reversal

When no rewards are reversed, the result includes:

- **`LoyaltyResponse.Response.Result`** — `Failure`.
- **`LoyaltyResponse.Response.ErrorCondition`** — the reason for failure. For example, `NotFound` if the original transaction or a `rewardRef` cannot be located, or `NotAllowed` if the reward was never redeemed.

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

- [Reverse a points award](./loyalty-reverse-award.md) — also cancel the points earned for the original sale.
- [Identify a loyalty member](./loyalty-identify-member.md) — confirm the rewards are back on the member's account.
