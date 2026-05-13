---
---

# Reverse a loyalty points award

Cancel a previous points award so the points are deducted from the member's balance.

A points award reversal is used when the underlying sale is voided or refunded. The loyalty provider cancels the recorded transaction and removes the points that were credited. Two nexo messages map to this operation: a `LoyaltyRequest` with `LoyaltyTransactionType` set to `AwardRefund`, or a top-level `Reversal` message that references the original loyalty transaction. Either form produces the same outcome — use whichever fits your POS reversal flow.

---

## Before you begin

Make sure you have:

1. Ordered a terminal and boarded it to a store.
2. Read and understood the [Integration Guide](./integration.md).
3. The `POITransactionID` and `TimeStamp` from the original [award](./loyalty-award-points.md) response.

---

## Option A — LoyaltyRequest (AwardRefund)

Use this form when your POS treats the reversal as part of the loyalty flow (for example, when reversing rewards and the award in a single sequence).

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
    - **`LoyaltyTransaction.LoyaltyTransactionType`** — `AwardRefund`.
    - **`LoyaltyTransaction.OriginalPOITransaction.POITransactionID.TransactionID`** — `POITransactionID.TransactionID` of the original award.
    - **`LoyaltyTransaction.OriginalPOITransaction.POITransactionID.TimeStamp`** — `POITransactionID.TimeStamp` of the original award.
    - **`LoyaltyData[].LoyaltyAccountID.LoyaltyID`** — The member's loyalty account ID.
    - **`LoyaltyData[].LoyaltyAccountID.EntryMode`** — Echoed from the prior identification (`Keyed`, `Scanned`, etc.).
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
         "ServiceID": "SVC-01140",
         "SaleID": "BiltPOS-Lane3",
         "POIID": "VictaLane-275839164"
       },
       "LoyaltyRequest": {
         "SaleData": {
           "SaleTransactionID": {
             "TransactionID": "CAN-20260430-00847",
             "TimeStamp": "2026-04-30T15:20:00+00:00"
           }
         },
         "LoyaltyTransaction": {
           "LoyaltyTransactionType": "AwardRefund",
           "OriginalPOITransaction": {
             "POITransactionID": {
               "TransactionID": "POI-LYL-702",
               "TimeStamp": "2026-04-30T14:45:02+00:00"
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

2. The terminal resolves the original award and cancels it with the loyalty provider.

### Successful reversal (Option A)

Your integration receives a `LoyaltyResponse` with:

- **`Response.Result`** — `Success`.
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
        "ServiceID": "SVC-01140",
        "SaleID": "BiltPOS-Lane3",
        "POIID": "VictaLane-275839164"
      },
      "LoyaltyResponse": {
        "Response": {
          "Result": "Success"
        },
        "SaleData": {
          "SaleTransactionID": {
            "TransactionID": "CAN-20260430-00847",
            "TimeStamp": "2026-04-30T15:20:00+00:00"
          }
        },
        "POIData": {
          "POITransactionID": {
            "TransactionID": "POI-LYL-705",
            "TimeStamp": "2026-04-30T15:20:01+00:00"
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

---

## Option B — Reversal message

Use this form when your POS already issues a `Reversal` message to undo the underlying payment and wants a single message to cover the loyalty transaction as well.

1. Send a Terminal API request with the following `MessageHeader` fields:

    - **`ProtocolVersion`** — `3.0`
    - **`MessageClass`** — `Service`
    - **`MessageCategory`** — `Reversal`
    - **`MessageType`** — `Request`
    - **`ServiceID`** — Unique ID, 1–10 alphanumeric characters, unique within 48 hours per terminal.
    - **`SaleID`** — Your POS system identifier.
    - **`POIID`** — Target terminal ID, format: `[model]-[serial]`.

   And the following `ReversalRequest` fields:

    - **`OriginalPOITransaction.POITransactionID.TransactionID`** — `POITransactionID.TransactionID` of the original award.
    - **`OriginalPOITransaction.POITransactionID.TimeStamp`** — `POITransactionID.TimeStamp` of the original award.
    - **`ReversalReason`** — One of `MerchantCancel`, `CustCancel`, `Malfunction`, or `Unable2Compl`.

   Example request:

   ```json
   {
     "SaleToPOIRequest": {
       "MessageHeader": {
         "ProtocolVersion": "3.0",
         "MessageClass": "Service",
         "MessageCategory": "Reversal",
         "MessageType": "Request",
         "ServiceID": "SVC-01141",
         "SaleID": "BiltPOS-Lane3",
         "POIID": "VictaLane-275839164"
       },
       "ReversalRequest": {
         "OriginalPOITransaction": {
           "POITransactionID": {
             "TransactionID": "POI-LYL-702",
             "TimeStamp": "2026-04-30T14:45:02+00:00"
           }
         },
         "ReversalReason": "MerchantCancel"
       }
     }
   }
   ```

2. The terminal resolves the original award and cancels it with the loyalty provider.

### Successful reversal (Option B)

Your integration receives a `ReversalResponse` with:

- **`Response.Result`** — `Success`.
- **`POIData.POITransactionID.TransactionID`** — terminal reference for this reversal.

  Example response:

  ```json
  {
    "SaleToPOIResponse": {
      "MessageHeader": {
        "ProtocolVersion": "3.0",
        "MessageClass": "Service",
        "MessageCategory": "Reversal",
        "MessageType": "Response",
        "ServiceID": "SVC-01141",
        "SaleID": "BiltPOS-Lane3",
        "POIID": "VictaLane-275839164"
      },
      "ReversalResponse": {
        "Response": {
          "Result": "Success"
        },
        "POIData": {
          "POITransactionID": {
            "TransactionID": "POI-REV-706",
            "TimeStamp": "2026-04-30T15:20:01+00:00"
          }
        }
      }
    }
  }
  ```

---

## Failed reversal

For either form, a failed reversal includes:

- **`Response.Result`** — `Failure`.
- **`Response.ErrorCondition`** — the reason for failure. For example, `NotFound` if the original transaction cannot be located, or `DuplicateTransaction` if the award was already reversed.

  Example response (Option A):

  ```json
  {
    "SaleToPOIResponse": {
      "MessageHeader": {
        "ProtocolVersion": "3.0",
        "MessageClass": "Service",
        "MessageCategory": "Loyalty",
        "MessageType": "Response",
        "ServiceID": "SVC-01140",
        "SaleID": "BiltPOS-Lane3",
        "POIID": "VictaLane-275839164"
      },
      "LoyaltyResponse": {
        "Response": {
          "Result": "Failure",
          "ErrorCondition": "DuplicateTransaction",
          "AdditionalResponse": "Award POI-LYL-702 has already been reversed."
        }
      }
    }
  }
  ```

For general guidance on handling failed requests, see [Handle responses](./error-scenarios.md).

---

## Next steps

- [Reverse a redemption](./loyalty-reverse-redemption.md) — also return rewards to the member's account.
- [Query a loyalty balance](./loyalty-balance-inquiry.md) — verify the updated point balance.
