---
---

# Redeem loyalty points

Debit points from a member's balance and apply their monetary value as a discount on the current sale.

A redemption debits the member's loyalty account. The point-to-money conversion happens in the loyalty provider: it converts the redeemed points to a monetary value and returns that value in the response, so the register simply subtracts the returned amount — it never does the conversion itself.

The amount to redeem can be decided on either side:

- **Register-driven** — the register specifies the amount to debit in `LoyaltyData[].LoyaltyAmount`, in points or as a monetary value.
- **Terminal-driven** — the register omits `LoyaltyAmount`, and the terminal applies the amount the customer selected on the terminal. The customer's point selection is typically collected during the preceding [rebate](./loyalty-apply-rebates.md) step, before they confirm — so this request does not prompt them again. Note that omitting `LoyaltyAmount` is legal in nexo, but the spec does not define who supplies the amount in that case; letting the terminal supply it is a convention of this terminal, not portable to other POIs.

Redemption is only available as a standalone loyalty transaction — the nexo payment message pair can carry awards and rebates, but never a redemption. A redemption is an independent loyalty transaction: it has its own `SaleTransactionID` and returns its own `POITransactionID`, separate from any [rebate](./loyalty-apply-rebates.md) or [award](./loyalty-award-points.md) in the same sale. It is committed when the response returns — if the sale is later abandoned, it must be [reversed](./loyalty-reverse-redemption.md).

---

## Before you begin

Make sure you have:

1. Ordered a terminal and boarded it to a store.
2. Read and understood the [Integration Guide](./integration.md).
3. The customer identified on the terminal, or a `LoyaltyID` from a prior [identification](./loyalty-identify-member.md).

---

## Make a redemption request

To redeem points, send a Terminal API request with `MessageCategory` set to `Loyalty` and `LoyaltyTransactionType` set to `Redemption`.

1. Send a Terminal API request with the following `MessageHeader` fields:

    - **`ProtocolVersion`** — `3.0`
    - **`MessageClass`** — `Service`
    - **`MessageCategory`** — `Loyalty`
    - **`MessageType`** — `Request`
    - **`ServiceID`** — Unique ID, 1–10 alphanumeric characters, unique within 48 hours per terminal.
    - **`SaleID`** — Your POS system identifier.
    - **`POIID`** — Target terminal ID, format: `[model]-[serial]`.

   And the following `LoyaltyRequest` fields:

    - **`SaleData.SaleTransactionID.TransactionID`** — Your reference for this redemption. Each loyalty request is an independent transaction — use a unique value, distinct from any rebate or award in the same sale.
    - **`SaleData.SaleTransactionID.TimeStamp`** — Date and time of the request in UTC format.
    - **`LoyaltyTransaction.LoyaltyTransactionType`** — `Redemption`.
    - **`LoyaltyData[].LoyaltyAccountID`** *(optional)* — The member's loyalty account. If omitted, the terminal identifies the account itself, falling back through `CardAcquisitionReference`, then `OriginalPOITransaction` with `ReuseCardDataFlag`, and finally the account the customer identified with on the terminal. When supplying it, include:
        - **`LoyaltyID`** — The member's loyalty account ID.
        - **`EntryMode`** — How the member was identified, echoed from the prior step (`Keyed`, `Scanned`, etc.).
        - **`IdentificationType`** — `PAN`, `BarCode`, `PhoneNumber`, or `AccountNumber`.
    - **`LoyaltyData[].LoyaltyAmount`** *(optional)* — The amount to debit, when the register decides it:
        - **`LoyaltyUnit`** — `Point` to debit a number of points (the default), or `Monetary` to redeem a monetary value.
        - **`Currency`** — The transaction currency code (e.g. `USD`). Required when `LoyaltyUnit` is `Monetary`.
        - **`AmountValue`** — The number of points, or the monetary value, to redeem.

      **Omit the field entirely** to let the terminal apply the customer's selection. Do not send a zero amount as a placeholder — a zero `AmountValue` requests a redemption of zero.

   Example request (register-driven, redeeming 1000 points):

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
             "TransactionID": "TXN-20260430-00846",
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
               "LoyaltyUnit": "Point",
               "AmountValue": 1000
             }
           }
         ]
       }
     }
   }
   ```

   In the terminal-driven flow, send the same request without the `LoyaltyAmount` object.

2. The request is routed to the terminal. The terminal submits the redemption to the loyalty provider.

3. The loyalty provider debits the account, converts the redeemed points to a monetary value, and returns it.

---

## Redemption response

The result is returned in the API response in a `LoyaltyResponse` body. The main result is in `LoyaltyResponse.Response.Result`.

### Successful redemption

When the redemption succeeds, your integration receives:

- **`LoyaltyResponse.Response.Result`** — `Success`.
- **`POIData.POITransactionID.TransactionID`** — terminal-assigned reference. Keep this — it is required to [reverse the redemption](./loyalty-reverse-redemption.md).
- **`POIData.POITransactionID.TimeStamp`** — timestamp of the transaction.
- **`LoyaltyResult[0].LoyaltyAccount.LoyaltyAccountID.LoyaltyID`** — echo of the member's loyalty ID.
- **`LoyaltyResult[0].LoyaltyAccount.LoyaltyBrand`** — the loyalty program (e.g. `K-Club`).
- **`LoyaltyResult[0].LoyaltyAmount.LoyaltyUnit`** — `Monetary`.
- **`LoyaltyResult[0].LoyaltyAmount.Currency`** — currency code.
- **`LoyaltyResult[0].LoyaltyAmount.AmountValue`** — the monetary value of the redeemed points. Apply this as a discount to the basket.
- **`LoyaltyResult[0].CurrentBalance`** — the member's remaining point balance after the redemption.
- **`Response.AdditionalResponse`** — the number of points debited (e.g. `pointsRedeemed=1000`). nexo has no dedicated field for points spent when the result is expressed as a monetary value, so it is carried here.

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
          "Result": "Success",
          "AdditionalResponse": "pointsRedeemed=1000"
        },
        "SaleData": {
          "SaleTransactionID": {
            "TransactionID": "TXN-20260430-00846",
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
            "CurrentBalance": 240,
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

When the redemption fails, the result includes:

- **`LoyaltyResponse.Response.Result`** — `Failure`.
- **`LoyaltyResponse.Response.ErrorCondition`** — the reason for failure. For example, `NotFound` if the member is unknown, `Refusal` if the balance is insufficient for the requested amount (the specific reason is carried in `AdditionalResponse`), or `Aborted` if the customer cancelled on the terminal.

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
          "AdditionalResponse": "Insufficient point balance: requested 1000, available 240."
        }
      }
    }
  }
  ```

For general guidance on handling failed requests, see [Handle responses](./error-scenarios.md).

---

## Complete the sale

Redemption (and any [rebate](./loyalty-apply-rebates.md)) is committed **before** payment. Compute the final amount as:

```
Final amount = TotalAmount − TotalRebate − per-item rebates − redeemed point value
```

and send the [payment](./make-payment.md) with `PaymentTransaction.TransactionConditions.LoyaltyHandling` set to `Processed`, indicating that loyalty has already been handled in standalone loyalty transactions.

If the customer rejects the final amount, reverse each committed loyalty transaction using its `POITransactionID` — see [Reverse a redemption](./loyalty-reverse-redemption.md) and [Reverse a rebate](./loyalty-reverse-rebate.md).

---

## Next steps

- [Reverse a redemption](./loyalty-reverse-redemption.md) — return the points to the member's account.
- [Apply loyalty rebates](./loyalty-apply-rebates.md) — apply coupons and offers before redeeming points.
- [Query a loyalty balance](./loyalty-balance-inquiry.md) — check the member's point balance first.
- [Award loyalty points](./loyalty-award-points.md) — credit points for the purchase after payment.
