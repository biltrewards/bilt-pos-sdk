---
---

# Redeem loyalty rewards

Apply rewards or coupons that a member has accrued to the current sale.

A redemption consumes one or more `rewardRef` values returned by a prior [member identification](./loyalty-identify-member.md). The loyalty provider marks each reward as redeemed, and the response returns the monetary value to apply as a discount to the basket. Multiple rewards and coupons can be redeemed in a single request; if some succeed and others fail, the response is marked as `Partial`.

---

## Before you begin

Make sure you have:

1. Ordered a terminal and boarded it to a store.
2. Read and understood the [Integration Guide](./integration.md).
3. [Identified the member](./loyalty-identify-member.md) and cached the `LoyaltyID` and `rewardRef` values.

---

## Make a redemption request

To redeem rewards, send a Terminal API request with `MessageCategory` set to `Loyalty` and `LoyaltyTransactionType` set to `Redemption`. Carry the list of reward references in `SaleToPOIData`.

1. Send a Terminal API request with the following `MessageHeader` fields:

    - **`ProtocolVersion`** — `3.0`
    - **`MessageClass`** — `Service`
    - **`MessageCategory`** — `Loyalty`
    - **`MessageType`** — `Request`
    - **`ServiceID`** — Unique ID, 1–10 alphanumeric characters, unique within 48 hours per terminal.
    - **`SaleID`** — Your POS system identifier.
    - **`POIID`** — Target terminal ID, format: `[model]-[serial]`.

   And the following `LoyaltyRequest` fields:

    - **`SaleData.SaleTransactionID.TransactionID`** — Your reference for this transaction. Use a unique value per transaction. Use the same value as the [award](./loyalty-award-points.md) when redemption is part of the same sale.
    - **`SaleData.SaleTransactionID.TimeStamp`** — Date and time of the request in UTC format.
    - **`LoyaltyTransaction.LoyaltyTransactionType`** — `Redemption`.
    - **`LoyaltyData[].LoyaltyAccountID.LoyaltyID`** — The member's loyalty account ID.
    - **`LoyaltyData[].LoyaltyAccountID.EntryMode`** — How the member was identified, echoed from the prior step (`Keyed`, `Scanned`, etc.).
    - **`LoyaltyData[].LoyaltyAccountID.IdentificationType`** — `PAN`, `BarCode`, `PhoneNumber`, or `AccountNumber`.
    - **`LoyaltyData[].LoyaltyAmount.LoyaltyUnit`** — `Monetary`.
    - **`LoyaltyData[].LoyaltyAmount.Currency`** — The transaction currency code (e.g. `USD`).
    - **`LoyaltyData[].LoyaltyAmount.AmountValue`** — `0.00`. The actual discount value is determined by the provider based on the rewards being redeemed.
    - **`SaleToPOIData`** — Base64-encoded JSON containing the list of `rewardRef` values to redeem. The payload has the shape `{"rewardRefs":["rwd:RWD-44021","cpn:CP-201:CT-15OFF"]}`.

   Example request:

   ```json
   {
     "SaleToPOIRequest": {
       "MessageHeader": {
         "ProtocolVersion": "3.0",
         "MessageClass": "Service",
         "MessageCategory": "Loyalty",
         "MessageType": "Request",
         "ServiceID": "SVC-01120",
         "SaleID": "BiltPOS-Lane3",
         "POIID": "VictaLane-275839164"
       },
       "LoyaltyRequest": {
         "SaleData": {
           "SaleTransactionID": {
             "TransactionID": "TXN-20260430-00847",
             "TimeStamp": "2026-04-30T14:22:00+00:00"
           }
         },
         "LoyaltyTransaction": {
           "LoyaltyTransactionType": "Redemption"
         },
         "LoyaltyData": [
           {
             "LoyaltyAccountID": {
               "EntryMode": "Keyed",
               "IdentificationType": "PAN",
               "LoyaltyID": "98234"
             },
             "LoyaltyAmount": {
               "LoyaltyUnit": "Monetary",
               "Currency": "USD",
               "AmountValue": 0.00
             }
           }
         ],
         "SaleToPOIData": "eyJyZXdhcmRSZWZzIjpbInJ3ZDpSV0QtNDQwMjEiLCJjcG46Q1AtMjAxOkNULTE1T0ZGIl19"
       }
     }
   }
   ```

   The `SaleToPOIData` value above decodes to:

   ```json
   {"rewardRefs":["rwd:RWD-44021","cpn:CP-201:CT-15OFF"]}
   ```

2. The request is routed to the terminal. The terminal submits the redemption to the loyalty provider.

3. The loyalty provider marks each reward as redeemed and returns per-item results.

---

## Redemption response

The result is returned in the API response in a `LoyaltyResponse` body. The main result is in `LoyaltyResponse.Response.Result`.

### Successful redemption

When all rewards redeem successfully, your integration receives:

- **`LoyaltyResponse.Response.Result`** — `Success`.
- **`POIData.POITransactionID.TransactionID`** — terminal-assigned reference. Keep this — it is required to [reverse the redemption](./loyalty-reverse-redemption.md).
- **`POIData.POITransactionID.TimeStamp`** — timestamp of the transaction.
- **`LoyaltyResult[0].LoyaltyAccount.LoyaltyAccountID.LoyaltyID`** — echo of the member's loyalty ID.
- **`LoyaltyResult[0].LoyaltyAccount.LoyaltyBrand`** — the loyalty program (e.g. `K-Club`).
- **`LoyaltyResult[0].LoyaltyAmount.LoyaltyUnit`** — `Monetary`.
- **`LoyaltyResult[0].LoyaltyAmount.Currency`** — currency code.
- **`LoyaltyResult[0].LoyaltyAmount.AmountValue`** — total monetary value of the redeemed rewards. Apply this as a discount to the basket.

  Example response:

  ```json
  {
    "SaleToPOIResponse": {
      "MessageHeader": {
        "ProtocolVersion": "3.0",
        "MessageClass": "Service",
        "MessageCategory": "Loyalty",
        "MessageType": "Response",
        "ServiceID": "SVC-01120",
        "SaleID": "BiltPOS-Lane3",
        "POIID": "VictaLane-275839164"
      },
      "LoyaltyResponse": {
        "Response": {
          "Result": "Success"
        },
        "SaleData": {
          "SaleTransactionID": {
            "TransactionID": "TXN-20260430-00847",
            "TimeStamp": "2026-04-30T14:22:00+00:00"
          }
        },
        "POIData": {
          "POITransactionID": {
            "TransactionID": "POI-LYL-703",
            "TimeStamp": "2026-04-30T14:22:08+00:00"
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
            "LoyaltyAmount": {
              "LoyaltyUnit": "Monetary",
              "Currency": "USD",
              "AmountValue": 10.00
            }
          }
        ]
      }
    }
  }
  ```

### Partial redemption

When some rewards redeem and others fail, the result includes:

- **`LoyaltyResponse.Response.Result`** — `Partial`.
- **`LoyaltyResponse.Response.AdditionalResponse`** — per-reward outcomes (the rewards that failed and why).
- **`LoyaltyResult[0].LoyaltyAmount.AmountValue`** — the monetary value of the rewards that did redeem.

  Example response:

  ```json
  {
    "SaleToPOIResponse": {
      "MessageHeader": {
        "ProtocolVersion": "3.0",
        "MessageClass": "Service",
        "MessageCategory": "Loyalty",
        "MessageType": "Response",
        "ServiceID": "SVC-01120",
        "SaleID": "BiltPOS-Lane3",
        "POIID": "VictaLane-275839164"
      },
      "LoyaltyResponse": {
        "Response": {
          "Result": "Partial",
          "AdditionalResponse": "failedRewards=cpn:CP-201:CT-15OFF&reason=REWARD_EXPIRED"
        },
        "SaleData": {
          "SaleTransactionID": {
            "TransactionID": "TXN-20260430-00847",
            "TimeStamp": "2026-04-30T14:22:00+00:00"
          }
        },
        "POIData": {
          "POITransactionID": {
            "TransactionID": "POI-LYL-703",
            "TimeStamp": "2026-04-30T14:22:08+00:00"
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
            "LoyaltyAmount": {
              "LoyaltyUnit": "Monetary",
              "Currency": "USD",
              "AmountValue": 10.00
            }
          }
        ]
      }
    }
  }
  ```

### Failed redemption

When no rewards redeem, the result includes:

- **`LoyaltyResponse.Response.Result`** — `Failure`.
- **`LoyaltyResponse.Response.ErrorCondition`** — the reason for failure. For example, `NotFound` if a `rewardRef` is unknown, `Refusal` if the reward is already redeemed (the specific reason is carried in `AdditionalResponse`), or `InvalidCard` if the reward has expired.

  Example response:

  ```json
  {
    "SaleToPOIResponse": {
      "MessageHeader": {
        "ProtocolVersion": "3.0",
        "MessageClass": "Service",
        "MessageCategory": "Loyalty",
        "MessageType": "Response",
        "ServiceID": "SVC-01120",
        "SaleID": "BiltPOS-Lane3",
        "POIID": "VictaLane-275839164"
      },
      "LoyaltyResponse": {
        "Response": {
          "Result": "Failure",
          "ErrorCondition": "Refusal",
          "AdditionalResponse": "Reward rwd:RWD-44021 has already been redeemed."
        }
      }
    }
  }
  ```

For general guidance on handling failed requests, see [Handle responses](./error-scenarios.md).

---

## Next steps

- [Reverse a redemption](./loyalty-reverse-redemption.md) — undo a previous redemption.
- [Award loyalty points](./loyalty-award-points.md) — credit points for the purchase.
- [Identify a loyalty member](./loyalty-identify-member.md) — fetch the latest set of rewards.
